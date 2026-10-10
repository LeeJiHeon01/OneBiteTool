import { type Adaptation, type Recipe } from '@/data/recipe';
import { type TimerState } from '@/state/use-timer';

// The dish being cooked, kept on the device so a closed app can pick it up again: the recipe,
// the adapted steps, the current step, the timer and the ticked checklist items. It holds
// what the recipe says, including the allergens each ingredient contains, but nothing about
// which allergies the user has: those stay in the encrypted profile and are compared on screen.

export type SavedCooking = {
  // Epoch ms of the last change; a session older than a day is dropped without asking.
  savedAt: number;
  recipe: Recipe;
  adaptation: Adaptation;
  stepIndex: number;
  timer: TimerState | null;
  // step id -> ids of the ticked checklist items
  checkedItems: Record<string, string[]>;
};

export const MAX_AGE_MS = 24 * 60 * 60 * 1000;

export function isFresh(saved: SavedCooking, now: number): boolean {
  return now - saved.savedAt >= 0 && now - saved.savedAt < MAX_AGE_MS;
}

// The timer as it stands now. Remaining time always comes from the end time: a timer whose
// end has passed while the app was closed is shown as finished. It never moves the step on.
export function timerAt(timer: TimerState | null, now: number): TimerState | null {
  if (!timer) return null;
  if (timer.status === 'running' && (timer.endAt === null || timer.endAt <= now)) {
    return { ...timer, status: 'finished', endAt: null };
  }
  return timer;
}

// The same dish from the first step: no timer, nothing ticked.
export function fromTheStart(saved: SavedCooking): SavedCooking {
  return { ...saved, stepIndex: 0, timer: null, checkedItems: {} };
}

export function toggleChecked(
  checkedItems: Record<string, string[]>,
  stepId: string,
  itemId: string,
): Record<string, string[]> {
  const current = checkedItems[stepId] ?? [];
  const next = current.includes(itemId) ? current.filter((id) => id !== itemId) : [...current, itemId];
  return { ...checkedItems, [stepId]: next };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function sanitizeTimer(value: unknown, stepIds: string[]): TimerState | null {
  if (!isRecord(value)) return null;
  const { stepId, stepNumber, status, durationSeconds, endAt, remainingOnPause } = value;
  if (typeof stepId !== 'string' || !stepIds.includes(stepId)) return null;
  if (status !== 'running' && status !== 'paused' && status !== 'finished') return null;
  if (typeof stepNumber !== 'number' || typeof durationSeconds !== 'number' || durationSeconds <= 0) return null;
  return {
    stepId,
    stepNumber,
    status,
    durationSeconds,
    endAt: typeof endAt === 'number' ? endAt : null,
    remainingOnPause: typeof remainingOnPause === 'number' ? remainingOnPause : null,
  };
}

// Stored values come from an earlier run of the app, so their shape is checked before use.
// Anything that does not look like a cooking session gives null: nothing is offered to resume.
export function sanitizeSaved(value: unknown): SavedCooking | null {
  if (!isRecord(value)) return null;
  const { savedAt, recipe, adaptation, stepIndex, timer, checkedItems } = value;
  if (typeof savedAt !== 'number' || !isRecord(recipe) || !isRecord(adaptation)) return null;
  if (typeof recipe.id !== 'string' || typeof recipe.name !== 'string') return null;
  const steps = adaptation.steps;
  if (!Array.isArray(steps) || steps.length === 0 || !Array.isArray(adaptation.ingredients)) return null;
  if (!steps.every((step) => isRecord(step) && typeof step.id === 'string' && typeof step.text === 'string')) {
    return null;
  }
  const stepIds = steps.map((step) => (step as { id: string }).id);
  const index = typeof stepIndex === 'number' && Number.isInteger(stepIndex) ? stepIndex : 0;
  const checked: Record<string, string[]> = {};
  if (isRecord(checkedItems)) {
    for (const [stepId, ids] of Object.entries(checkedItems)) {
      if (stepIds.includes(stepId) && Array.isArray(ids)) {
        checked[stepId] = ids.filter((id): id is string => typeof id === 'string');
      }
    }
  }
  return {
    savedAt,
    recipe: recipe as unknown as Recipe,
    adaptation: adaptation as unknown as Adaptation,
    stepIndex: Math.min(Math.max(0, index), steps.length - 1),
    timer: sanitizeTimer(timer, stepIds),
    checkedItems: checked,
  };
}
