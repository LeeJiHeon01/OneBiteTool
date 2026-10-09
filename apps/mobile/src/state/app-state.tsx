import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';

import { adaptRecipe, fetchRecipe, type SubstituteAnswer } from '@/api/recipes';
import { resolveHeatSource, type HeatSource, type SkillLevel } from '@/data/catalog';
import { type Adaptation, type Recipe } from '@/data/recipe';
import { loadProfile, saveProfile } from '@/state/profile-storage';
import { useTimer } from '@/state/use-timer';

// App state. The profile (skill, tools, allergies) is kept on the device and restored at
// start (see profile-storage); the recipe session comes from the server and is in memory only.

const MAX_RECENT_QUERIES = 8;

function toggleIn(list: string[], value: string): string[] {
  return list.includes(value) ? list.filter((item) => item !== value) : [...list, value];
}

function useProfile() {
  const [skillLevel, setSkillLevel] = useState<SkillLevel | null>(null);
  const [toolIds, setToolIds] = useState<string[]>([]);
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
    saveProfile({ skillLevel, toolIds, preferredHeat, allergenCodes, allergyConsent, onboarded });
  }, [hydrated, skillLevel, toolIds, preferredHeat, allergenCodes, allergyConsent, onboarded]);

  const completeOnboarding = useCallback(() => setOnboarded(true), []);
  const toggleTool = useCallback((id: string) => setToolIds((current) => toggleIn(current, id)), []);
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

function useRecipeSession(profile: ReturnType<typeof useProfile>) {
  const { skillLevel, toolIds, heatSource, allergenCodes } = profile;
  const [recentQueries, setRecentQueries] = useState<string[]>([]);
  const [recipe, setRecipe] = useState<Recipe | null>(null);
  const [missingIds, setMissingIds] = useState<string[]>([]);
  const [substituteAnswers, setSubstituteAnswers] = useState<SubstituteAnswer[]>([]);
  const [adaptation, setAdaptation] = useState<Adaptation | null>(null);
  const [cookStepIndex, setCookStepIndex] = useState(0);

  const addRecentQuery = useCallback((query: string) => {
    setRecentQueries((current) =>
      [query, ...current.filter((item) => item !== query)].slice(0, MAX_RECENT_QUERIES),
    );
  }, []);

  // Opening a recipe starts a new session: nothing chosen for another recipe carries over.
  const openRecipe = useCallback(async (id: string) => {
    const loaded = await fetchRecipe(id);
    setRecipe(loaded);
    setMissingIds([]);
    setSubstituteAnswers([]);
    setAdaptation(null);
  }, []);

  // Toggling an ingredient drops its earlier substitute answer so it is asked again.
  const toggleMissing = useCallback((id: string) => {
    setMissingIds((current) => toggleIn(current, id));
    setSubstituteAnswers((current) => current.filter((answer) => answer.ingredientId !== id));
    setAdaptation(null);
  }, []);

  const answerSubstitute = useCallback((answer: SubstituteAnswer) => {
    setSubstituteAnswers((current) => [
      ...current.filter((item) => item.ingredientId !== answer.ingredientId),
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
      heatSource,
      allergenCodes,
    });
    setAdaptation(result);
    return result;
  }, [recipe, missingIds, substituteAnswers, skillLevel, toolIds, heatSource, allergenCodes]);

  const clearSession = useCallback(() => {
    setRecentQueries([]);
    setRecipe(null);
    setMissingIds([]);
    setSubstituteAnswers([]);
    setAdaptation(null);
    setCookStepIndex(0);
  }, []);

  return {
    recentQueries,
    addRecentQuery,
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
  const timer = useTimer();
  const { clearProfile } = profile;
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
    cancel();
  }, [clearProfile, clearSession, cancel]);

  return { ...profile, ...session, timer, resetCooking, clearAll };
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
