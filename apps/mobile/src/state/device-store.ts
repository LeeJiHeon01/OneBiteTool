import AsyncStorage from '@react-native-async-storage/async-storage';

// Plain device storage for things that are not sensitive: recent searches, favourites, the dish being cooked.
// The profile (which holds allergies) is NOT stored here; it lives in the encrypted store
// (see profile-storage).

// Returns the fallback when nothing is stored or the stored value cannot be read.
export async function loadJson<T>(key: string, fallback: T): Promise<T> {
  try {
    const text = await AsyncStorage.getItem(key);
    return text ? (JSON.parse(text) as T) : fallback;
  } catch {
    return fallback;
  }
}

// A failed write only means the value is gone after a restart.
export async function saveJson(key: string, value: unknown): Promise<void> {
  try {
    await AsyncStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Nothing to recover: the in-memory value keeps working for this session.
  }
}

export async function removeJson(key: string): Promise<void> {
  try {
    await AsyncStorage.removeItem(key);
  } catch {
    // Nothing to recover: a stale value is checked again when it is read.
  }
}
