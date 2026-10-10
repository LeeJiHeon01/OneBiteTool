// What a failed search or import shows. The wording depends on why it failed: the server
// was not reached, today's limit for finding new recipes is used up, or the server had a problem.

export type ErrorKind = 'OFFLINE' | 'DAILY_LIMIT' | 'SERVER';

export type ErrorView = {
  kind: ErrorKind;
  title: string;
  description: string;
  // Trying again can help now; not when today's limit is used up.
  canRetry: boolean;
  // Recipes the server already has can still be opened from the home screen.
  offersStored: boolean;
};

// code is ApiError.code: NETWORK when the server was not reached, otherwise the server's code.
export function errorKind(code: string | null): ErrorKind {
  if (code === 'NETWORK') return 'OFFLINE';
  if (code === 'AI_DAILY_LIMIT') return 'DAILY_LIMIT';
  return 'SERVER';
}

const VIEWS: Record<ErrorKind, ErrorView> = {
  OFFLINE: {
    kind: 'OFFLINE',
    title: '인터넷 연결을\n확인해 주세요',
    description: '서버에 연결하지 못했어요.\n조리 중인 레시피는 계속 볼 수 있어요.',
    canRetry: true,
    offersStored: false,
  },
  DAILY_LIMIT: {
    kind: 'DAILY_LIMIT',
    title: '지금은 새 레시피를\n찾을 수 없어요',
    description: '오늘 새로 찾을 수 있는 횟수를 다 썼어요. 내일 다시 찾아 주세요.\n저장된 레시피는 볼 수 있어요.',
    canRetry: false,
    offersStored: true,
  },
  SERVER: {
    kind: 'SERVER',
    title: '지금은 새 레시피를\n찾을 수 없어요',
    description: '서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.\n저장된 레시피는 볼 수 있어요.',
    canRetry: true,
    offersStored: true,
  },
};

export function errorView(code: string | null): ErrorView {
  return VIEWS[errorKind(code)];
}
