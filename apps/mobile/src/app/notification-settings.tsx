import { router, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { Linking, Pressable, StyleSheet, Switch, View } from 'react-native';

import { PageHeader } from '@/components/headers';
import { Page } from '@/components/page';
import { AppText } from '@/components/text';
import { TopBar } from '@/components/top-bar';
import { useAppState } from '@/state/app-state';
import { timerNotificationPermission, type NotificationPermission } from '@/state/timer-notifications';
import { colors } from '@/theme/tokens';

export default function NotificationSettingsScreen() {
  const { timerNotificationEnabled, setTimerNotificationEnabled } = useAppState();
  const [permission, setPermission] = useState<NotificationPermission>('unknown');

  // Checked again every time the screen is shown: the user may come back from the device settings.
  useFocusEffect(
    useCallback(() => {
      let cancelled = false;
      timerNotificationPermission().then((result) => {
        if (!cancelled) setPermission(result);
      });
      return () => {
        cancelled = true;
      };
    }, []),
  );

  return (
    <Page>
      <TopBar onBack={() => router.back()} />
      <PageHeader style={styles.header} title="알림 설정" />
      <View style={styles.row}>
        <View style={styles.rowText}>
          <AppText weight="bold" style={styles.label}>
            타이머 종료 알림
          </AppText>
          <AppText style={styles.note}>꺼도 화면 타이머는 그대로 동작해요.</AppText>
        </View>
        <Switch
          accessibilityLabel="타이머 종료 알림"
          onValueChange={setTimerNotificationEnabled}
          thumbColor={colors.white}
          trackColor={{ false: colors.line, true: colors.primary }}
          value={timerNotificationEnabled}
        />
      </View>
      {timerNotificationEnabled && permission === 'denied' ? (
        <View style={styles.warning}>
          <AppText weight="bold" style={styles.warningText}>
            기기 설정에서 알림이 꺼져 있어요
          </AppText>
          <Pressable accessibilityRole="button" hitSlop={10} onPress={() => Linking.openSettings()}>
            <AppText weight="bold" style={styles.warningAction}>
              설정 열기
            </AppText>
          </Pressable>
        </View>
      ) : null}
      {permission === 'unsupported' ? (
        <AppText style={styles.unsupported}>이 기기에서는 알림을 보낼 수 없어요. 화면 타이머만 동작해요.</AppText>
      ) : null}
    </Page>
  );
}

const styles = StyleSheet.create({
  header: { paddingTop: 26, marginBottom: 20 },
  row: {
    minHeight: 84,
    paddingVertical: 14,
    borderTopWidth: 1,
    borderBottomWidth: 1,
    borderColor: colors.line,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
  },
  rowText: { flex: 1, gap: 4 },
  label: { fontSize: 16 },
  note: { fontSize: 13, lineHeight: 19, color: colors.sub },
  warning: {
    marginTop: 18,
    padding: 16,
    borderRadius: 16,
    backgroundColor: colors.cautionSoft,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  warningText: { flex: 1, fontSize: 14, lineHeight: 20, color: '#986500' },
  warningAction: { fontSize: 14, color: colors.text, textDecorationLine: 'underline' },
  unsupported: { marginTop: 18, fontSize: 13, lineHeight: 19, color: colors.sub },
});
