import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { BottomSheet } from '@/components/bottom-sheet';
import { Button } from '@/components/button';
import { Checkbox } from '@/components/checkbox';
import { PageHeader } from '@/components/headers';
import { Icon } from '@/components/icon';
import { Page } from '@/components/page';
import { AppText } from '@/components/text';
import { TopBar } from '@/components/top-bar';
import { ALLERGEN_GROUPS } from '@/data/catalog';
import { useAppState } from '@/state/app-state';
import { colors } from '@/theme/tokens';

function Chip({ label, selected, onPress }: { label: string; selected: boolean; onPress: () => void }) {
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityState={{ checked: selected }}
      onPress={onPress}
      style={[styles.chip, selected && styles.chipSelected]}
    >
      <AppText weight={selected ? 'bold' : 'regular'} style={[styles.chipLabel, selected && styles.chipLabelSelected]}>
        {label}
      </AppText>
    </Pressable>
  );
}

export default function AllergyScreen() {
  const { edit } = useLocalSearchParams<{ edit?: string }>();
  const { allergenCodes, toggleAllergen, clearAllergens, allergyConsent, setAllergyConsent } = useAppState();
  const [skipSheet, setSkipSheet] = useState(false);
  const hasSelection = allergenCodes.length > 0;

  const next = () => (edit ? router.back() : router.push('/onboarding/ready'));
  const skip = () => (hasSelection ? setSkipSheet(true) : next());
  const clearAndSkip = () => {
    clearAllergens();
    setSkipSheet(false);
    next();
  };

  return (
    <Page
      footer={
        <>
          {/* Allergy data is health information: it is only used after explicit consent. */}
          <Button disabled={hasSelection && !allergyConsent} label={edit ? '저장' : '다음'} onPress={next} />
          <Button label="없어요, 건너뛸게요" onPress={skip} variant="text" />
        </>
      }
    >
      <TopBar onBack={() => router.back()} step={edit ? undefined : 3} />
      <PageHeader description="레시피에 들어 있으면 미리 알려드려요." title={'피해야 할 재료가\n있나요?'} />
      <View style={styles.groups}>
        {ALLERGEN_GROUPS.map((group) => (
          <View key={group.id}>
            <AppText weight="semiBold" style={styles.groupTitle}>
              {group.title}
            </AppText>
            <View style={styles.chips}>
              {group.allergens.map((allergen) => (
                <Chip
                  key={allergen.code}
                  label={allergen.name}
                  onPress={() => toggleAllergen(allergen.code)}
                  selected={allergenCodes.includes(allergen.code)}
                />
              ))}
            </View>
          </View>
        ))}
      </View>
      <View style={styles.privacy}>
        <Icon name="shield" size={20} color={colors.sub} />
        <AppText style={styles.privacyText}>
          {'알레르기 정보는 이 기기에만 저장되고,\n레시피를 맞출 때만 사용돼요.'}
        </AppText>
      </View>
      {hasSelection ? (
        <Pressable
          accessibilityRole="checkbox"
          accessibilityState={{ checked: allergyConsent }}
          onPress={() => setAllergyConsent(!allergyConsent)}
          style={styles.consent}
        >
          <Checkbox checked={allergyConsent} />
          <AppText style={styles.consentLabel}>알레르기 정보 사용에 동의해요</AppText>
        </Pressable>
      ) : null}
      <BottomSheet
        description="선택한 항목이 모두 지워지고 다음 단계로 넘어가요."
        icon="alert"
        iconTone="dark"
        onClose={() => setSkipSheet(false)}
        title="선택한 알레르기를 지울까요?"
        visible={skipSheet}
      >
        <Button label="계속 선택하기" onPress={() => setSkipSheet(false)} variant="secondary" />
        <Button label="지우고 건너뛰기" onPress={clearAndSkip} />
      </BottomSheet>
    </Page>
  );
}

const styles = StyleSheet.create({
  groups: { gap: 22 },
  groupTitle: { marginBottom: 10, fontSize: 14, color: colors.sub },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 9 },
  chip: {
    minHeight: 43,
    paddingHorizontal: 16,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 99,
    backgroundColor: colors.white,
    justifyContent: 'center',
  },
  chipSelected: { borderColor: colors.primary, backgroundColor: colors.primary },
  chipLabel: { fontSize: 15 },
  chipLabelSelected: { color: colors.white },
  privacy: {
    marginTop: 28,
    padding: 16,
    borderRadius: 16,
    backgroundColor: colors.surface,
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 10,
  },
  privacyText: { flex: 1, fontSize: 13, lineHeight: 20, color: colors.sub },
  consent: { paddingVertical: 18, paddingHorizontal: 2, flexDirection: 'row', alignItems: 'center', gap: 10 },
  consentLabel: { fontSize: 14 },
});
