import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { Eyebrow } from '@/components/headers';
import { Icon } from '@/components/icon';
import { Page } from '@/components/page';
import { AppText } from '@/components/text';
import { TopBar } from '@/components/top-bar';
import { useAppState } from '@/state/app-state';
import { colors, fonts, gutter } from '@/theme/tokens';

const SUGGESTION_TONES = [
  { color: colors.primary, background: colors.primarySoft },
  { color: colors.info, background: colors.infoSoft },
  { color: colors.success, background: colors.successSoft },
];

// Search shortcuts: dish names only. Nothing about them is known until a recipe is found.
const SUGGESTED_DISHES = ['간장계란밥', '참치 김치찌개', '두부조림'];

export default function HomeScreen() {
  const { recentQueries, addRecentQuery } = useAppState();
  const [query, setQuery] = useState('');

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
          <AppText weight="bold" style={styles.sectionTitle}>
            최근에 찾은 요리
          </AppText>
          <ScrollView
            contentContainerStyle={styles.recentChips}
            horizontal
            showsHorizontalScrollIndicator={false}
            style={styles.recentScroll}
          >
            {recentQueries.map((item) => (
              <Pressable accessibilityRole="button" key={item} onPress={() => search(item)} style={styles.recentChip}>
                <AppText style={styles.recentLabel}>{item}</AppText>
              </Pressable>
            ))}
          </ScrollView>
        </View>
      ) : null}
      <View style={styles.section}>
        <View style={styles.sectionHeading}>
          <AppText weight="bold" style={styles.sectionTitle}>
            이런 요리는 어때요
          </AppText>
          <AppText style={styles.sectionNote}>눌러서 검색</AppText>
        </View>
        <View style={styles.suggestions}>
          {SUGGESTED_DISHES.map((title, index) => {
            const tone = SUGGESTION_TONES[index % SUGGESTION_TONES.length];
            return (
              <Pressable
                accessibilityRole="button"
                key={title}
                onPress={() => search(title)}
                style={styles.suggestion}
              >
                <View style={[styles.number, { backgroundColor: tone.background }]}>
                  <AppText weight="extraBold" style={{ color: tone.color }}>
                    {String(index + 1).padStart(2, '0')}
                  </AppText>
                </View>
                <View style={styles.suggestionText}>
                  <AppText weight="bold" style={styles.suggestionTitle}>
                    {title}
                  </AppText>
                </View>
                <Icon name="search" size={18} color={colors.hint} />
              </Pressable>
            );
          })}
        </View>
      </View>
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
});
