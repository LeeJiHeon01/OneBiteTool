// The volume of the user's own cup or bowl, found with a 500 mL water bottle whose volume is
// printed on its label (spec 4.8). Only the tool's volume is worked out here; turning recipe
// amounts into fills of the tool is always done by the server.

export const BOTTLE_ML = 500;
export const MIN_VOLUME_ML = 20;
export const MAX_VOLUME_ML = 1000;

// Tools whose volume can be measured this way (data/seed/units.json customVolumeTools).
// Spoons, standard measuring tools and the water bottle itself are not.
export const CALIBRATABLE_TOOL_IDS = [
  'paper_cup_small',
  'paper_cup_large',
  'glass_cup',
  'rice_bowl',
  'soup_bowl',
  'rice_cup',
  'soju_glass',
];

// "How many times does one bottle fill this tool?"
export const FILL_COUNTS = [1, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10, 12];

export type VolumeMethod = 'WATER_BOTTLE' | 'DIRECT';

export type CustomToolVolume = {
  toolCode: string;
  volumeMl: number;
  method: VolumeMethod;
  // ISO time the user measured it.
  measuredAt: string;
};

export function isCalibratable(toolId: string): boolean {
  return CALIBRATABLE_TOOL_IDS.includes(toolId);
}

export function isValidVolume(volumeMl: number): boolean {
  return Number.isFinite(volumeMl) && volumeMl >= MIN_VOLUME_ML && volumeMl <= MAX_VOLUME_ML;
}

// One bottle filled the tool `count` times, so the tool holds 500 / count mL.
export function volumeFromFills(count: number): number {
  return Math.round(BOTTLE_ML / count);
}

// "1½번", "2번"
export function fillCountLabel(count: number): string {
  const whole = Math.floor(count);
  return `${whole}${count === whole ? '' : '½'}번`;
}

// A typed mL value; null unless it is a whole number inside the accepted range.
export function parseDirectVolume(text: string): number | null {
  const trimmed = text.trim();
  if (!/^\d+$/.test(trimmed)) return null;
  const value = Number(trimmed);
  return isValidVolume(value) ? value : null;
}

export function findVolume(volumes: CustomToolVolume[], toolId: string): CustomToolVolume | undefined {
  return volumes.find((item) => item.toolCode === toolId);
}

// One value per tool: measuring again replaces the earlier value.
export function withVolume(volumes: CustomToolVolume[], volume: CustomToolVolume): CustomToolVolume[] {
  return [...volumes.filter((item) => item.toolCode !== volume.toolCode), volume];
}

export function withoutVolume(volumes: CustomToolVolume[], toolId: string): CustomToolVolume[] {
  return volumes.filter((item) => item.toolCode !== toolId);
}

// What goes to the server with an adaptation request: only tools the user has selected.
export function volumesToSend(
  volumes: CustomToolVolume[],
  ownedToolIds: string[],
): { toolCode: string; volumeMl: number }[] {
  return volumes
    .filter((item) => ownedToolIds.includes(item.toolCode))
    .map(({ toolCode, volumeMl }) => ({ toolCode, volumeMl }));
}

// Stored values come from an earlier run of the app, so their shape is checked before use.
export function sanitizeVolumes(value: unknown): CustomToolVolume[] {
  if (!Array.isArray(value)) return [];
  let volumes: CustomToolVolume[] = [];
  for (const item of value) {
    if (typeof item !== 'object' || item === null) continue;
    const { toolCode, volumeMl, method, measuredAt } = item as Record<string, unknown>;
    if (typeof toolCode !== 'string' || !isCalibratable(toolCode)) continue;
    if (typeof volumeMl !== 'number' || !isValidVolume(volumeMl)) continue;
    volumes = withVolume(volumes, {
      toolCode,
      volumeMl,
      method: method === 'DIRECT' ? 'DIRECT' : 'WATER_BOTTLE',
      measuredAt: typeof measuredAt === 'string' ? measuredAt : '',
    });
  }
  return volumes;
}
