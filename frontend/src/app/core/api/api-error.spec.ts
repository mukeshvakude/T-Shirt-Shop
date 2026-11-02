import { HttpErrorResponse } from '@angular/common/http';

import { apiErrorCode, apiErrorMessage } from './api-error';

describe('apiErrorMessage', () => {
  it('uses the server detail', () => {
    const error = new HttpErrorResponse({ status: 409, error: { code: 'SLUG_TAKEN', detail: 'Already used.' } });
    expect(apiErrorMessage(error)).toBe('Already used.');
    expect(apiErrorCode(error)).toBe('SLUG_TAKEN');
  });

  it('lists field validation errors in plain words', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: { code: 'VALIDATION_FAILED', detail: 'x', errors: [{ field: 'basePrice', message: 'must be at least 1' }] },
    });
    expect(apiErrorMessage(error)).toBe('Base price must be at least 1');
  });

  it('explains network failures', () => {
    expect(apiErrorMessage(new HttpErrorResponse({ status: 0 }))).toContain('Cannot reach the server');
  });

  it('falls back for unknown errors', () => {
    expect(apiErrorMessage(new Error('boom'), 'Fallback')).toBe('Fallback');
    expect(apiErrorCode('nope')).toBeNull();
  });
});
