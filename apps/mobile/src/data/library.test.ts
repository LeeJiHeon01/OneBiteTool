import { describe, expect, it } from '@jest/globals';

import {
  addRecentQuery,
  isFavorite,
  MAX_RECENT_QUERIES,
  removeRecentQuery,
  sanitizeFavorites,
  sanitizeQueries,
  toggleFavorite,
} from '@/data/library';

describe('recent searches', () => {
  it('puts the newest first and moves a repeated search to the front', () => {
    const queries = addRecentQuery(addRecentQuery(addRecentQuery([], '김치볶음밥'), '계란말이'), ' 김치볶음밥 ');
    expect(queries).toEqual(['김치볶음밥', '계란말이']);
  });

  it('keeps at most eight and ignores an empty search', () => {
    let queries: string[] = [];
    for (let index = 0; index < 12; index++) queries = addRecentQuery(queries, `요리${index}`);
    expect(queries).toHaveLength(MAX_RECENT_QUERIES);
    expect(queries[0]).toBe('요리11');
    expect(addRecentQuery(queries, '   ')).toBe(queries);
  });

  it('removes one search without touching the others', () => {
    expect(removeRecentQuery(['가', '나', '다'], '나')).toEqual(['가', '다']);
  });

  it('drops stored values that are not search text', () => {
    expect(sanitizeQueries(['김치찌개', 3, '', null, '된장찌개'])).toEqual(['김치찌개', '된장찌개']);
    expect(sanitizeQueries('김치찌개')).toEqual([]);
  });
});

describe('favourites', () => {
  const kimchi = { id: 'RECIPE_1', name: '김치볶음밥' };
  const egg = { id: 'RECIPE_2', name: '계란말이' };

  it('adds a recipe at the front and removes it when toggled again', () => {
    const added = toggleFavorite(toggleFavorite([], kimchi), egg);
    expect(added).toEqual([egg, kimchi]);
    expect(isFavorite(added, 'RECIPE_1')).toBe(true);
    expect(toggleFavorite(added, kimchi)).toEqual([egg]);
  });

  it('stores only the id and the name of a recipe', () => {
    const withExtra = { ...kimchi, allergenCodes: ['egg'], toolIds: ['knife'] };
    expect(toggleFavorite([], withExtra)).toEqual([kimchi]);
    expect(sanitizeFavorites([withExtra, { id: 1 }, 'x'])).toEqual([kimchi]);
  });
});
