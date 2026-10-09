import { Pressable, StyleSheet, View } from 'react-native';

import { SmallHeat } from '@/components/heat-indicator';
import { Icon } from '@/components/icon';
import { AppText } from '@/components/text';
import { type AdaptedStep } from '@/data/recipe';
import { HEAT_INFO } from '@/data/catalog';
import { useAppState } from '@/state/app-state';
import { formatSeconds, useRemainingSeconds } from '@/state/use-timer';
import { colors, titleStyle } from '@/theme/tokens';

function formatDuration(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return [minutes > 0 ? `${minutes}분` : '', seconds > 0 ? `${seconds}초` : ''].filter(Boolean).join(' ');
}

// Heat, device setting and time as the recipe states them; a recipe that states none shows none.
function metaOf(step: AdaptedStep): string {
  return [
    step.heatLevel ? HEAT_INFO[step.heatLevel].label : null,
    step.deviceSetting,
    step.durationSeconds ? formatDuration(step.durationSeconds) : null,
  ]
    .filter(Boolean)
    .join(' · ');
}

type Props = { steps: AdaptedStep[]; currentIndex: number; onSelect: (index: number) => void };

// Scroll view of every step; shares the current step and timer with the card view.
export function AllSteps({ steps, currentIndex, onSelect }: Props) {
  const { heatSource } = useAppState();
  return (
    <>
      <View style={styles.title}>
        <AppText accessibilityRole="header" style={titleStyle}>
          전체 순서
        </AppText>
        <AppText style={styles.subtitle}>지금은 {currentIndex + 1}단계예요.</AppText>
      </View>
      <View style={styles.list}>
        {steps.map((step, index) => {
          const current = index === currentIndex;
          const done = index < currentIndex;
          return (
            <Pressable
              accessibilityRole="button"
              key={step.id}
              onPress={() => onSelect(index)}
              style={[styles.row, current && styles.rowCurrent]}
            >
              <View style={[styles.circle, (current || done) && styles.circleActive]}>
                {done ? (
                  <Icon name="check" size={17} color={colors.white} />
                ) : (
                  <AppText weight="bold" style={current && styles.circleLabelActive}>
                    {index + 1}
                  </AppText>
                )}
              </View>
              <View style={styles.rowText}>
                <AppText weight="bold" style={[styles.stepText, done && styles.stepTextDone]}>
                  {step.text}
                </AppText>
                {metaOf(step) ? (
                  <View style={styles.meta}>
                    {step.heatLevel ? <SmallHeat level={step.heatLevel} source={heatSource} /> : null}
                    <AppText style={styles.metaLabel}>{metaOf(step)}</AppText>
                  </View>
                ) : null}
              </View>
            </Pressable>
          );
        })}
      </View>
    </>
  );
}

// Floating bar for the active timer while browsing all steps.
export function MiniTimer({ steps }: { steps: AdaptedStep[] }) {
  const { timer: controls } = useAppState();
  const { timer } = controls;
  const remaining = useRemainingSeconds(timer);
  if (!timer || timer.status === 'finished') return null;
  const running = timer.status === 'running';

  return (
    <View style={styles.mini}>
      <AppText style={styles.miniLabel}>{steps.findIndex((step) => step.id === timer.stepId) + 1}단계 ·</AppText>
      <AppText weight="bold" style={styles.miniTime}>
        {formatSeconds(remaining)}
      </AppText>
      <Pressable
        accessibilityLabel={running ? '타이머 일시정지' : '타이머 이어하기'}
        accessibilityRole="button"
        hitSlop={8}
        onPress={running ? controls.pause : controls.resume}
        style={styles.miniButton}
      >
        <Icon name={running ? 'pause' : 'play'} size={20} color={colors.white} />
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  title: { paddingTop: 25, paddingBottom: 22, paddingHorizontal: 2 },
  subtitle: { marginTop: 7, fontSize: 16, color: colors.sub },
  // Bottom space keeps the last row clear of the floating mini timer.
  list: { gap: 10, paddingBottom: 80 },
  row: {
    minHeight: 78,
    padding: 13,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 17,
    backgroundColor: colors.white,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  rowCurrent: { padding: 12, borderWidth: 2, borderColor: colors.primary, backgroundColor: colors.primarySoft },
  circle: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  circleActive: { backgroundColor: colors.primary },
  circleLabelActive: { color: colors.white },
  rowText: { flex: 1 },
  stepText: { fontSize: 14, lineHeight: 20 },
  stepTextDone: { color: colors.hint },
  meta: { minHeight: 30, marginTop: 4, flexDirection: 'row', alignItems: 'center', gap: 5 },
  metaLabel: { fontSize: 12, color: colors.sub },
  mini: {
    position: 'absolute',
    left: 20,
    right: 20,
    bottom: 20,
    height: 62,
    paddingHorizontal: 17,
    borderRadius: 18,
    backgroundColor: colors.text,
    flexDirection: 'row',
    alignItems: 'center',
    boxShadow: '0 10px 28px rgba(25, 31, 40, 0.24)',
  },
  miniLabel: { fontSize: 13, color: colors.hint },
  miniTime: { marginLeft: 'auto', fontSize: 21, color: colors.white, fontVariant: ['tabular-nums'] },
  miniButton: {
    width: 38,
    height: 38,
    marginLeft: 12,
    borderRadius: 12,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
