import { StyleSheet, View } from 'react-native';

import { Button } from '@/components/button';
import { CenterState } from '@/components/headers';
import { Icon } from '@/components/icon';
import { Page } from '@/components/page';
import { Picture } from '@/components/picture';
import { AppText } from '@/components/text';
import { ILLUSTRATIONS } from '@/data/images';
import { resetTo } from '@/nav';
import { useAppState } from '@/state/app-state';
import { colors, fonts } from '@/theme/tokens';

export default function CompleteScreen() {
  const { adaptation } = useAppState();

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
        eyebrow={`${adaptation?.steps.length ?? 0}단계 모두 완료`}
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
    </Page>
  );
}

const styles = StyleSheet.create({
  state: { paddingTop: 70 },
  title: { fontFamily: fonts.extraBold, fontSize: 35, lineHeight: 44 },
  visual: { marginBottom: 20 },
  tip: {
    marginTop: 54,
    padding: 18,
    borderRadius: 18,
    backgroundColor: colors.cautionSoft,
    flexDirection: 'row',
    gap: 12,
  },
  tipText: { flex: 1 },
  tipTitle: { fontSize: 14, lineHeight: 20, marginBottom: 2 },
  tipBody: { fontSize: 14, lineHeight: 20, color: '#986500' },
});
