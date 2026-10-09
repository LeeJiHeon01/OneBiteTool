import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/text';
import { colors } from '@/theme/tokens';

export type BadgeTone = 'info' | 'caution' | 'danger';

const TONES: Record<BadgeTone, { color: string; background: string }> = {
  info: { color: colors.info, background: colors.infoSoft },
  caution: { color: '#B66D00', background: colors.cautionSoft },
  danger: { color: colors.danger, background: colors.dangerSoft },
};

export function Badge({ label, tone }: { label: string; tone: BadgeTone }) {
  return (
    <View style={[styles.badge, { backgroundColor: TONES[tone].background }]}>
      <AppText weight="bold" style={[styles.label, { color: TONES[tone].color }]}>
        {label}
      </AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: { alignSelf: 'flex-start', borderRadius: 99, paddingVertical: 5, paddingHorizontal: 8 },
  label: { fontSize: 11, lineHeight: 14 },
});
