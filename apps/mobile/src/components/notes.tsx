import { StyleSheet, View } from 'react-native';

import { Icon } from '@/components/icon';
import { AppText } from '@/components/text';
import { PRODUCT_VARIES_NOTE } from '@/data/allergy';
import { colors } from '@/theme/tokens';

type SafetyProps = {
  title: string;
  message: string;
  // Cooking mode uses larger text readable at arm's length.
  large?: boolean;
};

export function SafetyNote({ title, message, large = false }: SafetyProps) {
  return (
    <View style={[styles.note, styles.safety, large && styles.large]}>
      <Icon name="shield" size={large ? 21 : 22} color={colors.cautionText} />
      <View style={styles.body}>
        <AppText weight={large ? 'extraBold' : 'bold'} style={large ? styles.safetyTitleLarge : styles.safetyTitle}>
          {title}
        </AppText>
        <AppText style={[styles.safetyMessage, large && styles.safetyMessageLarge]}>{message}</AppText>
      </View>
    </View>
  );
}

// Shown on every cooking step that uses an ingredient the user must avoid.
type AllergyBannerProps = {
  ingredientName: string;
  allergenNames: string;
  // The ingredient's contents differ by product, so the user is told to read the label.
  productVaries?: boolean;
};

export function AllergyBanner({ ingredientName, allergenNames, productVaries = false }: AllergyBannerProps) {
  return (
    <View style={[styles.note, styles.allergy]}>
      <Icon name="alert" size={20} color={colors.danger} />
      <View style={styles.body}>
        <AppText weight="bold" style={styles.allergyText}>
          <AppText weight="bold" style={styles.allergyStrong}>
            {ingredientName} — {allergenNames}
          </AppText>
          {' 알레르기 재료예요'}
        </AppText>
        {productVaries ? <AppText style={styles.allergyNote}>{PRODUCT_VARIES_NOTE}</AppText> : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  note: { flexDirection: 'row', borderRadius: 16 },
  body: { flex: 1 },
  safety: { gap: 11, alignItems: 'flex-start', padding: 15, backgroundColor: colors.cautionSoft },
  large: { padding: 18 },
  safetyTitle: { fontSize: 13, lineHeight: 19, marginBottom: 2 },
  safetyTitleLarge: { fontSize: 16, lineHeight: 24, marginBottom: 2 },
  safetyMessage: { fontSize: 13, lineHeight: 19, color: colors.cautionText },
  safetyMessageLarge: { fontSize: 18, lineHeight: 27 },
  allergy: {
    minHeight: 58,
    gap: 9,
    alignItems: 'center',
    paddingVertical: 10,
    paddingHorizontal: 13,
    borderRadius: 14,
    backgroundColor: colors.dangerSoft,
  },
  allergyText: { fontSize: 18, lineHeight: 25, color: colors.danger },
  allergyNote: { marginTop: 4, fontSize: 13, lineHeight: 19, color: colors.text },
  allergyStrong: { fontSize: 18, color: colors.text },
});
