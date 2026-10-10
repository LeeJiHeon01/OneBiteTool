import { describe, expect, it } from '@jest/globals';

import { ALLERGEN_GROUPS, STANDARD_MEASURE_TOOLS, TOOL_GROUPS } from '@/data/catalog';
import { CALIBRATABLE_TOOL_IDS, type CustomToolVolume } from '@/data/tool-volume';
import { MAX_VALUE_BYTES, PROFILE_KEYS, toStoredParts, type StoredProfile } from '@/state/profile-storage';

function bytes(text: string): number {
  return new TextEncoder().encode(text).length;
}

// The largest profile a user can have: every tool, every measurable tool measured, every allergen.
function largestProfile(): StoredProfile {
  const customToolVolumes: CustomToolVolume[] = CALIBRATABLE_TOOL_IDS.map((toolCode) => ({
    toolCode,
    volumeMl: 1000,
    method: 'WATER_BOTTLE',
    measuredAt: '2026-10-10T04:43:13.282Z',
  }));
  return {
    skillLevel: 'HIGH',
    toolIds: [...STANDARD_MEASURE_TOOLS, ...TOOL_GROUPS.flatMap((group) => group.tools)].map((tool) => tool.id),
    customToolVolumes,
    preferredHeat: 'induction',
    allergenCodes: ALLERGEN_GROUPS.flatMap((group) => group.allergens).map((allergen) => allergen.code),
    allergyConsent: true,
    onboarded: true,
  };
}

describe('profile keys', () => {
  it('keeps every stored value under the size limit for the largest profile', () => {
    const profile = largestProfile();
    const parts = toStoredParts(profile);

    expect(profile.toolIds).toHaveLength(28);
    expect(profile.allergenCodes).toHaveLength(19);
    for (const text of Object.values(parts)) {
      expect(bytes(text)).toBeLessThan(MAX_VALUE_BYTES);
    }
    // Measured sizes in bytes; a change here means the stored shape changed.
    expect({
      skill: bytes(parts.skill),
      tools: bytes(parts.tools),
      toolVolumes: bytes(parts.toolVolumes),
      allergy: bytes(parts.allergy),
    }).toEqual({ skill: 38, tools: 394, toolVolumes: 770, allergy: 211 });
  });

  it('keeps allergies in a key of their own', () => {
    const parts = toStoredParts(largestProfile());

    expect(Object.keys(JSON.parse(parts.allergy)).sort()).toEqual(['allergenCodes', 'allergyConsent']);
    for (const name of ['skill', 'tools', 'toolVolumes'] as const) {
      expect(parts[name]).not.toContain('allerg');
      expect(parts[name]).not.toContain('shrimp');
    }
    expect(new Set(Object.values(PROFILE_KEYS)).size).toBe(4);
  });
});
