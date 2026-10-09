import { type ReactNode } from 'react';
import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Icon, type IconName } from '@/components/icon';
import { AppText } from '@/components/text';
import { colors, gutter } from '@/theme/tokens';

type Tone = 'primary' | 'dark' | 'danger';

type Props = {
  visible: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  icon?: IconName;
  iconTone?: Tone;
  // Extra content and action buttons.
  children: ReactNode;
};

const ICON_TONES: Record<Tone, { background: string; color: string }> = {
  primary: { background: colors.primarySoft, color: colors.primary },
  dark: { background: colors.surface, color: colors.text },
  danger: { background: colors.dangerSoft, color: colors.danger },
};

export function BottomSheet({
  visible,
  onClose,
  title,
  description,
  icon,
  iconTone = 'primary',
  children,
}: Props) {
  const insets = useSafeAreaInsets();
  const tone = ICON_TONES[iconTone];
  return (
    <Modal animationType="fade" onRequestClose={onClose} transparent visible={visible}>
      <Pressable accessibilityLabel="닫기" onPress={onClose} style={styles.backdrop}>
        {/* Inner Pressable swallows taps so only the backdrop closes the sheet. */}
        <Pressable style={[styles.sheet, { paddingBottom: Math.max(25, insets.bottom) }]}>
          <View style={styles.handle} />
          {icon ? (
            <View style={[styles.icon, { backgroundColor: tone.background }]}>
              <Icon name={icon} size={25} color={tone.color} />
            </View>
          ) : null}
          <AppText weight="bold" style={styles.title}>
            {title}
          </AppText>
          {description ? <AppText style={styles.description}>{description}</AppText> : null}
          <View style={styles.body}>{children}</View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(25, 31, 40, 0.42)' },
  sheet: {
    paddingTop: 10,
    paddingHorizontal: gutter,
    borderTopLeftRadius: 26,
    borderTopRightRadius: 26,
    backgroundColor: colors.white,
  },
  handle: {
    alignSelf: 'center',
    width: 40,
    height: 5,
    marginBottom: 25,
    borderRadius: 99,
    backgroundColor: colors.line,
  },
  icon: {
    width: 48,
    height: 48,
    marginBottom: 18,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: { fontSize: 24, lineHeight: 31, letterSpacing: -0.3 },
  description: { marginTop: 10, color: colors.sub, fontSize: 15, lineHeight: 23 },
  body: { marginTop: 26, gap: 9 },
});
