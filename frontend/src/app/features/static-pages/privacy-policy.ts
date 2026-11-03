import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SITE } from '../../core/config/site.config';

@Component({
  selector: 'app-privacy-policy',
  imports: [RouterLink],
  styleUrl: './static-page.scss',
  template: `
    <section class="page-hero">
      <span class="eyebrow">Legal</span>
      <h1>Privacy policy</h1>
      <p>What we collect, why we collect it, and the choices you have.</p>
    </section>

    <div class="content">
      <p class="updated">Last updated: 3 November 2026</p>

      <p>This policy explains how {{ site.brandName }} ("we", "us") handles your personal data when you use this
        website, in line with India's Digital Personal Data Protection Act, 2023 and other applicable laws.</p>

      <h2>Information we collect</h2>
      <ul>
        <li><strong>Account details:</strong> your name, email address, mobile number and password (stored only as a
          secure one-way hash).</li>
        <li><strong>Order details:</strong> delivery addresses, items, sizes, colours and order history.</li>
        <li><strong>Custom designs:</strong> images and text you upload or create in the T-Shirt designer.</li>
        <li><strong>Payment information:</strong> payments are processed by Razorpay. We never see or store your full
          card number, UPI PIN or net-banking password; we keep only the payment reference and status.</li>
        <li><strong>Messages:</strong> anything you send us through the contact form, and newsletter sign-ups.</li>
        <li><strong>Technical data:</strong> IP address and browser details, used to keep the site secure and to
          prevent abuse.</li>
      </ul>

      <h2>How we use it</h2>
      <ul>
        <li>To process, print, ship and support your orders, including returns and refunds.</li>
        <li>To run your account and keep it secure.</li>
        <li>To send order updates and, if you subscribed, our newsletter. You can unsubscribe at any time.</li>
        <li>To meet legal, tax and accounting obligations.</li>
      </ul>

      <h2>Who we share it with</h2>
      <p>Only with service providers who help us run the shop, such as our payment gateway (Razorpay), courier
        partners, email delivery and hosting providers, and only as needed for those services. We do not sell your
        personal data.</p>

      <h2>Cookies</h2>
      <p>We use a secure, essential cookie to keep you signed in. It cannot be read by scripts on the page and is
        only sent to our sign-in service. We do not use advertising cookies.</p>

      <h2>How long we keep it</h2>
      <p>We keep account and order data while your account is active and for as long as tax and accounting laws
        require. Custom design files are kept so we can reprint or support your order, and you can ask us to delete
        them once the order is complete.</p>

      <h2>Your rights</h2>
      <p>You can ask to access, correct or delete your personal data, or withdraw consent for marketing. Contact us
        through the <a routerLink="/contact">Contact page</a> and we will respond within a reasonable time.</p>

      <h2>Security</h2>
      <p>All traffic is encrypted with HTTPS, passwords are hashed, and access to customer data is limited to staff
        who need it.</p>

      <h2>Changes</h2>
      <p>We may update this policy. The date at the top shows when it last changed.</p>
    </div>
  `,
})
export class PrivacyPolicy {
  protected readonly site = SITE;
}
