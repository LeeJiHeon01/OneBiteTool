import Constants from 'expo-constants';

// Calls to the Spring server. The address comes from EXPO_PUBLIC_API_BASE_URL; without it the
// development build uses the PC that serves the app (same host, port 8080), because on a
// phone "localhost" is the phone itself.

const SERVER_PORT = 8080;

function baseUrl(): string {
  const configured = process.env.EXPO_PUBLIC_API_BASE_URL;
  if (configured) return configured.replace(/\/$/, '');
  const devHost = Constants.expoConfig?.hostUri?.split(':')[0];
  return `http://${devHost ?? 'localhost'}:${SERVER_PORT}`;
}

// code is the server's error code ("AI_UNAVAILABLE"), or NETWORK when the server was not reached.
export class ApiError extends Error {
  constructor(
    readonly code: string,
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

const NETWORK_MESSAGE = '서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${baseUrl()}${path}`, init);
  } catch {
    throw new ApiError('NETWORK', 0, NETWORK_MESSAGE);
  }
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { code?: string; message?: string } | null;
    throw new ApiError(body?.code ?? 'UNKNOWN', response.status, body?.message ?? NETWORK_MESSAGE);
  }
  return (await response.json()) as T;
}

export function get<T>(path: string): Promise<T> {
  return request<T>(path);
}

// Allergy codes travel in request bodies only, never in the address.
export function post<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

export function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : NETWORK_MESSAGE;
}
