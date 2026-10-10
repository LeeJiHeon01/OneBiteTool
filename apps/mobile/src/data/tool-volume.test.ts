import { describe, expect, it } from '@jest/globals';

import {
  fillCountLabel,
  FILL_COUNTS,
  findVolume,
  isCalibratable,
  parseDirectVolume,
  sanitizeVolumes,
  volumeFromFills,
  volumesToSend,
  withoutVolume,
  withVolume,
  type CustomToolVolume,
} from '@/data/tool-volume';

const bowl: CustomToolVolume = {
  toolCode: 'soup_bowl',
  volumeMl: 250,
  method: 'WATER_BOTTLE',
  measuredAt: '2026-10-10T03:00:00.000Z',
};

describe('volume from a 500 mL water bottle', () => {
  it('divides the bottle by the number of fills', () => {
    expect(volumeFromFills(1)).toBe(500);
    expect(volumeFromFills(2)).toBe(250);
    expect(volumeFromFills(1.5)).toBe(333);
    expect(volumeFromFills(2.5)).toBe(200);
    expect(volumeFromFills(12)).toBe(42);
  });

  it('keeps every offered count inside the accepted range', () => {
    for (const count of FILL_COUNTS) {
      expect(volumeFromFills(count)).toBeGreaterThanOrEqual(20);
      expect(volumeFromFills(count)).toBeLessThanOrEqual(1000);
    }
  });

  it('labels half counts', () => {
    expect(fillCountLabel(1)).toBe('1번');
    expect(fillCountLabel(1.5)).toBe('1½번');
    expect(fillCountLabel(12)).toBe('12번');
  });
});

describe('typed volume', () => {
  it('accepts whole numbers from 20 to 1000 only', () => {
    expect(parseDirectVolume('250')).toBe(250);
    expect(parseDirectVolume(' 20 ')).toBe(20);
    expect(parseDirectVolume('1000')).toBe(1000);
    expect(parseDirectVolume('19')).toBeNull();
    expect(parseDirectVolume('1001')).toBeNull();
    expect(parseDirectVolume('2.5')).toBeNull();
    expect(parseDirectVolume('-50')).toBeNull();
    expect(parseDirectVolume('컵')).toBeNull();
    expect(parseDirectVolume('')).toBeNull();
  });
});

describe('which tools can be measured', () => {
  it('leaves out spoons, standard measuring tools and the bottle itself', () => {
    expect(isCalibratable('soup_bowl')).toBe(true);
    expect(isCalibratable('paper_cup_small')).toBe(true);
    for (const id of ['spoon_rice', 'teaspoon', 'measuring_spoon', 'measuring_cup', 'water_bottle_500', 'pot']) {
      expect(isCalibratable(id)).toBe(false);
    }
  });
});

describe('saved volumes', () => {
  it('keeps one value per tool and can go back to the representative volume', () => {
    const again = { ...bowl, volumeMl: 500 };
    const volumes = withVolume(withVolume([], bowl), again);

    expect(volumes).toEqual([again]);
    expect(findVolume(volumes, 'soup_bowl')?.volumeMl).toBe(500);
    expect(withoutVolume(volumes, 'soup_bowl')).toEqual([]);
  });

  it('sends only the tools the user has selected, without method or time', () => {
    const cup: CustomToolVolume = { ...bowl, toolCode: 'glass_cup', volumeMl: 300 };

    expect(volumesToSend([bowl, cup], ['soup_bowl', 'pot'])).toEqual([{ toolCode: 'soup_bowl', volumeMl: 250 }]);
  });

  it('drops stored values that are broken, out of range or for another tool', () => {
    const stored = [
      bowl,
      { ...bowl, toolCode: 'spoon_rice' },
      { ...bowl, toolCode: 'rice_bowl', volumeMl: 5000 },
      { ...bowl, toolCode: 'glass_cup', volumeMl: '300' },
      { toolCode: 'rice_cup', volumeMl: 160, method: 'OTHER' },
      null,
      'text',
    ];

    expect(sanitizeVolumes(stored)).toEqual([
      bowl,
      { toolCode: 'rice_cup', volumeMl: 160, method: 'WATER_BOTTLE', measuredAt: '' },
    ]);
    expect(sanitizeVolumes(undefined)).toEqual([]);
  });
});
