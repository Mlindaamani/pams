import { describe, expect, it } from 'vitest';
import { ApiError, problemMessage, unwrap } from '@/api/client';

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
