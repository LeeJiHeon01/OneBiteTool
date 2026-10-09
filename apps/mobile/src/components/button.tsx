import { type ReactNode } from 'react';
import { Pressable, StyleSheet, type StyleProp, type ViewStyle } from 'react-native';

import { Icon, type IconName } from '@/components/icon';
import { AppText } from '@/components/text';
import { colors } from '@/theme/tokens';

type Variant = 'primary' | 'secondary' | 'text' | 'danger';

type Props = {
  label: string;
  onPress?: () => void;
  variant?: Variant;
  disabled?: boolean;
  icon?: IconName;
  trailing?: ReactNode;
  // 'cook' is the larger size used in cooking mode (min touch height 62).
  size?: 'default' | 'cook';
  style?: StyleProp<ViewStyle>;
};

const LABEL_COLOR: Record<Variant, string> = {
  primary: colors.white,
  secondary: colors.text,
  text: colors.sub,
  danger: colors.white,
};

export function Button({
  label,
  onPress,
  variant = 'primary',
  disabled = false,
  icon,
  trailing,
  size = 'default',
  style,
}: Props) {
  const labelColor = disabled ? colors.hint : LABEL_COLOR[variant];
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.base,
        styles[variant],
        size === 'cook' && styles.cook,
        disabled && styles.disabled,
        pressed && styles.pressed,
        style,
      ]}
    >
      {icon ? <Icon name={icon} size={22} color={labelColor} /> : null}
      <AppText
        weight="bold"
        style={[
          styles.label,
          variant === 'text' && styles.textLabel,
          size === 'cook' && styles.cookLabel,
          { color: labelColor },
        ]}
      >
        {label}
      </AppText>
      {trailing}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    height: 58,
    borderRadius: 17,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 7,
  },
  primary: { backgroundColor: colors.primary, boxShadow: '0 8px 20px rgba(255, 107, 53, 0.18)' },
  secondary: { backgroundColor: colors.surface },
  text: { backgroundColor: 'transparent', height: 48 },
  danger: { backgroundColor: colors.danger, boxShadow: '0 8px 20px rgba(240, 68, 82, 0.17)' },
  cook: { height: 62, borderRadius: 16 },
  disabled: { backgroundColor: colors.surface, boxShadow: 'none' },
  pressed: { opacity: 0.9, transform: [{ scale: 0.985 }] },
  label: { fontSize: 17 },
  textLabel: { fontSize: 15 },
  cookLabel: { fontSize: 20 },
});
