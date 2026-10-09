import { StyleSheet, View } from 'react-native';

import { HeatIndicator } from '@/components/heat-indicator';
import { Icon } from '@/components/icon';
import { AllergyBanner, SafetyNote } from '@/components/notes';
import { StepTimer } from '@/components/step-timer';
import { AppText } from '@/components/text';
import { type AdaptedStep } from '@/data/recipe';
import { type AllergyMatch } from '@/data/allergy';
import { type HeatSource } from '@/data/catalog';
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
      </View>
      <AppText accessibilityRole="header" style={styles.instruction}>
        {step.text}
      </AppText>
      {step.details ? <AppText style={styles.details}>{step.details}</AppText> : null}
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
        {step.durationSeconds ? <StepTimer durationSeconds={step.durationSeconds} step={step} /> : null}
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
  instruction: {
    marginTop: 24,
    marginBottom: 20,
    fontFamily: fonts.extraBold,
    fontSize: 28,
    lineHeight: 38,
    letterSpacing: -0.8,
  },
  details: { marginTop: -8, marginBottom: 20, fontSize: 17, lineHeight: 27, color: colors.sub },
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
