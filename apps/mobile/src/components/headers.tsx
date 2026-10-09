import { type ReactNode } from 'react';
import { StyleSheet, View, type StyleProp, type TextStyle, type ViewStyle } from 'react-native';

import { AppText } from '@/components/text';
import { colors, titleStyle } from '@/theme/tokens';

type HeaderProps = {
  title: string;
  eyebrow?: string;
  description?: string;
  style?: StyleProp<ViewStyle>;
  titleStyle?: StyleProp<TextStyle>;
};

export function Eyebrow({ children, style }: { children: string; style?: StyleProp<TextStyle> }) {
  return (
    <AppText weight="bold" style={[styles.eyebrow, style]}>
      {children}
    </AppText>
  );
}

export function PageHeader({ title, eyebrow, description, style, titleStyle: titleOverride }: HeaderProps) {
  return (
    <View style={[styles.header, style]}>
      {eyebrow ? <Eyebrow>{eyebrow}</Eyebrow> : null}
      <AppText accessibilityRole="header" style={[titleStyle, eyebrow && styles.afterEyebrow, titleOverride]}>
        {title}
      </AppText>
      {description ? <AppText style={styles.description}>{description}</AppText> : null}
    </View>
  );
}

type CenterProps = HeaderProps & { visual: ReactNode };

// Centered illustration + title block used by loading, empty and result states.
export function CenterState({ visual, title, eyebrow, description, style, titleStyle: titleOverride }: CenterProps) {
  return (
    <View style={[styles.center, style]}>
      {visual}
      {eyebrow ? <Eyebrow style={styles.centerEyebrow}>{eyebrow}</Eyebrow> : null}
      <AppText accessibilityRole="header" style={[titleStyle, styles.centerText, titleOverride]}>
        {title}
      </AppText>
      {description ? <AppText style={[styles.description, styles.centerText]}>{description}</AppText> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  header: { paddingTop: 34, marginBottom: 32 },
  eyebrow: { color: colors.primary, fontSize: 14, letterSpacing: -0.1 },
  afterEyebrow: { marginTop: 9 },
  description: { marginTop: 10, fontSize: 16, lineHeight: 24, color: colors.sub },
  center: { alignItems: 'center' },
  centerEyebrow: { marginBottom: 11 },
  centerText: { textAlign: 'center' },
});
