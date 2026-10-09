-- =====================================================================
-- V1: 1차(포트폴리오) 스키마 — PostgreSQL 16+
--  - 로그인/회원 테이블 없음 (2차에서 V10 이후로 추가)
--  - 알레르기 '사용자 정보'는 서버에 저장하지 않음 (CLAUDE.md 규칙 14)
--    → allergen / ingredient_allergen 은 '재료→알레르기 매핑'(기준 데이터)일 뿐
--  - 재료·도구는 문자열이 아닌 code/ID로 식별 (규칙 27)
--  - 열거형은 PostgreSQL ENUM 대신 VARCHAR + CHECK 로 둔다 (값 추가가 쉬움)
--  - 기본 키(PK)는 CHAR(20) 문자열: 접두어(영문 대문자 6자 이내) + '_' + 0으로 채운 순번
--    전체 길이는 항상 20자. 예: RECIPE_0000000000001, TOOL_000000000000001
--    테이블마다 시퀀스를 두고 make_id() 가 기본값으로 만든다. 외래 키(FK)도 CHAR(20).
--  - 모든 테이블에 등록일시·등록자·수정일시·수정자(created_at/by, updated_at/by)를 둔다
--  - 모든 컬럼에 한글 설명(COMMENT)을 단다. 컬럼을 추가하면 설명도 함께 추가한다
-- =====================================================================

-- PK 생성: 접두어 + '_' + 순번(전체 20자가 되도록 0으로 채움)
CREATE OR REPLACE FUNCTION make_id(prefix TEXT, seq REGCLASS) RETURNS CHAR(20) AS $$
DECLARE
    digits INT := 20 - length(prefix) - 1;
    num    TEXT := nextval(seq)::TEXT;
BEGIN
    -- lpad 는 길면 잘라 버리므로, 자릿수를 넘으면 잘못된 ID 대신 오류를 낸다
    IF length(num) > digits THEN
        RAISE EXCEPTION 'ID sequence for prefix % is exhausted', prefix;
    END IF;
    RETURN prefix || '_' || lpad(num, digits, '0');
END;
$$ LANGUAGE plpgsql;

-- updated_at 자동 갱신용 함수
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- 1. 도구
-- ---------------------------------------------------------------------
CREATE SEQUENCE tool_category_id_seq;
CREATE TABLE tool_category (
    id CHAR(20) PRIMARY KEY DEFAULT make_id('TOOLCT', 'tool_category_id_seq')
       CONSTRAINT ck_tool_category_id CHECK (id ~ '^TOOLCT_[0-9]+$' AND length(id) = 20),
    code        VARCHAR(32)  NOT NULL UNIQUE,   -- measure / cut / heat_source / vessel
    name_ko     VARCHAR(32)  NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
COMMENT ON TABLE tool_category IS '도구 분류';

CREATE SEQUENCE tool_variant_id_seq;
CREATE TABLE tool_variant (
    id CHAR(20) PRIMARY KEY DEFAULT make_id('TOOL', 'tool_variant_id_seq')
       CONSTRAINT ck_tool_variant_id CHECK (id ~ '^TOOL_[0-9]+$' AND length(id) = 20),
    code                      VARCHAR(64)   NOT NULL UNIQUE,   -- spoon_rice, paper_cup_small ...
    category_id               CHAR(20)      NOT NULL REFERENCES tool_category (id),
    name_ko                   VARCHAR(64)   NOT NULL,
    sub_label_ko              VARCHAR(64),                     -- 자판기 컵
    icon_key                  VARCHAR(96)   NOT NULL,          -- manifest 키: tool.spoon_rice
    heat_source_type          VARCHAR(16)   CHECK (heat_source_type IN ('GAS','INDUCTION','MICROWAVE','AIR_FRYER','RICE_COOKER')),
    -- 계량 기준값 (원본은 data/seed/tools.json, 서버 시작 시 동기화)
    representative_volume_ml  NUMERIC(7,2)  CHECK (representative_volume_ml IS NULL OR representative_volume_ml > 0),
    is_approximate            BOOLEAN       NOT NULL DEFAULT TRUE,
    top_diameter_mm           NUMERIC(6,2),                    -- 컵 눈금 계산용 (spec §4.7)
    bottom_diameter_mm        NUMERIC(6,2),
    height_mm                 NUMERIC(6,2),
    fill_line_from_top_mm     NUMERIC(6,2),                    -- 가득 기준선(입구에서 아래로)
    value_source              VARCHAR(16)   CHECK (value_source IN ('MEASURED','PUBLISHED','ASSUMED')),
    source_ref                VARCHAR(512),
    sort_order                INT           NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
CREATE INDEX idx_tool_variant_category ON tool_variant (category_id);
COMMENT ON TABLE tool_variant IS '사용자가 고르는 도구 선택지';

CREATE SEQUENCE fill_level_id_seq;
CREATE TABLE fill_level (
    id CHAR(20) PRIMARY KEY DEFAULT make_id('FILL', 'fill_level_id_seq')
       CONSTRAINT ck_fill_level_id CHECK (id ~ '^FILL_[0-9]+$' AND length(id) = 20),
    tool_variant_id  CHAR(20)      NOT NULL REFERENCES tool_variant (id),
    code             VARCHAR(32)   NOT NULL,        -- powder.1_2, liquid.full, powder.heaped
    content_type     VARCHAR(8)    NOT NULL CHECK (content_type IN ('POWDER','LIQUID')),
    fraction         NUMERIC(5,4)  NOT NULL CHECK (fraction > 0 AND fraction <= 1),
    is_heaped        BOOLEAN       NOT NULL DEFAULT FALSE,
    image_key        VARCHAR(96)   NOT NULL,        -- fill.spoon_rice.powder.1_2
    value_source     VARCHAR(16)   NOT NULL DEFAULT 'ASSUMED' CHECK (value_source IN ('MEASURED','PUBLISHED','ASSUMED')),
    UNIQUE (tool_variant_id, code),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
COMMENT ON TABLE fill_level IS '숟가락 양 그림 기준 (컵 눈금은 코드 계산이라 여기 없음)';

-- ---------------------------------------------------------------------
-- 2. 재료 사전 · 알레르기 매핑 · 무게 기준
-- ---------------------------------------------------------------------
CREATE SEQUENCE ingredient_id_seq;
CREATE TABLE ingredient (
    id CHAR(20) PRIMARY KEY DEFAULT make_id('INGR', 'ingredient_id_seq')
       CONSTRAINT ck_ingredient_id CHECK (id ~ '^INGR_[0-9]+$' AND length(id) = 20),
    code                 VARCHAR(64)  NOT NULL UNIQUE,    -- soy_sauce, green_onion
    name_ko              VARCHAR(64)  NOT NULL UNIQUE,    -- 간장
    category             VARCHAR(32),                     -- 양념/채소/육류
    default_sensitivity  VARCHAR(8)   NOT NULL DEFAULT 'NORMAL' CHECK (default_sensitivity IN ('HIGH','NORMAL')),
    is_garnish           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
COMMENT ON TABLE ingredient IS '재료 사전';

CREATE SEQUENCE ingredient_alias_id_seq;
CREATE TABLE ingredient_alias (
    id CHAR(20) PRIMARY KEY DEFAULT make_id('ALIAS', 'ingredient_alias_id_seq')
       CONSTRAINT ck_ingredient_alias_id CHECK (id ~ '^ALIAS_[0-9]+$' AND length(id) = 20),
    ingredient_id  CHAR(20)     NOT NULL REFERENCES ingredient (id) ON DELETE CASCADE,
    alias          VARCHAR(64)  NOT NULL UNIQUE,   -- 진간장, 양조간장 → 간장 (파/대파/쪽파는 별도 재료)
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
CREATE INDEX idx_ingredient_alias_ingredient ON ingredient_alias (ingredient_id);
COMMENT ON TABLE ingredient_alias IS '재료 이름 정규화';

CREATE SEQUENCE allergen_id_seq;
CREATE TABLE allergen (
    id CHAR(20) PRIMARY KEY DEFAULT make_id('ALLGN', 'allergen_id_seq')
       CONSTRAINT ck_allergen_id CHECK (id ~ '^ALLGN_[0-9]+$' AND length(id) = 20),
    code        VARCHAR(32)  NOT NULL UNIQUE,   -- egg, soybean, wheat
    name_ko     VARCHAR(32)  NOT NULL,          -- 난류
    group_ko    VARCHAR(32)  NOT NULL,          -- 알·유제품
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
COMMENT ON TABLE allergen IS '알레르기 항목 (기준 데이터)';

CREATE TABLE ingredient_allergen (
    ingredient_id  CHAR(20)     NOT NULL REFERENCES ingredient (id) ON DELETE CASCADE,
    allergen_id    CHAR(20)     NOT NULL REFERENCES allergen (id),
    is_hidden      BOOLEAN      NOT NULL DEFAULT FALSE,   -- 숨은 성분: 간장→대두·밀
    verified_by    VARCHAR(64),
    verified_at    TIMESTAMPTZ,
    PRIMARY KEY (ingredient_id, allergen_id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
CREATE INDEX idx_ingredient_allergen_allergen ON ingredient_allergen (allergen_id);
COMMENT ON TABLE ingredient_allergen IS '재료→알레르기 매핑 (사람이 검증, 규칙 13)';

CREATE SEQUENCE weight_reference_id_seq;
CREATE TABLE weight_reference (
    id CHAR(20) PRIMARY KEY DEFAULT make_id('WREF', 'weight_reference_id_seq')
       CONSTRAINT ck_weight_reference_id CHECK (id ~ '^WREF_[0-9]+$' AND length(id) = 20),
    ingredient_id  CHAR(20)      NOT NULL REFERENCES ingredient (id) ON DELETE CASCADE,
    tool_variant_id CHAR(20)     REFERENCES tool_variant (id),   -- 도구에 담은 무게일 때: 밥그릇 1번
    unit_label_ko  VARCHAR(32)   NOT NULL,          -- 1개 / 1모 / 밥그릇 1번
    approx_grams   NUMERIC(8,2)  NOT NULL CHECK (approx_grams > 0),
    size_note_ko   VARCHAR(64),                     -- 중간 크기
    value_source   VARCHAR(16)   NOT NULL CHECK (value_source IN ('MEASURED','PUBLISHED','ASSUMED')),
    source_ref     VARCHAR(512),
    UNIQUE (ingredient_id, unit_label_ko),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
COMMENT ON TABLE weight_reference IS '무게 환산 기준: 개수(1개 = 100g) 또는 도구(밥그릇 1번 = 200g) (spec §4.6)';

-- ---------------------------------------------------------------------
-- 3. 레시피 (웹 검색으로 가져온 것 + 개발용 fixture)
-- ---------------------------------------------------------------------
CREATE SEQUENCE recipe_id_seq;
CREATE TABLE recipe (
    id CHAR(20) PRIMARY KEY DEFAULT make_id('RECIPE', 'recipe_id_seq')
       CONSTRAINT ck_recipe_id CHECK (id ~ '^RECIPE_[0-9]+$' AND length(id) = 20),
    name                 VARCHAR(128)   NOT NULL,
    normalized_name      VARCHAR(128)   NOT NULL,   -- 검색용: 공백 제거·소문자
    source               VARCHAR(16)    NOT NULL CHECK (source IN ('WEB_SEARCH','FOOD_SAFETY_KR','CURATED','FIXTURE')),
    source_url           VARCHAR(2048),
    source_url_hash      VARCHAR(64)    UNIQUE,     -- SHA-256(source_url): 같은 페이지 중복 가져오기 방지
    source_domain        VARCHAR(255),              -- 화면 표시: 10000recipe.com
    source_ref           VARCHAR(128),              -- 공공 API 일련번호 등
    base_servings        NUMERIC(4,1)   NOT NULL DEFAULT 1.0,
    total_minutes        INT,
    verification_status  VARCHAR(16)    NOT NULL DEFAULT 'IMPORTED'
                         CHECK (verification_status IN ('UNVERIFIED','IMPORTED','DATA_CHECKED','COOK_TESTED')),
    CONSTRAINT ck_recipe_web_has_url CHECK (source <> 'WEB_SEARCH' OR source_url IS NOT NULL),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
CREATE INDEX idx_recipe_normalized_name ON recipe (normalized_name);
COMMENT ON TABLE recipe IS '레시피. WEB_SEARCH는 원문 문장·사진을 저장하지 않는다 (규칙 24)';

CREATE SEQUENCE recipe_ingredient_id_seq;
CREATE TABLE recipe_ingredient (
    id CHAR(20) PRIMARY KEY DEFAULT make_id('RINGR', 'recipe_ingredient_id_seq')
       CONSTRAINT ck_recipe_ingredient_id CHECK (id ~ '^RINGR_[0-9]+$' AND length(id) = 20),
    recipe_id      CHAR(20)       NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
    ingredient_id  CHAR(20)       REFERENCES ingredient (id),   -- 사전 매칭 실패 시 NULL → 알레르기 "확인 필요"
    section_ko     VARCHAR(64),                                 -- 소제목: 양념장
    name_ko        VARCHAR(64)    NOT NULL,                     -- 레시피에 적힌 이름
    quantity       NUMERIC(10,3),                               -- 약간·적당량이면 NULL
    unit           VARCHAR(16),                                 -- ml, g, 큰술, 작은술, 컵, 개
    alt_quantity   NUMERIC(10,3),                               -- 괄호 환산값: 2g(1/3작은술) → 0.333
    alt_unit       VARCHAR(16),
    unit_kind      VARCHAR(8)     NOT NULL CHECK (unit_kind IN ('VOLUME','WEIGHT','COUNT','VAGUE')),
    raw_text       VARCHAR(255)   NOT NULL,                     -- 원본 값 보존 (규칙 8): "간장 1큰술"
    is_required    BOOLEAN        NOT NULL DEFAULT TRUE,
    sort_order     INT            NOT NULL DEFAULT 0,
    CONSTRAINT ck_ri_vague CHECK (unit_kind <> 'VAGUE' OR quantity IS NULL),   -- "약간"은 숫자를 가질 수 없음 (규칙 1)
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
CREATE INDEX idx_recipe_ingredient_recipe ON recipe_ingredient (recipe_id);
CREATE INDEX idx_recipe_ingredient_ingredient ON recipe_ingredient (ingredient_id);
COMMENT ON TABLE recipe_ingredient IS '레시피 재료';

CREATE SEQUENCE recipe_step_id_seq;
CREATE TABLE recipe_step (
    id CHAR(20) PRIMARY KEY DEFAULT make_id('RSTEP', 'recipe_step_id_seq')
       CONSTRAINT ck_recipe_step_id CHECK (id ~ '^RSTEP_[0-9]+$' AND length(id) = 20),
    recipe_id              CHAR(20)      NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
    step_order             INT           NOT NULL,
    action_ko              VARCHAR(255)  NOT NULL,   -- 앱 형식으로 다시 쓴 한 행동
    action_key             VARCHAR(96),              -- 행동 그림 키: action.stir_fry
    duration_seconds       INT           CHECK (duration_seconds IS NULL OR duration_seconds > 0),   -- 원문에 시간이 있을 때만 (규칙 9)
    timer_start_condition  VARCHAR(128),             -- 볶기 시작할 때
    heat_level             VARCHAR(12)   CHECK (heat_level IN ('OFF','LOW','MEDIUM_LOW','MEDIUM','MEDIUM_HIGH','HIGH')),   -- 불을 안 쓰면 NULL
    heat_raw               VARCHAR(32),              -- 원문 표현: 중약불
    completion_check       VARCHAR(255),             -- 김치 숨이 죽으면
    UNIQUE (recipe_id, step_order),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
COMMENT ON TABLE recipe_step IS '레시피 단계';

CREATE TABLE recipe_step_ingredient (
    step_id               CHAR(20) NOT NULL REFERENCES recipe_step (id) ON DELETE CASCADE,
    recipe_ingredient_id  CHAR(20) NOT NULL REFERENCES recipe_ingredient (id) ON DELETE CASCADE,
    PRIMARY KEY (step_id, recipe_ingredient_id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
COMMENT ON TABLE recipe_step_ingredient IS '단계에 쓰이는 재료 (알레르기 단계 경고용)';

CREATE TABLE recipe_step_tool (
    step_id          CHAR(20) NOT NULL REFERENCES recipe_step (id) ON DELETE CASCADE,
    tool_variant_id  CHAR(20) NOT NULL REFERENCES tool_variant (id),
    PRIMARY KEY (step_id, tool_variant_id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
COMMENT ON TABLE recipe_step_tool IS '단계에 필요한 도구';

-- ---------------------------------------------------------------------
-- 4. 검증된 규칙 (사람이 작성, 규칙 7·11·18)
-- ---------------------------------------------------------------------
CREATE SEQUENCE adaptation_rule_id_seq;
CREATE TABLE adaptation_rule (
    id CHAR(20) PRIMARY KEY DEFAULT make_id('ARULE', 'adaptation_rule_id_seq')
       CONSTRAINT ck_adaptation_rule_id CHECK (id ~ '^ARULE_[0-9]+$' AND length(id) = 20),
    type                      VARCHAR(16)   NOT NULL CHECK (type IN ('OMIT','SUBSTITUTE','METHOD_CHANGE','SERVING_SCALE')),
    ingredient_id             CHAR(20)      NOT NULL REFERENCES ingredient (id),
    substitute_ingredient_id  CHAR(20)      REFERENCES ingredient (id),
    ratio                     NUMERIC(6,3),
    impact_note_ko            VARCHAR(255),           -- 향이 조금 약해져요
    applies_to_recipe_id      CHAR(20)      REFERENCES recipe (id) ON DELETE CASCADE,   -- NULL이면 공통
    priority                  INT           NOT NULL DEFAULT 0,
    verified_by               VARCHAR(64)   NOT NULL,
    verified_at               TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_ar_substitute CHECK (type <> 'SUBSTITUTE' OR substitute_ingredient_id IS NOT NULL),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
CREATE INDEX idx_adaptation_rule_ingredient ON adaptation_rule (ingredient_id);
COMMENT ON TABLE adaptation_rule IS '생략·대체 규칙';

CREATE SEQUENCE tool_substitution_rule_id_seq;
CREATE TABLE tool_substitution_rule (
    id CHAR(20) PRIMARY KEY DEFAULT make_id('TRULE', 'tool_substitution_rule_id_seq')
       CONSTRAINT ck_tool_substitution_rule_id CHECK (id ~ '^TRULE_[0-9]+$' AND length(id) = 20),
    required_tool_id      CHAR(20)      NOT NULL REFERENCES tool_variant (id),
    substitute_tool_id    CHAR(20)      NOT NULL REFERENCES tool_variant (id),
    condition_note_ko     VARCHAR(255),           -- 부드러운 재료만
    applies_to_recipe_id  CHAR(20)      REFERENCES recipe (id) ON DELETE CASCADE,
    extra_safety_note_ko  VARCHAR(255),
    verified_by           VARCHAR(64)   NOT NULL,
    verified_at           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_tsr_different CHECK (required_tool_id <> substitute_tool_id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
CREATE INDEX idx_tsr_required ON tool_substitution_rule (required_tool_id);
COMMENT ON TABLE tool_substitution_rule IS '도구 대체 규칙: 칼→가위';

CREATE SEQUENCE safety_rule_id_seq;
CREATE TABLE safety_rule (
    id CHAR(20) PRIMARY KEY DEFAULT make_id('SRULE', 'safety_rule_id_seq')
       CONSTRAINT ck_safety_rule_id CHECK (id ~ '^SRULE_[0-9]+$' AND length(id) = 20),
    code           VARCHAR(64)   NOT NULL UNIQUE,   -- hot_oil_splash, pork_cook_through
    trigger_type   VARCHAR(16)   NOT NULL CHECK (trigger_type IN ('INGREDIENT','ACTION','TOOL','HEAT')),
    trigger_value  VARCHAR(64)   NOT NULL,          -- ingredient.code / action 키 / tool.code / heat_level
    message_ko     VARCHAR(255)  NOT NULL,          -- 모든 실력 수준에 표시
    detail_low_ko  VARCHAR(512),                    -- "처음 해봐요"에 추가로 보여줄 이유·확인 방법
    placement      VARCHAR(8)    NOT NULL DEFAULT 'BOTH' CHECK (placement IN ('STEP','SUMMARY','BOTH')),
    verified_by    VARCHAR(64)   NOT NULL,
    verified_at    TIMESTAMPTZ   NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
CREATE INDEX idx_safety_rule_trigger ON safety_rule (trigger_type, trigger_value);
COMMENT ON TABLE safety_rule IS '안전 주의사항 (규칙 11)';

-- ---------------------------------------------------------------------
-- 5. 웹 검색 가져오기 작업 · 재구성 결과 캐시 · AI 호출 기록
-- ---------------------------------------------------------------------
CREATE SEQUENCE recipe_import_job_id_seq;
CREATE TABLE recipe_import_job (
    id CHAR(20) PRIMARY KEY DEFAULT make_id('IMPORT', 'recipe_import_job_id_seq')
       CONSTRAINT ck_recipe_import_job_id CHECK (id ~ '^IMPORT_[0-9]+$' AND length(id) = 20),
    query_text        VARCHAR(128)  NOT NULL,
    normalized_query  VARCHAR(128)  NOT NULL,
    status            VARCHAR(16)   NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING','SEARCHING','EXTRACTING','VERIFYING','DONE','NOT_FOUND','FAILED')),
    recipe_id         CHAR(20)      REFERENCES recipe (id) ON DELETE SET NULL,
    error_code        VARCHAR(64),                  -- 사용자 입력·원문은 넣지 않음
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
CREATE INDEX idx_import_job_query ON recipe_import_job (normalized_query, status);
COMMENT ON TABLE recipe_import_job IS '요리명 검색 → 웹 검색 비동기 작업';

CREATE SEQUENCE adapted_recipe_id_seq;
CREATE TABLE adapted_recipe (
    id CHAR(20) PRIMARY KEY DEFAULT make_id('ADAPT', 'adapted_recipe_id_seq')
       CONSTRAINT ck_adapted_recipe_id CHECK (id ~ '^ADAPT_[0-9]+$' AND length(id) = 20),
    recipe_id        CHAR(20)     NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
    conditions_hash  VARCHAR(64)  NOT NULL,   -- SHA-256(실력+도구+없는 재료+대체 응답). 알레르기는 포함하지 않음
    conditions_json  JSONB        NOT NULL,   -- 알레르기 정보 저장 금지 (규칙 14)
    status           VARCHAR(20)  NOT NULL CHECK (status IN ('COOKABLE','NEEDS_CONFIRMATION','NOT_COOKABLE')),
    payload          JSONB        NOT NULL,   -- 알레르기 경고를 뺀 응답 본문. 경고는 응답 시점에 매핑으로 계산
    ai_model         VARCHAR(64),
    UNIQUE (recipe_id, conditions_hash),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
COMMENT ON TABLE adapted_recipe IS '재구성 결과 캐시 (같은 조건이면 GPT 재호출 안 함)';

CREATE SEQUENCE ai_call_log_id_seq;
CREATE TABLE ai_call_log (
    id CHAR(20) PRIMARY KEY DEFAULT make_id('AILOG', 'ai_call_log_id_seq')
       CONSTRAINT ck_ai_call_log_id CHECK (id ~ '^AILOG_[0-9]+$' AND length(id) = 20),
    purpose        VARCHAR(16)  NOT NULL CHECK (purpose IN ('WEB_SEARCH','EXTRACT','ADAPT','QUESTION')),
    model          VARCHAR(64)  NOT NULL,
    input_tokens   INT,
    output_tokens  INT,
    latency_ms     INT,
    success        BOOLEAN      NOT NULL,
    error_code     VARCHAR(64),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
CREATE INDEX idx_ai_call_log_created ON ai_call_log (created_at);
CREATE INDEX idx_ai_call_log_purpose ON ai_call_log (purpose, created_at);
COMMENT ON TABLE ai_call_log IS 'AI 비용·실패율 추적. 프롬프트·응답 본문은 저장하지 않음';

-- ---------------------------------------------------------------------
-- 6. 이미지 (묶음 + 파일, 2개 테이블)
--    원본과 목록은 assets/source/images/manifest.json 이고 서버 시작 시 동기화한다.
--    다른 테이블의 그림 키(icon_key, image_key, action_key)는 image_file.image_key 를 가리킨다.
-- ---------------------------------------------------------------------
CREATE SEQUENCE image_group_id_seq;
CREATE TABLE image_group (
    id CHAR(20) PRIMARY KEY DEFAULT make_id('IMGGRP', 'image_group_id_seq')
       CONSTRAINT ck_image_group_id CHECK (id ~ '^IMGGRP_[0-9]+$' AND length(id) = 20),
    code            VARCHAR(32)  NOT NULL UNIQUE,   -- tool / heat.gas / heat.induction / illust / fill / amount / action / app
    name_ko         VARCHAR(64)  NOT NULL,
    output_dir      VARCHAR(64),                    -- 앱 자산 폴더: tools, heat, illustrations
    target_size_px  INT          CHECK (target_size_px IS NULL OR target_size_px > 0),
    sort_order      INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
COMMENT ON TABLE image_group IS '이미지 묶음 (용도별 분류와 변환 규칙)';

CREATE SEQUENCE image_file_id_seq;
CREATE TABLE image_file (
    id CHAR(20) PRIMARY KEY DEFAULT make_id('IMAGE', 'image_file_id_seq')
       CONSTRAINT ck_image_file_id CHECK (id ~ '^IMAGE_[0-9]+$' AND length(id) = 20),
    image_group_id      CHAR(20)      NOT NULL REFERENCES image_group (id),
    image_key           VARCHAR(96)   NOT NULL UNIQUE,   -- tool.spoon_rice, heat.gas.LOW, fill.spoon_rice.liquid.full
    status              VARCHAR(8)    NOT NULL CHECK (status IN ('READY','MISSING')),
    original_file_name  VARCHAR(255),                    -- 원본: teaspoon.png
    stored_file_name    VARCHAR(255),                    -- 변환본: teaspoon.png
    file_path           VARCHAR(512),                    -- 앱 자산 기준 경로: tools/teaspoon.png
    file_ext            VARCHAR(8),
    file_size_bytes     INT           CHECK (file_size_bytes IS NULL OR file_size_bytes > 0),
    width_px            INT           CHECK (width_px IS NULL OR width_px > 0),
    height_px           INT           CHECK (height_px IS NULL OR height_px > 0),
    sort_order          INT           NOT NULL DEFAULT 0,
    CONSTRAINT ck_image_file_ready CHECK (status <> 'READY' OR (stored_file_name IS NOT NULL AND file_path IS NOT NULL)),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM'
);
CREATE INDEX idx_image_file_group ON image_file (image_group_id);
COMMENT ON TABLE image_file IS '이미지 파일 (그림 키 하나에 파일 하나. MISSING은 아직 그림이 없는 키)';

-- ---------------------------------------------------------------------
-- 7. 공통: 시퀀스 소유, 수정일시 자동 갱신, 컬럼 설명
-- ---------------------------------------------------------------------
-- 시퀀스를 해당 PK 열에 묶는다 (테이블을 지우면 함께 지워짐)
ALTER SEQUENCE tool_category_id_seq OWNED BY tool_category.id;
ALTER SEQUENCE tool_variant_id_seq OWNED BY tool_variant.id;
ALTER SEQUENCE fill_level_id_seq OWNED BY fill_level.id;
ALTER SEQUENCE ingredient_id_seq OWNED BY ingredient.id;
ALTER SEQUENCE ingredient_alias_id_seq OWNED BY ingredient_alias.id;
ALTER SEQUENCE allergen_id_seq OWNED BY allergen.id;
ALTER SEQUENCE weight_reference_id_seq OWNED BY weight_reference.id;
ALTER SEQUENCE recipe_id_seq OWNED BY recipe.id;
ALTER SEQUENCE recipe_ingredient_id_seq OWNED BY recipe_ingredient.id;
ALTER SEQUENCE recipe_step_id_seq OWNED BY recipe_step.id;
ALTER SEQUENCE adaptation_rule_id_seq OWNED BY adaptation_rule.id;
ALTER SEQUENCE tool_substitution_rule_id_seq OWNED BY tool_substitution_rule.id;
ALTER SEQUENCE safety_rule_id_seq OWNED BY safety_rule.id;
ALTER SEQUENCE recipe_import_job_id_seq OWNED BY recipe_import_job.id;
ALTER SEQUENCE adapted_recipe_id_seq OWNED BY adapted_recipe.id;
ALTER SEQUENCE ai_call_log_id_seq OWNED BY ai_call_log.id;
ALTER SEQUENCE image_group_id_seq OWNED BY image_group.id;
ALTER SEQUENCE image_file_id_seq OWNED BY image_file.id;

-- 행을 수정하면 updated_at 을 자동으로 갱신한다 (updated_by 는 수정하는 쪽에서 넣는다)
CREATE TRIGGER trg_tool_category_updated_at BEFORE UPDATE ON tool_category FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_tool_variant_updated_at BEFORE UPDATE ON tool_variant FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_fill_level_updated_at BEFORE UPDATE ON fill_level FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_ingredient_updated_at BEFORE UPDATE ON ingredient FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_ingredient_alias_updated_at BEFORE UPDATE ON ingredient_alias FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_allergen_updated_at BEFORE UPDATE ON allergen FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_ingredient_allergen_updated_at BEFORE UPDATE ON ingredient_allergen FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_weight_reference_updated_at BEFORE UPDATE ON weight_reference FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_recipe_updated_at BEFORE UPDATE ON recipe FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_recipe_ingredient_updated_at BEFORE UPDATE ON recipe_ingredient FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_recipe_step_updated_at BEFORE UPDATE ON recipe_step FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_recipe_step_ingredient_updated_at BEFORE UPDATE ON recipe_step_ingredient FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_recipe_step_tool_updated_at BEFORE UPDATE ON recipe_step_tool FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_adaptation_rule_updated_at BEFORE UPDATE ON adaptation_rule FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_tool_substitution_rule_updated_at BEFORE UPDATE ON tool_substitution_rule FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_safety_rule_updated_at BEFORE UPDATE ON safety_rule FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_recipe_import_job_updated_at BEFORE UPDATE ON recipe_import_job FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_adapted_recipe_updated_at BEFORE UPDATE ON adapted_recipe FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_ai_call_log_updated_at BEFORE UPDATE ON ai_call_log FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_image_group_updated_at BEFORE UPDATE ON image_group FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_image_file_updated_at BEFORE UPDATE ON image_file FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 컬럼 설명 (DB 도구의 Comment 열에 표시됨)
-- tool_category
COMMENT ON COLUMN tool_category.id IS '기본 키 (접두어_순번, 전체 20자)';
COMMENT ON COLUMN tool_category.code IS '분류 코드 (measure 계량 / cut 자르기 / heat_source 가열 기기 / vessel 조리 용기)';
COMMENT ON COLUMN tool_category.name_ko IS '분류 이름';
COMMENT ON COLUMN tool_category.sort_order IS '표시 순서 (작을수록 먼저)';
COMMENT ON COLUMN tool_category.created_at IS '등록일시';
COMMENT ON COLUMN tool_category.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN tool_category.updated_at IS '수정일시';
COMMENT ON COLUMN tool_category.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- tool_variant
COMMENT ON COLUMN tool_variant.id IS '기본 키 (접두어_순번, 전체 20자)';
COMMENT ON COLUMN tool_variant.code IS '도구 코드 (spoon_rice, teaspoon, paper_cup_small, knife 등). 앱과 API가 쓰는 도구 ID';
COMMENT ON COLUMN tool_variant.category_id IS '도구 분류 ID (tool_category.id)';
COMMENT ON COLUMN tool_variant.name_ko IS '도구 이름 (밥숟가락)';
COMMENT ON COLUMN tool_variant.sub_label_ko IS '보조 설명 (자판기 컵, 테이크아웃 컵)';
COMMENT ON COLUMN tool_variant.icon_key IS '아이콘 그림 키 (image_file.image_key, 예: tool.spoon_rice)';
COMMENT ON COLUMN tool_variant.heat_source_type IS '가열 기기 종류 (GAS / INDUCTION / MICROWAVE / AIR_FRYER / RICE_COOKER). 가열 기기가 아니면 NULL';
COMMENT ON COLUMN tool_variant.representative_volume_ml IS '대표 용량(mL). 컵은 가득 기준선까지의 용량. 계량 도구가 아니면 NULL';
COMMENT ON COLUMN tool_variant.is_approximate IS '대략적인 값 여부 (TRUE면 화면에 근사값임을 표시)';
COMMENT ON COLUMN tool_variant.top_diameter_mm IS '컵 윗지름(mm). 컵 눈금 계산용';
COMMENT ON COLUMN tool_variant.bottom_diameter_mm IS '컵 아랫지름(mm). 컵 눈금 계산용';
COMMENT ON COLUMN tool_variant.height_mm IS '컵 높이(mm). 컵 눈금 계산용';
COMMENT ON COLUMN tool_variant.fill_line_from_top_mm IS '가득 기준선 위치 (입구에서 아래로 몇 mm)';
COMMENT ON COLUMN tool_variant.value_source IS '값의 출처 등급 (MEASURED 실측 / PUBLISHED 자료 인용 / ASSUMED 임시값)';
COMMENT ON COLUMN tool_variant.source_ref IS '출처 (URL과 확인 날짜)';
COMMENT ON COLUMN tool_variant.sort_order IS '표시 순서 (작을수록 먼저)';
COMMENT ON COLUMN tool_variant.created_at IS '등록일시';
COMMENT ON COLUMN tool_variant.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN tool_variant.updated_at IS '수정일시';
COMMENT ON COLUMN tool_variant.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- fill_level
COMMENT ON COLUMN fill_level.id IS '기본 키 (접두어_순번, 전체 20자)';
COMMENT ON COLUMN fill_level.tool_variant_id IS '도구 ID (tool_variant.id)';
COMMENT ON COLUMN fill_level.code IS '담기 단계 코드 (powder.1_2, liquid.full, powder.heaped 등)';
COMMENT ON COLUMN fill_level.content_type IS '내용물 종류 (POWDER 가루 / LIQUID 액체)';
COMMENT ON COLUMN fill_level.fraction IS '대표 용량 대비 비율 (0 초과 1 이하. 1이면 가득)';
COMMENT ON COLUMN fill_level.is_heaped IS '소복이 여부 (TRUE면 용량을 알 수 없어 계산에 쓰지 않음)';
COMMENT ON COLUMN fill_level.image_key IS '담기 그림 키 (image_file.image_key, 예: fill.spoon_rice.powder.1_2)';
COMMENT ON COLUMN fill_level.value_source IS '값의 출처 등급 (MEASURED 실측 / PUBLISHED 자료 인용 / ASSUMED 임시값)';
COMMENT ON COLUMN fill_level.created_at IS '등록일시';
COMMENT ON COLUMN fill_level.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN fill_level.updated_at IS '수정일시';
COMMENT ON COLUMN fill_level.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- ingredient
COMMENT ON COLUMN ingredient.id IS '기본 키 (접두어_순번, 전체 20자)';
COMMENT ON COLUMN ingredient.code IS '재료 코드 (soy_sauce, green_onion 등)';
COMMENT ON COLUMN ingredient.name_ko IS '재료 대표 이름 (간장)';
COMMENT ON COLUMN ingredient.category IS '분류 (가루 양념, 액체 양념, 장류, 기름, 채소, 곡물, 육류, 달걀, 기타). 가루 양념이면 숟가락 그림을 가루 기준으로 고름';
COMMENT ON COLUMN ingredient.default_sensitivity IS '민감도 (HIGH 간을 정하는 재료라 많아지게 반올림하지 않음 / NORMAL)';
COMMENT ON COLUMN ingredient.is_garnish IS '장식용 재료 여부 (깨 등)';
COMMENT ON COLUMN ingredient.created_at IS '등록일시';
COMMENT ON COLUMN ingredient.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN ingredient.updated_at IS '수정일시';
COMMENT ON COLUMN ingredient.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- ingredient_alias
COMMENT ON COLUMN ingredient_alias.id IS '기본 키 (접두어_순번, 전체 20자)';
COMMENT ON COLUMN ingredient_alias.ingredient_id IS '재료 사전 ID (ingredient.id)';
COMMENT ON COLUMN ingredient_alias.alias IS '다른 이름 (진간장, 양조간장 → 간장)';
COMMENT ON COLUMN ingredient_alias.created_at IS '등록일시';
COMMENT ON COLUMN ingredient_alias.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN ingredient_alias.updated_at IS '수정일시';
COMMENT ON COLUMN ingredient_alias.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- allergen
COMMENT ON COLUMN allergen.id IS '기본 키 (접두어_순번, 전체 20자)';
COMMENT ON COLUMN allergen.code IS '알레르기 코드 (egg, soybean, wheat 등). 앱과 API가 쓰는 알레르기 ID';
COMMENT ON COLUMN allergen.name_ko IS '알레르기 이름 (난류)';
COMMENT ON COLUMN allergen.group_ko IS '화면 소제목 (곡물·콩, 알·유제품, 고기, 해산물, 과일·견과, 기타)';
COMMENT ON COLUMN allergen.sort_order IS '표시 순서 (작을수록 먼저)';
COMMENT ON COLUMN allergen.created_at IS '등록일시';
COMMENT ON COLUMN allergen.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN allergen.updated_at IS '수정일시';
COMMENT ON COLUMN allergen.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- ingredient_allergen
COMMENT ON COLUMN ingredient_allergen.ingredient_id IS '재료 사전 ID (ingredient.id)';
COMMENT ON COLUMN ingredient_allergen.allergen_id IS '알레르기 ID (allergen.id)';
COMMENT ON COLUMN ingredient_allergen.is_hidden IS '숨은 성분 여부 (간장 속 대두·밀처럼 이름만 봐서는 모르는 성분)';
COMMENT ON COLUMN ingredient_allergen.verified_by IS '검증자 (DRAFT_UNVERIFIED 는 사람이 아직 검증하지 않음)';
COMMENT ON COLUMN ingredient_allergen.verified_at IS '검증일시';
COMMENT ON COLUMN ingredient_allergen.created_at IS '등록일시';
COMMENT ON COLUMN ingredient_allergen.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN ingredient_allergen.updated_at IS '수정일시';
COMMENT ON COLUMN ingredient_allergen.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- weight_reference
COMMENT ON COLUMN weight_reference.id IS '기본 키 (접두어_순번, 전체 20자)';
COMMENT ON COLUMN weight_reference.ingredient_id IS '재료 사전 ID (ingredient.id)';
COMMENT ON COLUMN weight_reference.tool_variant_id IS '기준이 되는 도구 ID (tool_variant.id). 이 재료를 그 도구에 가득 담은 무게일 때만 넣고, 1개·1모 같은 개수 기준이면 NULL';
COMMENT ON COLUMN weight_reference.unit_label_ko IS '단위 표현 (1개, 1모, 밥그릇 1번)';
COMMENT ON COLUMN weight_reference.approx_grams IS '대략적인 무게(g)';
COMMENT ON COLUMN weight_reference.size_note_ko IS '크기 설명 (중간 크기)';
COMMENT ON COLUMN weight_reference.value_source IS '값의 출처 등급 (MEASURED 실측 / PUBLISHED 자료 인용 / ASSUMED 임시값)';
COMMENT ON COLUMN weight_reference.source_ref IS '출처 (URL과 확인 날짜)';
COMMENT ON COLUMN weight_reference.created_at IS '등록일시';
COMMENT ON COLUMN weight_reference.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN weight_reference.updated_at IS '수정일시';
COMMENT ON COLUMN weight_reference.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- recipe
COMMENT ON COLUMN recipe.id IS '기본 키 (접두어_순번, 전체 20자)';
COMMENT ON COLUMN recipe.name IS '요리 이름';
COMMENT ON COLUMN recipe.normalized_name IS '검색용 이름 (공백 제거, 소문자)';
COMMENT ON COLUMN recipe.source IS '출처 종류 (WEB_SEARCH 웹 검색 / FOOD_SAFETY_KR 식약처 / CURATED 직접 작성 / FIXTURE 개발·시연용)';
COMMENT ON COLUMN recipe.source_url IS '원문 페이지 주소 (WEB_SEARCH는 필수)';
COMMENT ON COLUMN recipe.source_url_hash IS '원문 주소의 SHA-256 (같은 페이지를 두 번 저장하지 않기 위함)';
COMMENT ON COLUMN recipe.source_domain IS '출처 도메인 (화면 표시용, 예: 10000recipe.com)';
COMMENT ON COLUMN recipe.source_ref IS '출처 쪽 식별값 (공공 API 일련번호, 고정 레시피의 ref)';
COMMENT ON COLUMN recipe.base_servings IS '기준 인분';
COMMENT ON COLUMN recipe.total_minutes IS '전체 소요 시간(분)';
COMMENT ON COLUMN recipe.verification_status IS '검증 상태 (UNVERIFIED 미검증 / IMPORTED 가져옴 / DATA_CHECKED 데이터 확인 / COOK_TESTED 조리 확인)';
COMMENT ON COLUMN recipe.created_at IS '등록일시';
COMMENT ON COLUMN recipe.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN recipe.updated_at IS '수정일시';
COMMENT ON COLUMN recipe.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- recipe_ingredient
COMMENT ON COLUMN recipe_ingredient.id IS '기본 키 (접두어_순번, 전체 20자)';
COMMENT ON COLUMN recipe_ingredient.recipe_id IS '레시피 ID (recipe.id)';
COMMENT ON COLUMN recipe_ingredient.ingredient_id IS '재료 사전 ID (ingredient.id). 사전에 없는 재료면 NULL이고 알레르기는 확인 필요로 표시';
COMMENT ON COLUMN recipe_ingredient.section_ko IS '재료 소제목 (양념장 등)';
COMMENT ON COLUMN recipe_ingredient.name_ko IS '레시피에 적힌 재료 이름';
COMMENT ON COLUMN recipe_ingredient.quantity IS '수량. 약간·적당량이면 NULL';
COMMENT ON COLUMN recipe_ingredient.unit IS '단위 (큰술, 작은술, 컵, ml, g, 개)';
COMMENT ON COLUMN recipe_ingredient.alt_quantity IS '괄호 안 환산 수량 (2g(1/3작은술) → 0.333)';
COMMENT ON COLUMN recipe_ingredient.alt_unit IS '괄호 안 환산 단위 (작은술)';
COMMENT ON COLUMN recipe_ingredient.unit_kind IS '단위 종류 (VOLUME 부피 / WEIGHT 무게 / COUNT 개수 / VAGUE 약간·적당량)';
COMMENT ON COLUMN recipe_ingredient.raw_text IS '원문 표현 그대로 (간장 1큰술)';
COMMENT ON COLUMN recipe_ingredient.is_required IS '필수 재료 여부';
COMMENT ON COLUMN recipe_ingredient.sort_order IS '표시 순서 (작을수록 먼저)';
COMMENT ON COLUMN recipe_ingredient.created_at IS '등록일시';
COMMENT ON COLUMN recipe_ingredient.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN recipe_ingredient.updated_at IS '수정일시';
COMMENT ON COLUMN recipe_ingredient.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- recipe_step
COMMENT ON COLUMN recipe_step.id IS '기본 키 (접두어_순번, 전체 20자)';
COMMENT ON COLUMN recipe_step.recipe_id IS '레시피 ID (recipe.id)';
COMMENT ON COLUMN recipe_step.step_order IS '단계 순서 (1부터)';
COMMENT ON COLUMN recipe_step.action_ko IS '행동 문장 (한 단계에 주요 행동 하나)';
COMMENT ON COLUMN recipe_step.action_key IS '행동 그림 키 (image_file.image_key, 예: action.stir_fry). 안전 규칙의 ACTION 트리거와도 비교';
COMMENT ON COLUMN recipe_step.duration_seconds IS '시간(초). 원문에 시간이 있을 때만 넣고 없으면 NULL';
COMMENT ON COLUMN recipe_step.timer_start_condition IS '타이머를 시작할 기준 (볶기 시작할 때 눌러주세요)';
COMMENT ON COLUMN recipe_step.heat_level IS '불 세기 (OFF / LOW 약 / MEDIUM_LOW 중약 / MEDIUM 중 / MEDIUM_HIGH 중강 / HIGH 강). 불을 안 쓰면 NULL';
COMMENT ON COLUMN recipe_step.heat_raw IS '불 세기 원문 표현 (중약불)';
COMMENT ON COLUMN recipe_step.completion_check IS '다음 단계로 넘어갈 상태 확인 문구';
COMMENT ON COLUMN recipe_step.created_at IS '등록일시';
COMMENT ON COLUMN recipe_step.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN recipe_step.updated_at IS '수정일시';
COMMENT ON COLUMN recipe_step.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- recipe_step_ingredient
COMMENT ON COLUMN recipe_step_ingredient.step_id IS '단계 ID (recipe_step.id)';
COMMENT ON COLUMN recipe_step_ingredient.recipe_ingredient_id IS '레시피 재료 ID (recipe_ingredient.id)';
COMMENT ON COLUMN recipe_step_ingredient.created_at IS '등록일시';
COMMENT ON COLUMN recipe_step_ingredient.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN recipe_step_ingredient.updated_at IS '수정일시';
COMMENT ON COLUMN recipe_step_ingredient.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- recipe_step_tool
COMMENT ON COLUMN recipe_step_tool.step_id IS '단계 ID (recipe_step.id)';
COMMENT ON COLUMN recipe_step_tool.tool_variant_id IS '도구 ID (tool_variant.id)';
COMMENT ON COLUMN recipe_step_tool.created_at IS '등록일시';
COMMENT ON COLUMN recipe_step_tool.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN recipe_step_tool.updated_at IS '수정일시';
COMMENT ON COLUMN recipe_step_tool.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- adaptation_rule
COMMENT ON COLUMN adaptation_rule.id IS '기본 키 (접두어_순번, 전체 20자)';
COMMENT ON COLUMN adaptation_rule.type IS '규칙 종류 (OMIT 생략 / SUBSTITUTE 대체 / METHOD_CHANGE 조리법 변경 / SERVING_SCALE 인분 조절)';
COMMENT ON COLUMN adaptation_rule.ingredient_id IS '대상 재료 ID (ingredient.id)';
COMMENT ON COLUMN adaptation_rule.substitute_ingredient_id IS '대체 재료 ID (ingredient.id). SUBSTITUTE일 때 필수';
COMMENT ON COLUMN adaptation_rule.ratio IS '대체 비율 (원래 양 대비)';
COMMENT ON COLUMN adaptation_rule.impact_note_ko IS '적용했을 때의 영향 안내 (향이 조금 약해질 수 있어요)';
COMMENT ON COLUMN adaptation_rule.applies_to_recipe_id IS '적용할 레시피 ID (recipe.id). NULL이면 모든 레시피';
COMMENT ON COLUMN adaptation_rule.priority IS '적용 우선순위 (작을수록 먼저 시도)';
COMMENT ON COLUMN adaptation_rule.verified_by IS '검증자 (DRAFT_UNVERIFIED 는 사람이 아직 검증하지 않음)';
COMMENT ON COLUMN adaptation_rule.verified_at IS '검증일시';
COMMENT ON COLUMN adaptation_rule.created_at IS '등록일시';
COMMENT ON COLUMN adaptation_rule.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN adaptation_rule.updated_at IS '수정일시';
COMMENT ON COLUMN adaptation_rule.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- tool_substitution_rule
COMMENT ON COLUMN tool_substitution_rule.id IS '기본 키 (접두어_순번, 전체 20자)';
COMMENT ON COLUMN tool_substitution_rule.required_tool_id IS '레시피가 요구하는 도구 ID (tool_variant.id)';
COMMENT ON COLUMN tool_substitution_rule.substitute_tool_id IS '대신 쓸 도구 ID (tool_variant.id)';
COMMENT ON COLUMN tool_substitution_rule.condition_note_ko IS '적용 조건 (부드러운 재료만)';
COMMENT ON COLUMN tool_substitution_rule.applies_to_recipe_id IS '적용할 레시피 ID (recipe.id). NULL이면 모든 레시피';
COMMENT ON COLUMN tool_substitution_rule.extra_safety_note_ko IS '대체 도구를 쓸 때 추가로 보여줄 안전 문구';
COMMENT ON COLUMN tool_substitution_rule.verified_by IS '검증자 (DRAFT_UNVERIFIED 는 사람이 아직 검증하지 않음)';
COMMENT ON COLUMN tool_substitution_rule.verified_at IS '검증일시';
COMMENT ON COLUMN tool_substitution_rule.created_at IS '등록일시';
COMMENT ON COLUMN tool_substitution_rule.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN tool_substitution_rule.updated_at IS '수정일시';
COMMENT ON COLUMN tool_substitution_rule.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- safety_rule
COMMENT ON COLUMN safety_rule.id IS '기본 키 (접두어_순번, 전체 20자)';
COMMENT ON COLUMN safety_rule.code IS '규칙 코드 (hot_oil_splash, pork_cook_through 등)';
COMMENT ON COLUMN safety_rule.trigger_type IS '트리거 종류 (INGREDIENT 재료 / ACTION 행동 / TOOL 도구 / HEAT 불 세기)';
COMMENT ON COLUMN safety_rule.trigger_value IS '트리거 값 (재료 코드 / 행동 그림 키 / 도구 코드 / 불 세기 값)';
COMMENT ON COLUMN safety_rule.message_ko IS '주의 문구 (모든 실력 수준에 표시)';
COMMENT ON COLUMN safety_rule.detail_low_ko IS '처음 해봐요(LOW)에게만 추가로 보여줄 이유와 확인 방법';
COMMENT ON COLUMN safety_rule.placement IS '표시 위치 (STEP 해당 단계 / SUMMARY 조리 전 요약 / BOTH 둘 다)';
COMMENT ON COLUMN safety_rule.verified_by IS '검증자 (DRAFT_UNVERIFIED 는 사람이 아직 검증하지 않음)';
COMMENT ON COLUMN safety_rule.verified_at IS '검증일시';
COMMENT ON COLUMN safety_rule.created_at IS '등록일시';
COMMENT ON COLUMN safety_rule.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN safety_rule.updated_at IS '수정일시';
COMMENT ON COLUMN safety_rule.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- recipe_import_job
COMMENT ON COLUMN recipe_import_job.id IS '기본 키 (접두어_순번, 전체 20자)';
COMMENT ON COLUMN recipe_import_job.query_text IS '사용자가 입력한 검색어';
COMMENT ON COLUMN recipe_import_job.normalized_query IS '검색어 정규화 값 (공백 제거, 소문자)';
COMMENT ON COLUMN recipe_import_job.status IS '진행 상태 (PENDING 대기 / SEARCHING 검색 / EXTRACTING 추출 / VERIFYING 대조 / DONE 완료 / NOT_FOUND 못 찾음 / FAILED 실패)';
COMMENT ON COLUMN recipe_import_job.recipe_id IS '가져오기에 성공한 레시피 ID (recipe.id)';
COMMENT ON COLUMN recipe_import_job.error_code IS '실패 원인 코드 (사용자 입력이나 원문은 넣지 않음)';
COMMENT ON COLUMN recipe_import_job.created_at IS '등록일시';
COMMENT ON COLUMN recipe_import_job.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN recipe_import_job.updated_at IS '수정일시';
COMMENT ON COLUMN recipe_import_job.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- adapted_recipe
COMMENT ON COLUMN adapted_recipe.id IS '기본 키 (접두어_순번, 전체 20자)';
COMMENT ON COLUMN adapted_recipe.recipe_id IS '레시피 ID (recipe.id)';
COMMENT ON COLUMN adapted_recipe.conditions_hash IS '조건의 SHA-256 (실력 + 도구 + 없는 재료 + 대체 응답. 알레르기는 포함하지 않음)';
COMMENT ON COLUMN adapted_recipe.conditions_json IS '조건 내용 JSON (알레르기 정보 저장 금지)';
COMMENT ON COLUMN adapted_recipe.status IS '재구성 결과 (COOKABLE 조리 가능 / NEEDS_CONFIRMATION 확인 필요 / NOT_COOKABLE 조리 불가)';
COMMENT ON COLUMN adapted_recipe.payload IS '응답 본문 JSON (알레르기 경고는 빼고 저장, 경고는 응답할 때 계산)';
COMMENT ON COLUMN adapted_recipe.ai_model IS '사용한 AI 모델 이름';
COMMENT ON COLUMN adapted_recipe.created_at IS '등록일시';
COMMENT ON COLUMN adapted_recipe.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN adapted_recipe.updated_at IS '수정일시';
COMMENT ON COLUMN adapted_recipe.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- ai_call_log
COMMENT ON COLUMN ai_call_log.id IS '기본 키 (접두어_순번, 전체 20자)';
COMMENT ON COLUMN ai_call_log.purpose IS '호출 목적 (WEB_SEARCH 웹 검색 / EXTRACT 추출 / ADAPT 재구성 / QUESTION 재료 질문)';
COMMENT ON COLUMN ai_call_log.model IS 'AI 모델 이름';
COMMENT ON COLUMN ai_call_log.input_tokens IS '입력 토큰 수';
COMMENT ON COLUMN ai_call_log.output_tokens IS '출력 토큰 수';
COMMENT ON COLUMN ai_call_log.latency_ms IS '응답 시간(ms)';
COMMENT ON COLUMN ai_call_log.success IS '성공 여부';
COMMENT ON COLUMN ai_call_log.error_code IS '실패 원인 코드 (프롬프트·응답 본문은 저장하지 않음)';
COMMENT ON COLUMN ai_call_log.created_at IS '등록일시';
COMMENT ON COLUMN ai_call_log.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN ai_call_log.updated_at IS '수정일시';
COMMENT ON COLUMN ai_call_log.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- image_group
COMMENT ON COLUMN image_group.id IS '기본 키 (접두어_순번, 전체 20자)';
COMMENT ON COLUMN image_group.code IS '묶음 코드 (tool 도구 / heat.gas 가스 불 세기 / heat.induction 인덕션 불 세기 / illust 일러스트 / fill 담기 기준 / amount 재료 양 / action 행동 / app 앱 아이콘)';
COMMENT ON COLUMN image_group.name_ko IS '묶음 이름';
COMMENT ON COLUMN image_group.output_dir IS '앱 자산 폴더 (apps/mobile/assets/images 아래, 예: tools)';
COMMENT ON COLUMN image_group.target_size_px IS '변환본 한 변의 크기(px). 정사각형';
COMMENT ON COLUMN image_group.sort_order IS '표시 순서 (작을수록 먼저)';
COMMENT ON COLUMN image_group.created_at IS '등록일시';
COMMENT ON COLUMN image_group.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN image_group.updated_at IS '수정일시';
COMMENT ON COLUMN image_group.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
-- image_file
COMMENT ON COLUMN image_file.id IS '기본 키 (접두어_순번, 전체 20자)';
COMMENT ON COLUMN image_file.image_group_id IS '이미지 묶음 ID (image_group.id)';
COMMENT ON COLUMN image_file.image_key IS '그림 키. 코드와 데이터가 그림을 가리키는 이름 (tool.spoon_rice, heat.gas.LOW)';
COMMENT ON COLUMN image_file.status IS '준비 상태 (READY 그림 있음 / MISSING 아직 없음, 화면은 자리 표시)';
COMMENT ON COLUMN image_file.original_file_name IS '원본 파일명 (assets/source/images 안)';
COMMENT ON COLUMN image_file.stored_file_name IS '변환본 파일명';
COMMENT ON COLUMN image_file.file_path IS '앱 자산 기준 경로 (tools/teaspoon.png)';
COMMENT ON COLUMN image_file.file_ext IS '확장자 (png)';
COMMENT ON COLUMN image_file.file_size_bytes IS '변환본 크기(바이트). 알 수 있을 때만';
COMMENT ON COLUMN image_file.width_px IS '변환본 너비(px)';
COMMENT ON COLUMN image_file.height_px IS '변환본 높이(px)';
COMMENT ON COLUMN image_file.sort_order IS '표시 순서 (작을수록 먼저)';
COMMENT ON COLUMN image_file.created_at IS '등록일시';
COMMENT ON COLUMN image_file.created_by IS '등록자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
COMMENT ON COLUMN image_file.updated_at IS '수정일시';
COMMENT ON COLUMN image_file.updated_by IS '수정자 (1차는 SYSTEM, 회원 기능 이후에는 사용자 ID)';
