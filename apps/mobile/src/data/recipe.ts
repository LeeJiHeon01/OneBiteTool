import { type IconName } from '@/components/icon';
import { type HeatLevel } from '@/data/catalog';

// What the recipe screens draw. The values come from the server (src/api); the app never
// computes amounts, times, heat or safety notes itself.

// tool: the plain picture of the measuring tool, with no fill mark.
export type AmountVisualType = 'cup' | 'tool' | 'bowl' | 'spoon' | 'spoon-small' | 'egg' | 'piece';

export type QuantityStatus = 'ORIGINAL' | 'CONVERTED_APPROX' | 'NEEDS_CHECK';

export type RecipeBadge = 'VERIFIED' | 'IMPORTED' | 'WEB_SEARCH';

export type AllergenRef = { code: string; hidden: boolean };

export type SearchResult = {
  id: string;
  title: string;
  ingredientCount: number;
  stepCount: number;
  minutes: number | null;
  badge: RecipeBadge;
  sourceDomain: string | null;
  allergenCodes: string[];
  // An ingredient is outside the verified allergen mapping, so "no allergens" is not known.
  allergyCheckNeeded: boolean;
};

// AI_RESEARCHED: a conservative mapping from public sources that no person has confirmed.
export type ReviewLevel = 'HUMAN_VERIFIED' | 'AI_RESEARCHED';

export type RecipeIngredient = {
  id: string;
  name: string;
  // Amount as written in the source; always shown.
  rawText: string;
  allergens: AllergenRef[];
  allergyCheckNeeded: boolean;
  // Null when the allergen mapping is not reviewed (same as allergyCheckNeeded).
  allergenReviewLevel: ReviewLevel | null;
  // Processed ingredient whose allergens differ by product (soy sauce, curry, ham).
  allergenProductVaries: boolean;
};

export type Recipe = {
  id: string;
  name: string;
  badge: RecipeBadge;
  sourceUrl: string | null;
  servings: number;
  minutes: number | null;
  requiredTools: { id: string; label: string; toolIds: string[] }[];
  ingredients: RecipeIngredient[];
  steps: { order: number; text: string }[];
};

export type AdaptedIngredient = {
  id: string;
  name: string;
  originalText: string;
  displayText: string;
  visual: AmountVisualType;
  visualToolId: string | null;
  quantityStatus: QuantityStatus;
  // Seasoning: never rounded up, the user tastes and adds.
  tasteAdjust: boolean;
  // Only the allergens the user asked to avoid.
  allergens: AllergenRef[];
  allergyCheckNeeded: boolean;
  // The ingredient's allergens differ by product; shown to users with allergies only.
  allergenProductVaries: boolean;
};

export type AdaptedStep = {
  id: string;
  text: string;
  // Extra explanation for the user's skill level; null when there is none.
  details: string | null;
  actionLabel: string;
  actionIcon: IconName;
  ingredientIds: string[];
  heatLevel: HeatLevel | null;
  // Oven, air fryer or microwave setting exactly as the source states it.
  deviceSetting: string | null;
  durationSeconds: number | null;
  timerStartCondition: string | null;
  completionCheck: string | null;
  safetyNotes: string[];
};

export type PendingQuestion = { ingredientId: string; substituteId: string; title: string; description: string };

export type Adaptation = {
  status: 'COOKABLE' | 'NEEDS_CONFIRMATION' | 'NOT_COOKABLE';
  requiredItems: { kind: 'INGREDIENT' | 'TOOL'; id: string; name: string; reason: string }[];
  pendingQuestions: PendingQuestion[];
  changes: { id: string; text: string; impact: string | null }[];
  safetySummary: string[];
  ingredients: AdaptedIngredient[];
  steps: AdaptedStep[];
};

export const BADGE_LABELS: Record<RecipeBadge, string> = {
  VERIFIED: '확인한 레시피',
  IMPORTED: '확인 전 레시피',
  WEB_SEARCH: '웹에서 가져온 레시피',
};
