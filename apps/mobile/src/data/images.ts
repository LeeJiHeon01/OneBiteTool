import { type ImageSource } from 'expo-image';

import { type HeatLevel, type HeatSource } from '@/data/catalog';

// Prepared artwork only. Files are generated from assets/source/images (see its README
// and manifest.json); a key that is missing here has no picture and must not be invented.

const TOOL_IMAGES: Record<string, ImageSource> = {
  spoon_rice: require('@/assets/images/tools/spoon_rice.png'),
  teaspoon: require('@/assets/images/tools/teaspoon.png'),
  paper_cup_small: require('@/assets/images/tools/paper_cup_small.png'),
  paper_cup_large: require('@/assets/images/tools/paper_cup_large.png'),
  glass_cup: require('@/assets/images/tools/glass_cup.png'),
  knife: require('@/assets/images/tools/knife.png'),
  scissors: require('@/assets/images/tools/scissors.png'),
  gas_stove: require('@/assets/images/tools/gas_stove.png'),
  induction: require('@/assets/images/tools/induction.png'),
  microwave: require('@/assets/images/tools/microwave.png'),
  air_fryer: require('@/assets/images/tools/air_fryer.png'),
  rice_cooker: require('@/assets/images/tools/rice_cooker.png'),
  pot: require('@/assets/images/tools/pot.png'),
  frying_pan: require('@/assets/images/tools/frying_pan.png'),
  microwave_container: require('@/assets/images/tools/microwave_container.png'),
  rice_bowl: require('@/assets/images/tools/rice_bowl.png'),
  measuring_spoon: require('@/assets/images/tools/measuring_spoon.png'),
  measuring_cup: require('@/assets/images/tools/measuring_cup.png'),
  water_bottle_500: require('@/assets/images/tools/water_bottle_500.png'),
  soup_bowl: require('@/assets/images/tools/soup_bowl.png'),
  rice_cup: require('@/assets/images/tools/rice_cup.png'),
  soju_glass: require('@/assets/images/tools/soju_glass.png'),
  slicer: require('@/assets/images/tools/slicer.png'),
  grater: require('@/assets/images/tools/grater.png'),
  peeler: require('@/assets/images/tools/peeler.png'),
  oven: require('@/assets/images/tools/oven.png'),
  steamer: require('@/assets/images/tools/steamer.png'),
  earthen_pot: require('@/assets/images/tools/earthen_pot.png'),
};

export function toolImage(toolId: string): ImageSource | undefined {
  return TOOL_IMAGES[toolId];
}

// One picture per heat level and stove type (heat.{gas|induction}.{level}).
export const HEAT_IMAGES: Record<HeatSource, Record<HeatLevel, ImageSource>> = {
  gas_stove: {
    LOW: require('@/assets/images/heat/gas_low.png'),
    MEDIUM_LOW: require('@/assets/images/heat/gas_medium_low.png'),
    MEDIUM: require('@/assets/images/heat/gas_medium.png'),
    MEDIUM_HIGH: require('@/assets/images/heat/gas_medium_high.png'),
    HIGH: require('@/assets/images/heat/gas_high.png'),
  },
  induction: {
    LOW: require('@/assets/images/heat/induction_low.png'),
    MEDIUM_LOW: require('@/assets/images/heat/induction_medium_low.png'),
    MEDIUM: require('@/assets/images/heat/induction_medium.png'),
    MEDIUM_HIGH: require('@/assets/images/heat/induction_medium_high.png'),
    HIGH: require('@/assets/images/heat/induction_high.png'),
  },
};

export const ILLUSTRATIONS = {
  welcome: require('@/assets/images/illustrations/welcome.png') as ImageSource,
  onboardingDone: require('@/assets/images/illustrations/onboarding_done.png') as ImageSource,
  searching: require('@/assets/images/illustrations/searching.png') as ImageSource,
  notFound: require('@/assets/images/illustrations/not_found.png') as ImageSource,
  notCookable: require('@/assets/images/illustrations/not_cookable.png') as ImageSource,
  cookingDone: require('@/assets/images/illustrations/cooking_done.png') as ImageSource,
};
