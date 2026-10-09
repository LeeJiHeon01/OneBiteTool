# 한끼툴 앱 (apps/mobile)

요리 초보자용 도구 맞춤 레시피 앱의 Expo(React Native + TypeScript) 프로젝트다.
규칙과 기획은 저장소 루트의 `CLAUDE.md`, `docs/spec.md`를 따른다.

## 실행

```bash
npm install
npx expo start          # iPhone의 Expo Go로 QR 접속, 또는 w 로 웹 미리보기
```

- 서버(`server`, 8080 포트)가 켜져 있어야 검색과 맞춤 레시피가 동작한다.
- 서버 주소는 `EXPO_PUBLIC_API_BASE_URL`로 정한다. 없으면 앱을 띄운 PC의 8080 포트를 쓴다(폰과 PC가 같은 Wi-Fi여야 한다).
- 코드를 고친 뒤 화면이 바뀌지 않으면 Expo를 다시 시작한다.
- 네이티브 라이브러리는 `npx expo install <package>`로 설치한다.

## 검사

```bash
npm test                # jest (알레르기 노출 규칙 등 순수 로직)
npx tsc --noEmit
npx expo lint
```

## 구조

| 경로 | 내용 |
| --- | --- |
| `src/app/` | 화면 (expo-router). `onboarding/` 처음 설정, `search` 검색, `recipe/` 확인·없는 재료·맞춤 결과, `cook` 조리 |
| `src/api/` | 서버 호출과 응답 변환. 양·시간·불·안전 문구는 서버 값만 쓴다 |
| `src/state/` | 앱 상태, 기기 저장(`expo-secure-store`), 타이머와 종료 알림(`expo-notifications`) |
| `src/data/` | 도구·알레르기 목록, 알레르기 판단·노출 규칙, 화면용 타입 |
| `src/components/` | 공통 화면 조각 |
| `assets/images/` | `assets/source/images`에서 변환한 그림 |

## 지켜야 할 것

- 사용자의 알레르기는 기기(암호화 저장소)에만 두고, 서버에는 요청 본문으로만 보낸다. 주소(URL)에 넣지 않는다.
- 타이머는 사용자가 눌러야 시작하고, 끝나도 다음 단계로 넘어가지 않는다.
- 그림이 없는 항목은 자리 표시로 두고 임의로 만들지 않는다.

Expo API를 쓰기 전에는 `AGENTS.md`의 안내대로 현재 SDK 버전 문서를 확인한다.
