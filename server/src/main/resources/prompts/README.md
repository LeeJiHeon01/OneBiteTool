# GPT 프롬프트

서버가 OpenAI API를 부를 때 쓰는 시스템 프롬프트와 응답 스키마다. 앱은 GPT를 직접 부르지 않는다.
응답은 Structured Outputs(`schemas/*.json`, `strict: true`)로 형식을 강제하고, **서버가 한 번 더 검증한 뒤에만** 저장하거나 앱에 전달한다.

| 프롬프트 | 하는 일 | 웹 검색 도구 | 입력 | 스키마 |
| --- | --- | --- | --- | --- |
| `recipe-search.md` | 요리 이름으로 실제 레시피 페이지 주소 찾기 (가진 도구로 만들 수 있는 것 우선, 그다음 만개의레시피 우선) | 사용 | `dishName`, `ownedTools`, `preferredSites` | `schemas/recipe-search.json` |
| `recipe-extract.md` | 서버가 가져온 페이지 글에서 재료·순서 추출 | 없음 | `dishName`, `pageUrl`, `pageText` | `schemas/recipe-extract.json` |
| `recipe-adapt.md` | 확정된 단계를 쉬운 문장으로 다듬고 실력별 설명 추가 | 없음 | `skillLevel`, `steps[]`, `changes[]` | `schemas/recipe-adapt.json` |
| `weight-reference.md` | 재료의 부피당 무게를 출처와 함께 조사 | 사용 | `ingredientName`, `preparation`, `toolName`, `toolVolumeMl` | `schemas/weight-reference.json` |

입력은 사용자 메시지에 JSON으로 넣는다. 사용자 알레르기 정보는 어떤 프롬프트에도 넣지 않는다.

## 역할 분담

GPT가 하는 것: 페이지 찾기, 페이지에 적힌 것 옮기기, 문장 다듬기, 출처 있는 수치 찾기.
GPT가 하지 않는 것: 단위 변환과 계산, 알레르기·안전 판단, 재료 생략·대체 결정, 레시피를 지어내기.

## 서버가 검증할 것

`recipe-search`와 `recipe-extract`는 `POST /api/recipe-imports`(`recipeimport/RecipeImportService`)에 연결돼 있다. 후보는 가진 도구로 만들 수 있다고 한 것부터 최대 3개까지 추출해 보고, 처음으로 검증을 통과한 것을 저장한다. 통과한 후보끼리 도구를 다시 비교하지는 않는다(추출 호출 수를 줄이기 위해).

**`recipe-search`**
- 주소가 `http(s)`이고 응답에 중복이 없는가
- 서버가 실제로 그 주소를 가져올 수 있는가 (가져오지 못하면 다음 후보)

**`recipe-extract`** — 하나라도 어긋나면 그 후보를 버리고 다음 후보로 넘어간다. 후보가 없으면 "찾지 못했어요".
- `isRecipe`가 `true`이고 재료와 단계가 하나 이상인가
- 모든 `ingredients[].evidence`, `steps[].evidence`가 `pageText`에 실제로 있는가 (공백을 정리한 뒤 비교)
- `amountText`가 그 재료의 `evidence` 안에 있는가
- `durationSeconds`가 있으면 `durationEvidence`가 `pageText`에 있고, 그 구절의 숫자와 초가 맞는가
- `heatLevel`이 있으면 `heatRaw`가 `pageText`에 있는가
- `deviceSettingRaw`가 있으면 그 글자가 `pageText`에 그대로 있는가. 있으면 그 단계에 `heatLevel`이 없는가
- 후보를 고를 때: 추출된 도구가 사용자 도구로 충족되는 후보를 먼저 쓴다(`needsOtherTools`는 참고용이고, 최종 판단은 추출 결과로 서버가 한다)
- `steps[].action`이 `pageText`의 문장을 그대로 옮긴 것이 아닌가 (공백 뺀 12자 이상이 원문에 그대로 있으면 탈락. 원문 문장은 저장하지 않는다)
- `steps[].action`의 숫자가 그 단계의 `evidence`에 있는가, 그리고 양·시간·온도 단위가 붙은 숫자가 아닌가 ("1cm 두께로"는 통과, "간장 2큰술"·"3분"은 탈락)
- `steps[].ingredientNames`가 모두 `ingredients[].name`에 있는가
- `actionKey`, `toolCodes`가 준비된 키·도구 코드인가 (스키마의 `enum`)
- `evidence`와 `pageText`는 대조에만 쓰고 저장하지 않는다. 저장하는 것은 재료 이름·양 표현과 다시 쓴 단계 문장뿐이다.

**`recipe-adapt`** — 연결됨(`adaptation/StepRewriter`, `RewriteValidator`). 어긋난 단계가 있으면 한 번 다시 요청하고, 그래도 어긋난 단계는 GPT 문장 대신 서버가 가진 원래 문장을 쓴다(단계별로 판단).
- `stepId`가 입력과 같은 순서로 빠짐없이 있는가 (아니면 응답 전체를 버림)
- `action`, `details`에 숫자가 없는가
- 불 세기 표현(약불, 중불, 강불, 센 불 등)이 없는가
- 입력에 없는 재료·도구 이름이 없는가 (재료 사전과 도구 목록으로 확인. 한 글자 재료 이름은 다른 낱말 안에도 나와서 검사하지 않음)
- 안전 문구를 대신하는 표현이 없는가. 안전 주의사항은 `safety_rule`의 문장만 쓴다.
- `HIGH`이면 `details`는 서버가 항상 비운다
- GPT가 바꿀 수 있는 것은 `action`, `details`뿐이다. 시간·불 세기·상태 확인·안전 문구·재료·도구는 서버 값 그대로다.
- API 키가 없거나, 하루 호출 횟수를 넘었거나, 호출이 실패하면 서버 문장 그대로 응답한다.

**`weight-reference`** — 자동으로 반영하지 않는다.
- `found`가 `true`이면 `grams`, `volumeMl`, `sourceUrl`, `sourceQuote`가 모두 있는가
- 서버가 `sourceUrl`을 가져와 `sourceQuote`가 실제로 있는지 확인
- 통과한 값은 검토 대기 목록에만 넣는다. 사람이 확인한 뒤 `data/seed/weight-references.json`에 `PUBLISHED`로 옮긴다.
- 도구 용량에 맞춘 환산(`grams × toolVolumeMl ÷ volumeMl`)은 서버가 한다.

## 아직 정하지 않은 것

- 단계별 모델과 월 비용 상한 (spec §14 Q-F1). 지금은 작은 모델 하나(`OPENAI_MODEL`)와 하루 호출 횟수 제한(`OPENAI_DAILY_CALL_LIMIT`)만 쓴다.
- 검색에서 제외할 사이트 (Q-B4). 우선 사이트는 만개의레시피로 정했다(`app.preferred-recipe-sites`).
- 조리 시간이 "2~3분"처럼 범위일 때 짧은 쪽을 타이머로 쓰는 규칙 (`recipe-extract.md`). 고기처럼 덜 익으면 안 되는 재료는 상태 확인 문구와 안전 규칙이 함께 표시되지만, 규칙 자체는 확인이 필요하다.
