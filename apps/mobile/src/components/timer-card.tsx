import { Pressable, StyleSheet, View } from 'react-native';
import Svg, { Circle } from 'react-native-svg';

import { Button } from '@/components/button';
import { AppText } from '@/components/text';
import { formatSeconds } from '@/state/use-timer';
import { colors } from '@/theme/tokens';

const RING_LENGTH = 289; // circumference of r=46
// Wider than the 150 in the Figma export so the 56pt digits fit inside the ring.
const RING_SIZE = 184;

export type TimerCardStatus = 'idle' | 'running' | 'paused' | 'finished';

type Props = {
  status: TimerCardStatus;
  remainingSeconds: number;
  durationSeconds: number;
  // When the user should press start, e.g. "볶기 시작할 때 눌러주세요".
  startCondition: string;
  finishedMessage: string;
  // Start, pause or resume depending on status. Never triggered automatically.
  onToggle: () => void;
  onRestart: () => void;
  onCancel: () => void;
};

const TOGGLE_LABEL: Record<Exclude<TimerCardStatus, 'finished'>, string> = {
  idle: '타이머 시작',
  running: '일시정지',
  paused: '이어하기',
};

function TimerRing({ status, remainingSeconds, durationSeconds }: Pick<Props, 'status' | 'remainingSeconds' | 'durationSeconds'>) {
  const paused = status === 'paused';
  const progress = durationSeconds > 0 ? remainingSeconds / durationSeconds : 0;
  return (
    <View style={styles.ring}>
      <Svg width={RING_SIZE} height={RING_SIZE} viewBox="0 0 104 104" style={styles.ringSvg}>
        <Circle cx={52} cy={52} r={46} fill="none" stroke={colors.line} strokeWidth={4} />
        <Circle
          cx={52}
          cy={52}
          r={46}
          fill="none"
          stroke={paused ? colors.hint : colors.primary}
          strokeWidth={4}
          strokeLinecap="round"
          strokeDasharray={RING_LENGTH}
          strokeDashoffset={RING_LENGTH * (1 - progress)}
        />
      </Svg>
      <AppText weight="bold" style={[styles.number, paused && styles.numberPaused]}>
        {formatSeconds(remainingSeconds)}
      </AppText>
    </View>
  );
}

export function TimerCard(props: Props) {
  const { status, startCondition, finishedMessage, onToggle, onRestart, onCancel } = props;
  const finished = status === 'finished';
  return (
    <View style={[styles.card, finished && styles.cardFinished]}>
      <AppText style={styles.criterion}>{finished ? '시간이 끝났어요' : startCondition}</AppText>
      <TimerRing {...props} />
      {finished ? (
        <>
          <AppText style={styles.finishedCopy}>{finishedMessage}</AppText>
          <Button label="처음부터 다시" onPress={onRestart} size="cook" style={styles.action} variant="secondary" />
        </>
      ) : (
        <>
          <Button
            icon={status === 'running' ? 'pause' : 'play'}
            label={TOGGLE_LABEL[status]}
            onPress={onToggle}
            size="cook"
            style={styles.action}
          />
          {status !== 'idle' ? (
            <View style={styles.subactions}>
              <Pressable accessibilityRole="button" hitSlop={12} onPress={onRestart}>
                <AppText style={styles.subaction}>처음부터</AppText>
              </Pressable>
              <AppText style={styles.divider}>·</AppText>
              <Pressable accessibilityRole="button" hitSlop={12} onPress={onCancel}>
                <AppText style={styles.subaction}>취소</AppText>
              </Pressable>
            </View>
          ) : null}
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    minHeight: 310,
    padding: 18,
    borderRadius: 20,
    borderWidth: 2,
    borderColor: colors.surface,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardFinished: { backgroundColor: colors.successSoft, borderColor: colors.success },
  criterion: { fontSize: 16, lineHeight: 22, color: colors.sub, textAlign: 'center' },
  ring: { width: RING_SIZE, height: RING_SIZE, marginVertical: 12, alignItems: 'center', justifyContent: 'center' },
  // Rotated so the arc starts at 12 o'clock.
  ringSvg: { position: 'absolute', transform: [{ rotate: '-90deg' }] },
  number: { fontSize: 56, lineHeight: 60, letterSpacing: -2, fontVariant: ['tabular-nums'] },
  numberPaused: { color: colors.hint },
  action: { alignSelf: 'stretch', height: 64 },
  finishedCopy: { marginBottom: 15, fontSize: 18, lineHeight: 26, textAlign: 'center' },
  subactions: { height: 42, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 12 },
  subaction: { fontSize: 14, color: colors.sub },
  divider: { color: colors.hint },
});
