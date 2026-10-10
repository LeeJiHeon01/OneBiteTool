import { type RecipeIngredient } from '@/data/recipe';

// A shopping list of the recipe's ingredients the user said they do not have. It only lists
// what the recipe already contains, with the amount as the source wrote it: nothing can be
// typed in, and allergy information is never part of it.

export type ShoppingItem = { id: string; name: string; amount: string };

// In the recipe's own order.
export function shoppingItems(ingredients: RecipeIngredient[], missingIds: string[]): ShoppingItem[] {
  return ingredients
    .filter((ingredient) => missingIds.includes(ingredient.id))
    .map((ingredient) => ({ id: ingredient.id, name: ingredient.name, amount: ingredient.rawText.trim() }));
}

// "참기름 · 1작은술"; an ingredient the source gives no amount for is listed by name only.
export function itemLabel(item: ShoppingItem): string {
  return item.amount ? `${item.name} · ${item.amount}` : item.name;
}

// The text that is copied or shared.
export function shoppingText(recipeName: string, items: ShoppingItem[]): string {
  return [`장보기 목록 (${recipeName})`, ...items.map((item) => `- ${itemLabel(item)}`)].join('\n');
}
