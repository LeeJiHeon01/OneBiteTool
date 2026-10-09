import { router, type Href } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';

import { Button } from '@/components/button';
import { CenterState } from '@/components/headers';
import { Page } from '@/components/page';
import { Picture } from '@/components/picture';
import { AppText } from '@/components/text';
import { TopBar } from '@/components/top-bar';
import { SKILL_LEVELS } from '@/data/catalog';
import { ILLUSTRATIONS } from '@/data/images';
import { resetTo } from '@/nav';
import { useAppState } from '@/state/app-state';
import { colors } from '@/theme/tokens';

export default function ReadyScreen() {
  const { skillLevel, toolIds, allergenCodes, completeOnboarding } = useAppState();
  const start = () => {
    completeOnboarding();
    resetTo('/home');
  };
  const rows: { label: string; value: string; href: Href }[] = [
    {
      label: '요리 실력',
      value: SKILL_LEVELS.find((level) => level.id === skillLevel)?.title ?? '선택 안 함',
      href: { pathname: '/onboarding/level', params: { edit: '1' } },
    },
    {
      label: '가진 도구',
      value: `${toolIds.length}개`,
      href: { pathname: '/onboarding/tools', params: { edit: '1' } },
    },
    {
      label: '알레르기',
      value: allergenCodes.length > 0 ? `${allergenCodes.length}개` : '없음',
      href: { pathname: '/onboarding/allergy', params: { edit: '1' } },
    },
  ];

  return (
    <Page footer={<Button label="요리 찾으러 가기" onPress={start} />}>
      <TopBar onBack={() => router.back()} step={4} />
      <CenterState
        description="이제 나에게 딱 맞는 레시피를 찾아볼까요?"
        style={styles.state}
        title="준비 끝!"
        visual={<Picture size={150} source={ILLUSTRATIONS.onboardingDone} style={styles.visual} />}
      />
      <View style={styles.list}>
        {rows.map((row) => (
          <View key={row.label} style={styles.row}>
            <AppText style={styles.label}>{row.label}</AppText>
            <AppText weight="bold" style={styles.value}>
              {row.value}
            </AppText>
            <Pressable accessibilityRole="button" hitSlop={12} onPress={() => router.push(row.href)}>
              <AppText weight="bold" style={styles.edit}>
                수정
              </AppText>
            </Pressable>
          </View>
        ))}
      </View>
    </Page>
  );
}

const styles = StyleSheet.create({
  state: { paddingTop: 56 },
  visual: { marginBottom: 18 },
  list: { marginTop: 62, borderTopWidth: 1, borderTopColor: colors.line },
  row: {
    minHeight: 64,
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  label: { flex: 1, fontSize: 15, color: colors.sub },
  value: { fontSize: 15 },
  edit: { fontSize: 13, color: colors.primary },
});
