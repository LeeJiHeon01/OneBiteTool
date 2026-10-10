import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { BottomSheet } from '@/components/bottom-sheet';
import { Button } from '@/components/button';
import { PageHeader } from '@/components/headers';
import { Page } from '@/components/page';
import { AppText } from '@/components/text';
import { TopBar } from '@/components/top-bar';
import { dateLabel, ratingLabel } from '@/data/history';
import { useAppState } from '@/state/app-state';
import { colors } from '@/theme/tokens';

// The user's own record of finished dishes. It lives on this device only.
export default function HistoryScreen() {
  const { cookRecords, removeCookRecord, clearCookRecords } = useAppState();
  const [clearSheet, setClearSheet] = useState(false);

  const clearAll = () => {
    clearCookRecords();
    setClearSheet(false);
  };

  return (
    <Page>
      <TopBar onBack={() => router.back()} />
      <PageHeader
        description="이 기기에만 저장돼요."
        style={styles.header}
        title="내 요리 기록"
      />
      {cookRecords.length === 0 ? (
        <AppText style={styles.empty}>아직 만든 요리가 없어요.</AppText>
      ) : (
        <>
          <View style={styles.list}>
            {cookRecords.map((record) => {
              const rating = ratingLabel(record.rating);
              return (
                <View key={record.id} style={styles.row}>
                  <View style={styles.rowText}>
                    <AppText weight="bold" style={styles.name}>
                      {record.recipeName}
                    </AppText>
                    <AppText style={styles.meta}>
                      {[dateLabel(record.completedAt), rating].filter(Boolean).join(' · ')}
                    </AppText>
                    {record.stuckStepOrders.length > 0 ? (
                      <AppText style={styles.meta}>막혔던 단계: {record.stuckStepOrders.join(', ')}</AppText>
                    ) : null}
                  </View>
                  <Pressable
                    accessibilityLabel={`${record.recipeName} 기록 지우기`}
                    accessibilityRole="button"
                    hitSlop={12}
                    onPress={() => removeCookRecord(record.id)}
                  >
                    <AppText style={styles.remove}>지우기</AppText>
                  </Pressable>
                </View>
              );
            })}
          </View>
          <Pressable accessibilityRole="button" onPress={() => setClearSheet(true)} style={styles.clear}>
            <AppText weight="bold" style={styles.clearLabel}>
              기록 모두 지우기
            </AppText>
          </Pressable>
        </>
      )}
      <BottomSheet
        description="만든 요리와 평가가 이 기기에서 삭제돼요."
        icon="alert"
        iconTone="danger"
        onClose={() => setClearSheet(false)}
        title="요리 기록을 모두 지울까요?"
        visible={clearSheet}
      >
        <Button label="취소" onPress={() => setClearSheet(false)} variant="secondary" />
        <Button label="모두 지우기" onPress={clearAll} variant="danger" />
      </BottomSheet>
    </Page>
  );
}

const styles = StyleSheet.create({
  header: { marginBottom: 20 },
  empty: { marginTop: 40, textAlign: 'center', fontSize: 15, color: colors.sub },
  list: { borderTopWidth: 1, borderTopColor: colors.line },
  row: {
    minHeight: 70,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  rowText: { flex: 1, gap: 3 },
  name: { fontSize: 16 },
  meta: { fontSize: 13, color: colors.sub },
  remove: { fontSize: 13, color: colors.sub },
  clear: { minHeight: 56, justifyContent: 'center' },
  clearLabel: { fontSize: 15, color: colors.danger },
});
