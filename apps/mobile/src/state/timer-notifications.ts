import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

// Local notification for the end of the cooking timer (spec §9). There is no push server.
// The app has one active timer, so at most one notification is ever scheduled: every change
// first cancels whatever is scheduled. Without permission, or on the web preview, nothing is
// scheduled and the on-screen timer keeps working.

const supported = Platform.OS !== 'web';
const CHANNEL_ID = 'timer';

if (supported) {
  // Also shown while the app is open, so the end is noticed from across the kitchen.
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: true,
      shouldSetBadge: false,
    }),
  });
}

// Schedule and cancel calls run one after another, in the order the user acted.
let queue: Promise<void> = Promise.resolve();

function enqueue(task: () => Promise<void>) {
  // A failed call must not block the ones after it; the screen timer does not depend on it.
  queue = queue.then(task).catch(() => undefined);
}

async function hasPermission(): Promise<boolean> {
  const current = await Notifications.getPermissionsAsync();
  if (current.granted) return true;
  if (!current.canAskAgain) return false;
  return (await Notifications.requestPermissionsAsync()).granted;
}

async function replaceScheduled(seconds: number | null, stepNumber: number) {
  await Notifications.cancelAllScheduledNotificationsAsync();
  if (seconds === null || seconds <= 0 || !(await hasPermission())) return;
  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync(CHANNEL_ID, {
      name: '조리 타이머',
      importance: Notifications.AndroidImportance.HIGH,
    });
  }
  await Notifications.scheduleNotificationAsync({
    content: {
      title: `${stepNumber}단계 타이머가 끝났어요`,
      // The app never moves on or changes the heat by itself.
      body: '상태를 확인하고 다음 단계로 가세요. 불은 직접 조절하세요.',
      sound: true,
    },
    trigger: {
      type: Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL,
      seconds,
      channelId: CHANNEL_ID,
    },
  });
}

// Called on start, resume and restart: the earlier notification is replaced, never added to.
export function scheduleTimerEnd(seconds: number, stepNumber: number) {
  if (supported) enqueue(() => replaceScheduled(seconds, stepNumber));
}

// Called on pause, cancel and when a new recipe starts.
export function cancelTimerEnd() {
  if (supported) enqueue(() => replaceScheduled(null, 0));
}
