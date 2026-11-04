# WhatsApp

The shop uses WhatsApp at two levels. You can stop at level 1.

| Level | What customers get | Needs | Cost |
|---|---|---|---|
| **1. No API** (works today) | A **WhatsApp us** button on the website (on an order page it mentions the order number); an opt-in for order updates at checkout and in their profile. Admins send updates with one click from the order page | `WHATSAPP_CHAT_NUMBER` in `.env` | Free |
| **2. Cloud API** (optional) | Order updates arrive **automatically** (confirmed, shipped, out for delivery, delivered, cancelled, refund), and you get a WhatsApp alert for every new order | A Meta WhatsApp Business account, approved templates, `WHATSAPP_ACCESS_TOKEN` and `WHATSAPP_PHONE_NUMBER_ID` | Meta charges per message ("utility" rate for India; see Meta's pricing page) |

Customers only get automatic updates for orders where they ticked **"Also send me order updates on WhatsApp"** at
checkout. Their choice is remembered and they can change it under **Account > Profile & security**.

## Level 1: chat button and one-click updates

1. Add the shop's WhatsApp number to `.env` (never commit it):
   ```
   WHATSAPP_CHAT_NUMBER=+91 98765 43210
   ```
2. Restart the backend. The green **WhatsApp us** button appears in the bottom right corner of the storefront
   (not on the designer and checkout pages, where it would cover buttons).
3. In **Admin > Orders**, open an order. The **WhatsApp** card has buttons such as *Send "Shipped"*. Each opens
   WhatsApp (app or WhatsApp Web) with the message already typed to the customer; you press send. The card
   shows whether the customer asked for WhatsApp updates.

The one-click messages are sent from whichever WhatsApp account you are signed in to on that device, normally
the shop number above.

## Level 2: automatic messages (Meta WhatsApp Cloud API)

### Set up the account (owner, about 30 minutes plus Meta review)

1. Go to <https://business.facebook.com>, create or open your **Meta Business** account and complete business
   verification when asked.
2. Go to <https://developers.facebook.com>, then **My Apps > Create app**, choose type **Business**, and add the
   **WhatsApp** product.
3. Under **WhatsApp > API Setup**, add a phone number for the shop. It must be able to receive an SMS or call,
   and it **cannot be in use on the WhatsApp or WhatsApp Business app** at the same time. Many shops use a new
   SIM for this.
4. Copy the **Phone number ID**. It is a long number, not the phone number itself.
5. Create a **permanent access token**:
   - Business Settings > Users > System users > Add (role Admin).
   - Assign the app and the WhatsApp account to that user.
   - Generate a token with the `whatsapp_business_messaging` and `whatsapp_business_management` permissions and
     no expiry.
   - The temporary token on the API Setup page expires after 24 hours, so don't use it.
6. Add a payment method in WhatsApp Manager so Meta can bill messages.

### Create the message templates

In **WhatsApp Manager > Message templates**, create these templates exactly:
- **Category:** Utility.
- **Language:** English. Its code `en` goes in `WHATSAPP_TEMPLATE_LANGUAGE`; if you choose English (UK) or
  English (US), set `en_GB` or `en_US` instead.
- **Body:** the text below.
- **Shop name:** replace `SHOP` with your `BUSINESS_NAME` exactly.

The shop fills the `{{1}}`, `{{2}}`… values. Meta reviews templates, usually within minutes to a day.

| Template name | Body |
|---|---|
| `order_confirmed` | `Hi {{1}}, thank you for shopping with SHOP! Your order {{2}} is confirmed. {{3}} Track it at {{4}} anytime.` |
| `order_shipped` | `Hi {{1}}, your SHOP order {{2}} has shipped with {{3}}. Track it at {{4}} anytime.` |
| `order_out_for_delivery` | `Hi {{1}}, your SHOP order {{2}} is out for delivery. {{3}} Thank you for shopping with us.` |
| `order_delivered` | `Hi {{1}}, your SHOP order {{2}} has been delivered. We hope you love it! Your invoice is at {{3}} anytime.` |
| `order_cancelled` | `Hi {{1}}, your SHOP order {{2}} has been cancelled. {{3}} Questions? Just reply to this message.` |
| `order_refund` | `Hi {{1}}, we have refunded {{3}} for your SHOP order {{2}}. Banks usually show it in 5 to 7 working days.` |
| `new_order_alert` | `New order {{1}} from {{2}}: {{3}}, {{4}}. Open the admin portal to process it.` |

Meta asks for sample values when you submit a template. Use these:

| Variable | Sample value |
|---|---|
| Name | `Neha` |
| Order number | `TS-20261104-123456` |
| `{{3}}` of `order_confirmed` | `Please keep ₹548 ready in cash at delivery.` |
| Link | `https://yourshop.in/order/TS-20261104-123456` |
| Courier | `Delhivery (tracking 1234567890)` |
| Amount | `₹548` |

### Switch it on

Add to `.env` (or `/etc/tshirt-shop/prod.env` on the server), then restart the backend:

```
WHATSAPP_ACCESS_TOKEN=<permanent token>      # secret: never commit or share it
WHATSAPP_PHONE_NUMBER_ID=<phone number id>
WHATSAPP_OWNER_NUMBER=+91 98765 43210        # optional: gets the new-order alert
WHATSAPP_TEMPLATE_LANGUAGE=en
```

Test it:
1. Place a Cash on Delivery order with **"Also send me order updates on WhatsApp"** ticked, using your own mobile.
2. You should receive `order_confirmed`, and the owner number `new_order_alert`.
3. The backend log shows `WhatsApp sent: order_confirmed to 91******3210`. On a failure it shows Meta's reason,
   for example a template that is not approved yet or wording that does not match.

While the token or phone number ID is missing, nothing is sent automatically; the log says "WhatsApp not sent
(Cloud API is not configured)". The chat button and one-click messages keep working either way.

## Privacy and rules

- WhatsApp updates go only to customers who opted in, and only about their own order.
- Automatic messages use Meta-approved templates only; the shop never sends marketing messages.
- Phone numbers are masked in logs (`91******3210`), and the access token is never logged or sent to the browser.
