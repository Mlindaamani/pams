import createClient from 'openapi-fetch';
import { appConfig } from '@/config';
import type { paths } from './schema';

let accessToken: string | null = null;

export function setApiToken(token: string | null) {
  accessToken = token;
}

export const api = createClient<paths>({
  baseUrl: appConfig.apiBaseUrl || window.location.origin,
  fetch: (input: Request) => globalThis.fetch(input),
});

export function isPublicApiPath(url: string): boolean {
  try {
    return new URL(url, window.location.origin).pathname.startsWith('/api/v1/public/');
  } catch {
    return url.includes('/api/v1/public/');
  }
}

api.use({
  onRequest({ request }) {
    if (accessToken && !isPublicApiPath(request.url)) {
      request.headers.set('Authorization', `Bearer ${accessToken}`);
    }
    if (!request.headers.has('X-Request-ID')) {
      request.headers.set('X-Request-ID', crypto.randomUUID());
    }
    return request;
  },
});

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  traceId?: string;
  errors?: { field: string; message: string }[];
}

function nonBlank(value?: string): string | undefined {
  return value?.trim() ? value : undefined;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly detail: string;
  readonly traceId?: string;
  readonly fieldErrors: { field: string; message: string }[];

  constructor(status: number, problem: ProblemDetail) {
    const message =
      nonBlank(problem.detail) ?? nonBlank(problem.title) ?? `Request failed with status ${status}`;
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = problem.code ?? 'unknown';
    this.detail = message;
    this.traceId = problem.traceId;
    this.fieldErrors = problem.errors ?? [];
  }
}

interface FetchResult<T> {
  data?: T;
  error?: unknown;
  response: Response;
}

function problemFromError(error: unknown): ProblemDetail | undefined {
  if (!error) return undefined;
  if (typeof error === 'object') return error as ProblemDetail;
  if (typeof error === 'string') {
    const value = error.trim();
    if (!value) return undefined;
    if (value.startsWith('{')) {
      try {
        return JSON.parse(value) as ProblemDetail;
      } catch {
        // Fall through and use the raw response text as the detail.
      }
    }
    return { detail: value };
  }
  return undefined;
}

export async function unwrap<T>(result: Promise<FetchResult<T>>): Promise<T> {
  const { data, error, response } = await result;
  if (!response.ok || error) {
    let problem: ProblemDetail = { status: response.status };
    const fromError = problemFromError(error);
    if (fromError) {
      problem = { ...problem, ...fromError };
    } else {
      try {
        const body = (await response.clone().json()) as ProblemDetail;
        problem = { ...problem, ...body };
      } catch {
        // The response body may already have been consumed by openapi-fetch.
      }
    }
    if (!nonBlank(problem.detail) && !nonBlank(problem.title)) {
      problem.detail =
        nonBlank(response.statusText) ?? `Request failed with status ${response.status}`;
    }
    throw new ApiError(response.status, problem);
  }
  return data as T;
}

export function idempotencyKey(): string {
  return crypto.randomUUID();
}

export function etag(version: number): string {
  return `"${version}"`;
}

export function problemMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.fieldErrors.length > 0
      ? error.fieldErrors.map((item) => `${item.field}: ${item.message}`).join(', ')
      : (nonBlank(error.detail) ?? `Request failed with status ${error.status}`);
  }
  if (error instanceof Error) {
    return nonBlank(error.message) ?? 'Unexpected error';
  }
  return 'Unexpected error';
}

export function isConflict(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 409 || error.status === 412);
}
