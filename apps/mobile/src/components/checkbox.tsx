import { StyleSheet, View } from 'react-native';

import { Icon } from '@/components/icon';
import { colors } from '@/theme/tokens';

// Visual only; the parent row owns the press handler.
export function Checkbox({ checked }: { checked: boolean }) {
  return (
    <View style={[styles.box, checked && styles.checked]}>
      {checked ? <Icon name="check" size={16} color={colors.white} /> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  box: {
    width: 24,
    height: 24,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 7,
    alignItems: 'center',
    justifyContent: 'center',
  },
  checked: { backgroundColor: colors.primary, borderColor: colors.primary },
});
