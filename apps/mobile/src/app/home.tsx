import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { fetchSuggestions } from '@/api/recipes';
import { Eyebrow } from '@/components/headers';
import { Icon } from '@/components/icon';
import { Page } from '@/components/page';
import { AppText } from '@/components/text';
import { TopBar } from '@/components/top-bar';
import { splitByVisibility } from '@/data/allergy-visibility';
import { dateLabel, ratingLabel, recentDishes } from '@/data/history';
import { HOME_FAVORITE_COUNT, type Favorite } from '@/data/library';
import { type SearchResult } from '@/data/recipe';
import { useAppState } from '@/state/app-state';
import { colors, fonts, gutter } from '@/theme/tokens';

const SUGGESTION_TONES = [
  { color: colors.primary, background: colors.primarySoft },
  { color: colors.info, background: colors.infoSoft },
  { color: colors.success, background: colors.successSoft },
];

export default function HomeScreen() {
  const {
    recentQueries,
    addRecentQuery,
    removeRecentQuery,
    clearRecentQueries,
    favorites,
    cookRecords,
    allergenCodes,
    openRecipe,
  } = useAppState();
  const [query, setQuery] = useState('');
  // Recipes the server already has: they open at once. A new dish goes through search instead.
  const [stored, setStored] = useState<SearchResult[]>([]);

  useEffect(() => {
    let cancelled = false;
    // Suggestions are optional: without the server the section is simply left out.
    fetchSuggestions()
      .then((results) => {
        if (!cancelled) setStored(results);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  // The same rule as the search list: nothing a user with allergies must avoid is suggested.
  const suggestions = splitByVisibility(stored, allergenCodes).shown;

  const open = async (recipe: { id: string; title: string }) => {
    try {
      await openRecipe(recipe.id);
      router.push('/recipe');
    } catch {
      // The search screen shows the error and a retry.
      router.push({ pathname: '/search', params: { q: recipe.title } });
    }
  };
  const openFavorite = (favorite: Favorite) => open({ id: favorite.id, title: favorite.name });

  const search = (value: string) => {
    const next = value.trim();
    if (!next) return;
    addRecentQuery(next);
    router.push({ pathname: '/search', params: { q: next } });
  };

  return (
    <Page>
      <TopBar
        action={
          <Pressable
            accessibilityLabel="설정"
            accessibilityRole="button"
            onPress={() => router.push('/settings')}
            style={styles.settings}
          >
            <Icon name="settings" />
          </Pressable>
        }
      />
      <View style={styles.header}>
        <Eyebrow>오늘도 쉽게, 맛있게</Eyebrow>
        <AppText accessibilityRole="header" style={styles.title}>
          {'오늘 뭐\n만들어볼까요?'}
        </AppText>
      </View>
      <View style={styles.searchBox}>
        <Icon name="search" size={22} color={colors.sub} />
        <TextInput
          accessibilityLabel="요리 검색"
          onChangeText={setQuery}
          onSubmitEditing={() => search(query)}
          placeholder="김치볶음밥, 계란말이..."
          placeholderTextColor={colors.hint}
          returnKeyType="search"
          style={styles.input}
          value={query}
        />
        {query ? (
          <Pressable accessibilityRole="button" hitSlop={12} onPress={() => search(query)}>
            <AppText weight="bold" style={styles.searchAction}>
              검색
            </AppText>
          </Pressable>
        ) : null}
      </View>
      {recentQueries.length > 0 ? (
        <View style={styles.section}>
          <View style={styles.sectionHeading}>
            <AppText weight="bold" style={styles.sectionTitle}>
              최근에 찾은 요리
            </AppText>
            <Pressable accessibilityRole="button" hitSlop={12} onPress={clearRecentQueries}>
              <AppText style={styles.sectionNote}>전체 삭제</AppText>
            </Pressable>
          </View>
          <ScrollView
            contentContainerStyle={styles.recentChips}
            horizontal
            showsHorizontalScrollIndicator={false}
            style={styles.recentScroll}
          >
            {recentQueries.map((item) => (
              <View key={item} style={styles.recentChip}>
                <Pressable accessibilityRole="button" onPress={() => search(item)}>
                  <AppText style={styles.recentLabel}>{item}</AppText>
                </Pressable>
                <Pressable
                  accessibilityLabel={`${item} 검색 기록 삭제`}
                  accessibilityRole="button"
                  hitSlop={10}
                  onPress={() => removeRecentQuery(item)}
                >
                  <AppText style={styles.recentRemove}>×</AppText>
                </Pressable>
              </View>
            ))}
          </ScrollView>
        </View>
      ) : null}
      {favorites.length > 0 ? (
        <View style={styles.section}>
          <AppText weight="bold" style={styles.sectionTitle}>
            즐겨찾기
          </AppText>
          <View style={styles.suggestions}>
            {favorites.slice(0, HOME_FAVORITE_COUNT).map((favorite) => (
              <Pressable
                accessibilityRole="button"
                key={favorite.id}
                onPress={() => openFavorite(favorite)}
                style={styles.suggestion}
              >
                <AppText style={styles.favoriteMark}>★</AppText>
                <View style={styles.suggestionText}>
                  <AppText weight="bold" style={styles.favoriteTitle}>
                    {favorite.name}
                  </AppText>
                </View>
                <Icon name="chevron" size={18} color={colors.hint} />
              </Pressable>
            ))}
          </View>
        </View>
      ) : null}
      {cookRecords.length > 0 ? (
        <View style={styles.section}>
          <AppText weight="bold" style={styles.sectionTitle}>
            최근 만든 요리
          </AppText>
          <View style={styles.suggestions}>
            {recentDishes(cookRecords).map((record) => (
              <Pressable
                accessibilityRole="button"
                key={record.id}
                onPress={() => open({ id: record.recipeId, title: record.recipeName })}
                style={styles.suggestion}
              >
                <View style={styles.suggestionText}>
                  <AppText weight="bold" style={styles.suggestionTitle}>
                    {record.recipeName}
                  </AppText>
                  <AppText style={styles.suggestionMeta}>
                    {[dateLabel(record.completedAt), ratingLabel(record.rating)].filter(Boolean).join(' · ')}
                  </AppText>
                </View>
                <Icon name="chevron" size={18} color={colors.hint} />
              </Pressable>
            ))}
          </View>
        </View>
      ) : null}
      {suggestions.length > 0 ? (
      <View style={styles.section}>
        <View style={styles.sectionHeading}>
          <AppText weight="bold" style={styles.sectionTitle}>
            이런 요리는 어때요
          </AppText>
          <AppText style={styles.sectionNote}>바로 볼 수 있어요</AppText>
        </View>
        <View style={styles.suggestions}>
          {suggestions.map((result, index) => {
            const tone = SUGGESTION_TONES[index % SUGGESTION_TONES.length];
            return (
              <Pressable
                accessibilityRole="button"
                key={result.id}
                onPress={() => open(result)}
                style={styles.suggestion}
              >
                <View style={[styles.number, { backgroundColor: tone.background }]}>
                  <AppText weight="extraBold" style={{ color: tone.color }}>
                    {String(index + 1).padStart(2, '0')}
                  </AppText>
                </View>
                <View style={styles.suggestionText}>
                  <AppText weight="bold" style={styles.suggestionTitle}>
                    {result.title}
                  </AppText>
                  <AppText style={styles.suggestionMeta}>
                    재료 {result.ingredientCount}개 · {result.stepCount}단계
                  </AppText>
                </View>
                <Icon name="chevron" size={18} color={colors.hint} />
              </Pressable>
            );
          })}
        </View>
      </View>
      ) : null}
    </Page>
  );
}

const styles = StyleSheet.create({
  settings: {
    width: 44,
    height: 44,
    borderRadius: 14,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  header: { paddingTop: 23, paddingBottom: 30 },
  title: { marginTop: 10, fontFamily: fonts.extraBold, fontSize: 38, lineHeight: 45, letterSpacing: -0.8 },
  searchBox: {
    height: 60,
    paddingHorizontal: 17,
    borderRadius: 18,
    backgroundColor: colors.surface,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  input: { flex: 1, minWidth: 0, height: '100%', fontFamily: fonts.regular, fontSize: 16, color: colors.text },
  searchAction: { color: colors.primary },
  section: { marginTop: 34 },
  sectionHeading: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  sectionTitle: { marginBottom: 15, fontSize: 19, letterSpacing: -0.3 },
  sectionNote: { marginBottom: 15, fontSize: 13, color: colors.sub },
  // Bleed to the right screen edge so chips scroll under the gutter.
  recentScroll: { marginRight: -gutter },
  recentChips: { gap: 8, paddingRight: gutter },
  recentChip: {
    height: 40,
    paddingHorizontal: 16,
    borderRadius: 99,
    backgroundColor: colors.primarySoft,
    justifyContent: 'center',
  },
  recentLabel: { color: colors.primaryDark },
  recentRemove: { fontSize: 16, lineHeight: 18, color: colors.primaryDark },
  favoriteMark: { width: 44, textAlign: 'center', fontSize: 20, color: colors.primary },
  favoriteTitle: { fontSize: 16 },
  suggestions: { borderTopWidth: 1, borderTopColor: colors.line },
  suggestion: {
    minHeight: 82,
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  number: { width: 44, height: 44, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  suggestionText: { flex: 1 },
  suggestionTitle: { marginBottom: 5, fontSize: 16 },
  suggestionMeta: { fontSize: 13, color: colors.sub },
});
