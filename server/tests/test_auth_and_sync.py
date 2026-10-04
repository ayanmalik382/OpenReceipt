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


async def test_sync_accepts_record_with_embedded_image(client):
    token = await register_and_verify(client, "img@test.com")
    headers = {"Authorization": f"Bearer {token}"}
    big = rec("business", "bimg", 1, {"name": "Shop", "logoBase64": "A" * 200_000})  # ~200KB logo payload
    r = await client.post("/sync", json={"cursor": 0, "changes": [big]}, headers=headers)
    assert r.status_code == 200
    r = await client.post("/sync", json={"cursor": 0, "changes": []}, headers=headers)
    assert len(r.json()["changes"]) == 1


def test_neon_connection_string_is_accepted_as_pasted():
    from app.config import _to_asyncpg
    neon = "postgresql://user:pw@ep-x-pooler.c-6.us-east-2.aws.neon.tech/neondb?sslmode=require&channel_binding=require"
    assert _to_asyncpg(neon) == "postgresql+asyncpg://user:pw@ep-x-pooler.c-6.us-east-2.aws.neon.tech/neondb"
    assert _to_asyncpg("postgres://u:p@h/db?sslmode=require&application_name=rb").endswith("/db?application_name=rb")
    assert _to_asyncpg("sqlite+aiosqlite:///:memory:") == "sqlite+aiosqlite:///:memory:"


async def test_purge_permanently_removes_a_soft_deleted_record(client):
    token = await register_and_verify(client, "purge@test.com")
    headers = {"Authorization": f"Bearer {token}"}

    # Push a business, then soft-delete it (ordinary sync behaviour: still present, deleted=true).
    await client.post("/sync", json={"cursor": 0, "changes": [rec("business", "bp1", 100, {"name": "Shop"})]}, headers=headers)
    soft = rec("business", "bp1", 200, {"name": "Shop"}); soft["deleted"] = True
    await client.post("/sync", json={"cursor": 0, "changes": [soft]}, headers=headers)
    r = await client.post("/sync", json={"cursor": 0, "changes": []}, headers=headers)
    row = next(c for c in r.json()["changes"] if c["id"] == "bp1")
    assert row["deleted"] is True

    # Now permanently purge it - the row should be gone entirely, not just marked deleted.
    r = await client.request("DELETE", "/sync/business/bp1", headers=headers)
    assert r.status_code == 200

    r = await client.post("/sync", json={"cursor": 0, "changes": []}, headers=headers)
    assert not any(c["id"] == "bp1" for c in r.json()["changes"])


async def test_purge_rejects_receipts(client):
    token = await register_and_verify(client, "purge2@test.com")
    headers = {"Authorization": f"Bearer {token}"}
    r = await client.request("DELETE", "/sync/order/anything", headers=headers)
    assert r.status_code == 400
    assert r.json()["code"] == "NOT_PURGEABLE"


async def test_purge_requires_auth(client):
    r = await client.request("DELETE", "/sync/business/x")
    assert r.status_code == 401


async def test_purge_is_scoped_to_the_owning_user(client):
    token_a = await register_and_verify(client, "purgea@test.com")
    token_b = await register_and_verify(client, "purgeb@test.com")
    await client.post("/sync", json={"cursor": 0, "changes": [rec("business", "bpx", 1)]}, headers={"Authorization": f"Bearer {token_a}"})
    # User B tries to purge User A's record id - should silently no-op, not delete A's data.
    r = await client.request("DELETE", "/sync/business/bpx", headers={"Authorization": f"Bearer {token_b}"})
    assert r.status_code == 200
    r = await client.post("/sync", json={"cursor": 0, "changes": []}, headers={"Authorization": f"Bearer {token_a}"})
    assert any(c["id"] == "bpx" for c in r.json()["changes"])


# ---------------------------------------------------------------- Business directory

async def test_directory_publish_search_and_excludes_own_businesses(client):
    token_a = await register_and_verify(client, "dira@test.com")
    token_b = await register_and_verify(client, "dirb@test.com")
    ha, hb = {"Authorization": f"Bearer {token_a}"}, {"Authorization": f"Bearer {token_b}"}

    body = {"name": "Ali Traders", "city": "Lahore", "field": "food", "nature": "retail", "phone": "0300", "address": "Main Rd"}
    r = await client.put("/directory/biz-a", json=body, headers=ha)
    assert r.status_code == 200

    # User A does not see their own listing in search results.
    r = await client.get("/directory", headers=ha)
    assert r.json()["results"] == []

    # User B finds it, and it's marked verified.
    r = await client.get("/directory", headers=hb)
    results = r.json()["results"]
    assert len(results) == 1 and results[0]["name"] == "Ali Traders" and results[0]["verified"] is True


async def test_directory_filters_by_field_nature_and_city(client):
    token = await register_and_verify(client, "dirc@test.com")
    other = await register_and_verify(client, "dird@test.com")
    h_other = {"Authorization": f"Bearer {other}"}
    await client.put("/directory/b1", json={"name": "Food Co", "city": "Karachi", "field": "food", "nature": "manufacturing", "phone": "", "address": ""}, headers={"Authorization": f"Bearer {token}"})
    await client.put("/directory/b2", json={"name": "IT Co", "city": "Lahore", "field": "it", "nature": "retail", "phone": "", "address": ""}, headers={"Authorization": f"Bearer {token}"})

    r = await client.get("/directory?field=food", headers=h_other)
    assert [x["name"] for x in r.json()["results"]] == ["Food Co"]

    r = await client.get("/directory?city=Lahore", headers=h_other)
    assert [x["name"] for x in r.json()["results"]] == ["IT Co"]

    r = await client.get("/directory?q=Food", headers=h_other)
    assert [x["name"] for x in r.json()["results"]] == ["Food Co"]


async def test_directory_unpublish_removes_listing(client):
    token = await register_and_verify(client, "dire@test.com")
    other = await register_and_verify(client, "dirf@test.com")
    h, h_other = {"Authorization": f"Bearer {token}"}, {"Authorization": f"Bearer {other}"}
    await client.put("/directory/b3", json={"name": "Shop", "city": "Lahore", "field": "food", "nature": "retail", "phone": "", "address": ""}, headers=h)
    assert len((await client.get("/directory", headers=h_other)).json()["results"]) == 1
    r = await client.delete("/directory/b3", headers=h)
    assert r.status_code == 200
    assert len((await client.get("/directory", headers=h_other)).json()["results"]) == 0


async def test_directory_cannot_unpublish_someone_elses_listing(client):
    token = await register_and_verify(client, "dirg@test.com")
    other = await register_and_verify(client, "dirh@test.com")
    h, h_other = {"Authorization": f"Bearer {token}"}, {"Authorization": f"Bearer {other}"}
    await client.put("/directory/b4", json={"name": "Shop", "city": "Lahore", "field": "food", "nature": "retail", "phone": "", "address": ""}, headers=h)
    await client.delete("/directory/b4", headers=h_other)  # other user tries to delete it - should no-op
    assert len((await client.get("/directory", headers=h_other)).json()["results"]) == 1


async def test_directory_listing_removed_when_account_deleted(client):
    token = await register_and_verify(client, "diri@test.com")
    other = await register_and_verify(client, "dirj@test.com")
    h, h_other = {"Authorization": f"Bearer {token}"}, {"Authorization": f"Bearer {other}"}
    await client.put("/directory/b5", json={"name": "Shop", "city": "Lahore", "field": "food", "nature": "retail", "phone": "", "address": ""}, headers=h)
    await client.request("DELETE", "/auth/account", json={"password": "password123"}, headers=h)
    assert len((await client.get("/directory", headers=h_other)).json()["results"]) == 0


async def test_directory_requires_auth(client):
    r = await client.get("/directory")
    assert r.status_code == 401