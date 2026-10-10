// The user's own lists kept on the device: recent searches and favourite recipes.
// Only a recipe's id and name are kept; nothing about allergies or tools.

export const MAX_RECENT_QUERIES = 8;
// How many favourites the home screen shows; more can be stored.
export const HOME_FAVORITE_COUNT = 6;
const MAX_FAVORITES = 30;

export type Favorite = { id: string; name: string };

// The newest search comes first; searching the same thing again moves it to the front.
export function addRecentQuery(queries: string[], query: string): string[] {
  const text = query.trim();
  if (!text) return queries;
  return [text, ...queries.filter((item) => item !== text)].slice(0, MAX_RECENT_QUERIES);
}

export function removeRecentQuery(queries: string[], query: string): string[] {
  return queries.filter((item) => item !== query);
}

export function isFavorite(favorites: Favorite[], recipeId: string): boolean {
  return favorites.some((item) => item.id === recipeId);
}

// Adds the recipe at the front, or removes it when it is already a favourite.
export function toggleFavorite(favorites: Favorite[], recipe: Favorite): Favorite[] {
  if (isFavorite(favorites, recipe.id)) return favorites.filter((item) => item.id !== recipe.id);
  return [{ id: recipe.id, name: recipe.name }, ...favorites].slice(0, MAX_FAVORITES);
}

// Stored values come from an earlier run of the app, so their shape is checked before use.
export function sanitizeQueries(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is string => typeof item === 'string' && item.trim() !== '').slice(0, MAX_RECENT_QUERIES);
}

export function sanitizeFavorites(value: unknown): Favorite[] {
  if (!Array.isArray(value)) return [];
  return value
    .filter(
      (item): item is Favorite =>
        typeof item === 'object' && item !== null && typeof item.id === 'string' && typeof item.name === 'string',
    )
    .map((item) => ({ id: item.id, name: item.name }))
    .slice(0, MAX_FAVORITES);
}
