import { router, useFocusEffect, type Href } from 'expo-router';
import { useCallback, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { Button } from '@/components/button';
import { CenterState } from '@/components/headers';
import { Page } from '@/components/page';
import { Picture } from '@/components/picture';
import { AppText } from '@/components/text';
import { TopBar } from '@/components/top-bar';
import { ILLUSTRATIONS } from '@/data/images';
import { useAppState } from '@/state/app-state';
import { colors } from '@/theme/tokens';

export default function CannotCookScreen() {
  const { adaptation, toolIds } = useAppState();
  const missingTool = (adaptation?.requiredItems ?? []).some((item) => item.kind === 'TOOL');
  // The tools the answer was made with. Back from the tool screen with a different set,
  // the recipe is checked again; while the user is still choosing, nothing happens.
  const [checkedTools] = useState(toolIds);

  useFocusEffect(
    useCallback(() => {
      if (toolIds !== checkedTools) router.replace('/recipe/adapting');
    }, [toolIds, checkedTools]),
  );

  const editTools = () => router.push({ pathname: '/onboarding/tools', params: { edit: '1' } } as Href);

  return (
    <Page
      footer={
        <>
          {missingTool ? <Button label="가진 도구 다시 고르기" onPress={editTools} /> : null}
          <Button
            label="다른 요리 보기"
            onPress={() => router.dismissTo('/search')}
            variant={missingTool ? 'secondary' : 'primary'}
          />
          <Button label="없는 재료 다시 고르기" onPress={() => router.back()} variant="text" />
        </>
      }
    >
      <TopBar onBack={() => router.back()} />
      <CenterState
        description="꼭 필요한 재료나 도구가 부족해요."
        style={styles.state}
        title={'이대로는\n만들기 어려워요'}
        visual={<Picture size={150} source={ILLUSTRATIONS.notCookable} style={styles.visual} />}
      />
      <View style={styles.list}>
        {(adaptation?.requiredItems ?? []).map((item) => (
          <View key={`${item.kind}-${item.id}`} style={styles.row}>
            <AppText weight="bold" style={styles.name}>
              {item.name}
            </AppText>
            <AppText style={styles.reason}>{item.reason}</AppText>
          </View>
        ))}
      </View>
    </Page>
  );
}

const styles = StyleSheet.create({
  state: { paddingTop: 40 },
  visual: { marginBottom: 18 },
  list: { marginTop: 52, borderTopWidth: 1, borderTopColor: colors.line },
  row: { minHeight: 72, justifyContent: 'center', gap: 5, borderBottomWidth: 1, borderBottomColor: colors.line },
  name: { fontSize: 16 },
  reason: { fontSize: 13, color: colors.sub },
});
