import { HttpErrorResponse } from '@angular/common/http';

/** RFC 7807 problem detail as returned by the Spring Boot GlobalExceptionHandler. */
export interface ApiProblem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  code?: string;
  requestId?: string;
  errors?: { field: string; message: string }[];
  retryAfterSeconds?: number;
}

export function apiProblem(error: unknown): ApiProblem | null {
  if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') {
    return error.error as ApiProblem;
  }
  return null;
}

export function apiErrorCode(error: unknown): string | null {
  return apiProblem(error)?.code ?? null;
}

/** A human-readable message for any HTTP error, preferring the server explanation. */
export function apiErrorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (error instanceof HttpErrorResponse && error.status === 0) {
    return 'Cannot reach the server. Check your connection and that the backend is running.';
  }
  const problem = apiProblem(error);
  if (problem?.errors?.length) {
    return problem.errors.map((e) => `${humanize(e.field)} ${e.message}`).join('. ');
  }
  return problem?.detail ?? fallback;
}

function humanize(field: string): string {
  const spaced = field.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/[._]/g, ' ').toLowerCase();
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}
