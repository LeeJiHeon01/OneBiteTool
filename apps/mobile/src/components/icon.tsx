import { type ReactNode } from 'react';
import Svg, { Circle, Ellipse, Path } from 'react-native-svg';

import { colors } from '@/theme/tokens';

export type IconName =
  | 'arrow'
  | 'check'
  | 'search'
  | 'settings'
  | 'clock'
  | 'pan'
  | 'knife'
  | 'spoon'
  | 'flame'
  | 'pause'
  | 'play'
  | 'sparkle'
  | 'shield'
  | 'alert'
  | 'chevron'
  | 'speaker';

// Shapes drawn on a 24x24 grid; stroke and fill are inherited from the parent.
export const ICON_SHAPES: Record<IconName, ReactNode> = {
  arrow: <Path d="m15 18-6-6 6-6" />,
  check: <Path d="m5 12 4 4L19 6" />,
  search: (
    <>
      <Circle cx="11" cy="11" r="7" />
      <Path d="m20 20-4-4" />
    </>
  ),
  settings: (
    <>
      <Circle cx="12" cy="12" r="3" />
      <Path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.83 2.83-.06-.06a1.7 1.7 0 0 0-1.88-.34 1.7 1.7 0 0 0-1.03 1.56V21h-4v-.08A1.7 1.7 0 0 0 9 19.37a1.7 1.7 0 0 0-1.88.34l-.06.06-2.83-2.83.06-.06A1.7 1.7 0 0 0 4.63 15 1.7 1.7 0 0 0 3.08 14H3v-4h.08A1.7 1.7 0 0 0 4.63 9a1.7 1.7 0 0 0-.34-1.88l-.06-.06 2.83-2.83.06.06A1.7 1.7 0 0 0 9 4.63h.01A1.7 1.7 0 0 0 10 3.08V3h4v.08A1.7 1.7 0 0 0 15 4.63a1.7 1.7 0 0 0 1.88-.34l.06-.06 2.83 2.83-.06.06A1.7 1.7 0 0 0 19.37 9v.01A1.7 1.7 0 0 0 20.92 10H21v4h-.08A1.7 1.7 0 0 0 19.4 15Z" />
    </>
  ),
  clock: (
    <>
      <Circle cx="12" cy="12" r="9" />
      <Path d="M12 7v5l3 2" />
    </>
  ),
  pan: (
    <>
      <Path d="M4 10h12v3a6 6 0 0 1-12 0v-3Z" />
      <Path d="M16 11h5" />
    </>
  ),
  knife: (
    <>
      <Path d="m14 4-8 9h8V4Z" />
      <Path d="M14 13v7" />
    </>
  ),
  speaker: (
    <>
      <Path d="M11 5 6 9H3v6h3l5 4V5Z" />
      <Path d="M15.5 8.5a5 5 0 0 1 0 7" />
      <Path d="M18.5 5.5a9 9 0 0 1 0 13" />
    </>
  ),
  spoon: (
    <>
      <Ellipse cx="12" cy="7" rx="4" ry="5" />
      <Path d="M12 12v9" />
    </>
  ),
  flame: (
    <Path d="M13 2c1 5-3 6-3 10 0 2 1 3 2 3 2 0 3-2 2-5 3 2 5 5 4 8-1 3-4 4-7 4-5 0-8-3-7-8 1-4 4-7 9-12Z" />
  ),
  pause: (
    <>
      <Path d="M9 7v10" />
      <Path d="M15 7v10" />
    </>
  ),
  play: <Path d="m9 7 8 5-8 5V7Z" />,
  sparkle: <Path d="m12 3 1.5 5.5L19 10l-5.5 1.5L12 17l-1.5-5.5L5 10l5.5-1.5L12 3Z" />,
  shield: <Path d="M12 3 5 6v5c0 4.6 2.8 8 7 10 4.2-2 7-5.4 7-10V6l-7-3Z" />,
  alert: (
    <>
      <Path d="M10.3 4.3 2.7 17.5A2 2 0 0 0 4.4 20h15.2a2 2 0 0 0 1.7-2.5L13.7 4.3a2 2 0 0 0-3.4 0Z" />
      <Path d="M12 9v4" />
      <Path d="M12 17h.01" />
    </>
  ),
  chevron: <Path d="m9 18 6-6-6-6" />,
};

type Props = { name: IconName; size?: number; color?: string };

export function Icon({ name, size = 24, color = colors.text }: Props) {
  return (
    <Svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke={color}
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {ICON_SHAPES[name]}
    </Svg>
  );
}
