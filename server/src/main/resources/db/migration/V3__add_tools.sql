-- =====================================================================
-- V3: 도구 13개 추가 + 도구 표시 구분 + 가열 기기 설정 텍스트
--  - 표준 계량 도구(계량스푼·계량컵)를 가진 사용자에게는 원래 레시피 단위를 그대로 보여준다
--  - '기본 도구'는 도구 선택 화면에 바로 보이고, 나머지는 '더 있어요'를 펼쳐야 보인다
--  - 오븐·에어프라이어·전자레인지 설정(온도·출력)은 변환하지 않고 원문 텍스트로만 보여준다
-- =====================================================================

-- 1. 도구 표시 구분 ------------------------------------------------------
ALTER TABLE tool_variant
    ADD COLUMN is_standard_measure BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN is_basic            BOOLEAN NOT NULL DEFAULT TRUE;
COMMENT ON COLUMN tool_variant.is_standard_measure IS 'TRUE면 이 도구를 가진 사용자에게 원래 단위(큰술·컵)를 그대로 표시';
COMMENT ON COLUMN tool_variant.is_basic IS 'FALSE면 도구 선택 화면의 "더 있어요" 안에 표시';

-- 2. 가열 기기 종류에 오븐 추가 -----------------------------------------
ALTER TABLE tool_variant DROP CONSTRAINT tool_variant_heat_source_type_check;
ALTER TABLE tool_variant ADD CONSTRAINT tool_variant_heat_source_type_check
    CHECK (heat_source_type IN ('GAS','INDUCTION','MICROWAVE','AIR_FRYER','RICE_COOKER','OVEN'));

-- 3. 단계의 가열 기기 설정 (원문 그대로) ---------------------------------
ALTER TABLE recipe_step
    ADD COLUMN device_setting_raw VARCHAR(64);
COMMENT ON COLUMN recipe_step.device_setting_raw IS '오븐·에어프라이어·전자레인지 설정 원문: "180°C 예열", "700W". 변환하지 않고 그대로 표시';

-- 4. 새 도구 13개 --------------------------------------------------------
INSERT INTO tool_variant (code, category_id, name_ko, sub_label_ko, icon_key, heat_source_type,
                          is_standard_measure, is_basic, sort_order)
SELECT v.code, c.id, v.name_ko, v.sub_label_ko, 'tool.' || v.code, v.heat_source_type,
       v.is_standard_measure, v.is_basic, v.sort_order
FROM (VALUES
  -- 계량
  ('measuring_spoon', 'measure',     '계량스푼',   '큰술·작은술', NULL,   TRUE,  TRUE,  0),
  ('measuring_cup',   'measure',     '계량컵',     NULL,          NULL,   TRUE,  TRUE,  0),
  ('water_bottle_500','measure',     '생수병',     '500mL',       NULL,   FALSE, TRUE,  6),
  ('rice_bowl',       'measure',     '밥공기',     NULL,          NULL,   FALSE, TRUE,  7),
  ('soup_bowl',       'measure',     '국그릇',     NULL,          NULL,   FALSE, TRUE,  8),
  ('rice_cup',        'measure',     '쌀컵',       '밥솥 계량컵', NULL,   FALSE, FALSE, 9),
  ('soju_glass',      'measure',     '소주잔',     NULL,          NULL,   FALSE, FALSE, 10),
  -- 자르기
  ('slicer',          'cut',         '채칼',       NULL,          NULL,   FALSE, FALSE, 3),
  ('grater',          'cut',         '강판',       NULL,          NULL,   FALSE, FALSE, 4),
  ('peeler',          'cut',         '필러',       '감자칼',      NULL,   FALSE, FALSE, 5),
  -- 가열 기기
  ('oven',            'heat_source', '오븐',       '광파오븐 포함','OVEN', FALSE, FALSE, 6),
  -- 조리 용기
  ('steamer',         'vessel',      '찜기',       NULL,          NULL,   FALSE, FALSE, 4),
  ('earthen_pot',     'vessel',      '뚝배기',     NULL,          NULL,   FALSE, FALSE, 5)
) AS v(code, cat, name_ko, sub_label_ko, heat_source_type, is_standard_measure, is_basic, sort_order)
JOIN tool_category c ON c.code = v.cat;
