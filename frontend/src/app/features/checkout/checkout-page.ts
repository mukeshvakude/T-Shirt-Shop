import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AccountApi } from '../../core/api/account.api';
import { apiErrorCode, apiErrorMessage } from '../../core/api/api-error';
import { CheckoutApi } from '../../core/api/checkout.api';
import { CartService } from '../../core/cart/cart.service';
import { landmarkText } from '../../core/models/account.models';
import { codeLabel } from '../../core/models/design.models';
import { CheckoutSummary, PaymentMethod, PlaceOrderRequest } from '../../core/models/order.models';
import { NotifyService } from '../../core/notify.service';
import { PaymentOutcome, RazorpayCheckoutService } from '../../core/payment/razorpay-checkout.service';
import { ImgFallback } from '../../shared/img-fallback.directive';
import { InrPipe } from '../../shared/inr.pipe';

const MOBILE = /^[6-9][0-9]{9}$/;
const PINCODE = /^[1-9][0-9]{5}$/;
/** Errors after which the summary is reloaded so the customer sees the current bag and totals. */
const RELOAD_ON = new Set(['PRICE_CHANGED', 'CART_NOT_READY', 'OUT_OF_STOCK', 'COD_UNAVAILABLE', 'ONLINE_PAYMENT_UNAVAILABLE']);

/**
 * Checkout: contact details, delivery address (saved or new), payment method and the order
 * summary. The server recalculates every amount when the order is placed.
 */
@Component({
  selector: 'app-checkout-page',
  imports: [ReactiveFormsModule, RouterLink, MatProgressSpinnerModule, ImgFallback, InrPipe],
  templateUrl: './checkout-page.html',
  styleUrl: './checkout-page.scss',
})
export class CheckoutPage implements OnInit {
  private readonly api = inject(CheckoutApi);
  private readonly accountApi = inject(AccountApi);
  private readonly cart = inject(CartService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly razorpay = inject(RazorpayCheckoutService);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly summary = signal<CheckoutSummary | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly placing = signal(false);
  protected readonly problem = signal<string | null>(null);
  protected readonly states = signal<string[]>([]);
  protected readonly addressMode = signal<'saved' | 'new'>('new');
  protected readonly selectedAddress = signal<number | null>(null);
  protected readonly label = codeLabel;
  protected readonly landmarkText = landmarkText;

  protected readonly contact = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(190)]],
    phone: ['', [Validators.required, Validators.pattern(MOBILE)]],
  });

  protected readonly address = this.fb.group({
    fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    phone: ['', [Validators.required, Validators.pattern(MOBILE)]],
    line1: ['', [Validators.required, Validators.maxLength(200)]],
    line2: ['', [Validators.maxLength(200)]],
    landmark: ['', [Validators.maxLength(120)]],
    city: ['', [Validators.required, Validators.maxLength(80)]],
    state: ['', [Validators.required]],
    pincode: ['', [Validators.required, Validators.pattern(PINCODE)]],
    save: [true],
  });

  protected readonly payment = this.fb.control<PaymentMethod | ''>('', Validators.required);
  protected readonly note = this.fb.control('', Validators.maxLength(500));
  /** Order updates on WhatsApp to the contact mobile; ticked when the customer chose it before. */
  protected readonly whatsapp = this.fb.control(false);

  constructor() {
    // The receiver defaults to the customer: copy name and phone until the address fields are edited.
    this.contact.valueChanges.pipe(takeUntilDestroyed()).subscribe((c) => {
      const { fullName, phone } = this.address.controls;
      if (!fullName.dirty) fullName.setValue(c.name ?? '', { emitEvent: false });
      if (!phone.dirty) phone.setValue(c.phone ?? '', { emitEvent: false });
    });
  }

  protected readonly items = computed(() => this.summary()?.cart.items ?? []);
  protected readonly options = computed(() => this.summary()?.paymentOptions ?? []);

  async ngOnInit(): Promise<void> {
    this.accountApi.states().subscribe({ next: (s) => this.states.set(s), error: () => this.states.set([]) });
    await this.load(true);
  }

  protected async load(first = false): Promise<void> {
    this.loading.set(true);
    try {
      const s = await firstValueFrom(this.api.summary());
      if (s.cart.items.length === 0) {
        void this.router.navigateByUrl('/cart');
        return;
      }
      this.summary.set(s);
      this.loadError.set(null);
      if (first) this.prefill(s);
      // Keep a payment choice that is still possible, otherwise pick the first available one.
      const chosen = s.paymentOptions.find((o) => o.method === this.payment.value && o.available);
      if (!chosen) this.payment.setValue(s.paymentOptions.find((o) => o.available)?.method ?? '');
    } catch (error) {
      this.loadError.set(apiErrorMessage(error, 'Checkout could not be loaded.'));
    } finally {
      this.loading.set(false);
    }
  }

  private prefill(s: CheckoutSummary): void {
    this.contact.patchValue({ name: s.contact.name ?? '', email: s.contact.email ?? '', phone: s.contact.phone ?? '' });
    this.whatsapp.setValue(!!s.contact.whatsappOptIn);
    const preferred = s.addresses.find((a) => a.isDefault) ?? s.addresses[0];
    if (preferred) {
      this.addressMode.set('saved');
      this.selectedAddress.set(preferred.id);
    }
    this.address.patchValue({ fullName: s.contact.name ?? '', phone: s.contact.phone ?? '' });
  }

  protected useNewAddress(): void {
    this.addressMode.set('new');
    this.selectedAddress.set(null);
    const c = this.contact.getRawValue();
    if (!this.address.controls.fullName.value) this.address.controls.fullName.setValue(c.name);
    if (!this.address.controls.phone.value) this.address.controls.phone.setValue(c.phone);
  }

  protected chooseSaved(id: number): void {
    this.addressMode.set('saved');
    this.selectedAddress.set(id);
  }

  protected invalid(group: 'contact' | 'address', name: string): boolean {
    const control = group === 'contact' ? this.contact.get(name) : this.address.get(name);
    return !!control && control.invalid && (control.touched || control.dirty);
  }

  async placeOrder(): Promise<void> {
    const s = this.summary();
    if (!s || this.placing()) return;
    this.problem.set(null);
    const useSaved = this.addressMode() === 'saved' && this.selectedAddress() !== null;
    this.contact.markAllAsTouched();
    if (!useSaved) this.address.markAllAsTouched();
    if (this.contact.invalid || (!useSaved && this.address.invalid) || !this.payment.value) {
      this.problem.set(!this.payment.value ? 'Choose how you want to pay.' : 'Please fix the highlighted details.');
      return;
    }
    const a = this.address.getRawValue();
    const body: PlaceOrderRequest = {
      contact: this.contact.getRawValue(),
      addressId: useSaved ? this.selectedAddress() : null,
      address: useSaved ? null : {
        label: null,
        fullName: a.fullName.trim(),
        phone: a.phone.trim(),
        line1: a.line1.trim(),
        line2: a.line2.trim() || null,
        landmark: a.landmark.trim() || null,
        city: a.city.trim(),
        state: a.state,
        pincode: a.pincode.trim(),
        makeDefault: false,
      },
      saveAddress: !useSaved && a.save,
      paymentMethod: this.payment.value,
      note: this.note.value.trim() || null,
      expectedTotal: s.totals.total,
      whatsappUpdates: this.whatsapp.value,
    };
    this.placing.set(true);
    try {
      const order = await firstValueFrom(this.api.placeOrder(body));
      void this.cart.refresh().catch(() => undefined);
      if (order.paymentMethod === 'RAZORPAY') {
        // The order exists now; open the payment window. Whatever happens, the order page follows.
        const result: PaymentOutcome = await this.razorpay.pay(order.orderNumber)
          .catch((error: unknown): PaymentOutcome => ({ outcome: 'error', error }));
        if (result.outcome === 'dismissed') {
          this.notify.success('Your order is saved. You can complete the payment from the order page.');
        } else if (result.outcome === 'error') {
          this.notify.error(result.error, 'The payment could not be completed. You can try again from the order page.');
        }
      }
      void this.router.navigate(['/order', order.orderNumber], { queryParams: { placed: 1 }, replaceUrl: true });
    } catch (error) {
      const code = apiErrorCode(error);
      this.problem.set(apiErrorMessage(error, 'Your order could not be placed. Please try again.'));
      if (code === 'CART_EMPTY') {
        void this.router.navigateByUrl('/cart');
      } else if (code && RELOAD_ON.has(code)) {
        await this.load();
      }
    } finally {
      this.placing.set(false);
    }
  }
}
