import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

import { getTool, HEAT_SOURCES, isAllergenCode, SKILL_LEVELS, type HeatSource, type SkillLevel } from '@/data/catalog';
import { sanitizeVolumes, type CustomToolVolume } from '@/data/tool-volume';

// The user's settings live on the device only (no accounts in phase 1). Allergies are health
// data, so the profile is kept in the encrypted store (iOS Keychain / Android Keystore).
// The web build is a development preview without an encrypted store: it keeps the other
// settings in localStorage and never writes allergies.
//
// The profile is split over four keys so that no single value comes near the size the
// encrypted store handles reliably (2048 bytes), and so that allergies sit in a key of
// their own, never mixed with anything else.

export type StoredProfile = {
  skillLevel: SkillLevel | null;
  toolIds: string[];
  // Volumes the user measured for their own cups and bowls (spec 4.8).
  customToolVolumes: CustomToolVolume[];
  preferredHeat: HeatSource;
  allergenCodes: string[];
  allergyConsent: boolean;
  onboarded: boolean;
};

export const PROFILE_KEYS = {
  skill: 'profile.skill.v1',
  tools: 'profile.tools.v1',
  toolVolumes: 'profile.tool-volumes.v1',
  allergy: 'profile.allergy.v1',
} as const;

type PartName = keyof typeof PROFILE_KEYS;

// Largest value one key of the encrypted store is allowed to hold.
export const MAX_VALUE_BYTES = 2048;

// The single key used before the profile was split; read once and then removed.
const LEGACY_KEY = 'profile.v1';
const isWeb = Platform.OS === 'web';

// What each key holds, as stored text.
export function toStoredParts(profile: StoredProfile): Record<PartName, string> {
  return {
    skill: JSON.stringify({ skillLevel: profile.skillLevel, onboarded: profile.onboarded }),
    tools: JSON.stringify({ toolIds: profile.toolIds, preferredHeat: profile.preferredHeat }),
    toolVolumes: JSON.stringify({ customToolVolumes: profile.customToolVolumes }),
    allergy: JSON.stringify({ allergenCodes: profile.allergenCodes, allergyConsent: profile.allergyConsent }),
  };
}

function strings(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
}

// Stored ids are checked against the current catalog, so a removed tool or allergen is dropped.
function sanitize(raw: Partial<StoredProfile>): StoredProfile {
  const skillLevel = SKILL_LEVELS.find((level) => level.id === raw.skillLevel)?.id ?? null;
  const allergenCodes = strings(raw.allergenCodes).filter(isAllergenCode);
  return {
    skillLevel,
    toolIds: strings(raw.toolIds).filter((id) => getTool(id) !== undefined),
    customToolVolumes: sanitizeVolumes(raw.customToolVolumes),
    preferredHeat: HEAT_SOURCES.find((id) => id === raw.preferredHeat) ?? 'gas_stove',
    allergenCodes,
    allergyConsent: raw.allergyConsent === true && allergenCodes.length > 0,
    onboarded: raw.onboarded === true && skillLevel !== null,
  };
}

async function readItem(key: string): Promise<string | null> {
  return isWeb ? (globalThis.localStorage?.getItem(key) ?? null) : SecureStore.getItemAsync(key);
}

async function writeItem(key: string, value: string): Promise<void> {
  if (isWeb) globalThis.localStorage?.setItem(key, value);
  else await SecureStore.setItemAsync(key, value);
}

async function removeItem(key: string): Promise<void> {
  if (isWeb) globalThis.localStorage?.removeItem(key);
  else await SecureStore.deleteItemAsync(key);
}

// A part that cannot be read counts as not stored; the other parts are still used.
async function readPart(name: PartName): Promise<Partial<StoredProfile> | null> {
  try {
    const text = await readItem(PROFILE_KEYS[name]);
    return text ? (JSON.parse(text) as Partial<StoredProfile>) : null;
  } catch {
    return null;
  }
}

// A profile saved before the split is moved to the new keys and the old key is removed.
async function migrateLegacy(): Promise<StoredProfile | null> {
  const text = await readItem(LEGACY_KEY);
  if (!text) return null;
  const profile = sanitize(JSON.parse(text));
  await saveProfile(profile);
  await removeItem(LEGACY_KEY);
  return profile;
}

// Returns null when nothing is stored or the stored value cannot be read.
export async function loadProfile(): Promise<StoredProfile | null> {
  try {
    const [skill, tools, toolVolumes, allergy] = await Promise.all([
      readPart('skill'),
      readPart('tools'),
      readPart('toolVolumes'),
      readPart('allergy'),
    ]);
    if (!skill && !tools && !toolVolumes && !allergy) return await migrateLegacy();
    return sanitize({ ...skill, ...tools, ...toolVolumes, ...allergy });
  } catch {
    return null;
  }
}

// What was last written to each key, so an unchanged part is not written again.
const lastWritten: Partial<Record<PartName, string>> = {};

// A failed write only means the settings are asked again on the next start.
export async function saveProfile(profile: StoredProfile): Promise<void> {
  const parts = toStoredParts(profile);
  for (const name of Object.keys(parts) as PartName[]) {
    // The web preview has no encrypted store, so allergies are never written there.
    if (isWeb && name === 'allergy') continue;
    if (lastWritten[name] === parts[name]) continue;
    try {
      await writeItem(PROFILE_KEYS[name], parts[name]);
      lastWritten[name] = parts[name];
    } catch {
      // Nothing to recover: the in-memory profile keeps working for this session.
    }
  }
}
