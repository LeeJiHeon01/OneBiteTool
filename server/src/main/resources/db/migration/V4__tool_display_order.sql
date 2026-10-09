-- =====================================================================
-- V4: 도구 선택 화면의 순서와 이름 조정 (2026-10-09)
--  - 분류마다 앞의 6개까지 바로 보이고, 7번째부터 '더 있어요' 안에 들어간다
--    → is_basic 은 "분류 안에서 6번째 이내"와 같게 맞춘다
--  - '작은 종이컵' → '종이컵' (큰 종이컵은 '더 있어요'로 내려감)
--  - 필러(감자칼)를 가위 옆으로 옮기고, 오븐은 다른 가열 기기와 함께 보여준다
-- =====================================================================

UPDATE tool_variant SET name_ko = '종이컵' WHERE code = 'paper_cup_small';

-- 분류 안에서의 표시 순서 (code, sort_order)
UPDATE tool_variant AS t
SET sort_order = v.sort_order,
    is_basic   = v.sort_order <= 6
FROM (VALUES
  -- 계량 (계량스푼·계량컵은 화면 맨 위에서 따로 묻는다: sort_order 0 유지)
  ('spoon_rice',        1),
  ('teaspoon',          2),
  ('paper_cup_small',   3),
  ('glass_cup',         4),
  ('water_bottle_500',  5),
  ('rice_bowl',         6),
  ('paper_cup_large',   7),
  ('soup_bowl',         8),
  ('rice_cup',          9),
  ('soju_glass',       10),
  -- 자르기
  ('knife',             1),
  ('scissors',          2),
  ('peeler',            3),
  ('slicer',            4),
  ('grater',            5),
  -- 가열 기기
  ('gas_stove',         1),
  ('induction',         2),
  ('microwave',         3),
  ('air_fryer',         4),
  ('rice_cooker',       5),
  ('oven',              6),
  -- 조리 용기
  ('pot',               1),
  ('frying_pan',        2),
  ('microwave_container', 3),
  ('steamer',           4),
  ('earthen_pot',       5)
) AS v(code, sort_order)
WHERE t.code = v.code;

COMMENT ON COLUMN tool_variant.is_basic IS 'TRUE면 도구 선택 화면에 바로 표시(분류 안에서 6번째 이내). FALSE면 "더 있어요" 안에 표시';
COMMENT ON COLUMN tool_variant.sort_order IS '분류 안에서의 표시 순서 (작을수록 먼저). 7번째부터는 "더 있어요" 안에 들어간다';
