import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { Animated, Easing, StyleSheet, View } from 'react-native';

import { errorMessage } from '@/api/client';
import { Button } from '@/components/button';
import { CenterState } from '@/components/headers';
import { Icon } from '@/components/icon';
import { Page } from '@/components/page';
import { AppText } from '@/components/text';
import { useAppState } from '@/state/app-state';
import { colors } from '@/theme/tokens';

const CHECKS = ['계량 바꾸기', '단계 쉽게 나누기', '안전 확인하기'];
// The list only shows that work is going on; the last item stays open until the server answers.
const CHECK_INTERVAL_MS = 900;

function Orbit() {
  const [spin] = useState(() => new Animated.Value(0));

  useEffect(() => {
    const loop = Animated.loop(
      Animated.timing(spin, { toValue: 1, duration: 7000, easing: Easing.linear, useNativeDriver: true }),
    );
    loop.start();
    return () => loop.stop();
  }, [spin]);

  const rotate = spin.interpolate({ inputRange: [0, 1], outputRange: ['0deg', '360deg'] });
  const counterRotate = spin.interpolate({ inputRange: [0, 1], outputRange: ['0deg', '-360deg'] });

  return (
    <Animated.View style={[styles.orbit, { transform: [{ rotate }] }]}>
      {/* Counter-rotate so the icon stays upright while the dashed ring spins. */}
      <Animated.View style={[styles.orbitCore, { transform: [{ rotate: counterRotate }] }]}>
        <Icon name="sparkle" size={28} color={colors.primary} />
      </Animated.View>
    </Animated.View>
  );
}

export default function AdaptingScreen() {
  const { requestAdaptation, resetCooking } = useAppState();
  const [doneCount, setDoneCount] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const [error, setError] = useState<string | null>(null);
  // The request is sent once per attempt, with the choices made on the previous screen.
  const [request] = useState(() => requestAdaptation);

  useEffect(() => {
    let cancelled = false;
    request()
      .then((adaptation) => {
        if (cancelled) return;
        if (adaptation.status === 'NOT_COOKABLE') router.replace('/recipe/cannot-cook');
        else if (adaptation.status === 'NEEDS_CONFIRMATION') router.back();
        else {
          resetCooking();
          router.replace('/recipe/summary');
        }
      })
      .catch((failure) => {
        if (!cancelled) setError(errorMessage(failure));
      });
    return () => {
      cancelled = true;
    };
  }, [request, resetCooking, attempt]);

  useEffect(() => {
    if (doneCount >= CHECKS.length - 1) return;
    const id = setTimeout(() => setDoneCount((count) => count + 1), CHECK_INTERVAL_MS);
    return () => clearTimeout(id);
  }, [doneCount]);

  const retry = () => {
    setError(null);
    setAttempt((current) => current + 1);
  };

  // The choices are kept, so trying again sends the same request.
  if (error) {
    return (
      <Page
        footer={
          <>
            <Button label="다시 시도" onPress={retry} />
            <Button label="돌아가기" onPress={() => router.back()} variant="text" />
          </>
        }
      >
        <CenterState description={error} style={styles.state} title={'레시피를 만들지\n못했어요'} visual={null} />
      </Page>
    );
  }

  return (
    <Page>
      <CenterState
        description="조금만 기다려 주세요."
        style={styles.state}
        title={'내 도구에 맞게\n바꾸고 있어요'}
        visual={<Orbit />}
      />
      <View style={styles.checks}>
        {CHECKS.map((label, index) => {
          const done = index < doneCount;
          const active = index === doneCount;
          return (
            <View key={label} style={styles.check}>
              {done ? <Icon name="check" size={18} color={colors.success} /> : <View style={[styles.dot, !active && styles.dotIdle]} />}
              <AppText
                weight={active ? 'bold' : 'regular'}
                style={[styles.checkLabel, done && styles.checkDone, active && styles.checkActive]}
              >
                {label}
              </AppText>
            </View>
          );
        })}
      </View>
    </Page>
  );
}

const styles = StyleSheet.create({
  state: { paddingTop: 155 },
  orbit: {
    width: 110,
    height: 110,
    marginBottom: 35,
    borderRadius: 55,
    borderWidth: 2,
    borderStyle: 'dashed',
    borderColor: '#FFD0BF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  orbitCore: {
    width: 72,
    height: 72,
    borderRadius: 24,
    backgroundColor: colors.primarySoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  checks: { width: 240, marginTop: 48, alignSelf: 'center', gap: 14 },
  check: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  dot: {
    width: 18,
    height: 18,
    borderRadius: 9,
    borderWidth: 5,
    borderColor: colors.primarySoft,
    backgroundColor: colors.primary,
  },
  dotIdle: { borderColor: colors.surface, backgroundColor: colors.line },
  checkLabel: { fontSize: 15, color: colors.hint },
  checkDone: { color: colors.success },
  checkActive: { color: colors.text },
});
