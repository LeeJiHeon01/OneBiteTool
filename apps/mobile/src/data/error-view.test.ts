import { describe, expect, it } from '@jest/globals';

import { errorKind, errorView } from '@/data/error-view';

describe('what a failed search shows', () => {
  it('tells apart no connection, the daily limit and a server problem', () => {
    expect(errorKind('NETWORK')).toBe('OFFLINE');
    expect(errorKind('AI_DAILY_LIMIT')).toBe('DAILY_LIMIT');
    expect(errorKind('AI_UNAVAILABLE')).toBe('SERVER');
    expect(errorKind('UNKNOWN')).toBe('SERVER');
    expect(errorKind(null)).toBe('SERVER');
  });

  it('uses the same title but a different explanation for the limit and a server problem', () => {
    const limit = errorView('AI_DAILY_LIMIT');
    const server = errorView('AI_UNAVAILABLE');

    expect(limit.title).toBe(server.title);
    expect(limit.description).not.toBe(server.description);
    expect(limit.description).toContain('내일');
    expect(server.description).toContain('잠시 후');
  });

  it('offers a retry only when trying again can help', () => {
    expect(errorView('NETWORK').canRetry).toBe(true);
    expect(errorView('AI_UNAVAILABLE').canRetry).toBe(true);
    expect(errorView('AI_DAILY_LIMIT').canRetry).toBe(false);
  });

  it('points to stored recipes when only finding new ones is broken', () => {
    expect(errorView('AI_DAILY_LIMIT').offersStored).toBe(true);
    expect(errorView('AI_UNAVAILABLE').offersStored).toBe(true);
    // Without a connection stored recipes cannot be opened either.
    expect(errorView('NETWORK').offersStored).toBe(false);
    expect(errorView('NETWORK').description).toContain('조리 중인 레시피는 계속 볼 수 있어요');
  });
});
