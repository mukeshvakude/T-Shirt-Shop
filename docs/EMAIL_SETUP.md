# Email setup with Gmail

The shop already sends these emails:
- **To customers:** order confirmed, shipped, out for delivery, delivered, cancelled, refund and password reset.
- **To you:** new-order and contact-form alerts.

It only needs a mail account to send from. Gmail is free and fine to start with (about 500 emails a day).

## 1. Create a Gmail app password

1. Sign in to the Gmail account the shop will send from. A separate account such as `yourshop.orders@gmail.com`
   is a good idea.
2. Turn on **2-Step Verification**: <https://myaccount.google.com/security>.
3. Open <https://myaccount.google.com/apppasswords>, enter the name `T-Shirt Shop`, and click **Create**.
4. Google shows a 16-letter password once. Copy it **without the spaces**. This is not your Gmail password, and
   you can revoke it at any time on the same page.

## 2. Put it in `.env`

Open `C:\Users\ADMIN\T-Shirt-Shop\.env` in a text editor and set these values. Never commit or share this file.

```
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=yourshop.orders@gmail.com
MAIL_PASSWORD=abcdefghijklmnop
MAIL_FROM=yourshop.orders@gmail.com
BUSINESS_EMAIL=you@example.com
```

`MAIL_PASSWORD` is the 16-letter app password, with no spaces. `BUSINESS_EMAIL` is where new-order and
contact-form alerts go.

With Gmail, `MAIL_FROM` must be the same address as `MAIL_USERNAME` (or an alias added to that account).

## 3. Restart and test

1. Restart the backend.
2. On the website, use **Forgot password** with your own account email. The email should arrive within a minute;
   check the spam folder the first time.
3. The backend log shows `Email sent: "Reset your T-Shirt Shop password" to y***@gmail.com` (with your shop name). If it says `could not be sent`,
   check that the app password has no spaces and that 2-Step Verification is on.

## For a live shop

Gmail works, but emails from your own domain (for example `orders@yourshop.in` through Zoho Mail, Brevo or
Amazon SES) look more trustworthy and reach inboxes more reliably. Only the five `MAIL_*` values change.
