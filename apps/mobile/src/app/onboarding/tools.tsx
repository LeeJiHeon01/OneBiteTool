import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { Button } from '@/components/button';
import { PageHeader } from '@/components/headers';
import { Icon } from '@/components/icon';
import { Page } from '@/components/page';
import { Picture } from '@/components/picture';
import { AppText } from '@/components/text';
import { TopBar } from '@/components/top-bar';
import {
  getTool,
  HEAT_SOURCES,
  STANDARD_MEASURE_TOOLS,
  TOOL_GROUPS,
  VISIBLE_TOOL_COUNT,
  type Tool,
} from '@/data/catalog';
import { toolImage } from '@/data/images';
import { useAppState } from '@/state/app-state';
import { colors } from '@/theme/tokens';

const COLUMNS = 3;

function toRows(tools: Tool[]): (Tool | null)[][] {
  const rows: (Tool | null)[][] = [];
  for (let index = 0; index < tools.length; index += COLUMNS) {
    const row: (Tool | null)[] = tools.slice(index, index + COLUMNS);
    while (row.length < COLUMNS) row.push(null);
    rows.push(row);
  }
  return rows;
}

function ToolCard({ tool, selected, onPress }: { tool: Tool; selected: boolean; onPress: () => void }) {
  const image = toolImage(tool.id);
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityState={{ checked: selected }}
      onPress={onPress}
      style={[styles.card, selected && styles.cardSelected]}
    >
      <View style={[styles.icon, selected && styles.iconSelected]}>
        {image ? <Picture size={40} source={image} /> : null}
      </View>
      <AppText weight="semiBold" style={styles.name}>
        {tool.name}
      </AppText>
      {tool.note ? <AppText style={styles.note}>{tool.note}</AppText> : null}
      <View style={[styles.check, selected && styles.checkSelected]}>
        <Icon name="check" size={13} color={colors.white} />
      </View>
    </Pressable>
  );
}

function PreferredHeatChoice() {
  const { preferredHeat, setPreferredHeat } = useAppState();
  return (
    <View accessibilityRole="radiogroup" style={styles.heatChoice}>
      <AppText weight="bold" style={styles.heatChoiceLabel}>
        주로 쓰는 것
      </AppText>
      {HEAT_SOURCES.map((id) => {
        const selected = preferredHeat === id;
        return (
          <Pressable
            accessibilityRole="radio"
            accessibilityState={{ selected }}
            hitSlop={10}
            key={id}
            onPress={() => setPreferredHeat(id)}
            style={styles.heatOption}
          >
            <View style={[styles.radio, selected && styles.radioSelected]} />
            <AppText style={styles.heatOptionLabel}>{getTool(id)?.name}</AppText>
          </Pressable>
        );
      })}
    </View>
  );
}

function ToolGrid({ tools }: { tools: Tool[] }) {
  const { toolIds, toggleTool } = useAppState();
  return (
    <View style={styles.grid}>
      {toRows(tools).map((row, rowIndex) => (
        <View key={rowIndex} style={styles.row}>
          {row.map((tool, columnIndex) =>
            tool ? (
              <ToolCard
                key={tool.id}
                onPress={() => toggleTool(tool.id)}
                selected={toolIds.includes(tool.id)}
                tool={tool}
              />
            ) : (
              <View key={`empty-${columnIndex}`} style={styles.cell} />
            ),
          )}
        </View>
      ))}
    </View>
  );
}

// Less common tools stay folded under "더 있어요" unless the user is an experienced cook
// or has already picked one of them.
function MoreTools({ tools }: { tools: Tool[] }) {
  const { skillLevel, toolIds } = useAppState();
  const [open, setOpen] = useState(
    () => skillLevel === 'HIGH' || tools.some((tool) => toolIds.includes(tool.id)),
  );
  return (
    <>
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ expanded: open }}
        onPress={() => setOpen(!open)}
        style={styles.more}
      >
        <AppText weight="semiBold" style={styles.moreLabel}>
          {open ? '접기' : `더 있어요 (${tools.length})`}
        </AppText>
        <View style={open ? styles.chevronUp : styles.chevronDown}>
          <Icon name="chevron" size={16} color={colors.sub} />
        </View>
      </Pressable>
      {open ? <ToolGrid tools={tools} /> : null}
    </>
  );
}

export default function ToolsScreen() {
  const { edit } = useLocalSearchParams<{ edit?: string }>();
  const { toolIds } = useAppState();
  const next = () => (edit ? router.back() : router.push('/onboarding/allergy'));
  const hasBothStoves = HEAT_SOURCES.every((id) => toolIds.includes(id));

  return (
    <Page
      footer={
        <Button
          disabled={toolIds.length === 0}
          label={edit ? '저장' : '다음'}
          onPress={next}
          trailing={
            toolIds.length > 0 ? (
              <View style={styles.count}>
                <AppText weight="bold" style={styles.countLabel}>
                  선택 {toolIds.length}개
                </AppText>
              </View>
            ) : null
          }
        />
      }
    >
      <TopBar onBack={() => router.back()} step={edit ? undefined : 2} />
      <PageHeader description="여러 개 고를 수 있어요." title={'집에 있는 도구를\n골라주세요'} />
      <View style={styles.groups}>
        <View>
          <AppText weight="bold" style={styles.groupTitle}>
            계량스푼·계량컵이 있나요?
          </AppText>
          <AppText style={styles.groupHint}>있으면 레시피의 큰술·컵을 바꾸지 않고 그대로 보여드려요.</AppText>
          <ToolGrid tools={STANDARD_MEASURE_TOOLS} />
        </View>
        {TOOL_GROUPS.map((group) => {
          const moreTools = group.tools.slice(VISIBLE_TOOL_COUNT);
          return (
            <View key={group.id}>
              <AppText weight="bold" style={styles.groupTitle}>
                {group.title}
              </AppText>
              <ToolGrid tools={group.tools.slice(0, VISIBLE_TOOL_COUNT)} />
              {group.id === 'heat' && hasBothStoves ? <PreferredHeatChoice /> : null}
              {moreTools.length > 0 ? <MoreTools tools={moreTools} /> : null}
            </View>
          );
        })}
      </View>
    </Page>
  );
}

const styles = StyleSheet.create({
  groups: { gap: 29 },
  groupTitle: { fontSize: 16, marginBottom: 12 },
  groupHint: { marginTop: -6, marginBottom: 12, fontSize: 13, lineHeight: 19, color: colors.sub },
  more: {
    minHeight: 48,
    marginVertical: 4,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
  },
  moreLabel: { fontSize: 14, color: colors.sub },
  chevronDown: { transform: [{ rotate: '90deg' }] },
  chevronUp: { transform: [{ rotate: '-90deg' }] },
  grid: { gap: 10 },
  row: { flexDirection: 'row', gap: 10 },
  cell: { flex: 1 },
  card: {
    flex: 1,
    height: 106,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 17,
    backgroundColor: colors.white,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 4,
  },
  cardSelected: { borderWidth: 2, borderColor: colors.primary, backgroundColor: colors.primarySoft },
  icon: {
    width: 50,
    height: 50,
    marginBottom: 8,
    borderRadius: 15,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconSelected: { backgroundColor: colors.white },
  name: { fontSize: 13, lineHeight: 16, textAlign: 'center' },
  note: { marginTop: 3, fontSize: 10, color: colors.sub },
  check: {
    position: 'absolute',
    right: 7,
    top: 7,
    width: 19,
    height: 19,
    borderRadius: 10,
    backgroundColor: colors.line,
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkSelected: { backgroundColor: colors.primary },
  count: { paddingVertical: 4, paddingHorizontal: 8, borderRadius: 99, backgroundColor: 'rgba(255, 255, 255, 0.2)' },
  countLabel: { fontSize: 12, color: colors.white },
  heatChoice: {
    minHeight: 52,
    marginTop: 10,
    paddingHorizontal: 13,
    borderRadius: 14,
    backgroundColor: colors.surface,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 11,
  },
  heatChoiceLabel: { marginRight: 'auto', fontSize: 12, color: colors.sub },
  heatOption: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  heatOptionLabel: { fontSize: 12 },
  radio: {
    width: 17,
    height: 17,
    borderRadius: 9,
    borderWidth: 1,
    borderColor: colors.hint,
    backgroundColor: colors.white,
  },
  radioSelected: { borderWidth: 5, borderColor: colors.primary },
});
