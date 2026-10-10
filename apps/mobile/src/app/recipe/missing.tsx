import { Redirect, router } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { Badge } from '@/components/badge';
import { BottomSheet } from '@/components/bottom-sheet';
import { Button } from '@/components/button';
import { Checkbox } from '@/components/checkbox';
import { PageHeader } from '@/components/headers';
import { Page } from '@/components/page';
import { ShoppingListSheet } from '@/components/shopping-list-sheet';
import { AppText } from '@/components/text';
import { TopBar } from '@/components/top-bar';
import { findAllergyMatches } from '@/data/allergy';
import { type Adaptation } from '@/data/recipe';
import { shoppingItems } from '@/data/shopping-list';
import { useAppState } from '@/state/app-state';
import { colors } from '@/theme/tokens';

export default function MissingIngredientsScreen() {
  const { allergenCodes, recipe, missingIds, toggleMissing, adaptation, answerSubstitute } = useAppState();
  // The answer the user closed the question for, so the sheet does not reopen by itself.
  const [dismissed, setDismissed] = useState<Adaptation | null>(null);
  const [shoppingList, setShoppingList] = useState(false);
  if (!recipe) return <Redirect href="/home" />;

  const allergyIds = findAllergyMatches(recipe.ingredients, allergenCodes).map((match) => match.ingredientId);
  // Substitutes are only offered by the server's rules; the user confirms whether they have one.
  const question = adaptation?.status === 'NEEDS_CONFIRMATION' ? adaptation.pendingQuestions[0] : undefined;
  const asking = Boolean(question) && dismissed !== adaptation;

  // The adapting screen sends the request and comes back here when a question is open.
  const makeRecipe = () => {
    setDismissed(null);
    router.push('/recipe/adapting');
  };

  const answer = (hasIt: boolean) => {
    if (!question) return;
    answerSubstitute({ ingredientId: question.ingredientId, substituteId: question.substituteId, hasIt });
    makeRecipe();
  };

  return (
    <Page
      footer={
        <>
          <Button label="맞춤 레시피 만들기" onPress={makeRecipe} />
          {missingIds.length > 0 ? (
            <Button label="장보기 목록 만들기" onPress={() => setShoppingList(true)} variant="text" />
          ) : null}
        </>
      }
    >
      <TopBar onBack={() => router.back()} />
      <PageHeader
        description="있는 재료는 그대로 두면 돼요."
        style={styles.header}
        title={'없는 재료를\n골라주세요'}
      />
      <View style={styles.list}>
        {recipe.ingredients.map((ingredient) => {
          const checked = missingIds.includes(ingredient.id);
          return (
            <Pressable
              accessibilityLabel={`${ingredient.name} 없음`}
              accessibilityRole="checkbox"
              accessibilityState={{ checked }}
              key={ingredient.id}
              onPress={() => toggleMissing(ingredient.id)}
              style={styles.row}
            >
              <View style={styles.name}>
                <AppText weight="bold" style={[styles.nameLabel, checked && styles.struck]}>
                  {ingredient.name}
                  {ingredient.dictionaryName ? ` (${ingredient.dictionaryName})` : ''}
                </AppText>
                {allergyIds.includes(ingredient.id) ? <Badge label="알레르기" tone="danger" /> : null}
              </View>
              <AppText style={[styles.amount, checked && styles.struck]}>{ingredient.rawText}</AppText>
              <Checkbox checked={checked} />
            </Pressable>
          );
        })}
      </View>
      <BottomSheet
        description={question?.description}
        icon="sparkle"
        onClose={() => setDismissed(adaptation)}
        title={question?.title ?? ''}
        visible={asking}
      >
        <Button label="있어요" onPress={() => answer(true)} />
        <Button label="없어요" onPress={() => answer(false)} variant="secondary" />
      </BottomSheet>
      <ShoppingListSheet
        items={shoppingItems(recipe.ingredients, missingIds)}
        onClose={() => setShoppingList(false)}
        recipeName={recipe.name}
        visible={shoppingList}
      />
    </Page>
  );
}

const styles = StyleSheet.create({
  header: { paddingTop: 25, marginBottom: 20 },
  list: { borderTopWidth: 1, borderTopColor: colors.line },
  row: {
    height: 61,
    paddingHorizontal: 2,
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 11,
  },
  name: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 7 },
  nameLabel: { fontSize: 16 },
  amount: { fontSize: 14, color: colors.sub },
  struck: { color: colors.hint, textDecorationLine: 'line-through' },
});
