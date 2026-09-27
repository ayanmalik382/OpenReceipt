import pytest


def rec(entity, id_, updated_at, extra=None):
    data = {"id": id_, "updatedAt": updated_at, **(extra or {})}
    return {"entity": entity, "id": id_, "updatedAt": updated_at, "deleted": False, "data": __import__("json").dumps(data)}


async def register_and_verify(client, email, password="password123"):
    from tests.conftest import last_otp
    r = await client.post("/auth/register", json={"email": email, "password": password})
    assert r.status_code == 200
    r = await client.post("/auth/verify", json={"email": email, "otp": last_otp()})
    assert r.status_code == 200
    return r.json()["token"]


async def test_register_validation(client):
    r = await client.post("/auth/register", json={"email": "owner@test.com", "password": "short"})
    assert r.status_code == 400 and r.json()["code"] == "WEAK_PASSWORD"


async def test_register_verify_login_flow(client):
    from tests.conftest import last_otp
    r = await client.post("/auth/register", json={"email": "Owner@Test.com", "password": "password123"})
    assert r.status_code == 200

    r = await client.post("/auth/login", json={"email": "owner@test.com", "password": "password123"})
    assert r.status_code == 403 and r.json()["code"] == "UNVERIFIED"

    r = await client.post("/auth/verify", json={"email": "owner@test.com", "otp": "000000"})
    assert r.status_code == 400 and r.json()["code"] == "INVALID_CODE"

    r = await client.post("/auth/verify", json={"email": "owner@test.com", "otp": last_otp()})
    assert r.status_code == 200 and "token" in r.json()

    r = await client.post("/auth/login", json={"email": "owner@test.com", "password": "wrong"})
    assert r.status_code == 401 and r.json()["code"] == "INVALID_CREDENTIALS"

    r = await client.post("/auth/login", json={"email": "owner@test.com", "password": "password123"})
    assert r.status_code == 200 and "token" in r.json()


async def test_sync_requires_auth(client):
    r = await client.post("/sync", json={"cursor": 0, "changes": []})
    assert r.status_code == 401


async def test_sync_push_pull_and_lww(client):
    token = await register_and_verify(client, "owner2@test.com")
    headers = {"Authorization": f"Bearer {token}"}

    r = await client.post("/sync", json={"cursor": 0, "changes": [
        rec("business", "b1", 100, {"name": "Shop"}), rec("product", "p1", 100, {"name": "Rice"}),
    ]}, headers=headers)
    assert r.status_code == 200
    body = r.json()
    assert body["changes"] == []  # no echo of what this device just sent
    cursor_a = body["cursor"]
    assert cursor_a >= 2

    r = await client.post("/sync", json={"cursor": 0, "changes": []}, headers=headers)
    assert len(r.json()["changes"]) == 2  # a "second device" starting from 0 sees everything

    # Older update is rejected (last-write-wins)
    await client.post("/sync", json={"cursor": cursor_a, "changes": [rec("product", "p1", 50, {"name": "OLD"})]}, headers=headers)
    r = await client.post("/sync", json={"cursor": 0, "changes": []}, headers=headers)
    p1 = next(c for c in r.json()["changes"] if c["id"] == "p1")
    assert __import__("json").loads(p1["data"])["name"] == "Rice"

    # Newer update is accepted
    await client.post("/sync", json={"cursor": cursor_a, "changes": [rec("product", "p1", 200, {"name": "Basmati"})]}, headers=headers)
    r = await client.post("/sync", json={"cursor": 0, "changes": []}, headers=headers)
    p1 = next(c for c in r.json()["changes"] if c["id"] == "p1")
    assert __import__("json").loads(p1["data"])["name"] == "Basmati"


async def test_sync_validation(client):
    token = await register_and_verify(client, "owner3@test.com")
    headers = {"Authorization": f"Bearer {token}"}

    r = await client.post("/sync", json={"cursor": 0, "changes": [
        {"entity": "evil", "id": "x", "updatedAt": 1, "deleted": False, "data": '{"id":"x"}'}
    ]}, headers=headers)
    assert r.status_code == 400

    bad = rec("product", "p9", 1)
    bad["id"] = "other"  # id no longer matches the id embedded in "data"
    r = await client.post("/sync", json={"cursor": 0, "changes": [bad]}, headers=headers)
    assert r.status_code == 400


async def test_sync_isolated_between_users(client):
    token_a = await register_and_verify(client, "usera@test.com")
    token_b = await register_and_verify(client, "userb@test.com")
    await client.post("/sync", json={"cursor": 0, "changes": [rec("business", "bx", 1)]}, headers={"Authorization": f"Bearer {token_a}"})
    r = await client.post("/sync", json={"cursor": 0, "changes": []}, headers={"Authorization": f"Bearer {token_b}"})
    assert r.json()["changes"] == []


async def test_password_reset(client):
    from tests.conftest import last_otp
    await register_and_verify(client, "reset@test.com")
    r = await client.post("/auth/reset", json={"email": "reset@test.com", "otp": "000000", "password": "newpassword1"})
    assert r.status_code == 400
    await client.post("/auth/otp", json={"email": "reset@test.com"})
    r = await client.post("/auth/reset", json={"email": "reset@test.com", "otp": last_otp(), "password": "newpassword1"})
    assert r.status_code == 200
    r = await client.post("/auth/login", json={"email": "reset@test.com", "password": "newpassword1"})
    assert r.status_code == 200


async def test_otp_cooldown_blocks_immediate_resend(client):
    import app.routers.auth as auth_mod
    r = await client.post("/auth/register", json={"email": "cooldown@test.com", "password": "password123"})
    assert r.status_code == 200
    try:
        auth_mod.settings.environment = "production"  # temporarily re-enable the real cooldown
        from tests.conftest import last_otp
        before = last_otp()
        await client.post("/auth/otp", json={"email": "cooldown@test.com"})
        assert last_otp() == before  # too soon - no new code was sent
    finally:
        auth_mod.settings.environment = "test"


async def test_delete_account_cascades(client):
    token = await register_and_verify(client, "delete@test.com")
    headers = {"Authorization": f"Bearer {token}"}
    await client.post("/sync", json={"cursor": 0, "changes": [rec("business", "bd", 1)]}, headers=headers)

    r = await client.request("DELETE", "/auth/account", json={"password": "wrong"}, headers=headers)
    assert r.status_code == 401

    r = await client.request("DELETE", "/auth/account", json={"password": "password123"}, headers=headers)
    assert r.status_code == 200

    r = await client.post("/sync", json={"cursor": 0, "changes": []}, headers=headers)
    assert r.status_code == 401  # token now refers to a deleted user
