import { describe, expect, it } from '@jest/globals';

import { type RecipeIngredient } from '@/data/recipe';
import { itemLabel, shoppingItems, shoppingText } from '@/data/shopping-list';
import { missingToolsLabel } from '@/data/tool-fit';

function ingredient(id: string, name: string, rawText: string): RecipeIngredient {
  return {
    id,
    name,
    dictionaryName: null,
    rawText,
    allergens: [{ code: 'soybean', hidden: true }],
    allergyCheckNeeded: false,
    allergenReviewLevel: 'AI_RESEARCHED',
    allergenProductVaries: true,
  };
}

const INGREDIENTS = [
  ingredient('a', '김치', '200g'),
  ingredient('b', '참기름', '1작은술'),
  ingredient('c', '후추', ' '),
];

describe('shopping list', () => {
  it('lists only the ingredients the user lacks, in recipe order, with the source amount', () => {
    const items = shoppingItems(INGREDIENTS, ['c', 'b']);

    expect(items).toEqual([
      { id: 'b', name: '참기름', amount: '1작은술' },
      { id: 'c', name: '후추', amount: '' },
    ]);
    expect(items.map(itemLabel)).toEqual(['참기름 · 1작은술', '후추']);
  });

  it('builds the text to copy or share without any allergy information', () => {
    const text = shoppingText('김치볶음밥', shoppingItems(INGREDIENTS, ['a', 'b']));

    expect(text).toBe('장보기 목록 (김치볶음밥)\n- 김치 · 200g\n- 참기름 · 1작은술');
    expect(text).not.toContain('soybean');
    expect(text).not.toContain('알레르기');
  });

  it('is empty when nothing is missing', () => {
    expect(shoppingItems(INGREDIENTS, [])).toEqual([]);
  });
});

describe('missing tools wording', () => {
  it('uses the right particle for the last word', () => {
    expect(missingToolsLabel({ ok: false, missingTools: [{ id: 'pot', name: '냄비' }] })).toBe('냄비가 필요해요');
    expect(missingToolsLabel({ ok: false, missingTools: [{ id: 'knife', name: '칼' }] })).toBe('칼이 필요해요');
    expect(
      missingToolsLabel({
        ok: false,
        missingTools: [
          { id: 'cooking_vessel', name: '프라이팬 또는 냄비' },
          { id: 'stove', name: '가스레인지 또는 인덕션' },
        ],
      }),
    ).toBe('프라이팬 또는 냄비, 가스레인지 또는 인덕션이 필요해요');
  });
});
