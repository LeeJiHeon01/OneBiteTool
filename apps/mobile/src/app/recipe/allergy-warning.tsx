import { Redirect, router } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { Button } from '@/components/button';
import { Icon } from '@/components/icon';
import { Page } from '@/components/page';
import { AppText } from '@/components/text';
import { TopBar } from '@/components/top-bar';
import { findAllergyMatches, needsResearchNotice, PRODUCT_VARIES_NOTE, RESEARCH_NOTICE } from '@/data/allergy';
import { useAppState } from '@/state/app-state';
import { colors, fonts } from '@/theme/tokens';

export default function AllergyWarningScreen() {
  const { allergenCodes, recipe } = useAppState();
  if (!recipe) return <Redirect href="/home" />;

  const matches = findAllergyMatches(recipe.ingredients, allergenCodes);
  // Not in the verified mapping: shown as "needs checking", never as safe.
  const unchecked = recipe.ingredients.filter((ingredient) => ingredient.allergyCheckNeeded);

  return (
    <Page
      footer={
        <>
          <Button label="다른 요리 보기" onPress={() => router.dismissTo('/search')} />
          <Button label="그대로 진행" onPress={() => router.push('/recipe/missing')} variant="secondary" />
        </>
      }
    >
      <TopBar onBack={() => router.back()} />
      <View style={styles.heading}>
        <View style={styles.icon}>
          <Icon name="alert" size={38} color={colors.danger} />
        </View>
        <AppText accessibilityRole="header" style={styles.title}>
          {matches.length > 0 ? '피해야 할 재료가\n들어 있어요' : '확인되지 않은\n재료가 있어요'}
        </AppText>
      </View>
      <View style={[styles.box, matches.length === 0 && styles.boxEmpty]}>
        {matches.map((match, index) => (
          <View key={match.ingredientId} style={[styles.row, index > 0 && styles.rowBorder]}>
            <AppText weight="bold" style={styles.ingredient}>
              {match.ingredientName}
              {match.productVaries ? <AppText style={styles.varies}> 제품마다 달라요</AppText> : null}
            </AppText>
            <View style={styles.allergens}>
              <AppText weight="bold" style={styles.allergenNames}>
                {match.allergenNames}
              </AppText>
              {match.hidden ? (
                <View style={styles.hidden}>
                  <AppText weight="bold" style={styles.hiddenLabel}>
                    숨은 성분
                  </AppText>
                </View>
              ) : null}
            </View>
          </View>
        ))}
      </View>
      {unchecked.length > 0 ? (
        <View style={styles.unchecked}>
          <AppText weight="bold" style={styles.uncheckedTitle}>
            알레르기 정보를 확인하지 못한 재료
          </AppText>
          <AppText style={styles.uncheckedNames}>
            {unchecked.map((ingredient) => ingredient.name).join(', ')}
          </AppText>
          <AppText style={styles.uncheckedNote}>포장의 성분 표시를 직접 확인해 주세요.</AppText>
        </View>
      ) : null}
      {matches.some((match) => match.productVaries) ? (
        <AppText style={styles.notice}>{PRODUCT_VARIES_NOTE}</AppText>
      ) : null}
      {needsResearchNotice(recipe.ingredients, allergenCodes) ? (
        <AppText style={styles.notice}>{RESEARCH_NOTICE}</AppText>
      ) : null}
      <AppText style={styles.description}>
        {'재료를 자동으로 바꾸지 않아요.\n진행하면 해당 단계마다 다시 알려드릴게요.'}
      </AppText>
    </Page>
  );
}

const styles = StyleSheet.create({
  heading: { paddingTop: 35 },
  icon: {
    width: 70,
    height: 70,
    borderRadius: 24,
    backgroundColor: colors.dangerSoft,
    alignItems: 'center',
    justifyContent: 'center',
    transform: [{ rotate: '-4deg' }],
  },
  title: {
    marginTop: 22,
    marginBottom: 30,
    fontFamily: fonts.extraBold,
    fontSize: 32,
    lineHeight: 40,
    letterSpacing: -0.9,
  },
  box: { paddingVertical: 7, paddingHorizontal: 18, borderRadius: 18, backgroundColor: colors.dangerSoft },
  boxEmpty: { display: 'none' },
  notice: { marginTop: 12, marginHorizontal: 2, fontSize: 13, lineHeight: 19, color: colors.sub },
  varies: { fontSize: 11, color: colors.danger },
  unchecked: { marginTop: 14, padding: 18, borderRadius: 18, backgroundColor: colors.cautionSoft, gap: 6 },
  uncheckedTitle: { fontSize: 14 },
  uncheckedNames: { fontSize: 15, lineHeight: 23 },
  uncheckedNote: { fontSize: 12, color: colors.sub },
  row: { minHeight: 61, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  rowBorder: { borderTopWidth: 1, borderTopColor: 'rgba(240, 68, 82, 0.13)' },
  ingredient: { fontSize: 16 },
  allergens: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  allergenNames: { fontSize: 14, color: colors.danger },
  hidden: { paddingVertical: 3, paddingHorizontal: 6, borderRadius: 99, backgroundColor: colors.white },
  hiddenLabel: { fontSize: 10, color: colors.danger },
  description: { marginTop: 22, marginHorizontal: 2, fontSize: 15, lineHeight: 25, color: colors.sub },
});
