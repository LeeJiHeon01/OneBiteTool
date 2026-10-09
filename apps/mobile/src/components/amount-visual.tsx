import { StyleSheet, View } from 'react-native';
import Svg, { ClipPath, Defs, Line, Polygon, Rect } from 'react-native-svg';

import { Icon, type IconName } from '@/components/icon';
import { Picture } from '@/components/picture';
import { AppText } from '@/components/text';
import { toolImage } from '@/data/images';
import { type AmountVisualType } from '@/data/recipe';
import { colors } from '@/theme/tokens';

// Cup outline on a 42x48 grid.
const CUP_POINTS = '4.2,0 37.8,0 31.5,48 10.5,48';
// Only the "full" guide exists: fraction marks must be computed from cup dimensions.
const FULL_LINE_Y = 4.8;

export function CupMeasure({ large = false }: { large?: boolean }) {
  const width = large ? 105 : 42;
  const height = large ? 120 : 48;
  return (
    <View accessibilityLabel="컵 가득" accessible style={{ width, height }}>
      <Svg width={width} height={height} viewBox="0 0 42 48">
        <Defs>
          <ClipPath id="cup">
            <Polygon points={CUP_POINTS} />
          </ClipPath>
        </Defs>
        <Polygon points={CUP_POINTS} fill={colors.white} />
        <Rect clipPath="url(#cup)" x={0} y={FULL_LINE_Y} width={42} height={48} fill={colors.infoSoft} />
        <Line clipPath="url(#cup)" x1={0} y1={FULL_LINE_Y} x2={42} y2={FULL_LINE_Y} stroke={colors.info} strokeWidth={large ? 1 : 2} />
        <Polygon points={CUP_POINTS} fill="none" stroke={colors.text} strokeWidth={large ? 1.6 : 3} strokeLinejoin="round" />
      </Svg>
      {large ? (
        <View style={styles.cupLabel}>
          <AppText weight="extraBold" style={styles.cupLabelText}>
            가득
          </AppText>
        </View>
      ) : null}
    </View>
  );
}

// Spoons use the prepared tool pictures.
const TOOL_PICTURES: Partial<Record<AmountVisualType, string>> = {
  spoon: 'spoon_rice',
  'spoon-small': 'teaspoon',
};
// No artwork yet (manifest: amount.bowl, amount.egg are missing) so these stay placeholders.
const PLACEHOLDER_ICONS: Partial<Record<AmountVisualType, IconName>> = { bowl: 'pan', egg: 'sparkle', piece: 'sparkle' };

export function AmountVisual({ type, toolId: measuredWith }: { type: AmountVisualType; toolId?: string | null }) {
  if (type === 'cup') return <CupMeasure />;
  const toolId = type === 'tool' ? measuredWith : TOOL_PICTURES[type];
  const image = toolId ? toolImage(toolId) : undefined;
  if (image) return <Picture size={44} source={image} />;
  return (
    <View style={styles.placeholder}>
      <Icon name={PLACEHOLDER_ICONS[type] ?? 'sparkle'} size={28} color={colors.sub} />
    </View>
  );
}

const styles = StyleSheet.create({
  placeholder: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center' },
  cupLabel: {
    position: 'absolute',
    right: 8,
    top: 10,
    paddingVertical: 3,
    paddingHorizontal: 5,
    borderRadius: 7,
    backgroundColor: colors.white,
  },
  cupLabelText: { fontSize: 11, color: colors.info },
});
