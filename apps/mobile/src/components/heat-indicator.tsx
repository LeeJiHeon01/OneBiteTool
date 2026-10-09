import { StyleSheet, View } from 'react-native';

import { Picture } from '@/components/picture';
import { AppText } from '@/components/text';
import { HEAT_INFO, type HeatLevel, type HeatSource } from '@/data/catalog';
import { HEAT_IMAGES } from '@/data/images';
import { colors } from '@/theme/tokens';

type Props = { level: HeatLevel; source: HeatSource };

// Picture first, label right under it; both come from the same level.
export function HeatIndicator({ level, source }: Props) {
  const { label } = HEAT_INFO[level];
  return (
    <View accessibilityLabel={`불 세기 ${label}`} accessible style={styles.indicator}>
      <Picture size={128} source={HEAT_IMAGES[source][level]} />
      <AppText weight="bold" style={styles.label}>
        {label}
      </AppText>
      <AppText style={styles.caption}>불 세기</AppText>
    </View>
  );
}

// Compact picture for the all-steps list; always shown next to the level label.
export function SmallHeat({ level, source }: Props) {
  return <Picture size={30} source={HEAT_IMAGES[source][level]} />;
}

const styles = StyleSheet.create({
  indicator: { alignItems: 'center' },
  label: { marginTop: 4, fontSize: 24, lineHeight: 30 },
  caption: { marginTop: 4, fontSize: 14, color: colors.sub },
});
