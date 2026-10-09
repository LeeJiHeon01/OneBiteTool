import { type ReactNode } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { Icon } from '@/components/icon';
import { AppText } from '@/components/text';
import { colors } from '@/theme/tokens';

const ONBOARDING_STEPS = 4;

type Props = {
  onBack?: () => void;
  // Onboarding step (1-4); shows the label and the progress bar.
  step?: number;
  action?: ReactNode;
};

export function TopBar({ onBack, step, action }: Props) {
  return (
    <>
      <View style={styles.bar}>
        {onBack ? (
          <Pressable
            accessibilityLabel="뒤로 가기"
            accessibilityRole="button"
            onPress={onBack}
            style={styles.iconButton}
          >
            <Icon name="arrow" />
          </Pressable>
        ) : (
          <View style={styles.spacer} />
        )}
        {step ? (
          <AppText weight="bold" style={styles.stepLabel}>
            {step} / {ONBOARDING_STEPS}
          </AppText>
        ) : null}
        {action ?? <View style={styles.spacer} />}
      </View>
      {step ? (
        <View style={styles.track}>
          <View style={[styles.value, { width: `${(step / ONBOARDING_STEPS) * 100}%` }]} />
        </View>
      ) : null}
    </>
  );
}

const styles = StyleSheet.create({
  bar: { height: 68, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  spacer: { width: 44, height: 44 },
  iconButton: {
    width: 44,
    height: 44,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepLabel: { fontSize: 14, color: colors.sub },
  track: { height: 4, borderRadius: 99, backgroundColor: colors.surface, overflow: 'hidden' },
  value: { height: '100%', borderRadius: 99, backgroundColor: colors.primary },
});
