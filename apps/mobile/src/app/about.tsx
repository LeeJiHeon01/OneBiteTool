import Constants from 'expo-constants';
import { router } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { Icon } from '@/components/icon';
import { Page } from '@/components/page';
import { AppText } from '@/components/text';
import { TopBar } from '@/components/top-bar';
import { colors, fonts } from '@/theme/tokens';

// TODO(spec): 문서 URL — 이용약관, 개인정보처리방침, 문의 주소가 정해지면 각 줄에 연결한다.
const DOCUMENTS = ['이용약관', '개인정보처리방침', '문의하기'];

export default function AboutScreen() {
  const version = Constants.expoConfig?.version ?? '';

  return (
    <Page>
      <TopBar onBack={() => router.back()} />
      <View style={styles.brand}>
        <View style={styles.mark}>
          <Icon name="spoon" size={34} color={colors.white} />
        </View>
        <AppText accessibilityRole="header" style={styles.name}>
          한끼툴
        </AppText>
        {version ? <AppText style={styles.version}>버전 {version}</AppText> : null}
      </View>
      <View style={styles.list}>
        {DOCUMENTS.map((label) => (
          <View accessibilityState={{ disabled: true }} key={label} style={styles.row}>
            <AppText style={styles.rowLabel}>{label}</AppText>
            <AppText style={styles.rowNote}>준비 중</AppText>
          </View>
        ))}
      </View>
      <AppText style={styles.disclaimer}>
        양은 대표적인 도구 크기를 기준으로 한 대략적인 값이에요. 알레르기 정보는 참고용이며, 제품 성분표를 꼭
        확인하세요. 불과 칼을 쓸 때는 안전에 주의해 주세요.
      </AppText>
    </Page>
  );
}

const styles = StyleSheet.create({
  brand: { paddingTop: 36, paddingBottom: 34, alignItems: 'center' },
  mark: {
    width: 72,
    height: 72,
    marginBottom: 16,
    borderRadius: 22,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  name: { fontFamily: fonts.extraBold, fontSize: 24 },
  version: { marginTop: 6, fontSize: 14, color: colors.sub },
  list: { borderTopWidth: 1, borderTopColor: colors.line },
  row: {
    height: 62,
    paddingHorizontal: 3,
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  rowLabel: { fontSize: 16 },
  rowNote: { fontSize: 13, color: colors.hint },
  disclaimer: { marginTop: 'auto', paddingTop: 28, fontSize: 12, lineHeight: 18, color: colors.hint, textAlign: 'center' },
});
