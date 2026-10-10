import { describe, expect, it } from '@jest/globals';

import {
  fromTheStart,
  isFresh,
  MAX_AGE_MS,
  sanitizeSaved,
  timerAt,
  toggleChecked,
  type SavedCooking,
} from '@/data/cooking-session';
import { type Adaptation, type Recipe } from '@/data/recipe';
import { type TimerState } from '@/state/use-timer';

const NOW = Date.UTC(2026, 9, 10, 9, 0, 0);

const recipe = { id: 'RECIPE_1', name: '김치볶음밥' } as Recipe;
const adaptation = {
  status: 'COOKABLE',
  ingredients: [{ id: 'I1', name: '김치', allergens: [{ code: 'shrimp', hidden: true }] }],
  steps: [
    { id: 'S1', text: '김치를 썰어요' },
    { id: 'S2', text: '김치를 볶아요' },
  ],
} as unknown as Adaptation;

const running: TimerState = {
  stepId: 'S2',
  stepNumber: 2,
  status: 'running',
  durationSeconds: 180,
  endAt: NOW + 60_000,
  remainingOnPause: null,
};

function saved(change: Partial<SavedCooking> = {}): SavedCooking {
  return { savedAt: NOW, recipe, adaptation, stepIndex: 1, timer: running, checkedItems: { S2: ['a'] }, ...change };
}

describe('a saved cooking session', () => {
  it('is offered for a day and dropped after that', () => {
    expect(isFresh(saved(), NOW + 1000)).toBe(true);
    expect(isFresh(saved(), NOW + MAX_AGE_MS - 1)).toBe(true);
    expect(isFresh(saved(), NOW + MAX_AGE_MS)).toBe(false);
    // A clock set back does not make an old session look new.
    expect(isFresh(saved(), NOW - 1000)).toBe(false);
  });

  it('starts again from the first step with no timer and nothing ticked', () => {
    expect(fromTheStart(saved())).toEqual(saved({ stepIndex: 0, timer: null, checkedItems: {} }));
  });

  it('ticks and unticks a checklist item of one step', () => {
    const once = toggleChecked({}, 'S2', 'a');
    expect(once).toEqual({ S2: ['a'] });
    expect(toggleChecked(toggleChecked(once, 'S2', 'b'), 'S2', 'a')).toEqual({ S2: ['b'] });
  });
});

describe('the timer after the app was closed', () => {
  it('keeps running from its end time', () => {
    expect(timerAt(running, NOW + 30_000)).toEqual(running);
  });

  it('is shown as finished once its end has passed, and stays on its own step', () => {
    const later = timerAt(running, NOW + 61_000);

    expect(later?.status).toBe('finished');
    expect(later?.stepId).toBe('S2');
    expect(later?.endAt).toBeNull();
  });

  it('stays paused with the time that was left', () => {
    const paused: TimerState = { ...running, status: 'paused', endAt: null, remainingOnPause: 45 };

    expect(timerAt(paused, NOW + 10 * 60_000)).toEqual(paused);
    expect(timerAt(null, NOW)).toBeNull();
  });
});

describe('a stored session read back', () => {
  it('comes back as it was saved', () => {
    expect(sanitizeSaved(JSON.parse(JSON.stringify(saved())))).toEqual(saved());
  });

  it('is refused when it is not a cooking session', () => {
    expect(sanitizeSaved(null)).toBeNull();
    expect(sanitizeSaved('text')).toBeNull();
    expect(sanitizeSaved({ savedAt: NOW })).toBeNull();
    expect(sanitizeSaved({ ...saved(), adaptation: { ...adaptation, steps: [] } })).toBeNull();
    expect(sanitizeSaved({ ...saved(), recipe: { id: 1 } })).toBeNull();
  });

  it('repairs a step index, a timer or ticks that no longer fit the steps', () => {
    const repaired = sanitizeSaved({
      ...saved(),
      stepIndex: 9,
      timer: { ...running, stepId: 'GONE' },
      checkedItems: { S2: ['a', 3], GONE: ['x'] },
    });

    expect(repaired?.stepIndex).toBe(1);
    expect(repaired?.timer).toBeNull();
    expect(repaired?.checkedItems).toEqual({ S2: ['a'] });
  });

  it('holds what each ingredient contains but not which allergies the user has', () => {
    const text = JSON.stringify(saved());

    expect(text).toContain('shrimp');
    expect(text).not.toContain('allergenCodes');
    expect(text).not.toContain('allergyConsent');
  });
});
