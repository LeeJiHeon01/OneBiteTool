import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

import { getTool, HEAT_SOURCES, isAllergenCode, SKILL_LEVELS, type HeatSource, type SkillLevel } from '@/data/catalog';

// The user's settings live on the device only (no accounts in phase 1). Allergies are health
// data, so the profile is kept in the encrypted store (iOS Keychain / Android Keystore).
// The web build is a development preview without an encrypted store: it keeps the other
// settings in localStorage and never writes allergies.

export type StoredProfile = {
  skillLevel: SkillLevel | null;
  toolIds: string[];
  preferredHeat: HeatSource;
  allergenCodes: string[];
  allergyConsent: boolean;
  onboarded: boolean;
};

const KEY = 'profile.v1';
const isWeb = Platform.OS === 'web';

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
    preferredHeat: HEAT_SOURCES.find((id) => id === raw.preferredHeat) ?? 'gas_stove',
    allergenCodes,
    allergyConsent: raw.allergyConsent === true && allergenCodes.length > 0,
    onboarded: raw.onboarded === true && skillLevel !== null,
  };
}

// Returns null when nothing is stored or the stored value cannot be read.
export async function loadProfile(): Promise<StoredProfile | null> {
  try {
    const text = isWeb ? globalThis.localStorage?.getItem(KEY) : await SecureStore.getItemAsync(KEY);
    return text ? sanitize(JSON.parse(text)) : null;
  } catch {
    return null;
  }
}

// A failed write only means the settings are asked again on the next start.
export async function saveProfile(profile: StoredProfile): Promise<void> {
  try {
    if (isWeb) {
      const withoutAllergies = { ...profile, allergenCodes: [], allergyConsent: false };
      globalThis.localStorage?.setItem(KEY, JSON.stringify(withoutAllergies));
    } else {
      await SecureStore.setItemAsync(KEY, JSON.stringify(profile));
    }
  } catch {
    // Nothing to recover: the in-memory profile keeps working for this session.
  }
}
