import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SITE } from '../../core/config/site.config';

@Component({
  selector: 'app-about',
  imports: [RouterLink],
  styleUrl: './static-page.scss',
  template: `
    <section class="page-hero">
      <span class="eyebrow">About us</span>
      <h1>Clothes you live in.</h1>
      <p>{{ site.brandName }} makes premium everyday T-Shirts, shirts and pants, and prints T-Shirts you design
        yourself.</p>
    </section>

    <div class="content">
      <h2>Our story</h2>
      <p>We started with a simple idea: the clothes you reach for every day should feel as good on the hundredth
        wear as on the first. That means heavyweight combed cotton, careful stitching and fits that work for real
        people, at prices that make sense.</p>
      <p>Then our customers asked for something more personal. Our custom T-Shirt studio lets you upload your
        artwork, add text, choose colours and print placements, and see the exact price before you order. Every
        custom piece is printed to order and checked by hand before it ships.</p>
    </div>

    <section class="values">
      <article>
        <span class="material-symbols-outlined">verified</span>
        <h3>Quality first</h3>
        <p>Combed cotton, reinforced seams and colours that stay true wash after wash.</p>
      </article>
      <article>
        <span class="material-symbols-outlined">straighten</span>
        <h3>Fits that work</h3>
        <p>Regular, slim and oversized cuts in sizes S to XXL, with waist sizes 28 to 38 for pants.</p>
      </article>
      <article>
        <span class="material-symbols-outlined">palette</span>
        <h3>Made by you</h3>
        <p>Design your own T-Shirt with a live preview and transparent pricing.</p>
      </article>
    </section>

    <div class="cta-row">
      <a routerLink="/">Shop the collection</a>
      <a class="outline" routerLink="/contact">Get in touch</a>
    </div>
  `,
})
export class About {
  protected readonly site = SITE;
}
