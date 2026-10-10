import { Redirect, router } from 'expo-router';
import { Linking, Pressable, StyleSheet, View } from 'react-native';

import { Badge } from '@/components/badge';
import { Button } from '@/components/button';
import { Page } from '@/components/page';
import { Picture } from '@/components/picture';
import { AppText } from '@/components/text';
import { TopBar } from '@/components/top-bar';
import { findAllergyMatches, needsResearchNotice, RESEARCH_NOTICE } from '@/data/allergy';
import { toolImage } from '@/data/images';
import { isFavorite } from '@/data/library';
import { BADGE_LABELS } from '@/data/recipe';
import { useAppState } from '@/state/app-state';
import { colors, fonts } from '@/theme/tokens';

export default function RecipeScreen() {
  const { allergenCodes, recipe, favorites, toggleFavorite } = useAppState();
  if (!recipe) return <Redirect href="/home" />;

  const hasAllergyMatch = findAllergyMatches(recipe.ingredients, allergenCodes).length > 0;
  // An ingredient outside the verified mapping cannot be called safe for someone with allergies.
  const needsAllergyCheck =
    allergenCodes.length > 0 && recipe.ingredients.some((ingredient) => ingredient.allergyCheckNeeded);
  // Opening a recipe with an allergen shows the warning first; nothing is swapped automatically.
  const choose = () =>
    router.push(hasAllergyMatch || needsAllergyCheck ? '/recipe/allergy-warning' : '/recipe/missing');
  const { sourceUrl } = recipe;
  const favorite = isFavorite(favorites, recipe.id);

  return (
    <Page footer={<Button label="이 레시피로 할게요" onPress={choose} />}>
      <TopBar onBack={() => router.back()} />
      <View style={styles.header}>
        <View style={styles.source}>
          <Badge label={BADGE_LABELS[recipe.badge]} tone="info" />
          {sourceUrl ? (
            <Pressable accessibilityRole="link" hitSlop={12} onPress={() => Linking.openURL(sourceUrl)}>
              <AppText style={styles.sourceLink}>원문 보기 ↗</AppText>
            </Pressable>
          ) : null}
          {/* Only the recipe's id and name are kept, on this device. */}
          <Pressable
            accessibilityLabel={favorite ? '즐겨찾기에서 빼기' : '즐겨찾기에 넣기'}
            accessibilityRole="button"
            accessibilityState={{ selected: favorite }}
            hitSlop={12}
            onPress={() => toggleFavorite({ id: recipe.id, name: recipe.name })}
            style={styles.favorite}
          >
            <AppText style={[styles.favoriteMark, favorite && styles.favoriteOn]}>{favorite ? '★' : '☆'}</AppText>
          </Pressable>
        </View>
        <AppText accessibilityRole="header" style={styles.title}>
          {recipe.name}
        </AppText>
        <AppText style={styles.meta}>
          {recipe.servings}인분{recipe.minutes ? ` · 약 ${recipe.minutes}분` : ''}
        </AppText>
        {needsResearchNotice(recipe.ingredients, allergenCodes) ? (
          <AppText style={styles.researchNotice}>{RESEARCH_NOTICE}</AppText>
        ) : null}
      </View>

      {recipe.requiredTools.length > 0 ? (
        <View style={styles.section}>
          <AppText weight="bold" style={styles.sectionTitle}>
            필요한 도구
          </AppText>
          <View style={styles.pills}>
            {recipe.requiredTools.map((need) => {
              const image = toolImage(need.toolIds[0]);
              return (
                <View key={need.id} style={styles.pill}>
                  {image ? <Picture size={24} source={image} /> : null}
                  <AppText weight="semiBold" style={styles.pillLabel}>
                    {need.label}
                  </AppText>
                </View>
              );
            })}
          </View>
        </View>
      ) : null}

      <View style={styles.section}>
        <View style={styles.sectionHeading}>
          <AppText weight="bold" style={styles.sectionTitle}>
            재료
          </AppText>
          <AppText style={styles.sectionNote}>{recipe.ingredients.length}개</AppText>
        </View>
        {recipe.ingredients.map((ingredient, index) => (
          <View key={ingredient.id} style={[styles.ingredient, index > 0 && styles.ingredientBorder]}>
            <AppText style={styles.ingredientName}>
              {ingredient.name}
              {ingredient.dictionaryName ? ` (${ingredient.dictionaryName})` : ''}
              {allergenCodes.length > 0 && ingredient.allergenProductVaries ? (
                <AppText style={styles.productVaries}> 제품마다 달라요</AppText>
              ) : null}
            </AppText>
            <AppText weight="medium" style={styles.ingredientAmount}>
              {ingredient.rawText}
            </AppText>
          </View>
        ))}
      </View>

      <View style={[styles.section, styles.lastSection]}>
        <AppText weight="bold" style={styles.sectionTitle}>
          만드는 순서
        </AppText>
        {recipe.steps.map((step) => (
          <View key={step.order} style={styles.step}>
            <View style={styles.stepNumber}>
              <AppText weight="bold" style={styles.stepNumberLabel}>
                {step.order}
              </AppText>
            </View>
            <AppText style={styles.stepText}>{step.text}</AppText>
          </View>
        ))}
      </View>
    </Page>
  );
}

const styles = StyleSheet.create({
  header: { paddingTop: 20, paddingBottom: 39, borderBottomWidth: 1, borderBottomColor: colors.line },
  source: { flexDirection: 'row', alignItems: 'center', gap: 10, marginBottom: 20 },
  sourceLink: { fontSize: 12, color: colors.sub },
  favorite: { marginLeft: 'auto' },
  favoriteMark: { fontSize: 26, lineHeight: 30, color: colors.hint },
  favoriteOn: { color: colors.primary },
  title: { fontFamily: fonts.extraBold, fontSize: 35, lineHeight: 42, letterSpacing: -0.8 },
  meta: { marginTop: 13, fontSize: 16, color: colors.sub },
  researchNotice: {
    marginTop: 14,
    padding: 12,
    borderRadius: 12,
    backgroundColor: colors.cautionSoft,
    fontSize: 13,
    lineHeight: 19,
  },
  productVaries: { fontSize: 11, color: colors.sub },
  section: { paddingVertical: 30, borderBottomWidth: 1, borderBottomColor: colors.line },
  lastSection: { borderBottomWidth: 0, paddingBottom: 0 },
  sectionHeading: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  sectionTitle: { marginBottom: 17, fontSize: 19, letterSpacing: -0.3 },
  sectionNote: { marginBottom: 17, fontSize: 13, color: colors.sub },
  pills: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  pill: {
    minHeight: 42,
    paddingHorizontal: 13,
    borderRadius: 13,
    backgroundColor: colors.surface,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
  },
  pillLabel: { fontSize: 14 },
  ingredient: { height: 47, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  ingredientBorder: { borderTopWidth: 1, borderTopColor: '#F1F2F4' },
  ingredientName: { fontSize: 15 },
  ingredientAmount: { fontSize: 15, color: colors.sub },
  step: { flexDirection: 'row', alignItems: 'flex-start', gap: 13, marginBottom: 16 },
  stepNumber: {
    width: 25,
    height: 25,
    borderRadius: 8,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepNumberLabel: { fontSize: 12, color: colors.sub },
  stepText: { flex: 1, marginTop: 2, fontSize: 14, lineHeight: 21 },
});
