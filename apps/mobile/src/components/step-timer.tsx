import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { BottomSheet } from '@/components/bottom-sheet';
import { Button } from '@/components/button';
import { Icon } from '@/components/icon';
import { AppText } from '@/components/text';
import { TimerCard, type TimerCardStatus } from '@/components/timer-card';
import { type AdaptedStep } from '@/data/recipe';
import { useAppState } from '@/state/app-state';
import { formatSeconds, useRemainingSeconds } from '@/state/use-timer';

type Props = { step: AdaptedStep; durationSeconds: number };

// Timer for one step. The app keeps a single active timer, so starting here while
// another step's timer is active asks whether to keep or replace it.
export function StepTimer({ step, durationSeconds }: Props) {
  const { timer: controls, adaptation } = useAppState();
  const { timer } = controls;
  const remaining = useRemainingSeconds(timer);
  const [confirmReplace, setConfirmReplace] = useState(false);

  const isMine = timer?.stepId === step.id;
  const status: TimerCardStatus = isMine && timer ? timer.status : 'idle';
  const otherActive = timer && !isMine && timer.status !== 'finished' ? timer : null;
  const otherStepNumber = otherActive ? (adaptation?.steps ?? []).findIndex((item) => item.id === otherActive.stepId) + 1 : 0;

  const stepNumber = (adaptation?.steps ?? []).findIndex((item) => item.id === step.id) + 1;
  const start = () => controls.start(step.id, stepNumber, durationSeconds);
  const toggle = () => {
    if (status === 'running') controls.pause();
    else if (status === 'paused') controls.resume();
    else if (otherActive) setConfirmReplace(true);
    else start();
  };
  const replace = () => {
    setConfirmReplace(false);
    start();
  };

  return (
    <>
      <TimerCard
        durationSeconds={durationSeconds}
        finishedMessage="상태를 확인하고 다음 단계로 가세요."
        onCancel={controls.cancel}
        onRestart={controls.restart}
        onToggle={toggle}
        remainingSeconds={isMine ? remaining : durationSeconds}
        startCondition={step.timerStartCondition ?? '준비되면 눌러주세요'}
        status={status}
      />
      {status === 'running' || status === 'paused' ? (
        <View style={styles.reminder}>
          <Icon name="alert" size={18} />
          <AppText style={styles.reminderText}>일시정지해도 불은 꺼지지 않아요. 불은 직접 조절하세요.</AppText>
        </View>
      ) : null}
      <BottomSheet
        description={`${otherStepNumber}단계 타이머(${formatSeconds(remaining)} 남음)를 멈추고\n이 타이머를 시작할까요?`}
        icon="clock"
        iconTone="dark"
        onClose={() => setConfirmReplace(false)}
        title="진행 중인 타이머가 있어요"
        visible={confirmReplace}
      >
        <Button label="기존 타이머 유지" onPress={() => setConfirmReplace(false)} variant="secondary" />
        <Button label="새 타이머로 바꾸기" onPress={replace} />
      </BottomSheet>
    </>
  );
}

const styles = StyleSheet.create({
  reminder: {
    paddingVertical: 12,
    paddingHorizontal: 4,
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'center',
    gap: 7,
  },
  reminderText: { flexShrink: 1, fontSize: 15, lineHeight: 22 },
});
