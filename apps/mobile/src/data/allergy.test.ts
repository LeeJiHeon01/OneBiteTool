import { describe, expect, it } from '@jest/globals';

import { findAllergyMatches, needsResearchNotice } from '@/data/allergy';
import { hideReason } from '@/data/allergy-visibility';
import type { RecipeIngredient, SearchResult } from '@/data/recipe';

// Acceptance criteria "알레르기 매핑 등급" (docs/spec.md §13).

function ingredient(name: string, extra: Partial<RecipeIngredient> = {}): RecipeIngredient {
  return {
    id: name,
    name,
    dictionaryName: null,
    rawText: '1큰술',
    allergens: [],
    allergyCheckNeeded: false,
    allergenReviewLevel: 'AI_RESEARCHED',
    allergenProductVaries: false,
    ...extra,
  };
}

const SOY_SAUCE = ingredient('간장', {
  allergens: [
    { code: 'soybean', hidden: true },
    { code: 'wheat', hidden: true },
  ],
  allergenProductVaries: true,
});
const KIMCHI = ingredient('김치', { allergens: [{ code: 'shrimp', hidden: true }], allergenProductVaries: true });
const ONION = ingredient('양파');
const UNKNOWN = ingredient('달래', { allergyCheckNeeded: true, allergenReviewLevel: null });

function searchResult(allergenCodes: string[], allergyCheckNeeded: boolean): SearchResult {
  return {
    id: 'recipe',
    title: 'recipe',
    ingredientCount: 3,
    stepCount: 3,
    minutes: null,
    badge: 'WEB_SEARCH',
    sourceDomain: null,
    allergenCodes,
    allergyCheckNeeded,
    toolFit: null,
  };
}

describe('recipes whose ingredients are all AI_RESEARCHED', () => {
  it('are not hidden from a user with an allergy the recipe does not contain', () => {
    expect(hideReason(searchResult(['soybean', 'wheat'], false), ['peanut'])).toBeNull();
  });

  it('show the "자료 기반" notice to a user with allergies when opened', () => {
    expect(needsResearchNotice([SOY_SAUCE, ONION], ['peanut'])).toBe(true);
  });

  it('show no allergy notice to a user without allergies', () => {
    expect(needsResearchNotice([SOY_SAUCE, ONION], [])).toBe(false);
  });

  it('show no notice when every ingredient was verified by a person', () => {
    const verified = [ingredient('양파', { allergenReviewLevel: 'HUMAN_VERIFIED' })];
    expect(needsResearchNotice(verified, ['peanut'])).toBe(false);
  });
});

describe('warnings', () => {
  it('warn about soybean and wheat in soy sauce as hidden and product-dependent', () => {
    expect(findAllergyMatches([SOY_SAUCE, ONION], ['wheat', 'soybean'])).toEqual([
      { ingredientId: '간장', ingredientName: '간장', allergenNames: '대두, 밀', hidden: true, productVaries: true },
    ]);
  });

  it('warn a user with a shrimp allergy about kimchi', () => {
    const matches = findAllergyMatches([KIMCHI, ONION], ['shrimp']);
    expect(matches.map((match) => match.ingredientName)).toEqual(['김치']);
    expect(matches[0].allergenNames).toBe('새우');
  });
});

describe('ingredients outside the dictionary', () => {
  it('still hide the recipe from a user with allergies', () => {
    expect(UNKNOWN.allergyCheckNeeded).toBe(true);
    expect(hideReason(searchResult([], true), ['peanut'])).toBe('unchecked');
  });
});
