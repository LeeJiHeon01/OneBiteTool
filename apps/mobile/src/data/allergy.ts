import { allergenName } from '@/data/catalog';

type AllergenCarrier = {
  id: string;
  name: string;
  allergens: { code: string; hidden: boolean }[];
  allergenReviewLevel?: 'HUMAN_VERIFIED' | 'AI_RESEARCHED' | null;
  allergenProductVaries?: boolean;
};

export type AllergyMatch = {
  ingredientId: string;
  ingredientName: string;
  // Matched allergen names joined for display, e.g. "대두, 밀".
  allergenNames: string;
  // True when the allergen is a hidden component (soy sauce -> soy, wheat).
  hidden: boolean;
  // The ingredient's contents differ by product: shown with "제품마다 달라요".
  productVaries: boolean;
};

export const RESEARCH_NOTICE = '알레르기 정보는 자료 기반이에요. 제품 성분표를 꼭 확인하세요.';
export const PRODUCT_VARIES_NOTE = '제품마다 달라요. 성분표를 확인하세요.';

// A user with allergies is told when the allergen information of a recipe comes from research
// that no person has confirmed. A user without allergies sees no allergy notices at all.
export function needsResearchNotice(items: AllergenCarrier[], userCodes: string[]): boolean {
  return userCodes.length > 0 && items.some((item) => item.allergenReviewLevel === 'AI_RESEARCHED');
}

// Matches use the verified ingredient -> allergen mapping carried in the data.
// No match means "not in the mapping", never a guarantee that the recipe is safe.
export function findAllergyMatches(items: AllergenCarrier[], userCodes: string[]): AllergyMatch[] {
  return items.flatMap((item) => {
    const matched = item.allergens.filter((allergen) => userCodes.includes(allergen.code));
    if (matched.length === 0) return [];
    return [
      {
        ingredientId: item.id,
        ingredientName: item.name,
        allergenNames: matched.map((allergen) => allergenName(allergen.code)).join(', '),
        hidden: matched.some((allergen) => allergen.hidden),
        productVaries: item.allergenProductVaries === true,
      },
    ];
  });
}
