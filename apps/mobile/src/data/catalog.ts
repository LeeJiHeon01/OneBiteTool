// Reference data shown in onboarding. Sample copy of what GET /api/tools will serve;
// tools and allergens are always identified by id, never by display name.

export type SkillLevel = 'LOW' | 'MID' | 'HIGH';

export const SKILL_LEVELS: { id: SkillLevel; title: string; description: string }[] = [
  { id: 'LOW', title: '처음 해봐요', description: '담는 법, 불 조절까지 자세히' },
  { id: 'MID', title: '기본 요리는 해요', description: '계량과 주요 과정 위주로' },
  { id: 'HIGH', title: '요리에 익숙해요', description: '핵심만 간단하게' },
];

export type Tool = { id: string; name: string; note?: string };

// A group shows its first VISIBLE_TOOL_COUNT tools; the rest go under "더 있어요".
// List order follows tool_variant.sort_order (is_basic = within the first six).
export const VISIBLE_TOOL_COUNT = 6;

// Standard measuring tools are asked about first: with them amounts are shown in the
// recipe's own units (tool_variant.is_standard_measure).
export const STANDARD_MEASURE_TOOLS: Tool[] = [
  { id: 'measuring_spoon', name: '계량스푼', note: '큰술·작은술' },
  { id: 'measuring_cup', name: '계량컵' },
];

export const TOOL_GROUPS: { id: string; title: string; tools: Tool[] }[] = [
  {
    id: 'measure',
    title: '계량',
    tools: [
      { id: 'spoon_rice', name: '밥숟가락' },
      { id: 'teaspoon', name: '티스푼' },
      { id: 'paper_cup_small', name: '종이컵', note: '자판기 컵' },
      { id: 'glass_cup', name: '유리컵' },
      { id: 'water_bottle_500', name: '생수병', note: '500mL' },
      { id: 'rice_bowl', name: '밥공기' },
      { id: 'paper_cup_large', name: '큰 종이컵', note: '테이크아웃 컵' },
      { id: 'soup_bowl', name: '국그릇' },
      { id: 'rice_cup', name: '쌀컵', note: '밥솥 계량컵' },
      { id: 'soju_glass', name: '소주잔' },
    ],
  },
  {
    id: 'cut',
    title: '자르기',
    tools: [
      { id: 'knife', name: '칼' },
      { id: 'scissors', name: '가위' },
      { id: 'peeler', name: '필러', note: '감자칼' },
      { id: 'slicer', name: '채칼' },
      { id: 'grater', name: '강판' },
    ],
  },
  {
    id: 'heat',
    title: '가열 기기',
    tools: [
      { id: 'gas_stove', name: '가스레인지' },
      { id: 'induction', name: '인덕션' },
      { id: 'microwave', name: '전자레인지' },
      { id: 'air_fryer', name: '에어프라이어' },
      { id: 'rice_cooker', name: '전기밥솥' },
      { id: 'oven', name: '오븐', note: '광파오븐 포함' },
    ],
  },
  {
    id: 'vessel',
    title: '조리 용기',
    tools: [
      { id: 'pot', name: '냄비' },
      { id: 'frying_pan', name: '프라이팬' },
      { id: 'microwave_container', name: '전자레인지용 용기' },
      { id: 'steamer', name: '찜기' },
      { id: 'earthen_pot', name: '뚝배기' },
    ],
  },
];

const TOOLS_BY_ID = new Map(
  [...STANDARD_MEASURE_TOOLS, ...TOOL_GROUPS.flatMap((group) => group.tools)].map((tool) => [tool.id, tool]),
);

export function getTool(id: string): Tool | undefined {
  return TOOLS_BY_ID.get(id);
}

export type HeatSource = 'gas_stove' | 'induction';

export const HEAT_SOURCES: HeatSource[] = ['gas_stove', 'induction'];

// With both stoves selected the user's pick wins; otherwise use whichever they own.
export function resolveHeatSource(toolIds: string[], preferred: HeatSource): HeatSource {
  const hasGas = toolIds.includes('gas_stove');
  const hasInduction = toolIds.includes('induction');
  if (hasGas && hasInduction) return preferred;
  return hasInduction ? 'induction' : 'gas_stove';
}

export type Allergen = { code: string; name: string };

export const ALLERGEN_GROUPS: { id: string; title: string; allergens: Allergen[] }[] = [
  {
    id: 'grain',
    title: '곡물·콩',
    allergens: [
      { code: 'wheat', name: '밀' },
      { code: 'buckwheat', name: '메밀' },
      { code: 'soybean', name: '대두' },
      { code: 'peanut', name: '땅콩' },
    ],
  },
  {
    id: 'egg_dairy',
    title: '알·유제품',
    allergens: [
      { code: 'egg', name: '알류' },
      { code: 'milk', name: '우유' },
    ],
  },
  {
    id: 'meat',
    title: '고기',
    allergens: [
      { code: 'pork', name: '돼지고기' },
      { code: 'chicken', name: '닭고기' },
      { code: 'beef', name: '쇠고기' },
    ],
  },
  {
    id: 'seafood',
    title: '해산물',
    allergens: [
      { code: 'mackerel', name: '고등어' },
      { code: 'crab', name: '게' },
      { code: 'shrimp', name: '새우' },
      { code: 'squid', name: '오징어' },
      { code: 'shellfish', name: '조개류' },
    ],
  },
  {
    id: 'fruit_nut',
    title: '과일·견과',
    allergens: [
      { code: 'peach', name: '복숭아' },
      { code: 'tomato', name: '토마토' },
      { code: 'walnut', name: '호두' },
      { code: 'pine_nut', name: '잣' },
    ],
  },
  { id: 'etc', title: '기타', allergens: [{ code: 'sulfite', name: '아황산류' }] },
];

const ALLERGEN_NAMES = new Map(
  ALLERGEN_GROUPS.flatMap((group) => group.allergens).map((allergen) => [allergen.code, allergen.name]),
);

export function isAllergenCode(code: string): boolean {
  return ALLERGEN_NAMES.has(code);
}

export function allergenName(code: string): string {
  return ALLERGEN_NAMES.get(code) ?? code;
}

// Heat is stored as five levels (spec §7). The picture and the label both read from
// this one table so they can never show different levels.
export type HeatLevel = 'LOW' | 'MEDIUM_LOW' | 'MEDIUM' | 'MEDIUM_HIGH' | 'HIGH';

export const HEAT_INFO: Record<HeatLevel, { label: string; step: number }> = {
  LOW: { label: '약불', step: 1 },
  MEDIUM_LOW: { label: '중약불', step: 2 },
  MEDIUM: { label: '중불', step: 3 },
  MEDIUM_HIGH: { label: '중강불', step: 4 },
  HIGH: { label: '강불', step: 5 },
};
