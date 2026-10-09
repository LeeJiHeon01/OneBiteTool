# DB 세팅 가이드 (1차, PostgreSQL)

PostgreSQL 하나를 쓰고, 테이블은 **Flyway**가 서버 시작 시 자동으로 만든다. 직접 SQL을 실행할 필요는 없다.
마이그레이션은 로컬 PostgreSQL 18.6에서 적용을 확인했다 (V1~V7, 테이블 21개, 컬럼 252개 전부 설명 있음).

## 1. 파일 위치

```text
cooking-helper/
  docker-compose.yml                  # Docker로 DB를 띄울 때만 (이미 설치·연결했다면 불필요)
  .env.example                        # 복사해서 .env 로 (커밋 금지)
  data/seed/tools.json                # 도구 용량·컵 치수 기준값 (원본)
  server/src/main/resources/db/migration/
    V1__init_schema.sql               # 테이블 21개 + 컬럼 설명
    V2__seed_catalog.sql              # 도구 15개, 알레르기 19개, 숟가락 양 14개
    V3__add_tools.sql                 # 도구 13개 추가(총 28개), 표준 계량·기본 도구 구분, 가열 기기 설정 텍스트
    V4__tool_display_order.sql        # 도구 표시 순서, '작은 종이컵' → '종이컵'
    V5__ingredient_allergen_verified.sql  # 재료의 알레르기 확인자·확인 시각
    V6__allergen_review_level.sql     # 알레르기 매핑 검토 등급, 제품마다 다름 표시, '난류' → '알류'
    V7__ingredient_form_and_ratio.sql # 재료 형태(form), 민감도에 RATIO 추가
  docs/db-setup.md                    # 이 문서
```

## 2. 서버 설정

`server/build.gradle` dependencies:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-jdbc'
implementation 'org.springframework.boot:spring-boot-starter-flyway'
implementation 'org.flywaydb:flyway-database-postgresql'
runtimeOnly 'org.postgresql:postgresql'
```

- Spring Boot 4에서는 Flyway 자동 설정이 `spring-boot-starter-flyway`에 들어 있다.
- 서버는 JPA 엔티티 없이 `JdbcClient`로 SQL을 직접 실행한다. 테이블은 Flyway만 만든다. 회원 기능(2차)에서 JPA가 필요해지면 그때 `ddl-auto: validate`와 함께 추가한다.
- 접속 정보는 `server/.env`에 둔다(커밋 금지, `server/.env.example` 참고). `application.yml`이 `spring.config.import`로 이 파일을 읽는다.
  - `SPRING_DATASOURCE_URL`: 개발 DB (`cooking_helper`)
  - `SPRING_DATASOURCE_TEST_URL`: 테스트 전용 DB (`cooking_helper_test`). `gradlew test`는 `test` 프로필로 이 DB만 쓴다.

## 3. 처음 실행 전 확인

- 연결한 DB(`cooking_helper`)가 **비어 있어야** 한다. 직접 만든 테이블이 있으면 Flyway가 오류를 낸다.
- DB 사용자에게 `public` 스키마에 테이블을 만들 권한이 있어야 한다. PostgreSQL 15부터는 기본 권한이 줄어서, DB 소유자가 아니면 아래가 필요하다:
  ```sql
  ALTER DATABASE cooking_helper OWNER TO cooking_app;
  -- 또는
  GRANT ALL ON SCHEMA public TO cooking_app;
  ```

## 4. 제대로 됐는지 확인

서버 로그:
```text
Migrating schema "public" to version "1 - init schema"
Migrating schema "public" to version "2 - seed catalog"
...
Successfully applied 7 migrations
```

DB에서:
```sql
SELECT COUNT(*) FROM tool_variant;   -- 28
SELECT code FROM tool_variant WHERE is_standard_measure;   -- measuring_spoon, measuring_cup
SELECT COUNT(*) FROM allergen;       -- 19
SELECT COUNT(*) FROM fill_level;     -- 14
SELECT version, success FROM flyway_schema_history;
```

마이그레이션이 중간에 실패하면 DB를 비우고(`DROP SCHEMA public CASCADE; CREATE SCHEMA public;`) 다시 실행한다.

처음부터 실행하면 "Successfully applied 7 migrations", 이미 앞 버전이 적용된 DB에는 새 버전 수만큼만 적용된다.

## 5. 테이블 구성 (21개)

| 묶음 | 테이블 | 내용 |
| --- | --- | --- |
| 도구 | `tool_category`, `tool_variant`, `fill_level` | 도구 분류·선택지, 용량·컵 치수, 숟가락 양 그림 기준 |
| 재료·알레르기 | `ingredient`, `ingredient_alias`, `allergen`, `ingredient_allergen`, `weight_reference` | 재료 사전(분류·형태·민감도·알레르기 검토 등급), 이름 정규화, 알레르기 매핑(숨은 성분 포함), 무게→개수 기준 |
| 레시피 | `recipe`, `recipe_ingredient`, `recipe_step`, `recipe_step_ingredient`, `recipe_step_tool` | 웹에서 가져온 레시피와 fixture |
| 검증된 규칙 | `adaptation_rule`, `tool_substitution_rule`, `safety_rule` | 생략·대체, 도구 대체, 안전 주의 (모두 `verified_by` 필수) |
| 작업·캐시 | `recipe_import_job`, `adapted_recipe`, `ai_call_log` | 웹 검색 비동기 작업, 재구성 결과 캐시, AI 비용 기록 |
| 이미지 | `image_group`, `image_file` | 이미지 묶음(용도·변환 규칙)과 파일(그림 키 하나에 한 행) |

**모든 테이블 공통 컬럼**

| 컬럼 | 내용 |
| --- | --- |
| `created_at` | 등록일시. 기본값 `now()` |
| `created_by` | 등록자. 1차는 `SYSTEM`, 회원 기능 이후에는 사용자 ID |
| `updated_at` | 수정일시. 행을 수정하면 트리거(`set_updated_at`)가 자동으로 갱신 |
| `updated_by` | 수정자. 수정하는 쪽에서 넣는다(트리거는 시간만 갱신) |

**컬럼 설명**: 모든 컬럼에 한글 설명(`COMMENT ON COLUMN`)이 달려 있다. DBeaver에서는 테이블의 Columns 탭 `Comment` 열에 보인다. 컬럼을 추가할 때는 설명도 함께 추가한다(테스트가 설명 없는 컬럼과 공통 컬럼이 빠진 테이블을 잡아낸다).

**이미지 테이블**
- 원본 목록은 `assets/source/images/manifest.json`이고 서버 시작 시 두 테이블에 반영한다.
- `image_group`: 묶음 코드(`tool`, `heat.gas`, `fill` 등), 이름, 앱 자산 폴더, 변환 크기.
- `image_file`: 그림 키(`image_key`, 고유), 상태(`READY` 그림 있음 / `MISSING` 아직 없음), 원본·변환본 파일명, 앱 자산 기준 경로, 크기.
- 다른 테이블의 그림 키(`tool_variant.icon_key`, `fill_level.image_key`, `recipe_step.action_key`)는 `image_file.image_key`를 가리킨다. 목록에 없는 키가 있으면 서버가 시작하지 않는다(CLAUDE.md 규칙 26).
- 이미지 파일 자체는 DB에 넣지 않는다. 앱에 함께 묶여 나가는 자산이라 경로만 저장한다.

## 6. PostgreSQL에서 정한 것

| 항목 | 선택 | 이유 |
| --- | --- | --- |
| 기본 키 | `CHAR(20)` 문자열. 접두어(영문 대문자 6자 이내) + `_` + 0으로 채운 순번, 전체 20자 고정. 예: `RECIPE_0000000000001` | 테이블마다 시퀀스(`{테이블}_id_seq`)를 두고 `make_id()` 함수가 기본값으로 만든다. 형식은 `CHECK`로 강제한다. 외래 키도 `CHAR(20)` |
| 열거형 | `VARCHAR + CHECK` (PostgreSQL ENUM 타입 안 씀) | JPA `@Enumerated(STRING)`과 그대로 맞고, 값 추가가 쉬움 |
| 시간 | `TIMESTAMPTZ`, 기본값 `now()` | 시간대 혼동 방지. 엔티티는 `Instant`/`OffsetDateTime` |
| `updated_at` | 트리거 `set_updated_at()`로 자동 갱신 | |
| JSON | `JSONB` | 재구성 캐시. 엔티티에 `@JdbcTypeCode(SqlTypes.JSON)` |
| 해시 | `VARCHAR(64)` | `CHAR`는 JPA 검사에서 타입 불일치가 나기 쉬워서 |

### 기본 키 접두어

| 테이블 | 접두어 | 테이블 | 접두어 |
| --- | --- | --- | --- |
| `tool_category` | `TOOLCT` | `recipe_ingredient` | `RINGR` |
| `tool_variant` | `TOOL` | `recipe_step` | `RSTEP` |
| `fill_level` | `FILL` | `adaptation_rule` | `ARULE` |
| `ingredient` | `INGR` | `tool_substitution_rule` | `TRULE` |
| `ingredient_alias` | `ALIAS` | `safety_rule` | `SRULE` |
| `allergen` | `ALLGN` | `recipe_import_job` | `IMPORT` |
| `weight_reference` | `WREF` | `adapted_recipe` | `ADAPT` |
| `recipe` | `RECIPE` | `ai_call_log` | `AILOG` |
| `image_group` | `IMGGRP` | `image_file` | `IMAGE` |

- 숫자 자릿수는 `20 - 접두어 길이 - 1`이다(`RECIPE`는 13자리, `TOOL`은 15자리).
- 연결 테이블(`ingredient_allergen`, `recipe_step_ingredient`, `recipe_step_tool`)은 자체 ID 없이 두 외래 키의 조합이 기본 키다.
- 시퀀스라서 번호가 중간에 빌 수 있다. 서버가 시작할 때마다 다시 쓰는 테이블(규칙, 별칭)은 번호가 계속 올라간다.
- 새 테이블을 추가할 때는 `CREATE SEQUENCE` → `id CHAR(20) PRIMARY KEY DEFAULT make_id('접두어', '시퀀스')` + 형식 `CHECK`를 같은 방식으로 쓴다.

## 7. 설계 원칙 (CLAUDE.md 규칙과 연결)

DB가 직접 막는 것 (실제로 시험해서 확인함):
- "약간" 재료(`unit_kind = VAGUE`)에 숫자를 넣을 수 없다 (규칙 1)
- 웹 레시피는 출처 URL 없이 저장할 수 없고, 같은 페이지는 두 번 저장되지 않는다 (규칙 24)
- 단계 시간은 0이나 음수일 수 없다. 원문에 없으면 NULL (규칙 9)
- 불 세기는 정해진 6개 값만 (규칙 10)
- 대체 규칙은 대체 재료와 검증자가 반드시 있어야 한다 (규칙 7)
- 민감도는 `HIGH`/`RATIO`/`NORMAL`, 재료 형태는 `LIQUID`/`POWDER`/`PASTE`/`SOLID`, 알레르기 검토 등급은 `HUMAN_VERIFIED`/`AI_RESEARCHED`만
- 숟가락 양 비율은 0 초과 1 이하

코드에서 지킬 것:
- **알레르기 사용자 정보는 DB에 저장하지 않는다.** `adapted_recipe`의 조건 해시·JSON에도 넣지 않고, 경고는 응답할 때마다 매핑으로 계산한다 (규칙 14)
- 웹 레시피는 재료 원문(`raw_text`)만 보존하고 단계는 다시 쓴 문장만 저장한다 (규칙 8, 24)
- AI 호출 기록은 횟수·토큰·실패만, 내용은 저장하지 않는다

## 8. 서버 시작 시 하는 일과 남은 일

서버가 시작하면 Flyway 다음에 `SeedSync`가 `data/seed`와 `data/fixtures`를 DB에 반영한다. 파일이 원본이고, 잘못된 코드가 있으면 서버가 시작하지 않는다.

| 파일 | 테이블 | 방식 |
| --- | --- | --- |
| `assets/source/images/manifest.json` | `image_group`, `image_file` | 키 기준으로 추가·갱신 |
| `data/seed/tools.json` | `tool_variant`의 용량·컵 치수·출처 등급 | 갱신. 컵 용량이 치수 계산값과 5% 넘게 다르면 시작 실패 |
| `data/seed/ingredients.json` | `ingredient`, `ingredient_alias` | `code` 기준으로 추가·갱신(분류 `category`, 형태 `form`, 민감도). 별칭은 매번 다시 씀 |
| `data/seed/allergen-mapping.json` | `ingredient_allergen`, `ingredient`의 검토 등급 칸 | 매번 다시 씀. `verifiedBy`가 있으면 `HUMAN_VERIFIED`, `reviewLevel`이 `AI_RESEARCHED`이면 그 등급, 아니면 등급 없음 |
| `data/seed/weight-references.json` | `weight_reference` | 매번 다시 씀. 재료를 도구에 가득 담은 무게(밥그릇 1번 = 200g) |
| `data/seed/safety-rules.json` | `safety_rule` | 매번 다시 씀. `verifiedBy`가 없으면 건너뜀 |
| `data/seed/adaptation-rules.json` | `adaptation_rule`, `tool_substitution_rule` | 매번 다시 씀. `verifiedBy`가 없으면 건너뜀 |
| `data/fixtures/recipes/*.json` | `recipe` 외 4개 (`source = FIXTURE`) | 없을 때만 추가. 고친 파일을 다시 넣으려면 해당 `recipe` 행을 지우고 재시작 |

`data/seed/units.json`(큰술 15mL 같은 원문 단위 해석 기준, 허용 오차)과 `data/seed/action-tools.json`(행동으로 필요한 도구를 정하는 규칙)은 DB에 넣지 않고 서버가 직접 읽는다.

seed 동기화가 끝나면, 저장된 레시피의 재료 중 사전에 연결되지 않았던 것을 이름·별칭이 정확히 같은 경우에만 다시 연결한다.

서버가 실행 중에 쓰는 테이블: `recipe_import_job`(웹에서 가져오기 작업과 탈락 사유), `ai_call_log`(GPT 호출 기록, 하루 한도 계산), `recipe` 외 4개(가져온 레시피). 아직 쓰지 않는 테이블: `adapted_recipe`(재구성 결과 캐시, 로드맵 P1-11).

**기준 데이터는 사람이 검증하지 않은 상태다.** `tools.json`의 값은 `ASSUMED`(임시값), 규칙 파일의 `verifiedBy`가 `DRAFT_UNVERIFIED`인 항목은 초안, 알레르기 매핑은 `AI_RESEARCHED`(AI가 공개 자료로 조사)다. 출시 전에 검증하고 검증자 이름과 날짜로 바꾼다(`docs/allergen-review.md`, 로드맵 P2-12).

## 9. 2차에서 추가할 것

회원, 프로필, 비밀번호 재설정 토큰, 게스트 사용 횟수 테이블은 2차에서 추가한다. 한 번 실행된 마이그레이션 파일(V1~V7)은 **수정하지 않고** 다음 번호의 새 파일로 바꾼다.
