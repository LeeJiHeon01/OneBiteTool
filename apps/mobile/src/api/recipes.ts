import { get, post } from '@/api/client';
import { type IconName } from '@/components/icon';
import { type HeatLevel, type HeatSource, type SkillLevel } from '@/data/catalog';
import {
  type Adaptation,
  type AdaptedIngredient,
  type AdaptedStep,
  type AmountVisualType,
  type QuantityStatus,
  type Recipe,
  type RecipeIngredient,
  type RecipeBadge,
  type SearchResult,
} from '@/data/recipe';

// Server responses (docs/spec.md 8.2, 8.3, 8.3.1) and their conversion to what the screens draw.

type SearchResultDto = {
  id: string;
  name: string;
  ingredientCount: number;
  stepCount: number;
  minutes: number | null;
  recipeBadge: RecipeBadge;
  sourceDomain: string | null;
  allergenCodes: string[];
  allergyCheckNeeded: boolean;
};

type DetailDto = {
  id: string;
  name: string;
  recipeBadge: RecipeBadge;
  sourceUrl: string | null;
  sourceDomain: string | null;
  baseServings: number;
  minutes: number | null;
  requiredTools: { id: string; label: string; toolIds: string[] }[];
  ingredients: RecipeIngredient[];
  steps: { order: number; action: string }[];
};

type AdaptationDto = {
  status: Adaptation['status'];
  ignoredCustomTools?: string[];
  allergyWarnings: { allergenCode: string; ingredientId: string; hidden: boolean }[];
  allergyCheckNeededIngredientIds: string[];
  pendingQuestions: { ingredientId: string; substituteId: string; substituteName: string }[];
  changes: { type: string; target: string; description: string; impact: string | null }[];
  requiredItems: Adaptation['requiredItems'];
  safetySummary: string[];
  ingredients: {
    ingredientId: string;
    name: string;
    original: { rawText: string };
    display: {
      toolVariantId: string | null;
      count: number | null;
      text: string;
      tasteAdjust: boolean;
      userMeasured: boolean;
    };
    quantityStatus: QuantityStatus;
    allergenProductVaries: boolean;
  }[];
  steps: {
    stepId: string;
    action: string;
    actionKey: string | null;
    details: string | null;
    ingredientIds: string[];
    durationSeconds: number | null;
    timerStartCondition: string | null;
    heatLevel: HeatLevel | 'OFF' | null;
    deviceSettingRaw: string | null;
    completionCheck: string | null;
    safetyNotes: { message: string; detail: string | null }[];
    subItems: { stepId: string; text: string; ingredientIds: string[] }[];
  }[];
};

export type ImportJob = {
  id: string;
  status: 'PENDING' | 'SEARCHING' | 'EXTRACTING' | 'VERIFYING' | 'DONE' | 'NOT_FOUND' | 'FAILED';
  recipeId: string | null;
};

export type SubstituteAnswer = { ingredientId: string; substituteId: string; hasIt: boolean };

export type AdaptationInput = {
  recipe: Recipe;
  missingIngredientIds: string[];
  substituteAnswers: SubstituteAnswer[];
  skillLevel: SkillLevel;
  toolIds: string[];
  // Volumes the user measured for their own tools; the server converts with them and stores nothing.
  customToolVolumes: { toolCode: string; volumeMl: number }[];
  heatSource: HeatSource;
  allergenCodes: string[];
};

const ACTIONS: Record<string, { label: string; icon: IconName }> = {
  'action.cut': { label: '썰기', icon: 'knife' },
  'action.heat_oil': { label: '팬 달구기', icon: 'pan' },
  'action.stir_fry': { label: '볶기', icon: 'pan' },
  'action.boil': { label: '끓이기', icon: 'pan' },
  'action.grill': { label: '굽기', icon: 'pan' },
  'action.add': { label: '넣기', icon: 'spoon' },
  'action.mix': { label: '섞기', icon: 'spoon' },
  'action.plate': { label: '담기', icon: 'spoon' },
};
const OTHER_ACTION = { label: '조리', icon: 'sparkle' as IconName };

// Picture shown next to an amount, by the tool the server measured with.
const VISUALS: Record<string, AmountVisualType> = {
  spoon_rice: 'spoon',
  teaspoon: 'spoon-small',
  paper_cup_small: 'cup',
  paper_cup_large: 'cup',
  glass_cup: 'cup',
  rice_bowl: 'bowl',
  soup_bowl: 'bowl',
};

function hasFinalConsonant(word: string): boolean {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  return code >= 0 && code <= 11171 && code % 28 !== 0;
}

// "(으)로" takes "로" after a vowel or a final ㄹ.
function withDirection(word: string): string {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  const endsInRieul = code >= 0 && code <= 11171 && code % 28 === 8;
  return `${word}${hasFinalConsonant(word) && !endsInRieul ? '으로' : '로'}`;
}

function toSearchResult(dto: SearchResultDto): SearchResult {
  return {
    id: dto.id,
    title: dto.name,
    ingredientCount: dto.ingredientCount,
    stepCount: dto.stepCount,
    minutes: dto.minutes,
    badge: dto.recipeBadge,
    sourceDomain: dto.sourceDomain,
    allergenCodes: dto.allergenCodes,
    allergyCheckNeeded: dto.allergyCheckNeeded,
  };
}

function toRecipe(dto: DetailDto): Recipe {
  return {
    id: dto.id,
    name: dto.name,
    badge: dto.recipeBadge,
    sourceUrl: dto.sourceUrl,
    servings: dto.baseServings,
    minutes: dto.minutes,
    requiredTools: dto.requiredTools,
    ingredients: dto.ingredients,
    steps: dto.steps.map((step) => ({ order: step.order, text: step.action })),
  };
}

// A result card for a recipe that was just imported, built from its detail.
function detailToSearchResult(dto: DetailDto): SearchResult {
  const codes = dto.ingredients.flatMap((ingredient) => ingredient.allergens.map((allergen) => allergen.code));
  return {
    id: dto.id,
    title: dto.name,
    ingredientCount: dto.ingredients.length,
    stepCount: dto.steps.length,
    minutes: dto.minutes,
    badge: dto.recipeBadge,
    sourceDomain: dto.sourceDomain,
    allergenCodes: [...new Set(codes)],
    allergyCheckNeeded: dto.ingredients.some((ingredient) => ingredient.allergyCheckNeeded),
  };
}

// The cup drawing has only a "full" line: marks for fractions must be computed from the cup's
// dimensions, so an amount with a fraction shows the plain cup instead of a wrong fill line.
function visualOf(display: AdaptationDto['ingredients'][number]['display']): AmountVisualType {
  const visual = (display.toolVariantId && VISUALS[display.toolVariantId]) || 'piece';
  if (visual === 'cup' && !Number.isInteger(display.count)) return 'tool';
  return visual;
}

function toIngredient(dto: AdaptationDto, row: AdaptationDto['ingredients'][number]): AdaptedIngredient {
  return {
    id: row.ingredientId,
    name: row.name,
    originalText: row.original.rawText,
    displayText: row.display.text,
    visual: visualOf(row.display),
    visualToolId: row.display.toolVariantId,
    quantityStatus: row.quantityStatus,
    tasteAdjust: row.display.tasteAdjust,
    userMeasured: row.display.userMeasured === true,
    allergens: dto.allergyWarnings
      .filter((warning) => warning.ingredientId === row.ingredientId)
      .map((warning) => ({ code: warning.allergenCode, hidden: warning.hidden })),
    allergyCheckNeeded: dto.allergyCheckNeededIngredientIds.includes(row.ingredientId),
    allergenProductVaries: row.allergenProductVaries,
  };
}

function toStep(step: AdaptationDto['steps'][number]): AdaptedStep {
  const action = (step.actionKey && ACTIONS[step.actionKey]) || OTHER_ACTION;
  return {
    id: step.stepId,
    text: step.action,
    details: step.details,
    actionLabel: action.label,
    actionIcon: action.icon,
    ingredientIds: step.ingredientIds,
    heatLevel: step.heatLevel === 'OFF' ? null : step.heatLevel,
    deviceSetting: step.deviceSettingRaw,
    durationSeconds: step.durationSeconds,
    timerStartCondition: step.timerStartCondition,
    completionCheck: step.completionCheck,
    safetyNotes: step.safetyNotes.map((note) => (note.detail ? `${note.message} ${note.detail}` : note.message)),
    subItems: (step.subItems ?? []).map((item) => ({
      id: item.stepId,
      text: item.text,
      ingredientIds: item.ingredientIds,
    })),
  };
}

function toAdaptation(dto: AdaptationDto, recipe: Recipe): Adaptation {
  const nameOf = (id: string) => recipe.ingredients.find((ingredient) => ingredient.id === id)?.name ?? '';
  return {
    status: dto.status,
    requiredItems: dto.requiredItems,
    pendingQuestions: dto.pendingQuestions.map((question) => ({
      ingredientId: question.ingredientId,
      substituteId: question.substituteId,
      title: `${nameOf(question.ingredientId)}${hasFinalConsonant(nameOf(question.ingredientId)) ? '이' : '가'} 없으시군요`,
      description: `${withDirection(question.substituteName)} 바꿀 수 있어요.\n집에 있나요?`,
    })),
    changes: dto.changes.map((change) => ({ id: change.target, text: change.description, impact: change.impact })),
    safetySummary: dto.safetySummary,
    ingredients: dto.ingredients.map((row) => toIngredient(dto, row)),
    steps: dto.steps.map(toStep),
    ignoredCustomToolIds: dto.ignoredCustomTools ?? [],
  };
}

export async function searchRecipes(query: string): Promise<SearchResult[]> {
  const results = await get<SearchResultDto[]>(`/api/recipes?q=${encodeURIComponent(query)}`);
  return results.map(toSearchResult);
}

// Recipes the server already has; opening one needs no web import.
export async function fetchSuggestions(): Promise<SearchResult[]> {
  const results = await get<SearchResultDto[]>('/api/recipes/suggestions');
  return results.map(toSearchResult);
}

export async function fetchRecipe(id: string): Promise<Recipe> {
  return toRecipe(await get<DetailDto>(`/api/recipes/${id}`));
}

export async function fetchSearchResult(recipeId: string): Promise<SearchResult> {
  return detailToSearchResult(await get<DetailDto>(`/api/recipes/${recipeId}`));
}

// Tools only help pick a page the user can cook from; allergies are never sent here.
export function startImport(query: string, toolIds: string[]): Promise<ImportJob> {
  return post<ImportJob>('/api/recipe-imports', { query, toolVariantIds: toolIds });
}

export function fetchImport(id: string): Promise<ImportJob> {
  return get<ImportJob>(`/api/recipe-imports/${id}`);
}

export async function adaptRecipe(input: AdaptationInput): Promise<Adaptation> {
  const dto = await post<AdaptationDto>('/api/recipe-adaptations', {
    recipeId: input.recipe.id,
    servings: input.recipe.servings,
    missingIngredientIds: input.missingIngredientIds,
    substituteAnswers: input.substituteAnswers,
    profile: {
      skillLevel: input.skillLevel,
      toolVariantIds: input.toolIds,
      defaultHeatSource: input.heatSource,
      allergenCodes: input.allergenCodes,
      customToolVolumes: input.customToolVolumes,
    },
  });
  return toAdaptation(dto, input.recipe);
}

const IMPORT_POLL_MS = 2000;
// An import needs a web search, a page download and an extraction per candidate.
const IMPORT_MAX_POLLS = 120;

// Brings a dish in from the web and waits for the result. Null means nothing passed the
// server's check against the source page ("not found").
export async function importRecipe(
  query: string,
  toolIds: string[],
  isCancelled: () => boolean,
): Promise<SearchResult | null> {
  let job = await startImport(query, toolIds);
  for (let poll = 0; poll < IMPORT_MAX_POLLS && !isCancelled(); poll++) {
    if (job.status === 'DONE' && job.recipeId) return fetchSearchResult(job.recipeId);
    if (job.status === 'DONE' || job.status === 'NOT_FOUND' || job.status === 'FAILED') return null;
    await new Promise((resolve) => setTimeout(resolve, IMPORT_POLL_MS));
    job = await fetchImport(job.id);
  }
  return null;
}
