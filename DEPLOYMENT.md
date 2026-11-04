# Deployment guide

This guide takes the shop from this repository to a live HTTPS site that takes real Razorpay payments. It
assumes one Linux server (Ubuntu 24.04 LTS, 2 vCPU and 4 GB RAM is plenty to start) running Nginx, the Spring
Boot API and MySQL. Every file mentioned here is in [deploy/](deploy/).

```
Browser ──HTTPS──> Nginx :443 ──> /            Angular files (/var/www/tshirt-shop/browser)
                              ──> /api, /media Spring Boot 127.0.0.1:8080 (systemd, prod profile)
                                                 └─> MySQL 127.0.0.1:3306, uploads /var/lib/tshirt-shop/uploads
Razorpay ──HTTPS webhook──> /api/payments/webhook
```

## 1. Before you start (owner actions)

| What | Where | Why |
|---|---|---|
| Domain name | Any registrar; point an `A` record (and `www`) at the server IP | HTTPS and the Razorpay website check need a real domain |
| Razorpay KYC and settlement bank account | Razorpay Dashboard > Account & Settings | Live keys are only issued after KYC. Money settles to this **business bank account**, never to a personal UPI ID |
| Website policies | Already in the shop: Terms, Privacy policy, Returns and refunds, Contact and About pages. Review the wording, add your delivery times and fill in your business name and address | Razorpay checks them during activation |
| SMTP account | Gmail app password, Zoho Mail, Amazon SES or Brevo | Order and password reset emails |
| Business details | Legal name, address, GSTIN (if registered), email, phone | Printed on invoices |

## 2. Prepare the server

```bash
sudo apt update && sudo apt install -y openjdk-21-jre-headless nginx mysql-server certbot
sudo useradd --system --home /opt/tshirt-shop --shell /usr/sbin/nologin tshirt
sudo mkdir -p /opt/tshirt-shop /var/lib/tshirt-shop/uploads /etc/tshirt-shop /var/www/tshirt-shop /var/www/certbot
sudo chown -R tshirt:tshirt /var/lib/tshirt-shop
sudo ufw allow OpenSSH && sudo ufw allow 'Nginx Full' && sudo ufw enable   # MySQL stays private
```

## 3. Database

```sql
-- sudo mysql
CREATE DATABASE tshirt_shop CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'tshirt_app'@'localhost' IDENTIFIED BY '<strong password>';
-- Flyway creates and changes tables on start-up, so the app user owns this one database only
GRANT ALL PRIVILEGES ON tshirt_shop.* TO 'tshirt_app'@'localhost';
CREATE USER 'tshirt_backup'@'localhost' IDENTIFIED BY '<another strong password>';
GRANT SELECT, SHOW VIEW, TRIGGER, LOCK TABLES, EVENT, PROCESS ON *.* TO 'tshirt_backup'@'localhost';
```

The `prod` profile runs only the schema migrations: reference data (categories, sizes, colours, print options,
prices, settings and the blank customizable T-Shirts used by the designer). The development sample catalog
and reviews are not loaded, so you add your products in the admin portal.

## 4. Settings and secrets

```bash
sudo cp deploy/prod.env.example /etc/tshirt-shop/prod.env
sudo chown root:root /etc/tshirt-shop/prod.env && sudo chmod 600 /etc/tshirt-shop/prod.env
sudo nano /etc/tshirt-shop/prod.env
```

- `JWT_SECRET`: `openssl rand -base64 64 | tr -d '\n'`. Use a new value, never the development one.
- `APP_FRONTEND_URL` and `APP_CORS_ALLOWED_ORIGINS`: `https://your-domain`.
- `RAZORPAY_*`: see section 8. You can start with test keys to rehearse; the log then warns "TEST mode".
- `ADMIN_EMAIL` / `ADMIN_PASSWORD`: the first admin, created on the first start. Afterwards, change the
  password in the portal and delete `ADMIN_PASSWORD` from the file.

The app **refuses to start** in production with an `http://` or localhost address, a non-Secure refresh cookie,
password reset links in the log, API docs on, or missing Razorpay keys. It **warns** at start-up about test-mode
keys, a missing webhook secret, missing email settings, empty business details and a relative upload folder.
Secret values are never logged.

## 5. Build and install

On your development machine (or a CI runner):

```bash
./deploy/build-release.sh        # tests, then release/app.jar and release/browser/
scp -r release/app.jar release/browser deploy you@server:/tmp/
```

On the server:

```bash
sudo install -o root -g tshirt -m 640 /tmp/app.jar /opt/tshirt-shop/app.jar
sudo rsync -a --delete /tmp/browser/ /var/www/tshirt-shop/browser/
sudo cp /tmp/deploy/tshirt-shop.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now tshirt-shop
journalctl -u tshirt-shop -f      # wait for "Started TShirtShopApplication"
curl -s localhost:8080/actuator/health    # {"status":"UP",...}
```

## 6. Nginx and HTTPS

First certificate (only port 80 is open at this point):

```bash
sudo tee /etc/nginx/sites-available/certbot >/dev/null <<'EOF'
server { listen 80; server_name shop.example.in www.shop.example.in;
         location /.well-known/acme-challenge/ { root /var/www/certbot; } }
EOF
sudo ln -s /etc/nginx/sites-available/certbot /etc/nginx/sites-enabled/ && sudo rm -f /etc/nginx/sites-enabled/default
sudo systemctl reload nginx
sudo certbot certonly --webroot -w /var/www/certbot -d shop.example.in -d www.shop.example.in
sudo rm /etc/nginx/sites-enabled/certbot
```

Then the shop site (replace `shop.example.in` with your domain in the file first):

```bash
sudo cp /tmp/deploy/nginx-headers.conf /etc/nginx/snippets/tshirt-shop-headers.conf
sudo cp /tmp/deploy/nginx-tshirt-shop.conf /etc/nginx/sites-available/tshirt-shop
sudo ln -s /etc/nginx/sites-available/tshirt-shop /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot renew --dry-run      # renewals run automatically from the certbot timer
```

What the Nginx config does:
- Redirects HTTP and `www` to `https://your-domain`.
- Serves the Angular files, with every app route falling back to `index.html`.
- Proxies `/api` and `/media` to the API, with rate limiting and a 12 MB upload limit.
- Sets HSTS and the other security headers.
- Hides actuator endpoints apart from `/actuator/health`.
- Caches the hashed bundles for a year but never caches `index.html`.

The Content Security Policy starts in **report-only** mode. After a full test, including a Razorpay payment,
shows no CSP messages in the browser console, change `Content-Security-Policy-Report-Only` to
`Content-Security-Policy` in the snippet and reload Nginx.

## 7. Email

Set `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` and `BUSINESS_EMAIL` (which
receives new-order and contact alerts), then `sudo systemctl restart tshirt-shop`. Test it with **Forgot
password** on your own account. For good delivery, set up SPF and DKIM for the sending domain as your mail
provider describes.

## 8. Razorpay live mode

1. Finish KYC and add the settlement bank account (Razorpay Dashboard > Account & Settings > Bank details).
2. Switch the Dashboard to **Live Mode**. Under Account & Settings > API Keys, generate a key and put the Key ID
   (`rzp_live_...`) and Key Secret in `prod.env`. The Key Secret stays on the server only; the browser only ever
   receives the Key ID.
3. Under Account & Settings > Webhooks (Live Mode), add a webhook:
   - URL: `https://your-domain/api/payments/webhook`
   - Secret: a new random value (`openssl rand -hex 32`), also stored as `RAZORPAY_WEBHOOK_SECRET`
   - Events: `payment.authorized`, `payment.captured`, `payment.failed`, `refund.processed`, `refund.failed`
4. `sudo systemctl restart tshirt-shop` and check that the start-up log has no "TEST mode" warning.
5. Place a small real order (for example a low-priced test product that you hide afterwards), pay with your own
   UPI or card, and check:
   - the order is *Confirmed*;
   - Admin > Payments shows it as captured;
   - the Razorpay Dashboard shows it;
   - the webhook delivery shows status 200 under Webhooks.
6. Refund it from Admin > Payments and check that the refund appears in the Razorpay Dashboard.
7. Go through [docs/PAYMENT_TEST_CHECKLIST.md](docs/PAYMENT_TEST_CHECKLIST.md) again if anything differs.

Payments are always captured automatically by the shop; Razorpay settles them to the bank account (normally
T+2 working days).

## 9. Backups

```bash
sudo install -m 750 /tmp/deploy/backup.sh /usr/local/bin/tshirt-shop-backup
sudo tee /etc/tshirt-shop/backup.cnf >/dev/null <<'EOF'
[client]
user=tshirt_backup
password=<backup password>
EOF
sudo chmod 600 /etc/tshirt-shop/backup.cnf
sudo /usr/local/bin/tshirt-shop-backup                  # first run by hand
echo '30 2 * * * root /usr/local/bin/tshirt-shop-backup >> /var/log/tshirt-shop-backup.log 2>&1' | sudo tee /etc/cron.d/tshirt-shop-backup
```

Each night this writes `db-<time>.sql.gz` and `uploads-<time>.tar.gz` to `/var/backups/tshirt-shop` and keeps 14
days. **Copy them off the server** as well, for example with `rclone` to Google Drive, S3 or Backblaze B2.

Restore test (do this once, on a spare database):

```bash
gunzip -c db-<time>.sql.gz | sudo mysql tshirt_shop_restore_test
sudo tar -xzf uploads-<time>.tar.gz -C /tmp/uploads-check
```

## 10. Logging and monitoring

- **Logs:** `journalctl -u tshirt-shop` (JSON, one event per line, with a request id). Logs never contain
  passwords, tokens, card data or email bodies, and email addresses are masked. Nginx logs are in
  `/var/log/nginx/`.
- **Uptime:** point a free monitor (UptimeRobot, Better Stack, Uptime Kuma) at
  `https://your-domain/actuator/health`, which returns `{"status":"UP"}`. Also monitor the home page.
- **Daily checks in the admin portal:** Dashboard, and Payments filtered to **Needs review** (payments that
  did not match their order).
- **Worth alerting on:** log lines at `ERROR` level, "Rejected a webhook with an invalid signature", failed
  backups, disk above 80% (`df -h`), and certificate expiry (certbot renews automatically).
- **Razorpay:** Dashboard > Webhooks shows failed deliveries. The shop also reconciles open payments with
  Razorpay every 5 minutes, so a missed webhook only delays a confirmation.

## 11. Updating the shop

```bash
./deploy/build-release.sh && scp -r release/app.jar release/browser you@server:/tmp/
# on the server
sudo /usr/local/bin/tshirt-shop-backup                         # always back up first
sudo cp /opt/tshirt-shop/app.jar /opt/tshirt-shop/app.jar.previous
sudo install -o root -g tshirt -m 640 /tmp/app.jar /opt/tshirt-shop/app.jar
sudo systemctl restart tshirt-shop && journalctl -u tshirt-shop -n 50
sudo rsync -a --delete /tmp/browser/ /var/www/tshirt-shop/browser/
```

Flyway applies new database migrations on start-up. Migrations only go forward, so to roll back a release that
changed the schema, restore the backup taken just before the update together with `app.jar.previous`. Without a
schema change, swapping the jar back is enough.

## 12. Go-live checklist

- [ ] `https://your-domain` loads with a valid certificate, and `http://` redirects to it.
- [ ] The start-up log has no "Production setting" warnings, or only ones you have accepted.
- [ ] The first admin has signed in, changed the password, and `ADMIN_PASSWORD` is removed from `prod.env`.
- [ ] Catalog, prices, shipping charge and free-shipping threshold are set in the admin portal.
- [ ] A test email (forgot password) arrives.
- [ ] A small live payment and its refund work (section 8), and the webhook delivery shows 200.
- [ ] Cash on Delivery order, cancellation and invoice PDF checked.
- [ ] The backup ran and a copy exists off the server.
- [ ] The uptime monitor is set up.
- [ ] The CSP has been switched from report-only to enforced after a clean test.
