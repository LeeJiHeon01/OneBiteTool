import { router, type Href } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { BottomSheet } from '@/components/bottom-sheet';
import { Button } from '@/components/button';
import { PageHeader } from '@/components/headers';
import { Icon } from '@/components/icon';
import { Page } from '@/components/page';
import { AppText } from '@/components/text';
import { TopBar } from '@/components/top-bar';
import { SKILL_LEVELS } from '@/data/catalog';
import { resetTo } from '@/nav';
import { useAppState } from '@/state/app-state';
import { colors, gutter } from '@/theme/tokens';

function Row({ label, value, onPress }: { label: string; value?: string; onPress?: () => void }) {
  return (
    <Pressable accessibilityRole="button" disabled={!onPress} onPress={onPress} style={styles.row}>
      <AppText style={styles.rowLabel}>{label}</AppText>
      <View style={styles.rowValue}>
        {value ? <AppText style={styles.rowValueLabel}>{value}</AppText> : null}
        <Icon name="chevron" size={18} color={colors.sub} />
      </View>
    </Pressable>
  );
}

export default function SettingsScreen() {
  const { skillLevel, toolIds, allergenCodes, cookRecords, timerNotificationEnabled, clearAll } = useAppState();
  const [deleteSheet, setDeleteSheet] = useState(false);
  const edit = (pathname: '/onboarding/level' | '/onboarding/tools' | '/onboarding/allergy') => () =>
    router.push({ pathname, params: { edit: '1' } } as Href);

  const deleteAll = () => {
    clearAll();
    setDeleteSheet(false);
    resetTo('/');
  };

  return (
    <Page>
      <TopBar onBack={() => router.back()} />
      <PageHeader style={styles.header} title="설정" />
      <View style={styles.list}>
        <Row
          label="요리 실력"
          onPress={edit('/onboarding/level')}
          value={SKILL_LEVELS.find((level) => level.id === skillLevel)?.title ?? '선택 안 함'}
        />
        <Row label="가진 도구" onPress={edit('/onboarding/tools')} value={`${toolIds.length}개`} />
        <Row
          label="알레르기"
          onPress={edit('/onboarding/allergy')}
          value={allergenCodes.length > 0 ? `${allergenCodes.length}개` : '없음'}
        />
        <View style={styles.divider} />
        <Row label="내 요리 기록" onPress={() => router.push('/history' as Href)} value={`${cookRecords.length}개`} />
        <View style={styles.divider} />
        <Row
          label="알림 설정"
          onPress={() => router.push('/notification-settings' as Href)}
          value={timerNotificationEnabled ? '켜짐' : '꺼짐'}
        />
        <Row label="앱 정보" onPress={() => router.push('/about' as Href)} />
        <View style={styles.divider} />
        <Pressable accessibilityRole="button" onPress={() => setDeleteSheet(true)} style={styles.row}>
          <AppText weight="bold" style={styles.delete}>
            저장된 정보 모두 지우기
          </AppText>
        </Pressable>
      </View>
      <AppText style={styles.note}>양은 대표적인 도구 크기를 기준으로 한 대략적인 값이에요.</AppText>
      <BottomSheet
        description="실력·도구·알레르기 정보와 검색·즐겨찾기·요리 기록이 이 기기에서 삭제돼요."
        icon="alert"
        iconTone="danger"
        onClose={() => setDeleteSheet(false)}
        title="저장된 정보를 모두 지울까요?"
        visible={deleteSheet}
      >
        <Button label="취소" onPress={() => setDeleteSheet(false)} variant="secondary" />
        <Button label="모두 지우기" onPress={deleteAll} variant="danger" />
      </BottomSheet>
    </Page>
  );
}

const styles = StyleSheet.create({
  header: { paddingTop: 26, marginBottom: 25 },
  list: { borderTopWidth: 1, borderTopColor: colors.line },
  row: {
    height: 66,
    paddingHorizontal: 3,
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  rowLabel: { fontSize: 16 },
  rowValue: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  rowValueLabel: { fontSize: 14, color: colors.sub },
  divider: { height: 12, marginHorizontal: -gutter, backgroundColor: colors.surface },
  delete: { fontSize: 16, color: colors.danger },
  note: { marginTop: 'auto', paddingTop: 25, fontSize: 12, lineHeight: 18, color: colors.hint, textAlign: 'center' },
});
