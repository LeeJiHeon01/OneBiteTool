import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppText } from '@/components/text';
import { colors, gutter } from '@/theme/tokens';

// A short message over the bottom of any screen that goes away by itself.

const VISIBLE_MS = 2600;

const ToastContext = createContext<((message: string) => void) | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const insets = useSafeAreaInsets();
  const [message, setMessage] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // A new message replaces the one on screen.
  const show = useCallback((next: string) => {
    if (timer.current) clearTimeout(timer.current);
    setMessage(next);
    timer.current = setTimeout(() => setMessage(null), VISIBLE_MS);
  }, []);

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );

  return (
    <ToastContext.Provider value={show}>
      {children}
      {message ? (
        <View pointerEvents="none" style={[styles.layer, { bottom: Math.max(24, insets.bottom) + 84 }]}>
          <View accessibilityLiveRegion="polite" accessibilityRole="alert" style={styles.toast}>
            <AppText weight="semiBold" style={styles.label}>
              {message}
            </AppText>
          </View>
        </View>
      ) : null}
    </ToastContext.Provider>
  );
}

export function useToast(): (message: string) => void {
  const show = useContext(ToastContext);
  if (!show) throw new Error('useToast must be used inside ToastProvider');
  return show;
}

const styles = StyleSheet.create({
  layer: { position: 'absolute', left: gutter, right: gutter, alignItems: 'center' },
  toast: { maxWidth: 420, paddingVertical: 13, paddingHorizontal: 18, borderRadius: 14, backgroundColor: colors.text },
  label: { fontSize: 14, lineHeight: 20, color: colors.white, textAlign: 'center' },
});
