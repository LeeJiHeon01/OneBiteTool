import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';

import { adaptRecipe, fetchRecipe, type SubstituteAnswer } from '@/api/recipes';
import { resolveHeatSource, type HeatSource, type SkillLevel } from '@/data/catalog';
import {
  addRecord,
  newRecord,
  removeRecord,
  sanitizeRecords,
  updateRecord,
  type CookRecord,
} from '@/data/history';
import {
  addRecentQuery as withRecentQuery,
  removeRecentQuery as withoutRecentQuery,
  sanitizeFavorites,
  sanitizeQueries,
  toggleFavorite as withFavoriteToggled,
  type Favorite,
} from '@/data/library';
import { type Adaptation, type Recipe } from '@/data/recipe';
import {
  volumesToSend,
  withoutVolume,
  withVolume,
  type CustomToolVolume,
} from '@/data/tool-volume';
import { loadJson, saveJson } from '@/state/device-store';
import { loadProfile, saveProfile } from '@/state/profile-storage';
import { useTimer } from '@/state/use-timer';

// App state. The profile (skill, tools, allergies) is kept on the device and restored at
// start (see profile-storage); the recipe session comes from the server and is in memory only.

const RECENT_QUERIES_KEY = 'recent-queries.v1';
const FAVORITES_KEY = 'favorites.v1';
const COOK_HISTORY_KEY = 'cook-history.v1';

function toggleIn(list: string[], value: string): string[] {
  return list.includes(value) ? list.filter((item) => item !== value) : [...list, value];
}

function useProfile() {
  const [skillLevel, setSkillLevel] = useState<SkillLevel | null>(null);
  const [toolIds, setToolIds] = useState<string[]>([]);
  const [customToolVolumes, setCustomToolVolumes] = useState<CustomToolVolume[]>([]);
  const [preferredHeat, setPreferredHeat] = useState<HeatSource>('gas_stove');
  const [allergenCodes, setAllergenCodes] = useState<string[]>([]);
  const [allergyConsent, setAllergyConsent] = useState(false);
  // True once the user has finished the first-run screens; the app then opens on home.
  const [onboarded, setOnboarded] = useState(false);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    loadProfile().then((stored) => {
      if (stored) {
        setSkillLevel(stored.skillLevel);
        setToolIds(stored.toolIds);
        setCustomToolVolumes(stored.customToolVolumes);
        setPreferredHeat(stored.preferredHeat);
        setAllergenCodes(stored.allergenCodes);
        setAllergyConsent(stored.allergyConsent);
        setOnboarded(stored.onboarded);
      }
      setHydrated(true);
    });
  }, []);

  // Saved only after the stored profile is loaded, so the empty start state never overwrites it.
  useEffect(() => {
    if (!hydrated) return;
    saveProfile({
      skillLevel,
      toolIds,
      customToolVolumes,
      preferredHeat,
      allergenCodes,
      allergyConsent,
      onboarded,
    });
  }, [hydrated, skillLevel, toolIds, customToolVolumes, preferredHeat, allergenCodes, allergyConsent, onboarded]);

  const completeOnboarding = useCallback(() => setOnboarded(true), []);
  const toggleTool = useCallback((id: string) => setToolIds((current) => toggleIn(current, id)), []);
  const saveToolVolume = useCallback(
    (volume: CustomToolVolume) => setCustomToolVolumes((current) => withVolume(current, volume)),
    [],
  );
  // Back to the representative volume.
  const resetToolVolume = useCallback(
    (toolId: string) => setCustomToolVolumes((current) => withoutVolume(current, toolId)),
    [],
  );
  const toggleAllergen = useCallback(
    (code: string) => setAllergenCodes((current) => toggleIn(current, code)),
    [],
  );
  const clearAllergens = useCallback(() => {
    setAllergenCodes([]);
    setAllergyConsent(false);
  }, []);
  const clearProfile = useCallback(() => {
    setSkillLevel(null);
    setToolIds([]);
    setCustomToolVolumes([]);
    setPreferredHeat('gas_stove');
    setOnboarded(false);
    clearAllergens();
  }, [clearAllergens]);

  return {
    hydrated,
    onboarded,
    completeOnboarding,
    skillLevel,
    setSkillLevel,
    toolIds,
    toggleTool,
    customToolVolumes,
    saveToolVolume,
    resetToolVolume,
    preferredHeat,
    setPreferredHeat,
    heatSource: resolveHeatSource(toolIds, preferredHeat),
    allergenCodes,
    toggleAllergen,
    clearAllergens,
    allergyConsent,
    setAllergyConsent,
    clearProfile,
  };
}

// Recent searches, favourite recipes and finished dishes, kept on the device across restarts.
function useLibrary() {
  const [recentQueries, setRecentQueries] = useState<string[]>([]);
  const [favorites, setFavorites] = useState<Favorite[]>([]);
  const [cookRecords, setCookRecords] = useState<CookRecord[]>([]);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    Promise.all([
      loadJson<unknown>(RECENT_QUERIES_KEY, []),
      loadJson<unknown>(FAVORITES_KEY, []),
      loadJson<unknown>(COOK_HISTORY_KEY, []),
    ]).then(([queries, stored, records]) => {
      setRecentQueries(sanitizeQueries(queries));
      setFavorites(sanitizeFavorites(stored));
      setCookRecords(sanitizeRecords(records));
      setLoaded(true);
    });
  }, []);

  // Saved only after the stored lists are read, so the empty start state never overwrites them.
  useEffect(() => {
    if (loaded) saveJson(RECENT_QUERIES_KEY, recentQueries);
  }, [loaded, recentQueries]);

  useEffect(() => {
    if (loaded) saveJson(FAVORITES_KEY, favorites);
  }, [loaded, favorites]);

  useEffect(() => {
    if (loaded) saveJson(COOK_HISTORY_KEY, cookRecords);
  }, [loaded, cookRecords]);

  const addRecentQuery = useCallback((query: string) => setRecentQueries((current) => withRecentQuery(current, query)), []);
  const removeRecentQuery = useCallback(
    (query: string) => setRecentQueries((current) => withoutRecentQuery(current, query)),
    [],
  );
  const clearRecentQueries = useCallback(() => setRecentQueries([]), []);
  const toggleFavorite = useCallback(
    (recipe: Favorite) => setFavorites((current) => withFavoriteToggled(current, recipe)),
    [],
  );
  // Records a finished dish and returns the record's id, so the rating can be added to it.
  const addCookRecord = useCallback((recipe: { id: string; name: string }) => {
    const record = newRecord(recipe, new Date());
    setCookRecords((current) => addRecord(current, record));
    return record.id;
  }, []);
  const rateCookRecord = useCallback(
    (id: string, change: Partial<Pick<CookRecord, 'rating' | 'stuckStepOrders'>>) =>
      setCookRecords((current) => updateRecord(current, id, change)),
    [],
  );
  const removeCookRecord = useCallback((id: string) => setCookRecords((current) => removeRecord(current, id)), []);
  const clearCookRecords = useCallback(() => setCookRecords([]), []);
  const clearLibrary = useCallback(() => {
    setRecentQueries([]);
    setFavorites([]);
    setCookRecords([]);
  }, []);

  return {
    recentQueries,
    addRecentQuery,
    removeRecentQuery,
    clearRecentQueries,
    favorites,
    toggleFavorite,
    cookRecords,
    addCookRecord,
    rateCookRecord,
    removeCookRecord,
    clearCookRecords,
    clearLibrary,
  };
}

function useRecipeSession(profile: ReturnType<typeof useProfile>) {
  const { skillLevel, toolIds, customToolVolumes, heatSource, allergenCodes } = profile;
  const [recipe, setRecipe] = useState<Recipe | null>(null);
  const [missingIds, setMissingIds] = useState<string[]>([]);
  const [substituteAnswers, setSubstituteAnswers] = useState<SubstituteAnswer[]>([]);
  const [adaptation, setAdaptation] = useState<Adaptation | null>(null);
  const [cookStepIndex, setCookStepIndex] = useState(0);

  // Opening a recipe starts a new session: nothing chosen for another recipe carries over.
  const openRecipe = useCallback(
    async (id: string) => {
      const loaded = await fetchRecipe(id, toolIds);
      setRecipe(loaded);
      setMissingIds([]);
      setSubstituteAnswers([]);
      setAdaptation(null);
    },
    [toolIds],
  );

  // "Can I make this with my tools?" is asked again when the user changes their tools while a
  // recipe is open; the rest of the session (missing ingredients, answers) is kept.
  const recipeId = recipe?.id;
  useEffect(() => {
    if (!recipeId) return;
    let cancelled = false;
    fetchRecipe(recipeId, toolIds)
      .then((loaded) => {
        if (cancelled) return;
        setRecipe((current) => (current?.id === recipeId ? { ...current, toolFit: loaded.toolFit } : current));
      })
      // The earlier answer stays on screen; the adaptation checks the tools again anyway.
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [recipeId, toolIds]);

  // Toggling an ingredient drops its earlier substitute answer so it is asked again.
  const toggleMissing = useCallback((id: string) => {
    setMissingIds((current) => toggleIn(current, id));
    setSubstituteAnswers((current) => current.filter((answer) => answer.ingredientId !== id));
    setAdaptation(null);
  }, []);

  const answerSubstitute = useCallback((answer: SubstituteAnswer) => {
    setSubstituteAnswers((current) => [
      ...current.filter(
        (item) => item.ingredientId !== answer.ingredientId || item.substituteId !== answer.substituteId,
      ),
      answer,
    ]);
  }, []);

  // The server decides amounts, steps, safety notes and what can be left out.
  const requestAdaptation = useCallback(async () => {
    if (!recipe) throw new Error('No recipe is open');
    const result = await adaptRecipe({
      recipe,
      missingIngredientIds: missingIds,
      substituteAnswers,
      skillLevel: skillLevel ?? 'LOW',
      toolIds,
      customToolVolumes: volumesToSend(customToolVolumes, toolIds),
      heatSource,
      allergenCodes,
    });
    setAdaptation(result);
    return result;
  }, [recipe, missingIds, substituteAnswers, skillLevel, toolIds, customToolVolumes, heatSource, allergenCodes]);

  const clearSession = useCallback(() => {
    setRecipe(null);
    setMissingIds([]);
    setSubstituteAnswers([]);
    setAdaptation(null);
    setCookStepIndex(0);
  }, []);

  return {
    recipe,
    openRecipe,
    missingIds,
    toggleMissing,
    answerSubstitute,
    adaptation,
    requestAdaptation,
    cookStepIndex,
    setCookStepIndex,
    clearSession,
  };
}

function useAppStateValue() {
  const profile = useProfile();
  const session = useRecipeSession(profile);
  const library = useLibrary();
  const timer = useTimer();
  const { clearProfile } = profile;
  const { clearLibrary } = library;
  const { clearSession, setCookStepIndex } = session;
  const { cancel } = timer;

  // A new adaptation starts cooking from the first step with no timer.
  const resetCooking = useCallback(() => {
    setCookStepIndex(0);
    cancel();
  }, [setCookStepIndex, cancel]);

  const clearAll = useCallback(() => {
    clearProfile();
    clearSession();
    clearLibrary();
    cancel();
  }, [clearProfile, clearSession, clearLibrary, cancel]);

  return { ...profile, ...session, ...library, timer, resetCooking, clearAll };
}

type AppState = ReturnType<typeof useAppStateValue>;

const AppStateContext = createContext<AppState | null>(null);

export function AppStateProvider({ children }: { children: ReactNode }) {
  const value = useAppStateValue();
  // Nothing is drawn until the stored profile is read, so the first screen is the right one.
  if (!value.hydrated) return null;
  return <AppStateContext.Provider value={value}>{children}</AppStateContext.Provider>;
}

export function useAppState(): AppState {
  const value = useContext(AppStateContext);
  if (!value) throw new Error('useAppState must be used inside AppStateProvider');
  return value;
}
