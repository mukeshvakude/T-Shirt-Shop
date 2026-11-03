import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { NewsletterForm } from './newsletter-form';

describe('NewsletterForm', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [NewsletterForm], providers: [provideHttpClient(), provideHttpClientTesting()] });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function render() {
    const fixture = TestBed.createComponent(NewsletterForm);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const submit = (email: string) => {
      const input = el.querySelector<HTMLInputElement>('input')!;
      input.value = email;
      input.dispatchEvent(new Event('input'));
      el.querySelector<HTMLButtonElement>('button')!.click();
      fixture.detectChanges();
    };
    return { fixture, el, submit };
  }

  it('validates the email before calling the API', () => {
    const { el, submit } = render();
    submit('not-an-email');
    httpMock.expectNone('/api/newsletter');
    expect(el.textContent).toContain('Enter a valid email address.');
  });

  it('subscribes and thanks the user', () => {
    const { el, fixture, submit } = render();
    submit('fan@example.com');
    const req = httpMock.expectOne('/api/newsletter');
    expect(req.request.body).toEqual({ email: 'fan@example.com' });
    req.flush(null, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();
    expect(el.querySelector('.done')?.textContent).toContain('Thanks for subscribing');
  });

  it('shows the server message when rate limited', () => {
    const { el, fixture, submit } = render();
    submit('fan@example.com');
    httpMock.expectOne('/api/newsletter').flush(
      { code: 'TOO_MANY_REQUESTS', detail: 'Too many requests. Please wait a moment and try again.' },
      { status: 429, statusText: 'Too Many Requests' },
    );
    fixture.detectChanges();
    expect(el.querySelector('.error')?.textContent).toContain('Too many requests');
  });
});
