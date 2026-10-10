import { type AllergyMatch } from '@/data/allergy';
import { HEAT_INFO } from '@/data/catalog';
import { type AdaptedStep } from '@/data/recipe';

// What is read aloud for one cooking step. The text comes from the same server values the
// card shows; nothing is added. Allergy warnings and safety notes are always part of it.

// Units written as symbols are spoken as words; numbers stay as they are.
export function speakable(text: string): string {
  return text
    .replace(/\s*(°C|℃|° C)/g, '도')
    .replace(/(\d)\s*W\b/g, '$1와트')
    .replace(/(\d)\s*(ml|mL|㎖)\b/g, '$1밀리리터')
    .replace(/(\d)\s*g\b/g, '$1그램')
    .replace(/\s+/g, ' ')
    .trim();
}

export function spokenDuration(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return [minutes > 0 ? `${minutes}분` : '', seconds > 0 ? `${seconds}초` : ''].filter(Boolean).join(' ');
}

// In reading order: action, heat or device setting, timer, allergy warnings, safety notes,
// then what to look for before moving on.
export function stepSpeechLines(step: AdaptedStep, allergyMatches: AllergyMatch[]): string[] {
  const lines: string[] = [step.text];
  // The checklist of a finishing step is part of what to do in the step.
  for (const item of step.subItems) lines.push(item.text);
  if (step.heatLevel) lines.push(`불 세기는 ${HEAT_INFO[step.heatLevel].label}이에요.`);
  if (step.deviceSetting) lines.push(`기기 설정은 ${step.deviceSetting}. 기기마다 조금씩 달라요.`);
  if (step.durationSeconds) {
    const start = step.timerStartCondition ? `${step.timerStartCondition}. ` : '';
    // A range is read as it is: the user picks one of the two, no middle value is made up.
    const time = step.durationMaxSeconds
      ? `${spokenDuration(step.durationSeconds)}이나 ${spokenDuration(step.durationMaxSeconds)} 가운데 고를 수 있어요`
      : `${spokenDuration(step.durationSeconds)}이에요`;
    lines.push(`${start}타이머는 ${time}. 타이머는 직접 눌러 시작해 주세요.`);
  }
  for (const match of allergyMatches) {
    lines.push(`알레르기 주의. ${match.ingredientName}에 ${match.allergenNames}이 들어 있어요.`);
    if (match.productVaries) lines.push('제품마다 달라요. 성분표를 확인하세요.');
  }
  for (const note of step.safetyNotes) lines.push(`조심하세요. ${note}`);
  if (step.completionCheck) lines.push(`눈으로 확인해요. ${step.completionCheck}`);
  return lines.map(speakable).filter((line) => line !== '');
}
