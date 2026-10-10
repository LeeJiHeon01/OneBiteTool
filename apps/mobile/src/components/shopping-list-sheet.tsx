import * as Clipboard from 'expo-clipboard';
import { useState } from 'react';
import { Pressable, ScrollView, Share, StyleSheet, View } from 'react-native';

import { BottomSheet } from '@/components/bottom-sheet';
import { Button } from '@/components/button';
import { Checkbox } from '@/components/checkbox';
import { AppText } from '@/components/text';
import { itemLabel, shoppingText, type ShoppingItem } from '@/data/shopping-list';
import { colors } from '@/theme/tokens';

type Props = {
  visible: boolean;
  onClose: () => void;
  recipeName: string;
  // Ingredients of the recipe the user lacks. Nothing can be added by hand.
  items: ShoppingItem[];
};

export function ShoppingListSheet({ visible, onClose, recipeName, items }: Props) {
  // Every line starts checked; unchecking one leaves it out of what is copied or shared.
  const [excluded, setExcluded] = useState<string[]>([]);
  const [notice, setNotice] = useState('');

  const chosen = items.filter((item) => !excluded.includes(item.id));
  const text = shoppingText(recipeName, chosen);

  const toggle = (id: string) => {
    setNotice('');
    setExcluded((current) => (current.includes(id) ? current.filter((item) => item !== id) : [...current, id]));
  };

  const copy = async () => {
    try {
      await Clipboard.setStringAsync(text);
      setNotice('목록을 복사했어요.');
    } catch {
      setNotice('복사하지 못했어요. 다시 시도해 주세요.');
    }
  };

  const share = async () => {
    try {
      await Share.share({ message: text });
    } catch {
      // No share sheet on this device (the web preview): copying still works.
      setNotice('이 기기에서는 공유할 수 없어요. 복사하기를 써 주세요.');
    }
  };

  const close = () => {
    setNotice('');
    onClose();
  };

  return (
    <BottomSheet
      description={items.length > 0 ? '레시피에 적힌 양이에요. 뺄 것은 체크를 풀어 주세요.' : '없는 재료를 고르면 여기에 모여요.'}
      onClose={close}
      title="장보기 목록"
      visible={visible}
    >
      {items.length > 0 ? (
        <ScrollView style={styles.list}>
          {items.map((item) => {
            const checked = !excluded.includes(item.id);
            return (
              <Pressable
                accessibilityLabel={itemLabel(item)}
                accessibilityRole="checkbox"
                accessibilityState={{ checked }}
                key={item.id}
                onPress={() => toggle(item.id)}
                style={styles.row}
              >
                <Checkbox checked={checked} />
                <AppText style={[styles.label, !checked && styles.labelOff]}>{itemLabel(item)}</AppText>
              </Pressable>
            );
          })}
        </ScrollView>
      ) : null}
      {notice ? <AppText style={styles.notice}>{notice}</AppText> : null}
      <View style={styles.actions}>
        <Button disabled={chosen.length === 0} label="복사하기" onPress={copy} variant="secondary" />
        <Button disabled={chosen.length === 0} label="공유하기" onPress={share} />
      </View>
    </BottomSheet>
  );
}

const styles = StyleSheet.create({
  list: { maxHeight: 280, marginBottom: 6 },
  row: {
    minHeight: 52,
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  label: { flex: 1, fontSize: 15 },
  labelOff: { color: colors.hint },
  notice: { marginTop: 8, fontSize: 13, color: colors.sub },
  actions: { marginTop: 8, gap: 8 },
});
