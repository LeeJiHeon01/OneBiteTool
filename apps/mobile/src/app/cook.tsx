import { Redirect, router } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { AllSteps, MiniTimer } from '@/components/all-steps';
import { Button } from '@/components/button';
import { CookStep } from '@/components/cook-step';
import { Page } from '@/components/page';
import { AppText } from '@/components/text';
import { findAllergyMatches } from '@/data/allergy';
import { useAppState } from '@/state/app-state';
import { colors } from '@/theme/tokens';

function ViewToggle({ fullView, onChange }: { fullView: boolean; onChange: (fullView: boolean) => void }) {
  return (
    <View style={styles.toggle}>
      {[
        { label: '카드', value: false },
        { label: '전체', value: true },
      ].map((option) => {
        const active = option.value === fullView;
        return (
          <Pressable
            accessibilityRole="button"
            accessibilityState={{ selected: active }}
            hitSlop={{ top: 12, bottom: 12 }}
            key={option.label}
            onPress={() => onChange(option.value)}
            style={[styles.toggleButton, active && styles.toggleButtonActive]}
          >
            <AppText weight={active ? 'bold' : 'regular'} style={[styles.toggleLabel, active && styles.toggleLabelActive]}>
              {option.label}
            </AppText>
          </Pressable>
        );
      })}
    </View>
  );
}

export default function CookScreen() {
  const { adaptation, recipe, allergenCodes, heatSource, cookStepIndex, setCookStepIndex } = useAppState();
  const [fullView, setFullView] = useState(false);
  if (!adaptation || adaptation.steps.length === 0) return <Redirect href="/home" />;

  const { steps } = adaptation;
  const index = Math.min(cookStepIndex, steps.length - 1);
  const step = steps[index];
  const isLast = index === steps.length - 1;

  const stepIngredients = adaptation.ingredients.filter((row) => step.ingredientIds.includes(row.id));
  const allergyMatches = findAllergyMatches(stepIngredients, allergenCodes);

  // Moving on is always the user's action; a finished timer never advances the step.
  const goNext = () => (isLast ? router.replace('/complete') : setCookStepIndex(index + 1));
  const selectStep = (next: number) => {
    setCookStepIndex(next);
    setFullView(false);
  };

  return (
    <Page
      contentStyle={styles.content}
      footer={
        fullView ? null : (
          <>
            <Button
              disabled={index === 0}
              label="이전"
              onPress={() => setCookStepIndex(index - 1)}
              size="cook"
              style={styles.previous}
              variant="secondary"
            />
            <Button label="다음" onPress={goNext} size="cook" style={styles.next} />
          </>
        )
      }
      footerStyle={styles.footer}
      overlay={fullView ? <MiniTimer steps={steps} /> : null}
    >
      <View style={styles.top}>
        <Pressable accessibilityLabel="닫기" accessibilityRole="button" onPress={() => router.back()} style={styles.close}>
          <AppText style={styles.closeLabel}>×</AppText>
        </Pressable>
        <AppText weight="bold" style={styles.topLabel}>
          {fullView ? (recipe?.name ?? '') : `${index + 1} / ${steps.length}`}
        </AppText>
        <ViewToggle fullView={fullView} onChange={setFullView} />
      </View>
      {fullView ? (
        <AllSteps currentIndex={index} onSelect={selectStep} steps={steps} />
      ) : (
        <>
          <View style={styles.progress}>
            <View style={[styles.progressValue, { width: `${((index + 1) / steps.length) * 100}%` }]} />
          </View>
          <CookStep allergyMatches={allergyMatches} heatSource={heatSource} key={step.id} step={step} />
        </>
      )}
    </Page>
  );
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: 18 },
  top: { height: 65, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  close: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  closeLabel: { fontSize: 28, lineHeight: 32 },
  topLabel: { fontSize: 14 },
  toggle: { flexDirection: 'row', padding: 3, borderRadius: 11, backgroundColor: colors.surface },
  toggleButton: { height: 30, paddingHorizontal: 10, borderRadius: 8, justifyContent: 'center' },
  toggleButtonActive: { backgroundColor: colors.white, boxShadow: '0 2px 5px rgba(0, 0, 0, 0.06)' },
  toggleLabel: { fontSize: 12, color: colors.sub },
  toggleLabelActive: { color: colors.text },
  progress: { height: 4, borderRadius: 99, backgroundColor: colors.surface, overflow: 'hidden' },
  progressValue: { height: '100%', borderRadius: 99, backgroundColor: colors.primary },
  footer: { flexDirection: 'row', gap: 10, paddingHorizontal: 18, paddingTop: 12 },
  previous: { flex: 0.62 },
  next: { flex: 1 },
});
