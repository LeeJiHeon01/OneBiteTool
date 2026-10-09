import { type ReactNode } from 'react';
import { ScrollView, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { colors, gutter } from '@/theme/tokens';

type Props = {
  children: ReactNode;
  // Pinned below the scroll area (primary action).
  footer?: ReactNode;
  // Floats above the content, e.g. the mini timer bar.
  overlay?: ReactNode;
  contentStyle?: StyleProp<ViewStyle>;
  footerStyle?: StyleProp<ViewStyle>;
};

export function Page({ children, footer, overlay, contentStyle, footerStyle }: Props) {
  return (
    <SafeAreaView edges={['top', 'bottom']} style={styles.root}>
      <ScrollView
        contentContainerStyle={[styles.content, contentStyle]}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        {children}
      </ScrollView>
      {footer ? <View style={[styles.footer, footerStyle]}>{footer}</View> : null}
      {overlay}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.white },
  content: { flexGrow: 1, paddingHorizontal: gutter, paddingBottom: 28 },
  footer: { paddingHorizontal: gutter, paddingTop: 14, paddingBottom: 12, gap: 5 },
});
