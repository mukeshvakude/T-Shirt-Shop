import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SITE } from '../../core/config/site.config';

@Component({
  selector: 'app-terms',
  imports: [RouterLink],
  styleUrl: './static-page.scss',
  template: `
    <section class="page-hero">
      <span class="eyebrow">Legal</span>
      <h1>Terms &amp; conditions</h1>
      <p>The rules that apply when you shop with us.</p>
    </section>

    <div class="content">
      <p class="updated">Last updated: 3 November 2026</p>

      <h2>1. About these terms</h2>
      <p>By using this website or placing an order with {{ site.brandName }}, you agree to these terms. Please read
        them together with our <a routerLink="/privacy-policy">Privacy policy</a> and
        <a routerLink="/returns-policy">Returns &amp; refunds policy</a>.</p>

      <h2>2. Your account</h2>
      <p>You must be at least 18 years old, or use the site with a parent or guardian. Keep your password private;
        you are responsible for activity on your account. Tell us straight away if you suspect misuse.</p>

      <h2>3. Products, prices and availability</h2>
      <ul>
        <li>Prices are in Indian Rupees and include applicable taxes unless stated otherwise.</li>
        <li>Colours may look slightly different on your screen than in person.</li>
        <li>All orders are subject to availability. If an item becomes unavailable after you pay, we will refund
          you in full.</li>
        <li>The final price, including customisation and shipping charges, is confirmed at checkout. If an obvious
          pricing error occurs we may cancel the order and refund any payment.</li>
      </ul>

      <h2>4. Custom T-Shirt designs</h2>
      <ul>
        <li>You confirm that you own, or have permission to use, every image, logo and text you upload.</li>
        <li>We may refuse or cancel designs that are unlawful, hateful, obscene, or that infringe trademarks or
          copyright. Any payment for a refused design is refunded.</li>
        <li>You keep the rights to your design. You allow us to use it only to produce and deliver your order.</li>
        <li>Printed colours and placement can vary slightly from the on-screen preview.</li>
      </ul>

      <h2>5. Orders and payment</h2>
      <p>An order is confirmed once payment is verified, or once a Cash on Delivery order is accepted. Online
        payments are processed securely by Razorpay. Cash on Delivery may be limited to certain order values and
        locations.</p>

      <h2>6. Shipping</h2>
      <p>Estimated delivery times are shown at checkout and are not guaranteed. Risk in the goods passes to you on
        delivery.</p>

      <h2>7. Returns and cancellations</h2>
      <p>See our <a routerLink="/returns-policy">Returns &amp; refunds policy</a>.</p>

      <h2>8. Intellectual property</h2>
      <p>The site design, text, graphics and logos belong to {{ site.brandName }} and may not be copied without
        permission.</p>

      <h2>9. Liability</h2>
      <p>To the extent permitted by law, our liability for any order is limited to the amount you paid for it. We
        are not responsible for delays or failures caused by events outside our reasonable control.</p>

      <h2>10. Governing law</h2>
      <p>These terms are governed by the laws of India. Disputes are subject to the jurisdiction of the courts where
        our business is registered.</p>

      <h2>11. Contact</h2>
      <p>Questions about these terms? Reach us through the <a routerLink="/contact">Contact page</a>.</p>
    </div>
  `,
})
export class Terms {
  protected readonly site = SITE;
}
