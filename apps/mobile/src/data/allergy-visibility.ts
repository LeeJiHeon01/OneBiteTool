import type { SearchResult } from '@/data/recipe';

// Which search results a user with allergies sees by default (spec "검색 결과 노출 규칙").
// The comparison happens on the device: the user's allergies are never sent with a search.

// match: the recipe contains one of the user's allergens.
// unchecked: none found, but an ingredient has no person-verified allergen mapping, so it
// cannot be called safe and is treated with the same care.
export type HideReason = 'match' | 'unchecked';

// Null means the recipe is listed as usual. A user without allergies never has anything hidden.
export function hideReason(result: SearchResult, userAllergenCodes: string[]): HideReason | null {
  if (userAllergenCodes.length === 0) return null;
  if (result.allergenCodes.some((code) => userAllergenCodes.includes(code))) return 'match';
  return result.allergyCheckNeeded ? 'unchecked' : null;
}

export type Visibility = {
  // Listed by default.
  shown: SearchResult[];
  // Left out until the user asks to see them; counted separately by reason.
  hidden: { result: SearchResult; reason: HideReason }[];
  matchCount: number;
  uncheckedCount: number;
};

export function splitByVisibility(results: SearchResult[], userAllergenCodes: string[]): Visibility {
  const shown: SearchResult[] = [];
  const hidden: Visibility['hidden'] = [];
  for (const result of results) {
    const reason = hideReason(result, userAllergenCodes);
    if (reason) hidden.push({ result, reason });
    else shown.push(result);
  }
  return {
    shown,
    hidden,
    matchCount: hidden.filter((item) => item.reason === 'match').length,
    uncheckedCount: hidden.filter((item) => item.reason === 'unchecked').length,
  };
}

// "알레르기 재료가 든 레시피 2개 · 확인 안 된 레시피 3개": only the parts that are not zero.
export function hiddenSummary(visibility: Pick<Visibility, 'matchCount' | 'uncheckedCount'>): string {
  return [
    visibility.matchCount > 0 ? `알레르기 재료가 든 레시피 ${visibility.matchCount}개` : null,
    visibility.uncheckedCount > 0 ? `확인 안 된 레시피 ${visibility.uncheckedCount}개` : null,
  ]
    .filter(Boolean)
    .join(' · ');
}
