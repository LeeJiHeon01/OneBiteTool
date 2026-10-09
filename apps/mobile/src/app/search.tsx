import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Animated, Easing, Pressable, StyleSheet, TextInput, View } from 'react-native';

import { errorMessage } from '@/api/client';
import { importRecipe, searchRecipes } from '@/api/recipes';
import { Badge } from '@/components/badge';
import { Button } from '@/components/button';
import { CenterState } from '@/components/headers';
import { Icon } from '@/components/icon';
import { Page } from '@/components/page';
import { Picture } from '@/components/picture';
import { AppText } from '@/components/text';
import { TopBar } from '@/components/top-bar';
import { hiddenSummary, splitByVisibility } from '@/data/allergy-visibility';
import { ILLUSTRATIONS } from '@/data/images';
import { type RecipeBadge, type SearchResult } from '@/data/recipe';
import { useAppState } from '@/state/app-state';
import { colors, fonts } from '@/theme/tokens';

// searching: stored recipes; importing: the server is bringing the dish in from the web.
type Phase = 'searching' | 'importing' | 'results' | 'noResults' | 'error';

const BADGES: Record<RecipeBadge, string> = { VERIFIED: '확인한 레시피', IMPORTED: '확인 전 레시피', WEB_SEARCH: '웹 레시피' };

function LoadingDots() {
  const [progress] = useState(() => [0, 1, 2].map(() => new Animated.Value(0)));

  useEffect(() => {
    const loops = progress.map((value, index) =>
      Animated.loop(
        Animated.sequence([
          Animated.delay(index * 140),
          Animated.timing(value, { toValue: 1, duration: 300, easing: Easing.out(Easing.quad), useNativeDriver: true }),
          Animated.timing(value, { toValue: 0, duration: 300, easing: Easing.in(Easing.quad), useNativeDriver: true }),
          Animated.delay(400 - index * 140),
        ]),
      ),
    );
    loops.forEach((loop) => loop.start());
    return () => loops.forEach((loop) => loop.stop());
  }, [progress]);

  return (
    <View accessibilityLabel="검색 중" accessible style={styles.loader}>
      {progress.map((value, index) => (
        <Animated.View
          key={index}
          style={[
            styles.dot,
            { opacity: 1 - index * 0.3 },
            { transform: [{ translateY: value.interpolate({ inputRange: [0, 1], outputRange: [0, -14] }) }] },
          ]}
        />
      ))}
    </View>
  );
}

type CardProps = {
  result: SearchResult;
  index: number;
  flagged: boolean;
  // The user has allergies and an ingredient is outside the verified mapping.
  checkNeeded: boolean;
  onPress: () => void;
};

function ResultCard({ result, index, flagged, checkNeeded, onPress }: CardProps) {
  const meta = [`재료 ${result.ingredientCount}개`, `${result.stepCount}단계`];
  if (result.minutes) meta.push(`약 ${result.minutes}분`);
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={styles.card}>
      <View style={styles.cardTop}>
        <View style={styles.cardIndex}>
          <AppText weight="extraBold" style={styles.cardIndexLabel}>
            {index + 1}
          </AppText>
        </View>
        <View style={styles.badges}>
          {flagged ? <Badge label="알레르기" tone="danger" /> : null}
          {checkNeeded ? <Badge label="알레르기 확인 필요" tone="caution" /> : null}
          <Badge label={BADGES[result.badge]} tone="info" />
        </View>
      </View>
      <AppText weight="bold" style={styles.cardTitle}>
        {result.title}
      </AppText>
      <AppText style={styles.cardMeta}>{meta.join(' · ')}</AppText>
      <View style={styles.source}>
        <AppText style={styles.sourceLabel}>{result.sourceDomain ?? ''}</AppText>
        <Icon name="chevron" size={16} color={colors.hint} />
      </View>
    </Pressable>
  );
}

export default function SearchScreen() {
  const params = useLocalSearchParams<{ q?: string }>();
  const { allergenCodes, toolIds, addRecentQuery, openRecipe } = useAppState();
  const [query, setQuery] = useState(params.q ?? '');
  const [input, setInput] = useState(params.q ?? '');
  const [phase, setPhase] = useState<Phase>('searching');
  const [attempt, setAttempt] = useState(0);
  const [results, setResults] = useState<SearchResult[]>([]);
  const [message, setMessage] = useState('');
  const [showHidden, setShowHidden] = useState(false);

  // Stored recipes first; only a dish that is not stored is looked up on the web.
  useEffect(() => {
    let cancelled = false;
    const run = async () => {
      try {
        const found = await searchRecipes(query);
        if (cancelled) return;
        if (found.length > 0) {
          setResults(found);
          setPhase('results');
          return;
        }
        setPhase('importing');
        const imported = await importRecipe(query, toolIds, () => cancelled);
        if (cancelled) return;
        setResults(imported ? [imported] : []);
        setPhase(imported ? 'results' : 'noResults');
      } catch (error) {
        if (cancelled) return;
        setMessage(errorMessage(error));
        setPhase('error');
      }
    };
    run();
    return () => {
      cancelled = true;
    };
  }, [query, attempt, toolIds]);

  const retry = () => {
    setPhase('searching');
    setAttempt((current) => current + 1);
  };

  const searchAgain = () => {
    const next = input.trim();
    if (!next) return;
    addRecentQuery(next);
    setQuery(next);
    setShowHidden(false);
    retry();
  };

  const open = async (result: SearchResult) => {
    try {
      await openRecipe(result.id);
      router.push('/recipe');
    } catch (error) {
      setMessage(errorMessage(error));
      setPhase('error');
    }
  };

  if (phase === 'searching' || phase === 'importing') {
    const importing = phase === 'importing';
    return (
      <Page>
        <TopBar onBack={() => router.back()} />
        <View style={styles.searchBox}>
          <Icon name="search" size={21} color={colors.sub} />
          <AppText style={styles.queryText}>{query}</AppText>
        </View>
        <CenterState
          description={importing ? '실제 레시피 페이지를 살펴보고 있어요.\n1분 정도 걸릴 수 있어요.' : '잠시만 기다려 주세요.'}
          style={styles.searchingState}
          title={importing ? '웹에서 레시피를\n찾고 있어요' : '레시피를\n찾고 있어요'}
          visual={
            <>
              <Picture size={150} source={ILLUSTRATIONS.searching} />
              <LoadingDots />
            </>
          }
        />
      </Page>
    );
  }

  if (phase === 'error') {
    return (
      <Page footer={<Button label="다시 시도" onPress={retry} />}>
        <TopBar onBack={() => router.back()} />
        <CenterState
          description={message}
          style={styles.emptyState}
          title={'지금은 찾을 수\n없어요'}
          visual={<Picture size={150} source={ILLUSTRATIONS.notFound} style={styles.emptyVisual} />}
        />
      </Page>
    );
  }

  if (phase === 'noResults') {
    return (
      <Page footer={<Button label="다시 검색" onPress={() => router.back()} />}>
        <TopBar onBack={() => router.back()} />
        <CenterState
          description={'다른 이름이나 더 간단한 이름으로\n검색해 보세요.'}
          style={styles.emptyState}
          title={'레시피를\n찾지 못했어요'}
          visual={<Picture size={150} source={ILLUSTRATIONS.notFound} style={styles.emptyVisual} />}
        />
      </Page>
    );
  }

  // For a user with allergies, recipes with a matching allergen and recipes with an unchecked
  // ingredient stay out of the list unless the user asks. Without allergies nothing is hidden.
  const visibility = splitByVisibility(results, allergenCodes);
  const reasonOf = (result: SearchResult) => visibility.hidden.find((item) => item.result === result)?.reason;
  const visible = showHidden ? results : visibility.shown;
  const hiddenCount = visibility.hidden.length;

  return (
    <Page>
      <TopBar onBack={() => router.back()} />
      <View style={styles.searchBox}>
        <Icon name="search" size={21} color={colors.sub} />
        <TextInput
          accessibilityLabel="검색어"
          onChangeText={setInput}
          onSubmitEditing={searchAgain}
          returnKeyType="search"
          style={styles.input}
          value={input}
        />
      </View>
      <View style={styles.count}>
        <AppText weight="bold" style={styles.countTitle}>
          {visible.length}개의 레시피
        </AppText>
      </View>
      {visible.length === 0 ? (
        <View style={styles.allHidden}>
          <AppText weight="bold" style={styles.allHiddenTitle}>
            조건에 맞는 레시피가 숨겨졌어요
          </AppText>
          <AppText style={styles.allHiddenText}>{hiddenSummary(visibility)}</AppText>
          <Button label="보기" onPress={() => setShowHidden(true)} variant="secondary" />
        </View>
      ) : null}
      <View style={styles.list}>
        {visible.map((result, index) => (
          <ResultCard
            checkNeeded={reasonOf(result) === 'unchecked'}
            flagged={reasonOf(result) === 'match'}
            index={index}
            key={result.id}
            onPress={() => open(result)}
            result={result}
          />
        ))}
      </View>
      {hiddenCount > 0 && visible.length > 0 ? (
        <View style={styles.hiddenNote}>
          <Icon name="shield" size={18} color={colors.sub} />
          <AppText style={styles.hiddenText}>
            {hiddenSummary(visibility)}
            {showHidden ? ' (함께 보는 중)' : ''}
          </AppText>
          <Pressable accessibilityRole="button" hitSlop={12} onPress={() => setShowHidden(!showHidden)}>
            <AppText weight="bold" style={styles.hiddenAction}>
              {showHidden ? '숨기기' : '보기'}
            </AppText>
          </Pressable>
        </View>
      ) : null}
    </Page>
  );
}

const styles = StyleSheet.create({
  searchBox: {
    height: 54,
    marginTop: 10,
    paddingHorizontal: 17,
    borderRadius: 18,
    backgroundColor: colors.surface,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  queryText: { fontSize: 16 },
  input: { flex: 1, minWidth: 0, height: '100%', fontFamily: fonts.regular, fontSize: 16, color: colors.text },
  searchingState: { paddingTop: 80 },
  loader: { height: 56, marginBottom: 20, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  dot: { width: 13, height: 13, borderRadius: 7, backgroundColor: colors.primary },
  emptyState: { paddingTop: 100 },
  emptyVisual: { marginBottom: 22 },
  count: {
    marginTop: 32,
    marginBottom: 14,
    marginHorizontal: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  countTitle: { fontSize: 18 },
  list: { gap: 12 },
  card: { padding: 18, borderWidth: 1, borderColor: colors.line, borderRadius: 20, backgroundColor: colors.white },
  cardTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 15 },
  cardIndex: {
    width: 30,
    height: 30,
    borderRadius: 10,
    backgroundColor: colors.primarySoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardIndexLabel: { color: colors.primary },
  badges: { flexShrink: 1, flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'flex-end', gap: 6 },
  cardTitle: { fontSize: 18, marginBottom: 7 },
  cardMeta: { fontSize: 13, color: colors.sub, marginBottom: 17 },
  source: {
    paddingTop: 13,
    borderTopWidth: 1,
    borderTopColor: colors.line,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  sourceLabel: { fontSize: 12, color: colors.hint },
  hiddenNote: {
    marginTop: 18,
    padding: 14,
    borderRadius: 14,
    backgroundColor: colors.surface,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  hiddenText: { flex: 1, fontSize: 12, lineHeight: 18, color: colors.sub },
  allHidden: { marginBottom: 12, padding: 20, borderRadius: 20, backgroundColor: colors.surface, gap: 10 },
  allHiddenTitle: { fontSize: 17 },
  allHiddenText: { fontSize: 13, lineHeight: 19, color: colors.sub },
  hiddenAction: { fontSize: 12 },
});
