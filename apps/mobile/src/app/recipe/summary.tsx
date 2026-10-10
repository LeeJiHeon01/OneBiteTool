import { Redirect, router } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { AmountVisual } from '@/components/amount-visual';
import { Badge, type BadgeTone } from '@/components/badge';
import { Button } from '@/components/button';
import { PageHeader } from '@/components/headers';
import { Icon } from '@/components/icon';
import { SafetyNote } from '@/components/notes';
import { Page } from '@/components/page';
import { AppText } from '@/components/text';
import { TopBar } from '@/components/top-bar';
import { findAllergyMatches, PRODUCT_VARIES_NOTE, type AllergyMatch } from '@/data/allergy';
import { getTool } from '@/data/catalog';
import { type AdaptedIngredient } from '@/data/recipe';
import { useAppState } from '@/state/app-state';
import { colors, fonts } from '@/theme/tokens';

function badgesFor(row: AdaptedIngredient, hasAllergy: boolean): { label: string; tone: BadgeTone }[] {
  const badges: { label: string; tone: BadgeTone }[] = [];
  if (hasAllergy) badges.push({ label: '알레르기', tone: 'danger' });
  // Not in the verified mapping: its allergens are unknown, so it is never shown as safe.
  if (row.allergyCheckNeeded) badges.push({ label: '알레르기 확인 필요', tone: 'caution' });
  // Seasoning is never rounded up: the user tastes and adds.
  if (row.tasteAdjust) badges.push({ label: '맛보고 추가', tone: 'caution' });
  if (row.quantityStatus === 'NEEDS_CHECK') badges.push({ label: '확인 필요', tone: 'caution' });
  else if (row.quantityStatus === 'CONVERTED_APPROX') {
    badges.push(
      row.userMeasured
        ? { label: '내가 잰 용량 기준', tone: 'calibrated' }
        : { label: '대략적인 기준', tone: 'info' },
    );
  }
  return badges;
}

function AllergySummary({ matches }: { matches: AllergyMatch[] }) {
  return (
    <View style={styles.allergy}>
      <View style={styles.allergyHeading}>
        <Icon name="alert" size={21} color={colors.danger} />
        <AppText weight="bold" style={styles.allergyTitle}>
          피해야 할 재료가 들어 있어요
        </AppText>
      </View>
      {matches.map((match) => (
        <View key={match.ingredientId} style={styles.allergyRow}>
          <AppText weight="bold" style={styles.allergyItem}>
            {match.ingredientName} — {match.allergenNames}
          </AppText>
          {match.hidden ? (
            <View style={styles.hidden}>
              <AppText weight="bold" style={styles.hiddenLabel}>
                숨은 성분
              </AppText>
            </View>
          ) : null}
        </View>
      ))}
      {matches.some((match) => match.productVaries) ? (
        <AppText style={styles.allergyNote}>{PRODUCT_VARIES_NOTE}</AppText>
      ) : null}
      <AppText style={styles.allergyNote}>해당 조리 단계에서도 다시 알려드릴게요.</AppText>
    </View>
  );
}

type RowProps = {
  row: AdaptedIngredient;
  hasAllergy: boolean;
  // Shown to users with allergies only: the ingredient's allergens differ by product.
  showProductVaries: boolean;
};

function IngredientRow({ row, hasAllergy, showProductVaries }: RowProps) {
  return (
    <View style={styles.row}>
      <View style={styles.visual}>
        <AmountVisual toolId={row.visualToolId} type={row.visual} />
      </View>
      <View style={styles.rowText}>
        <AppText weight="bold" style={styles.rowTitle}>
          {row.name} · {row.displayText}
        </AppText>
        {/* The original amount is always shown under the converted one. */}
        {row.originalText ? <AppText style={styles.rowOriginal}>원래: {row.originalText}</AppText> : null}
        {showProductVaries ? <AppText style={styles.rowVaries}>제품마다 달라요</AppText> : null}
      </View>
      <View style={styles.badges}>
        {badgesFor(row, hasAllergy).map((badge) => (
          <Badge key={badge.label} label={badge.label} tone={badge.tone} />
        ))}
      </View>
    </View>
  );
}

export default function SummaryScreen() {
  const { adaptation, allergenCodes } = useAppState();
  if (!adaptation) return <Redirect href="/home" />;

  const matches = findAllergyMatches(adaptation.ingredients, allergenCodes);
  const allergyIds = matches.map((match) => match.ingredientId);

  return (
    <Page footer={<Button label="요리 시작하기" onPress={() => router.push('/cook')} />}>
      <TopBar onBack={() => router.back()} />
      <PageHeader
        eyebrow="맞춤 레시피가 준비됐어요"
        style={styles.header}
        title={'이렇게\n바뀌었어요'}
        titleStyle={styles.title}
      />
      {matches.length > 0 ? <AllergySummary matches={matches} /> : null}
      {adaptation.ignoredCustomToolIds.length > 0 ? (
        <AppText style={styles.ignoredVolumes}>
          {adaptation.ignoredCustomToolIds.map((id) => getTool(id)?.name ?? id).join(', ')}: 맞춘 용량을 쓸 수 없어 대표
          용량으로 안내했어요.
        </AppText>
      ) : null}
      {adaptation.changes.length > 0 ? (
        <View style={styles.changes}>
          <AppText weight="bold" style={styles.sectionTitle}>
            바뀐 점
          </AppText>
          {adaptation.changes.map((change) => (
            <View key={change.id} style={styles.change}>
              <View style={styles.changeIcon}>
                <AppText weight="extraBold" style={styles.changeIconLabel}>
                  −
                </AppText>
              </View>
              <View style={styles.changeText}>
                <AppText style={styles.changeLine}>{change.text}</AppText>
                {change.impact ? <AppText style={styles.changeImpact}>{change.impact}</AppText> : null}
              </View>
            </View>
          ))}
        </View>
      ) : null}
      <View style={styles.section}>
        <AppText weight="bold" style={styles.sectionTitle}>
          재료와 양
        </AppText>
        {adaptation.ingredients.map((row) => (
          <IngredientRow
            hasAllergy={allergyIds.includes(row.id)}
            key={row.id}
            row={row}
            showProductVaries={allergenCodes.length > 0 && row.allergenProductVaries}
          />
        ))}
      </View>
      {adaptation.safetySummary.map((message) => (
        <SafetyNote key={message} message={message} title="주의할 점" />
      ))}
      <AppText style={styles.disclaimer}>양은 선택한 도구의 대표 크기를 기준으로 한 대략적인 값이에요.</AppText>
    </Page>
  );
}

const styles = StyleSheet.create({
  ignoredVolumes: {
    marginBottom: 18,
    padding: 14,
    borderRadius: 14,
    backgroundColor: colors.cautionSoft,
    fontSize: 13,
    lineHeight: 19,
    color: '#986500',
  },
  header: { marginBottom: 25 },
  title: { fontFamily: fonts.extraBold, fontSize: 34, lineHeight: 42 },
  allergy: { marginBottom: 14, padding: 16, borderRadius: 18, backgroundColor: colors.dangerSoft },
  allergyHeading: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 12 },
  allergyTitle: { fontSize: 15 },
  allergyRow: { flexDirection: 'row', alignItems: 'center', gap: 6, marginBottom: 7 },
  allergyItem: { fontSize: 13, color: colors.danger },
  hidden: { paddingVertical: 3, paddingHorizontal: 6, borderRadius: 99, backgroundColor: colors.white },
  hiddenLabel: { fontSize: 10, color: colors.danger },
  allergyNote: { fontSize: 12, color: colors.sub },
  changes: { padding: 20, borderRadius: 20, backgroundColor: colors.surface },
  sectionTitle: { fontSize: 19, letterSpacing: -0.3 },
  change: { flexDirection: 'row', gap: 11, marginTop: 14 },
  changeIcon: {
    width: 24,
    height: 24,
    borderRadius: 8,
    backgroundColor: colors.white,
    alignItems: 'center',
    justifyContent: 'center',
  },
  changeIconLabel: { color: colors.primary },
  changeText: { flex: 1, marginTop: 1 },
  changeLine: { fontSize: 14, lineHeight: 20 },
  changeImpact: { marginTop: 3, fontSize: 12, color: colors.sub },
  section: { paddingTop: 30, paddingBottom: 18 },
  row: {
    minHeight: 82,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 11,
  },
  rowOmitted: { opacity: 0.5 },
  visual: {
    width: 56,
    height: 56,
    borderRadius: 16,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  rowText: { flex: 1 },
  rowTitle: { fontSize: 14, lineHeight: 20 },
  rowOriginal: { marginTop: 4, fontSize: 12, color: colors.sub },
  rowVaries: { marginTop: 2, fontSize: 11, color: colors.sub },
  struck: { textDecorationLine: 'line-through' },
  badges: { alignItems: 'flex-end', gap: 4 },
  disclaimer: { marginTop: 18, fontSize: 12, lineHeight: 18, color: colors.hint, textAlign: 'center' },
  guideVisual: { height: 144, alignItems: 'center', justifyContent: 'center' },
  guide: { padding: 14, borderRadius: 14, backgroundColor: colors.infoSoft },
  guideText: { fontSize: 15, lineHeight: 23, color: colors.info, textAlign: 'center' },
  guideButton: { marginTop: 13 },
});
