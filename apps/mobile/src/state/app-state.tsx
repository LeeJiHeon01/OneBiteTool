import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';

import { adaptRecipe, fetchRecipe, type SubstituteAnswer } from '@/api/recipes';
import { resolveHeatSource, type HeatSource, type SkillLevel } from '@/data/catalog';
import {
  fromTheStart,
  isFresh,
  sanitizeSaved,
  timerAt,
  toggleChecked,
  type SavedCooking,
} from '@/data/cooking-session';
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
import { loadJson, removeJson, saveJson } from '@/state/device-store';
import { loadProfile, saveProfile } from '@/state/profile-storage';
import { setTimerNotificationsEnabled } from '@/state/timer-notifications';
import { useTimer } from '@/state/use-timer';

// App state. The profile (skill, tools, allergies) is kept on the device and restored at
// start (see profile-storage); the recipe session comes from the server and is in memory only.

const RECENT_QUERIES_KEY = 'recent-queries.v1';
const FAVORITES_KEY = 'favorites.v1';
const COOK_HISTORY_KEY = 'cook-history.v1';
const SETTINGS_KEY = 'settings.v1';
const COOKING_KEY = 'cooking-session.v1';

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

// App settings that are not about the user: kept on the device, on by default.
function useSettings() {
  const [timerNotificationEnabled, setTimerNotificationEnabled] = useState(true);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    loadJson<{ timerNotification?: unknown }>(SETTINGS_KEY, {}).then((stored) => {
      setTimerNotificationEnabled(stored.timerNotification !== false);
      setLoaded(true);
    });
  }, []);

  useEffect(() => {
    if (!loaded) return;
    saveJson(SETTINGS_KEY, { timerNotification: timerNotificationEnabled });
    // Turning it off also cancels a notification that is already scheduled.
    setTimerNotificationsEnabled(timerNotificationEnabled);
  }, [loaded, timerNotificationEnabled]);

  const clearSettings = useCallback(() => setTimerNotificationEnabled(true), []);

  return { timerNotificationEnabled, setTimerNotificationEnabled, clearSettings };
}

function useRecipeSession(profile: ReturnType<typeof useProfile>) {
  const { skillLevel, toolIds, customToolVolumes, heatSource, allergenCodes } = profile;
  const [recipe, setRecipe] = useState<Recipe | null>(null);
  const [missingIds, setMissingIds] = useState<string[]>([]);
  const [substituteAnswers, setSubstituteAnswers] = useState<SubstituteAnswer[]>([]);
  const [adaptation, setAdaptation] = useState<Adaptation | null>(null);
  const [cookStepIndex, setCookStepIndex] = useState(0);
  // Ticked checklist items by step; a reminder for the user only, never a condition to move on.
  const [checkedItems, setCheckedItems] = useState<Record<string, string[]>>({});
  // True from the moment the cooking screen opens until the dish is finished.
  const [cooking, setCooking] = useState(false);

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

  const toggleCheckedItem = useCallback(
    (stepId: string, itemId: string) => setCheckedItems((current) => toggleChecked(current, stepId, itemId)),
    [],
  );
  const clearCheckedItems = useCallback(() => setCheckedItems({}), []);

  // Puts back a dish that was being cooked when the app was closed.
  const restoreSession = useCallback((saved: SavedCooking) => {
    setRecipe(saved.recipe);
    setMissingIds([]);
    setSubstituteAnswers([]);
    setAdaptation(saved.adaptation);
    setCookStepIndex(saved.stepIndex);
    setCheckedItems(saved.checkedItems);
  }, []);

  const clearSession = useCallback(() => {
    setRecipe(null);
    setMissingIds([]);
    setSubstituteAnswers([]);
    setAdaptation(null);
    setCookStepIndex(0);
    setCheckedItems({});
    setCooking(false);
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
    checkedItems,
    toggleCheckedItem,
    clearCheckedItems,
    cooking,
    setCooking,
    restoreSession,
    clearSession,
  };
}

function useAppStateValue() {
  const profile = useProfile();
  const session = useRecipeSession(profile);
  const library = useLibrary();
  const settings = useSettings();
  const timer = useTimer();
  const { clearProfile } = profile;
  const { clearLibrary } = library;
  const { clearSettings } = settings;
  const { clearSession, setCookStepIndex, clearCheckedItems, restoreSession, setCooking } = session;
  const { recipe, adaptation, cookStepIndex, checkedItems, cooking } = session;
  const { cancel, restore: restoreTimer } = timer;
  const activeTimer = timer.timer;

  // A dish that was being cooked when the app was closed, waiting for the user's answer.
  const [savedCooking, setSavedCooking] = useState<SavedCooking | null>(null);
  const [cookingLoaded, setCookingLoaded] = useState(false);

  useEffect(() => {
    loadJson<unknown>(COOKING_KEY, null).then((stored) => {
      const saved = sanitizeSaved(stored);
      // Older than a day: dropped without asking.
      if (saved && isFresh(saved, Date.now())) setSavedCooking(saved);
      else if (stored !== null) removeJson(COOKING_KEY);
      setCookingLoaded(true);
    });
  }, []);

  // While a dish is being cooked, every change is written down: the step, the timer's end time
  // and the ticked items. Which allergies the user has is not part of it.
  useEffect(() => {
    if (!cookingLoaded || !cooking || !recipe || !adaptation) return;
    saveJson(COOKING_KEY, {
      savedAt: Date.now(),
      recipe,
      adaptation,
      stepIndex: cookStepIndex,
      timer: activeTimer,
      checkedItems,
    } satisfies SavedCooking);
  }, [cookingLoaded, cooking, recipe, adaptation, cookStepIndex, activeTimer, checkedItems]);

  // A new adaptation starts cooking from the first step with no timer.
  const resetCooking = useCallback(() => {
    setCookStepIndex(0);
    clearCheckedItems();
    cancel();
  }, [setCookStepIndex, clearCheckedItems, cancel]);

  // Called when the cooking screen opens: from here on the dish is saved as it goes.
  const startCooking = useCallback(() => {
    setCooking(true);
    setSavedCooking(null);
  }, [setCooking]);

  // Called when the dish is finished: there is nothing left to pick up.
  const finishCooking = useCallback(() => {
    setCooking(false);
    removeJson(COOKING_KEY);
  }, [setCooking]);

  // The user's answer to "pick up where you left off?". The timer's remaining time comes from
  // its end time; one that ran out while the app was closed is shown as finished.
  const resumeCooking = useCallback(
    (fromStart: boolean) => {
      if (!savedCooking) return;
      const saved = fromStart ? fromTheStart(savedCooking) : savedCooking;
      restoreSession(saved);
      restoreTimer(timerAt(saved.timer, Date.now()));
      setCooking(true);
      setSavedCooking(null);
    },
    [savedCooking, restoreSession, restoreTimer, setCooking],
  );

  const discardSavedCooking = useCallback(() => {
    setSavedCooking(null);
    removeJson(COOKING_KEY);
  }, []);

  const clearAll = useCallback(() => {
    clearProfile();
    clearSession();
    clearLibrary();
    clearSettings();
    cancel();
    setSavedCooking(null);
    removeJson(COOKING_KEY);
  }, [clearProfile, clearSession, clearLibrary, clearSettings, cancel]);

  return {
    ...profile,
    ...session,
    ...library,
    ...settings,
    timer,
    resetCooking,
    savedCooking,
    startCooking,
    finishCooking,
    resumeCooking,
    discardSavedCooking,
    clearAll,
  };
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
