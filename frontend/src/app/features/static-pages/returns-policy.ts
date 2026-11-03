import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SITE } from '../../core/config/site.config';

@Component({
  selector: 'app-returns-policy',
  imports: [RouterLink],
  styleUrl: './static-page.scss',
  template: `
    <section class="page-hero">
      <span class="eyebrow">Help</span>
      <h1>Returns &amp; refunds</h1>
      <p>Simple returns on ready-made styles. Custom pieces are made just for you, so they follow different rules.</p>
    </section>

    <div class="content">
      <p class="updated">Last updated: 3 November 2026</p>

      <h2>Ready-made products</h2>
      <p>You can return or exchange T-Shirts, shirts and pants within <strong>7 days of delivery</strong> if they are
        unworn, unwashed and have their original tags and packaging.</p>
      <ul>
        <li>Exchanges for a different size or colour depend on stock at the time of your request.</li>
        <li>Items bought on final sale cannot be returned unless they arrive damaged or incorrect.</li>
      </ul>

      <h2>Custom T-Shirts</h2>
      <p>Custom T-Shirts are printed to order with your design, so we cannot accept returns for a change of mind, a
        wrong size chosen at checkout, or spelling in text you entered.</p>
      <p>If your custom T-Shirt arrives <strong>damaged, misprinted or different from your approved preview</strong>,
        contact us within <strong>48 hours of delivery</strong> with photos and we will reprint it or refund you in full.</p>

      <h2>How to start a return</h2>
      <ol>
        <li>Send us a message from the <a routerLink="/contact">Contact page</a> with your order number and the
          reason for the return.</li>
        <li>We confirm eligibility and arrange a pickup where available, or share the return address.</li>
        <li>Once the item reaches us and passes inspection, we process your refund or send the exchange.</li>
      </ol>

      <h2>Refunds</h2>
      <ul>
        <li><strong>Online payments</strong> (UPI, cards, net banking, wallets through Razorpay) are refunded to the
          original payment method within 5 to 7 business days after approval. Your bank may take a little longer to
          show the credit.</li>
        <li><strong>Cash on Delivery</strong> orders are refunded by bank transfer or UPI to details you share with us.</li>
        <li>Shipping charges are refundable only when the item was damaged, defective or incorrect.</li>
      </ul>

      <h2>Cancellations</h2>
      <p>You can cancel an order before it is processed for shipping. Custom T-Shirt orders can be cancelled until
        printing has started. Paid orders that are cancelled are refunded in full to the original payment method.</p>

      <h2>Questions</h2>
      <p>Reach us through the <a routerLink="/contact">Contact page</a>@if (site.supportEmail) { or at
        <a [href]="'mailto:' + site.supportEmail">{{ site.supportEmail }}</a>}. We are happy to help.</p>
    </div>
  `,
})
export class ReturnsPolicy {
  protected readonly site = SITE;
}
