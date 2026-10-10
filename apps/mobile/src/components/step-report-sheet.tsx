import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { BottomSheet } from '@/components/bottom-sheet';
import { Button } from '@/components/button';
import { AppText } from '@/components/text';
import { colors } from '@/theme/tokens';

// Reasons a user can pick for "this step looks wrong". There is no free text on purpose.
export const REPORT_REASONS = [
  { id: 'AMOUNT', label: '양이 이상해요' },
  { id: 'TIME', label: '시간이 이상해요' },
  { id: 'HEAT', label: '불 세기가 이상해요' },
  { id: 'ORDER', label: '순서가 이상해요' },
  { id: 'UNCLEAR', label: '설명이 이해가 안 돼요' },
  { id: 'SAFETY', label: '안전 문구가 필요해요' },
] as const;

export type ReportReason = (typeof REPORT_REASONS)[number]['id'];

type Props = {
  visible: boolean;
  onClose: () => void;
  // Called with the picked reason when the user presses send.
  onSend: (reason: ReportReason) => void;
};

export function StepReportSheet({ visible, onClose, onSend }: Props) {
  const [reason, setReason] = useState<ReportReason | null>(null);

  const close = () => {
    setReason(null);
    onClose();
  };

  const send = () => {
    if (!reason) return;
    onSend(reason);
    close();
  };

  return (
    <BottomSheet onClose={close} title="무엇이 이상한가요?" visible={visible}>
      <View accessibilityRole="radiogroup" style={styles.list}>
        {REPORT_REASONS.map((item) => {
          const selected = reason === item.id;
          return (
            <Pressable
              accessibilityRole="radio"
              accessibilityState={{ selected }}
              key={item.id}
              onPress={() => setReason(item.id)}
              style={styles.row}
            >
              <View style={[styles.radio, selected && styles.radioOn]} />
              <AppText weight={selected ? 'bold' : 'regular'} style={styles.label}>
                {item.label}
              </AppText>
            </Pressable>
          );
        })}
      </View>
      <Button disabled={!reason} label="보내기" onPress={send} />
    </BottomSheet>
  );
}

const styles = StyleSheet.create({
  list: { marginBottom: 8, borderTopWidth: 1, borderTopColor: colors.line },
  row: {
    minHeight: 50,
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  radio: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: colors.hint,
    backgroundColor: colors.white,
  },
  radioOn: { borderWidth: 6, borderColor: colors.primary },
  label: { fontSize: 15 },
});
