import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api, isPublicApiPath, problemMessage, setApiToken, unwrap } from '@/api/client';

describe('API error handling', () => {
  it('uses the problem detail when openapi-fetch already parsed the error body', async () => {
    const response = new Response(JSON.stringify({ detail: 'Cannot exceed ordered quantity' }), {
      status: 422,
      headers: { 'Content-Type': 'application/problem+json' },
    });
    const error = await response.json();

    await expect(
      unwrap(Promise.resolve({ error, response, data: undefined })),
    ).rejects.toMatchObject({
      status: 422,
      detail: 'Cannot exceed ordered quantity',
    });
  });

  it('parses a raw problem JSON string when the body was consumed', async () => {
    const response = new Response('', { status: 409 });
    const error = JSON.stringify({ detail: 'Purchase order changed', code: 'conflict' });

    const failure = await unwrap(Promise.resolve({ error, response, data: undefined })).catch(
      (value: unknown) => value,
    );

    expect(failure).toBeInstanceOf(ApiError);
    expect((failure as ApiError).detail).toBe('Purchase order changed');
    expect(problemMessage(failure)).toBe('Purchase order changed');
  });

  it('falls back to a useful message when no problem body exists', async () => {
    const response = new Response(null, { status: 500 });

    const failure = await unwrap(
      Promise.resolve({ error: undefined, response, data: undefined }),
    ).catch((value: unknown) => value);

    expect(problemMessage(failure)).toBe('Request failed with status 500');
  });
});

describe('API auth scoping', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    setApiToken(null);
  });

  it('recognises public endpoints', () => {
    expect(isPublicApiPath('/api/v1/public/demo-requests')).toBe(true);
    expect(isPublicApiPath('http://localhost:8080/api/v1/public/plans')).toBe(true);
    expect(isPublicApiPath('/api/v1/purchase-requests')).toBe(false);
  });

  it('never sends a stale bearer token to public endpoints', async () => {
    let publicAuthorization: string | null = 'not-called';
    let privateAuthorization: string | null = null;
    const fetchMock = vi.fn(async (input: Request) => {
      const request = input instanceof Request ? input : new Request(input);
      if (request.url.includes('/api/v1/public/')) {
        publicAuthorization = request.headers.get('Authorization');
        return new Response('[]', {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }
      privateAuthorization = request.headers.get('Authorization');
      return new Response('{}', {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });
    vi.stubGlobal('fetch', fetchMock);
    setApiToken('expired-token');

    await api.GET('/api/v1/public/plans');
    await api.GET('/api/v1/users/me');

    expect(publicAuthorization).toBeNull();
    expect(privateAuthorization).toBe('Bearer expired-token');
  });
});
