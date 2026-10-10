import { useCallback, useEffect, useState } from 'react';
import { AppState } from 'react-native';

import { cancelTimerEnd, scheduleTimerEnd } from '@/state/timer-notifications';

// Cooking timer (spec §9). Only one timer exists at a time. It never starts by itself and
// never moves to the next step. Each change replaces or cancels the scheduled end notification.
// The timer lives in memory; the dish being cooked is saved with it (data/cooking-session) and
// puts it back through restore() when a closed app is opened again.
export type TimerState = {
  stepId: string;
  // 1-based step number, shown in the notification.
  stepNumber: number;
  status: 'running' | 'paused' | 'finished';
  durationSeconds: number;
  endAt: number | null; // epoch ms
  remainingOnPause: number | null; // seconds
};

// Remaining time is always derived from endAt; intervals are for display only.
export function remainingSeconds(timer: TimerState, now: number): number {
  if (timer.status === 'paused') return timer.remainingOnPause ?? 0;
  if (timer.status === 'finished' || timer.endAt === null) return 0;
  const seconds = Math.max(0, Math.ceil((timer.endAt - now) / 1000));
  return Math.min(timer.durationSeconds, seconds);
}

export function formatSeconds(totalSeconds: number): string {
  const minutes = String(Math.floor(totalSeconds / 60)).padStart(2, '0');
  const seconds = String(totalSeconds % 60).padStart(2, '0');
  return `${minutes}:${seconds}`;
}

function finishIfDue(timer: TimerState | null): TimerState | null {
  if (timer?.status !== 'running' || timer.endAt === null || timer.endAt > Date.now()) return timer;
  return { ...timer, status: 'finished', endAt: null };
}

export function useTimer() {
  const [timer, setTimer] = useState<TimerState | null>(null);

  // Mark the timer finished once endAt passes. It never advances the step by itself.
  useEffect(() => {
    if (timer?.status !== 'running' || timer.endAt === null) return;
    const id = setTimeout(() => setTimer(finishIfDue), Math.max(0, timer.endAt - Date.now()));
    return () => clearTimeout(id);
  }, [timer]);

  // JS timers pause while the app is in the background; on return the end time decides.
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') setTimer(finishIfDue);
    });
    return () => subscription.remove();
  }, []);

  const start = useCallback((stepId: string, stepNumber: number, durationSeconds: number) => {
    scheduleTimerEnd(durationSeconds, stepNumber);
    setTimer({
      stepId,
      stepNumber,
      status: 'running',
      durationSeconds,
      endAt: Date.now() + durationSeconds * 1000,
      remainingOnPause: null,
    });
  }, []);

  // Pausing stops the app timer and its notification only; the heat is the user's to turn down.
  const pause = useCallback(() => {
    if (timer?.status !== 'running') return;
    cancelTimerEnd();
    setTimer({ ...timer, status: 'paused', remainingOnPause: remainingSeconds(timer, Date.now()), endAt: null });
  }, [timer]);

  const resume = useCallback(() => {
    if (timer?.status !== 'paused') return;
    const remaining = timer.remainingOnPause ?? 0;
    scheduleTimerEnd(remaining, timer.stepNumber);
    setTimer({ ...timer, status: 'running', endAt: Date.now() + remaining * 1000, remainingOnPause: null });
  }, [timer]);

  const restart = useCallback(() => {
    if (!timer) return;
    scheduleTimerEnd(timer.durationSeconds, timer.stepNumber);
    setTimer({
      ...timer,
      status: 'running',
      endAt: Date.now() + timer.durationSeconds * 1000,
      remainingOnPause: null,
    });
  }, [timer]);

  const cancel = useCallback(() => {
    cancelTimerEnd();
    setTimer(null);
  }, []);

  // Puts back a saved timer. A running one keeps its end time and gets its notification
  // scheduled again (replacing whatever is scheduled); a finished or paused one schedules nothing.
  const restore = useCallback((saved: TimerState | null) => {
    const secondsLeft = saved?.status === 'running' && saved.endAt !== null
      ? Math.ceil((saved.endAt - Date.now()) / 1000)
      : 0;
    if (saved && secondsLeft > 0) scheduleTimerEnd(secondsLeft, saved.stepNumber);
    else cancelTimerEnd();
    setTimer(finishIfDue(saved));
  }, []);

  return { timer, start, pause, resume, restart, cancel, restore };
}

// Ticks while the timer runs and returns the seconds left.
export function useRemainingSeconds(timer: TimerState | null): number {
  const [now, setNow] = useState(Date.now);

  useEffect(() => {
    if (timer?.status !== 'running') return;
    const id = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(id);
  }, [timer]);

  return timer ? remainingSeconds(timer, now) : 0;
}
