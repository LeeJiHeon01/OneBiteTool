import { Redirect, router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';

import { Button } from '@/components/button';
import { PageHeader } from '@/components/headers';
import { Page } from '@/components/page';
import { AppText } from '@/components/text';
import { TopBar } from '@/components/top-bar';
import { getTool } from '@/data/catalog';
import {
  BOTTLE_ML,
  fillCountLabel,
  FILL_COUNTS,
  findVolume,
  isCalibratable,
  MAX_VOLUME_ML,
  MIN_VOLUME_ML,
  parseDirectVolume,
  volumeFromFills,
  type VolumeMethod,
} from '@/data/tool-volume';
import { useAppState } from '@/state/app-state';
import { colors, fonts } from '@/theme/tokens';

// Finds the volume of the user's own cup or bowl with a 500 mL water bottle. Recipe amounts
// are still converted by the server; this screen only stores the tool's volume on the device.
export default function ToolVolumeScreen() {
  const { tool: toolId } = useLocalSearchParams<{ tool?: string }>();
  const { toolIds, customToolVolumes, saveToolVolume, resetToolVolume } = useAppState();
  const [fillCount, setFillCount] = useState<number | null>(null);
  const [directText, setDirectText] = useState('');

  const tool = toolId ? getTool(toolId) : undefined;
  if (!tool || !isCalibratable(tool.id)) return <Redirect href="/home" />;

  const saved = findVolume(customToolVolumes, tool.id);
  // Typing mL needs something to read it from: only users with a measuring cup are offered it.
  const canTypeVolume = toolIds.includes('measuring_cup');
  const directVolume = parseDirectVolume(directText);
  const typed = directText.trim() !== '';
  const method: VolumeMethod = typed ? 'DIRECT' : 'WATER_BOTTLE';
  const volumeMl = typed ? directVolume : fillCount === null ? null : volumeFromFills(fillCount);

  const pickCount = (count: number) => {
    setFillCount(count);
    setDirectText('');
  };

  const save = () => {
    if (volumeMl === null) return;
    saveToolVolume({ toolCode: tool.id, volumeMl, method, measuredAt: new Date().toISOString() });
    router.back();
  };

  const reset = () => {
    resetToolVolume(tool.id);
    router.back();
  };

  return (
    <Page
      footer={
        <>
          <Button disabled={volumeMl === null} label="이 용량으로 저장" onPress={save} />
          {saved ? <Button label="대표 용량으로 되돌리기" onPress={reset} variant="text" /> : null}
        </>
      }
    >
      <TopBar onBack={() => router.back()} />
      <PageHeader
        description={saved ? `지금은 내가 잰 용량 약 ${saved.volumeMl}mL로 안내하고 있어요.` : '지금은 대표 용량으로 안내하고 있어요.'}
        style={styles.header}
        title={`${tool.name}\n내 도구로 맞추기`}
      />
      <View style={styles.step}>
        <AppText weight="bold" style={styles.stepNumber}>
          1
        </AppText>
        <AppText style={styles.stepText}>{BOTTLE_ML}mL 생수병에 물을 가득 채워 주세요.</AppText>
      </View>
      <View style={styles.step}>
        <AppText weight="bold" style={styles.stepNumber}>
          2
        </AppText>
        <AppText style={styles.stepText}>이 도구에 가득 따르면 병 하나로 몇 번 채울 수 있나요?</AppText>
      </View>
      <View accessibilityRole="radiogroup" style={styles.counts}>
        {FILL_COUNTS.map((count) => {
          const selected = !typed && fillCount === count;
          return (
            <Pressable
              accessibilityRole="radio"
              accessibilityState={{ selected }}
              key={count}
              onPress={() => pickCount(count)}
              style={[styles.count, selected && styles.countOn]}
            >
              <AppText weight={selected ? 'bold' : 'regular'} style={[styles.countLabel, selected && styles.countLabelOn]}>
                {fillCountLabel(count)}
              </AppText>
            </Pressable>
          );
        })}
      </View>
      {canTypeVolume ? (
        <View style={styles.direct}>
          <AppText weight="bold" style={styles.directTitle}>
            계량컵으로 재 봤다면 mL로 직접 입력
          </AppText>
          <View style={styles.directRow}>
            <TextInput
              accessibilityLabel="용량 직접 입력 (mL)"
              inputMode="numeric"
              keyboardType="number-pad"
              maxLength={4}
              onChangeText={setDirectText}
              placeholder={`${MIN_VOLUME_ML}~${MAX_VOLUME_ML}`}
              placeholderTextColor={colors.hint}
              style={styles.input}
              value={directText}
            />
            <AppText style={styles.unit}>mL</AppText>
          </View>
          {typed && directVolume === null ? (
            <AppText style={styles.error}>
              {MIN_VOLUME_ML}에서 {MAX_VOLUME_ML} 사이의 숫자로 적어 주세요.
            </AppText>
          ) : null}
        </View>
      ) : null}
      {volumeMl !== null ? (
        <View style={styles.result}>
          <AppText weight="bold" style={styles.resultText}>
            이 도구는 약 {volumeMl}mL예요
          </AppText>
          <AppText style={styles.resultNote}>저장하면 이 용량으로 양을 알려드려요. 이 기기에만 저장돼요.</AppText>
        </View>
      ) : null}
    </Page>
  );
}

const styles = StyleSheet.create({
  header: { marginBottom: 22 },
  step: { marginBottom: 12, flexDirection: 'row', gap: 10 },
  stepNumber: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: colors.primarySoft,
    color: colors.primaryDark,
    textAlign: 'center',
    lineHeight: 24,
    fontSize: 13,
  },
  stepText: { flex: 1, fontSize: 15, lineHeight: 24 },
  counts: { marginTop: 6, flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  count: {
    minWidth: 64,
    height: 46,
    paddingHorizontal: 12,
    borderRadius: 14,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  countOn: { backgroundColor: colors.primarySoft },
  countLabel: { fontSize: 15, color: colors.text },
  countLabelOn: { color: colors.primaryDark },
  direct: { marginTop: 26 },
  directTitle: { marginBottom: 10, fontSize: 14 },
  directRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  input: {
    width: 130,
    height: 50,
    paddingHorizontal: 14,
    borderRadius: 14,
    backgroundColor: colors.surface,
    fontFamily: fonts.regular,
    fontSize: 16,
    color: colors.text,
  },
  unit: { fontSize: 15, color: colors.sub },
  error: { marginTop: 8, fontSize: 13, color: colors.danger },
  result: { marginTop: 26, padding: 18, borderRadius: 18, backgroundColor: colors.infoSoft },
  resultText: { fontSize: 18 },
  resultNote: { marginTop: 6, fontSize: 13, lineHeight: 19, color: colors.sub },
});
