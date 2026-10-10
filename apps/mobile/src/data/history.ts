// Dishes the user has finished cooking, kept on the device only. A record holds the recipe's
// id and name, when it was finished and the user's own short feedback. Nothing about
// allergies or tools is kept, and nothing is sent to the server.

export type Rating = 'EASY' | 'OK' | 'HARD';

export const RATINGS: { id: Rating; label: string }[] = [
  { id: 'EASY', label: '쉬웠어요' },
  { id: 'OK', label: '할 만했어요' },
  { id: 'HARD', label: '어려웠어요' },
];

export type CookRecord = {
  // Unique per finished cook; the same recipe can be recorded many times.
  id: string;
  recipeId: string;
  recipeName: string;
  // ISO time the last step was finished.
  completedAt: string;
  // Null when the user skipped the question.
  rating: Rating | null;
  // 1-based numbers of the steps the user got stuck on; empty when none were picked.
  stuckStepOrders: number[];
};

// How many recently cooked dishes the home screen shows.
export const HOME_RECENT_COUNT = 5;
const MAX_RECORDS = 100;

export function newRecord(recipe: { id: string; name: string }, now: Date): CookRecord {
  return {
    id: `${now.getTime()}`,
    recipeId: recipe.id,
    recipeName: recipe.name,
    completedAt: now.toISOString(),
    rating: null,
    stuckStepOrders: [],
  };
}

// Newest first.
export function addRecord(records: CookRecord[], record: CookRecord): CookRecord[] {
  return [record, ...records.filter((item) => item.id !== record.id)].slice(0, MAX_RECORDS);
}

export function updateRecord(
  records: CookRecord[],
  id: string,
  change: Partial<Pick<CookRecord, 'rating' | 'stuckStepOrders'>>,
): CookRecord[] {
  return records.map((item) => (item.id === id ? { ...item, ...change } : item));
}

export function removeRecord(records: CookRecord[], id: string): CookRecord[] {
  return records.filter((item) => item.id !== id);
}

// Picking a step again takes it out; the result is kept in step order.
export function toggleStuckStep(orders: number[], order: number): number[] {
  const next = orders.includes(order) ? orders.filter((item) => item !== order) : [...orders, order];
  return next.sort((a, b) => a - b);
}

// The latest record of each dish, newest first: a dish cooked twice is listed once.
export function recentDishes(records: CookRecord[], count = HOME_RECENT_COUNT): CookRecord[] {
  const seen = new Set<string>();
  const dishes: CookRecord[] = [];
  for (const record of records) {
    if (seen.has(record.recipeId)) continue;
    seen.add(record.recipeId);
    dishes.push(record);
    if (dishes.length === count) break;
  }
  return dishes;
}

export function ratingLabel(rating: Rating | null): string | null {
  return RATINGS.find((item) => item.id === rating)?.label ?? null;
}

// "10월 10일" in the device's own time.
export function dateLabel(completedAt: string): string {
  const date = new Date(completedAt);
  return Number.isNaN(date.getTime()) ? '' : `${date.getMonth() + 1}월 ${date.getDate()}일`;
}

// Stored values come from an earlier run of the app, so their shape is checked before use.
// Only the known fields are kept.
export function sanitizeRecords(value: unknown): CookRecord[] {
  if (!Array.isArray(value)) return [];
  const records: CookRecord[] = [];
  for (const item of value) {
    if (typeof item !== 'object' || item === null) continue;
    const { id, recipeId, recipeName, completedAt, rating, stuckStepOrders } = item as Record<string, unknown>;
    if (typeof id !== 'string' || typeof recipeId !== 'string' || typeof recipeName !== 'string') continue;
    if (typeof completedAt !== 'string') continue;
    records.push({
      id,
      recipeId,
      recipeName,
      completedAt,
      rating: RATINGS.some((option) => option.id === rating) ? (rating as Rating) : null,
      stuckStepOrders: Array.isArray(stuckStepOrders)
        ? stuckStepOrders.filter((order): order is number => Number.isInteger(order) && (order as number) > 0)
        : [],
    });
  }
  return records.slice(0, MAX_RECORDS);
}
