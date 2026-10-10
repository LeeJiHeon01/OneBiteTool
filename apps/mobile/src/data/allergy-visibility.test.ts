import { describe, expect, it } from '@jest/globals';

import { hiddenSummary, hideReason, splitByVisibility } from '@/data/allergy-visibility';
import type { SearchResult } from '@/data/recipe';

// Acceptance criteria "알레르기 노출" (docs/spec.md §13).

function recipe(id: string, allergenCodes: string[], allergyCheckNeeded: boolean): SearchResult {
  return {
    id,
    title: id,
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

const WITH_EGG = recipe('egg-dish', ['egg'], false);
const WITH_EGG_AND_UNCHECKED = recipe('egg-and-unknown', ['egg', 'wheat'], true);
const UNCHECKED = recipe('unknown-dish', [], true);
const UNCHECKED_OTHER_ALLERGEN = recipe('wheat-and-unknown', ['wheat'], true);
const CHECKED_SAFE = recipe('checked-dish', ['wheat'], false);
const ALL = [WITH_EGG, WITH_EGG_AND_UNCHECKED, UNCHECKED, UNCHECKED_OTHER_ALLERGEN, CHECKED_SAFE];

describe('search results for a user with allergies', () => {
  const eggAllergy = ['egg'];

  it('hides a recipe with an unchecked ingredient and counts it as "확인 안 된 레시피"', () => {
    const visibility = splitByVisibility(ALL, eggAllergy);

    expect(hideReason(UNCHECKED, eggAllergy)).toBe('unchecked');
    expect(hideReason(UNCHECKED_OTHER_ALLERGEN, eggAllergy)).toBe('unchecked');
    expect(visibility.shown).not.toContain(UNCHECKED);
    expect(visibility.uncheckedCount).toBe(2);
    expect(hiddenSummary(visibility)).toContain('확인 안 된 레시피 2개');
  });

  it('hides a recipe with a matching allergen and counts it separately', () => {
    const visibility = splitByVisibility(ALL, eggAllergy);

    expect(hideReason(WITH_EGG, eggAllergy)).toBe('match');
    // A match wins over "unchecked": the recipe is counted once, as a match.
    expect(hideReason(WITH_EGG_AND_UNCHECKED, eggAllergy)).toBe('match');
    expect(visibility.matchCount).toBe(2);
    expect(hiddenSummary(visibility)).toBe('알레르기 재료가 든 레시피 2개 · 확인 안 된 레시피 2개');
  });

  it('shows a recipe whose ingredients are all checked and contain none of the allergens', () => {
    const visibility = splitByVisibility(ALL, eggAllergy);

    expect(hideReason(CHECKED_SAFE, eggAllergy)).toBeNull();
    expect(visibility.shown).toEqual([CHECKED_SAFE]);
  });

  it('reports every result as hidden so the screen can offer "보기" instead of an empty list', () => {
    const visibility = splitByVisibility([WITH_EGG, UNCHECKED], eggAllergy);

    expect(visibility.shown).toEqual([]);
    expect(visibility.hidden.map((item) => item.result)).toEqual([WITH_EGG, UNCHECKED]);
    expect(hiddenSummary(visibility)).toBe('알레르기 재료가 든 레시피 1개 · 확인 안 된 레시피 1개');
  });

  it('names only the reasons that occur', () => {
    expect(hiddenSummary(splitByVisibility([UNCHECKED], eggAllergy))).toBe('확인 안 된 레시피 1개');
    expect(hiddenSummary(splitByVisibility([WITH_EGG], eggAllergy))).toBe('알레르기 재료가 든 레시피 1개');
  });
});

describe('search results for a user without allergies', () => {
  it('hides nothing and gives no reason for any recipe', () => {
    const visibility = splitByVisibility(ALL, []);

    expect(visibility.shown).toEqual(ALL);
    expect(visibility.hidden).toEqual([]);
    expect(visibility.matchCount + visibility.uncheckedCount).toBe(0);
    expect(hiddenSummary(visibility)).toBe('');
    expect(ALL.map((item) => hideReason(item, []))).toEqual([null, null, null, null, null]);
  });
});
