import { Redirect, router } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { Button } from '@/components/button';
import { Eyebrow } from '@/components/headers';
import { Icon } from '@/components/icon';
import { Page } from '@/components/page';
import { Picture } from '@/components/picture';
import { AppText } from '@/components/text';
import { ILLUSTRATIONS } from '@/data/images';
import { useAppState } from '@/state/app-state';
import { colors, fonts } from '@/theme/tokens';

export default function WelcomeScreen() {
  const { onboarded } = useAppState();
  // A returning user's settings are already on the device.
  if (onboarded) return <Redirect href="/home" />;

  return (
    <Page footer={<Button label="시작하기" onPress={() => router.push('/onboarding/level')} />}>
      <View style={styles.brand}>
        <View style={styles.brandMark}>
          <Icon name="spoon" size={30} color={colors.white} />
        </View>
        <AppText weight="extraBold" style={styles.brandName}>
          한끼툴
        </AppText>
      </View>
      <View style={styles.copy}>
        <Eyebrow>요리 초보를 위한 쉬운 레시피</Eyebrow>
        <AppText accessibilityRole="header" style={styles.title}>
          {'가진 도구로,\n어렵지 않게'}
        </AppText>
        <AppText style={styles.description}>
          {'계량스푼이 없어도 괜찮아요.\n집에 있는 숟가락과 컵으로 알려드릴게요.'}
        </AppText>
      </View>
      <Picture size={250} source={ILLUSTRATIONS.welcome} style={styles.visual} />
    </Page>
  );
}

const styles = StyleSheet.create({
  brand: { paddingTop: 26, flexDirection: 'row', alignItems: 'center', gap: 10 },
  brandMark: {
    width: 42,
    height: 42,
    borderRadius: 14,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    transform: [{ rotate: '-6deg' }],
  },
  brandName: { fontSize: 20 },
  copy: { paddingTop: 58 },
  title: {
    marginTop: 12,
    marginBottom: 16,
    fontFamily: fonts.extraBold,
    fontSize: 42,
    lineHeight: 47,
    letterSpacing: -1.8,
  },
  description: { fontSize: 17, lineHeight: 28, color: colors.sub },
  visual: { marginTop: 24, alignSelf: 'center' },
});
