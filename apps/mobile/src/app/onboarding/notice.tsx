import { router } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { Button } from '@/components/button';
import { PageHeader } from '@/components/headers';
import { Icon, type IconName } from '@/components/icon';
import { Page } from '@/components/page';
import { AppText } from '@/components/text';
import { TopBar } from '@/components/top-bar';
import { resetTo } from '@/nav';
import { useAppState } from '@/state/app-state';
import { colors } from '@/theme/tokens';

const NOTES: { icon: IconName; text: string }[] = [
  { icon: 'spoon', text: '양은 가진 도구 기준의 대략적인 값이에요' },
  { icon: 'shield', text: '알레르기 정보는 참고용이에요. 제품 성분표를 꼭 확인하세요' },
  { icon: 'alert', text: '불과 칼을 쓸 때는 안전에 주의해 주세요' },
];

// Shown once, at the end of the first-run screens. Confirming it finishes onboarding, and
// that is stored on the device, so the app opens on home from then on.
export default function FirstRunNoticeScreen() {
  const { completeOnboarding } = useAppState();
  const confirm = () => {
    completeOnboarding();
    resetTo('/home');
  };

  return (
    <Page footer={<Button label="확인했어요" onPress={confirm} />}>
      <TopBar onBack={() => router.back()} />
      <PageHeader style={styles.header} title={'시작하기 전에\n알아주세요'} />
      <View style={styles.list}>
        {NOTES.map((note) => (
          <View key={note.text} style={styles.card}>
            <View style={styles.icon}>
              <Icon name={note.icon} size={22} color={colors.primary} />
            </View>
            <AppText style={styles.text}>{note.text}</AppText>
          </View>
        ))}
      </View>
    </Page>
  );
}

const styles = StyleSheet.create({
  header: { paddingTop: 26, marginBottom: 28 },
  list: { gap: 12 },
  card: {
    minHeight: 76,
    padding: 16,
    borderRadius: 18,
    backgroundColor: colors.surface,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
  },
  icon: {
    width: 44,
    height: 44,
    borderRadius: 14,
    backgroundColor: colors.white,
    alignItems: 'center',
    justifyContent: 'center',
  },
  text: { flex: 1, fontSize: 15, lineHeight: 22 },
});
