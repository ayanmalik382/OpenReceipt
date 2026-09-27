import logging

import aiosmtplib
from email.message import EmailMessage

from .config import get_settings

log = logging.getLogger("receiptbook.mailer")
settings = get_settings()

# In-memory outbox the test suite reads from instead of a real inbox.
OUTBOX: list[dict] = []


async def send_otp(email: str, otp: str) -> None:
    if settings.environment == "test":
        OUTBOX.append({"email": email, "otp": otp})
        return

    if not settings.has_mailer:
        log.warning("[DEV ONLY] SMTP not configured. OTP for %s: %s", email, otp)
        return

    msg = EmailMessage()
    msg["From"] = settings.mail_from
    msg["To"] = email
    msg["Subject"] = "Your ReceiptBook verification code"
    msg.set_content(
        f"Your ReceiptBook code is {otp}. It expires in 10 minutes.\n"
        "If you did not request this, you can ignore this email."
    )
    await aiosmtplib.send(
        msg,
        hostname=settings.smtp_host,
        port=settings.smtp_port,
        start_tls=settings.smtp_starttls,
        username=settings.smtp_user or None,
        password=settings.smtp_pass or None,
    )
