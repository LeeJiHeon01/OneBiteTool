import { router, useLocalSearchParams } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';

import { Button } from '@/components/button';
import { PageHeader } from '@/components/headers';
import { Icon } from '@/components/icon';
import { Page } from '@/components/page';
import { AppText } from '@/components/text';
import { TopBar } from '@/components/top-bar';
import { SKILL_LEVELS } from '@/data/catalog';
import { useAppState } from '@/state/app-state';
import { colors } from '@/theme/tokens';

export default function LevelScreen() {
  // edit=1 when opened from the summary or settings: save and go back instead of continuing.
  const { edit } = useLocalSearchParams<{ edit?: string }>();
  const { skillLevel, setSkillLevel } = useAppState();
  const next = () => (edit ? router.back() : router.push('/onboarding/tools'));

  return (
    <Page footer={<Button disabled={!skillLevel} label={edit ? '저장' : '다음'} onPress={next} />}>
      <TopBar onBack={() => router.back()} step={edit ? undefined : 1} />
      <PageHeader description="설명의 자세함만 달라져요." title={'요리, 얼마나\n해보셨어요?'} />
      <View accessibilityRole="radiogroup" style={styles.list}>
        {SKILL_LEVELS.map((level) => {
          const selected = skillLevel === level.id;
          return (
            <Pressable
              accessibilityRole="radio"
              accessibilityState={{ selected }}
              key={level.id}
              onPress={() => setSkillLevel(level.id)}
              style={[styles.card, selected && styles.cardSelected]}
            >
              <View style={styles.cardText}>
                <AppText weight="bold" style={styles.cardTitle}>
                  {level.title}
                </AppText>
                <AppText style={styles.cardDescription}>{level.description}</AppText>
              </View>
              <View style={[styles.check, selected && styles.checkSelected]}>
                <Icon name="check" size={18} color={colors.white} />
              </View>
            </Pressable>
          );
        })}
      </View>
    </Page>
  );
}

const styles = StyleSheet.create({
  list: { gap: 12 },
  card: {
    minHeight: 102,
    paddingVertical: 21,
    paddingHorizontal: 18,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 20,
    backgroundColor: colors.white,
  },
  // Padding shrinks by the extra border pixel so the card does not jump.
  cardSelected: {
    borderWidth: 2,
    borderColor: colors.primary,
    backgroundColor: colors.primarySoft,
    paddingVertical: 20,
    paddingHorizontal: 17,
  },
  cardText: { flex: 1 },
  cardTitle: { fontSize: 18, marginBottom: 7 },
  cardDescription: { fontSize: 14, color: colors.sub },
  check: {
    width: 26,
    height: 26,
    borderRadius: 13,
    backgroundColor: colors.line,
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkSelected: { backgroundColor: colors.primary },
});
