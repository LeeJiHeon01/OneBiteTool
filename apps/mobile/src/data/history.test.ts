import { describe, expect, it } from '@jest/globals';

import {
  addRecord,
  dateLabel,
  newRecord,
  ratingLabel,
  recentDishes,
  removeRecord,
  sanitizeRecords,
  toggleStuckStep,
  updateRecord,
} from '@/data/history';

const KIMCHI = { id: 'RECIPE_1', name: '김치볶음밥' };
const EGG = { id: 'RECIPE_2', name: '계란말이' };

describe('cook records', () => {
  it('starts without a rating and holds only the recipe id, name and time', () => {
    const record = newRecord({ ...KIMCHI, allergenCodes: ['egg'], toolIds: ['knife'] } as typeof KIMCHI, new Date(2026, 9, 10, 12));

    expect(Object.keys(record).sort()).toEqual(
      ['completedAt', 'id', 'recipeId', 'recipeName', 'rating', 'stuckStepOrders'].sort(),
    );
    expect(record.rating).toBeNull();
    expect(record.stuckStepOrders).toEqual([]);
  });

  it('keeps the newest first and lets the rating and stuck steps be filled in afterwards', () => {
    const first = newRecord(KIMCHI, new Date(2026, 9, 9));
    const second = newRecord(EGG, new Date(2026, 9, 10));
    let records = addRecord(addRecord([], first), second);
    records = updateRecord(records, second.id, { rating: 'HARD', stuckStepOrders: [5, 6] });

    expect(records.map((record) => record.recipeName)).toEqual(['계란말이', '김치볶음밥']);
    expect(records[0].rating).toBe('HARD');
    expect(records[0].stuckStepOrders).toEqual([5, 6]);
    expect(records[1].rating).toBeNull();
  });

  it('removes one record without touching the others', () => {
    const first = newRecord(KIMCHI, new Date(2026, 9, 9));
    const second = newRecord(EGG, new Date(2026, 9, 10));
    expect(removeRecord([second, first], second.id)).toEqual([first]);
  });

  it('toggles a stuck step and keeps the numbers in step order', () => {
    expect(toggleStuckStep(toggleStuckStep([], 5), 2)).toEqual([2, 5]);
    expect(toggleStuckStep([2, 5], 2)).toEqual([5]);
  });
});

describe('recently cooked dishes on the home screen', () => {
  it('lists a dish once, by its latest cook, and at most five', () => {
    let records = addRecord([], newRecord(KIMCHI, new Date(2026, 9, 1)));
    for (let index = 0; index < 6; index++) {
      records = addRecord(records, newRecord({ id: `RECIPE_X${index}`, name: `요리${index}` }, new Date(2026, 9, 2 + index)));
    }
    records = addRecord(records, newRecord(KIMCHI, new Date(2026, 9, 20)));

    const dishes = recentDishes(records);
    expect(dishes).toHaveLength(5);
    expect(dishes[0].recipeName).toBe('김치볶음밥');
    expect(dishes.filter((dish) => dish.recipeId === KIMCHI.id)).toHaveLength(1);
  });
});

describe('labels', () => {
  it('names the rating and shows nothing for a skipped one', () => {
    expect(ratingLabel('EASY')).toBe('쉬웠어요');
    expect(ratingLabel('OK')).toBe('할 만했어요');
    expect(ratingLabel('HARD')).toBe('어려웠어요');
    expect(ratingLabel(null)).toBeNull();
  });

  it('shows the day a dish was finished', () => {
    expect(dateLabel(new Date(2026, 9, 10, 18, 30).toISOString())).toBe('10월 10일');
    expect(dateLabel('not a date')).toBe('');
  });
});

describe('stored records', () => {
  it('drops broken entries and unknown fields', () => {
    const good = newRecord(KIMCHI, new Date(2026, 9, 10));
    const stored = [
      { ...good, rating: 'GREAT', stuckStepOrders: [2, 'x', -1, 4], allergenCodes: ['egg'] },
      { id: 'x' },
      'text',
      null,
    ];

    expect(sanitizeRecords(stored)).toEqual([{ ...good, rating: null, stuckStepOrders: [2, 4] }]);
    expect(sanitizeRecords('nothing')).toEqual([]);
  });
});
