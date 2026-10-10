import * as Speech from 'expo-speech';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { HeatIndicator } from '@/components/heat-indicator';
import { Icon } from '@/components/icon';
import { Checkbox } from '@/components/checkbox';
import { AllergyBanner, SafetyNote } from '@/components/notes';
import { StepReportSheet, type ReportReason } from '@/components/step-report-sheet';
import { StepTimer } from '@/components/step-timer';
import { AppText } from '@/components/text';
import { useToast } from '@/components/toast';
import { type AdaptedStep } from '@/data/recipe';
import { type AllergyMatch } from '@/data/allergy';
import { type HeatSource } from '@/data/catalog';
import { stepSpeechLines } from '@/data/step-speech';
import { colors, fonts } from '@/theme/tokens';

type Props = {
  step: AdaptedStep;
  heatSource: HeatSource;
  // Allergen ingredients used in this step; warned on every such step.
  allergyMatches: AllergyMatch[];
};

// One cooking step: a single action with its heat, timer, check and safety notes.
export function CookStep({ step, heatSource, allergyMatches }: Props) {
  const hasBanner = allergyMatches.length > 0;
  const [speaking, setSpeaking] = useState(false);
  const [reporting, setReporting] = useState(false);
  const showToast = useToast();

  // The contract (spec 8, POST /api/reports) is settled but not built yet: nothing is sent for now.
  // When it is, a failed send still shows the same thanks and is not retried.
  const sendReport = (_reason: ReportReason) => showToast('알려주셔서 고마워요');
  // Ticks are only a reminder for the user; moving on never depends on them.
  const [checkedIds, setCheckedIds] = useState<string[]>([]);
  const toggleItem = (id: string) =>
    setCheckedIds((current) => (current.includes(id) ? current.filter((item) => item !== id) : [...current, id]));

  // Reading stops when the step leaves the screen (next, previous, list view, closing).
  useEffect(() => () => void Speech.stop(), []);

  // Never starts by itself: the user presses the button. The device's own voice is used.
  const toggleSpeech = () => {
    if (speaking) {
      Speech.stop();
      setSpeaking(false);
      return;
    }
    setSpeaking(true);
    const stop = () => setSpeaking(false);
    Speech.speak(stepSpeechLines(step, allergyMatches).join(' '), {
      language: 'ko-KR',
      onDone: stop,
      onStopped: stop,
      onError: stop,
    });
  };

  return (
    <>
      {hasBanner ? (
        <View style={styles.banners}>
          {allergyMatches.map((match) => (
            <AllergyBanner
              allergenNames={match.allergenNames}
              ingredientName={match.ingredientName}
              key={match.ingredientId}
              productVaries={match.productVaries}
            />
          ))}
        </View>
      ) : null}
      <View style={[styles.action, hasBanner && styles.actionCompact]}>
        <Icon name={step.actionIcon} size={58} color={colors.primary} />
        <AppText weight="extraBold" style={styles.actionLabel}>
          {step.actionLabel}
        </AppText>
        <Pressable
          accessibilityLabel="이 단계가 이상해요"
          accessibilityRole="button"
          hitSlop={8}
          onPress={() => setReporting(true)}
          style={styles.more}
        >
          <AppText weight="extraBold" style={styles.moreLabel}>
            ⋯
          </AppText>
        </Pressable>
        <Pressable
          accessibilityLabel={speaking ? '읽기 멈추기' : '이 단계 읽어주기'}
          accessibilityRole="button"
          hitSlop={8}
          onPress={toggleSpeech}
          style={[styles.speak, speaking && styles.speakOn]}
        >
          <Icon name={speaking ? 'pause' : 'speaker'} size={22} color={speaking ? colors.white : colors.primary} />
        </Pressable>
      </View>
      <AppText accessibilityRole="header" style={styles.instruction}>
        {step.text}
      </AppText>
      {step.details ? <AppText style={styles.details}>{step.details}</AppText> : null}
      {step.subItems.length > 0 ? (
        <View style={styles.checklist}>
          {step.subItems.map((item) => {
            const checked = checkedIds.includes(item.id);
            // The warning stays next to the item that uses the ingredient, as well as on top.
            const allergy = allergyMatches.filter((match) => item.ingredientIds.includes(match.ingredientId));
            return (
              <Pressable
                accessibilityRole="checkbox"
                accessibilityState={{ checked }}
                key={item.id}
                onPress={() => toggleItem(item.id)}
                style={styles.checkItem}
              >
                <Checkbox checked={checked} />
                <View style={styles.checkBody}>
                  <AppText weight="semiBold" style={[styles.checkText, checked && styles.checkTextDone]}>
                    {item.text}
                  </AppText>
                  {allergy.map((match) => (
                    <AppText key={match.ingredientId} weight="bold" style={styles.checkAllergy}>
                      알레르기 주의: {match.ingredientName} — {match.allergenNames}
                    </AppText>
                  ))}
                </View>
              </Pressable>
            );
          })}
        </View>
      ) : null}
      <View style={styles.info}>
        {/* Oven, air fryer and microwave settings are shown as the recipe wrote them, never converted. */}
        {step.deviceSetting ? (
          <View style={styles.device}>
            <AppText weight="bold" style={styles.deviceLabel}>
              기기 설정 (레시피에 적힌 그대로)
            </AppText>
            <AppText weight="extraBold" style={styles.deviceText}>
              {step.deviceSetting}
            </AppText>
            <AppText style={styles.deviceNote}>기기마다 조금씩 달라요. 상태를 보며 조절하세요.</AppText>
          </View>
        ) : null}
        {step.heatLevel ? (
          <View style={styles.heatCard}>
            <HeatIndicator level={step.heatLevel} source={heatSource} />
          </View>
        ) : null}
        {step.durationSeconds ? (
          <StepTimer
            durationMaxSeconds={step.durationMaxSeconds}
            durationSeconds={step.durationSeconds}
            step={step}
          />
        ) : null}
      </View>
      {step.completionCheck ? (
        <View style={styles.look}>
          <AppText weight="bold" style={styles.lookLabel}>
            눈으로 확인해요
          </AppText>
          <AppText weight="semiBold" style={styles.lookText}>
            {step.completionCheck}
          </AppText>
        </View>
      ) : null}
      <View style={styles.safety}>
        {step.safetyNotes.map((message) => (
          <SafetyNote key={message} large message={message} title="조심하세요" />
        ))}
      </View>
      <StepReportSheet onClose={() => setReporting(false)} onSend={sendReport} visible={reporting} />
    </>
  );
}

const styles = StyleSheet.create({
  banners: { marginTop: 13, gap: 8 },
  action: {
    height: 122,
    marginTop: 18,
    borderRadius: 22,
    backgroundColor: colors.primarySoft,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 16,
  },
  actionCompact: { height: 96, marginTop: 10 },
  actionLabel: { fontSize: 18, color: colors.primary },
  speak: {
    position: 'absolute',
    right: 12,
    top: 12,
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: colors.white,
    alignItems: 'center',
    justifyContent: 'center',
  },
  speakOn: { backgroundColor: colors.primary },
  more: {
    position: 'absolute',
    left: 12,
    top: 12,
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  moreLabel: { fontSize: 22, lineHeight: 26, color: colors.primary },
  instruction: {
    marginTop: 24,
    marginBottom: 20,
    fontFamily: fonts.extraBold,
    fontSize: 28,
    lineHeight: 38,
    letterSpacing: -0.8,
  },
  details: { marginTop: -8, marginBottom: 20, fontSize: 17, lineHeight: 27, color: colors.sub },
  checklist: { marginBottom: 20, borderTopWidth: 1, borderTopColor: colors.line },
  checkItem: {
    minHeight: 56,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  checkBody: { flex: 1, gap: 3 },
  checkText: { fontSize: 18, lineHeight: 26 },
  checkTextDone: { color: colors.hint, textDecorationLine: 'line-through' },
  checkAllergy: { fontSize: 13, lineHeight: 19, color: colors.danger },
  info: { gap: 12 },
  device: { padding: 18, borderWidth: 1, borderColor: colors.line, borderRadius: 20 },
  deviceLabel: { fontSize: 13, color: colors.sub },
  deviceText: { marginTop: 7, fontSize: 24, lineHeight: 32 },
  deviceNote: { marginTop: 8, fontSize: 13, lineHeight: 19, color: colors.sub },
  heatCard: {
    minHeight: 190,
    padding: 18,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  look: { marginTop: 17, padding: 18, borderRadius: 16, backgroundColor: '#EEF7FF' },
  lookLabel: { fontSize: 14, color: colors.info },
  lookText: { marginTop: 7, fontSize: 18, lineHeight: 27 },
  safety: { marginTop: 10, gap: 10 },
});
