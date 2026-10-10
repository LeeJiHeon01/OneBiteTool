import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { Button } from '@/components/button';
import { CenterState } from '@/components/headers';
import { Icon } from '@/components/icon';
import { Page } from '@/components/page';
import { Picture } from '@/components/picture';
import { AppText } from '@/components/text';
import { RATINGS, toggleStuckStep, type Rating } from '@/data/history';
import { ILLUSTRATIONS } from '@/data/images';
import { resetTo } from '@/nav';
import { useAppState } from '@/state/app-state';
import { colors, fonts } from '@/theme/tokens';

export default function CompleteScreen() {
  const { adaptation, recipe, addCookRecord, rateCookRecord } = useAppState();
  const [rating, setRating] = useState<Rating | null>(null);
  const [stuckSteps, setStuckSteps] = useState<number[]>([]);
  // The finished dish is recorded once when this screen opens; the answers below are optional
  // and are added to that record as the user picks them.
  const recordId = useRef<string | null>(null);

  useEffect(() => {
    if (!recipe || recordId.current) return;
    recordId.current = addCookRecord({ id: recipe.id, name: recipe.name });
  }, [recipe, addCookRecord]);

  const pickRating = (next: Rating) => {
    const value = rating === next ? null : next;
    setRating(value);
    if (recordId.current) rateCookRecord(recordId.current, { rating: value });
  };

  const pickStuckStep = (order: number) => {
    const value = toggleStuckStep(stuckSteps, order);
    setStuckSteps(value);
    if (recordId.current) rateCookRecord(recordId.current, { stuckStepOrders: value });
  };

  const stepCount = adaptation?.steps.length ?? 0;

  return (
    <Page
      footer={
        <>
          <Button label="다른 요리 찾기" onPress={() => resetTo('/home')} />
          <Button label="처음 화면으로" onPress={() => resetTo('/')} variant="text" />
        </>
      }
    >
      <CenterState
        description="끝까지 잘 따라왔어요."
        eyebrow={`${stepCount}단계 모두 완료`}
        style={styles.state}
        title={'완성!\n맛있게 드세요'}
        titleStyle={styles.title}
        visual={<Picture size={170} source={ILLUSTRATIONS.cookingDone} style={styles.visual} />}
      />
      <View style={styles.tip}>
        <Icon name="sparkle" size={24} color="#986500" />
        <View style={styles.tipText}>
          <AppText weight="bold" style={styles.tipTitle}>
            맛을 한 번 볼까요?
          </AppText>
          <AppText style={styles.tipBody}>싱거우면 간을 조금씩 더해 보세요.</AppText>
        </View>
      </View>
      {recipe ? (
        <View style={styles.feedback}>
          <AppText weight="bold" style={styles.question}>
            어땠나요?
          </AppText>
          <View style={styles.options}>
            {RATINGS.map((option) => {
              const selected = rating === option.id;
              return (
                <Pressable
                  accessibilityRole="button"
                  accessibilityState={{ selected }}
                  key={option.id}
                  onPress={() => pickRating(option.id)}
                  style={[styles.option, selected && styles.optionOn]}
                >
                  <AppText weight={selected ? 'bold' : 'regular'} style={[styles.optionLabel, selected && styles.optionLabelOn]}>
                    {option.label}
                  </AppText>
                </Pressable>
              );
            })}
          </View>
          {stepCount > 0 ? (
            <>
              <AppText weight="bold" style={[styles.question, styles.secondQuestion]}>
                막혔던 단계가 있나요?
              </AppText>
              <View style={styles.options}>
                {adaptation?.steps.map((step, index) => {
                  const order = index + 1;
                  const selected = stuckSteps.includes(order);
                  return (
                    <Pressable
                      accessibilityLabel={`${order}단계`}
                      accessibilityRole="button"
                      accessibilityState={{ selected }}
                      key={step.id}
                      onPress={() => pickStuckStep(order)}
                      style={[styles.stepOption, selected && styles.optionOn]}
                    >
                      <AppText weight={selected ? 'bold' : 'regular'} style={[styles.optionLabel, selected && styles.optionLabelOn]}>
                        {order}
                      </AppText>
                    </Pressable>
                  );
                })}
              </View>
            </>
          ) : null}
          <AppText style={styles.feedbackNote}>고르지 않아도 돼요. 이 기기에만 저장돼요.</AppText>
        </View>
      ) : null}
    </Page>
  );
}

const styles = StyleSheet.create({
  state: { paddingTop: 40 },
  title: { fontFamily: fonts.extraBold, fontSize: 35, lineHeight: 44 },
  visual: { marginBottom: 20 },
  tip: {
    marginTop: 34,
    padding: 18,
    borderRadius: 18,
    backgroundColor: colors.cautionSoft,
    flexDirection: 'row',
    gap: 12,
  },
  tipText: { flex: 1 },
  tipTitle: { fontSize: 14, lineHeight: 20, marginBottom: 2 },
  tipBody: { fontSize: 14, lineHeight: 20, color: '#986500' },
  feedback: { marginTop: 30, paddingBottom: 10 },
  question: { marginBottom: 12, fontSize: 16 },
  secondQuestion: { marginTop: 24 },
  options: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  option: {
    minHeight: 44,
    paddingHorizontal: 16,
    borderRadius: 99,
    backgroundColor: colors.surface,
    justifyContent: 'center',
  },
  stepOption: {
    width: 44,
    height: 44,
    borderRadius: 99,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  optionOn: { backgroundColor: colors.primarySoft },
  optionLabel: { fontSize: 14, color: colors.text },
  optionLabelOn: { color: colors.primaryDark },
  feedbackNote: { marginTop: 14, fontSize: 12, color: colors.hint },
});
