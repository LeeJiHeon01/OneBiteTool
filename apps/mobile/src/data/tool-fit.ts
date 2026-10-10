// Whether a recipe can be made with the user's tools. The server decides (it holds the tool
// substitution rules); the app only words the answer.

export type ToolFit = {
  ok: boolean;
  // Needs none of the user's tools covers. A need can be a choice ("프라이팬 또는 냄비").
  missingTools: { id: string; name: string }[];
};

export const TOOL_FIT_OK_BADGE = '내 도구로 만들 수 있어요';
export const TOOL_FIT_OK_NOTICE = '가진 도구로 만들 수 있어요';

function hasFinalConsonant(word: string): boolean {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  return code >= 0 && code <= 11171 && code % 28 !== 0;
}

// "냄비가 필요해요", "칼, 프라이팬이 필요해요"
export function missingToolsLabel(fit: ToolFit): string {
  const names = fit.missingTools.map((tool) => tool.name).join(', ');
  return `${names}${hasFinalConsonant(names) ? '이' : '가'} 필요해요`;
}
