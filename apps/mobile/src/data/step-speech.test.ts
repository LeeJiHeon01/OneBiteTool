import { describe, expect, it } from '@jest/globals';

import { type AllergyMatch } from '@/data/allergy';
import { type AdaptedStep } from '@/data/recipe';
import { speakable, stepSpeechLines } from '@/data/step-speech';

function step(extra: Partial<AdaptedStep> = {}): AdaptedStep {
  return {
    id: 'S1',
    text: '김치를 저어가며 볶아요',
    details: null,
    actionLabel: '볶기',
    actionIcon: 'pan',
    ingredientIds: ['I1'],
    heatLevel: null,
    deviceSetting: null,
    durationSeconds: null,
    durationMaxSeconds: null,
    timerStartCondition: null,
    completionCheck: null,
    safetyNotes: [],
    subItems: [],
    ...extra,
  };
}

const SOY: AllergyMatch = {
  ingredientId: 'I2',
  ingredientName: '간장',
  allergenNames: '대두, 밀',
  hidden: true,
  productVaries: true,
};

describe('reading a step aloud', () => {
  it('always includes the allergy warning and the safety notes of the step', () => {
    const lines = stepSpeechLines(step({ safetyNotes: ['뜨거운 기름이 튈 수 있어요.'] }), [SOY]);

    expect(lines).toContain('알레르기 주의. 간장에 대두, 밀이 들어 있어요.');
    expect(lines).toContain('제품마다 달라요. 성분표를 확인하세요.');
    expect(lines).toContain('조심하세요. 뜨거운 기름이 튈 수 있어요.');
  });

  it('reads in the order action, heat, timer, allergy, safety, check', () => {
    const lines = stepSpeechLines(
      step({
        heatLevel: 'MEDIUM',
        durationSeconds: 180,
        timerStartCondition: '볶기 시작할 때',
        completionCheck: '김치 숨이 죽으면 다음으로 가세요.',
        safetyNotes: ['뜨거운 기름이 튈 수 있어요.'],
      }),
      [SOY],
    );

    expect(lines[0]).toBe('김치를 저어가며 볶아요');
    expect(lines[1]).toBe('불 세기는 중불이에요.');
    expect(lines[2]).toBe('볶기 시작할 때. 타이머는 3분이에요. 타이머는 직접 눌러 시작해 주세요.');
    expect(lines[3]).toContain('알레르기 주의');
    expect(lines[5]).toContain('조심하세요');
    expect(lines[6]).toBe('눈으로 확인해요. 김치 숨이 죽으면 다음으로 가세요.');
  });

  it('reads the checklist of a finishing step right after the action', () => {
    const lines = stepSpeechLines(
      step({
        text: '그릇에 담고 마무리해요',
        subItems: [
          { id: 'S7', text: '밥을 그릇에 담아요', ingredientIds: ['I3'] },
          { id: 'S8', text: '깨를 뿌려요', ingredientIds: ['I4'] },
        ],
        safetyNotes: ['그릇이 뜨거울 수 있어요.'],
      }),
      [],
    );
    expect(lines).toEqual(['그릇에 담고 마무리해요', '밥을 그릇에 담아요', '깨를 뿌려요', '조심하세요. 그릇이 뜨거울 수 있어요.']);
  });

  it('reads only what the step has', () => {
    expect(stepSpeechLines(step(), [])).toEqual(['김치를 저어가며 볶아요']);
  });

  it('reads a device setting as written, with the unit spoken as a word', () => {
    const lines = stepSpeechLines(step({ text: '오븐에 넣어 구워요', deviceSetting: '180°C' }), []);
    expect(lines[1]).toBe('기기 설정은 180도. 기기마다 조금씩 달라요.');
  });

  it('speaks symbols as words without changing numbers', () => {
    expect(speakable('180°C 예열')).toBe('180도 예열');
    expect(speakable('200℃')).toBe('200도');
    expect(speakable('700W')).toBe('700와트');
    expect(speakable('1큰술')).toBe('1큰술');
    expect(speakable('1분 30초')).toBe('1분 30초');
  });
});
