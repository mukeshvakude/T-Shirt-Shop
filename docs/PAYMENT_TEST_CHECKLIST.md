# Payment test checklist

Run through this list in **Razorpay Test Mode** (keys start with `rzp_test_`) before going live, and again after
any change to checkout, payments or orders. No real money moves in test mode.

Watch these while testing:
- Admin portal > Payments and the order page (status, payment state, timeline).
- Razorpay Dashboard (Test Mode) > Transactions > Payments and Refunds.
- Backend log: lines from `PaymentServiceImpl`, `PaymentProcessor`, `PaymentWebhookService`, `PaymentReconciliationService`.

## Test payment details (Razorpay test mode)

| Method | Success | Failure |
|---|---|---|
| UPI | `success@razorpay` | `failure@razorpay` |
| Card | Any test card listed in the Razorpay docs ("Test card details"), any future expiry, any CVV | Test cards marked as failing in the same list |
| Netbanking | Pick any bank, then **Success** on the test bank page | **Failure** on the test bank page |

Razorpay changes its test card list from time to time; use the current list from the Razorpay docs.

## Checklist

### Happy paths
- [ ] **UPI success.** Order becomes *Confirmed* and payment *Captured*. The bag is emptied, stock goes down,
      a confirmation email is sent (or logged when SMTP is not set), and the payment shows in the admin portal.
- [ ] **Card success.** Same result as UPI.
- [ ] **Netbanking success.** Same result as UPI.
- [ ] **Cash on Delivery.** Order is *Confirmed* with payment *Pending (COD)*, and no Razorpay order is created.
- [ ] **Coupon plus online payment.** The amount in the Razorpay popup equals the discounted total on the
      checkout page.

### Failures and drop-offs
- [ ] **UPI failure.** The shop shows "payment failed" with **Pay now** to retry. The order stays
      *Payment pending* and stock stays held.
- [ ] **Close the popup without paying.** Same as failure; retry with Pay now works.
- [ ] **Retry after failure** succeeds and confirms the order once (one captured payment).
- [ ] **Leave without paying.** When the payment window ends the stock hold is released; after
      `order.unpaid_cancel_hours` the order is cancelled and the coupon returned (OrderExpiryJob, every minute).

### Server-side safety
- [ ] **Price tampering.** Prices and totals are always worked out on the server, and editing a request in the
      browser cannot change the amount charged.
- [ ] **Forged verify call** (wrong `razorpay_signature`) is rejected with 400 and recorded in the payment
      history. The order is not confirmed.
- [ ] **Amount mismatch** (paid amount differs from the order) is not confirmed and is marked *Needs review*.
- [ ] **Late payment** (paid after the stock hold ended) confirms the order when the stock can be taken again,
      and is refunded automatically when an item sold out or the order was already cancelled.
- [ ] **Double payment** for one order: the second payment is refunded automatically.

### Webhooks and reconciliation
- [ ] Webhook URL `https://<your-domain>/api/payments/webhook` is registered in the Dashboard with events
      `payment.authorized`, `payment.captured`, `payment.failed`, `refund.processed`, `refund.failed`, and the
      secret is in `RAZORPAY_WEBHOOK_SECRET`.
- [ ] **Close the browser right after paying.** The webhook still confirms the order.
- [ ] **Duplicate webhook** (the same event id delivered again) is ignored: the order and payment history are
      unchanged.
- [ ] **Bad webhook signature** is rejected with 400.
- [ ] **Reconciliation.** With webhooks off, a paid order is still confirmed within about 5 minutes.

### Cancellation and refunds
- [ ] **Customer cancels a paid order** before shipping. The order is *Cancelled*, a full refund is started,
      stock is put back and the cancellation email mentions the refund.
- [ ] **Admin refund** (full amount, with a reason) from Admin > Payments. The refund shows in the Razorpay
      Dashboard, and `refund.processed` marks it complete.
- [ ] **Refund twice** is rejected ("not refundable").
- [ ] **Cancel a shipped order** is not allowed for customers.

### Invoices
- [ ] The invoice PDF for a paid order shows "Online (Razorpay)", the amounts in rupees and the seller details from
      `BUSINESS_*`.

## Going live

Repeat the happy paths with a small real payment using **live** keys (`rzp_live_`), then refund it from the
admin portal. See [../DEPLOYMENT.md](../DEPLOYMENT.md).
