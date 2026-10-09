-- =====================================================================
-- V2: 변하지 않는 목록(카탈로그)만 넣는다. — PostgreSQL
--  - 도구 분류·선택지, 알레르기 항목, 숟가락 양 그림 목록
--  - 측정값(용량·컵 치수)은 넣지 않는다 → data/seed/tools.json 이 원본, 서버 시작 시 동기화
--  - 재료 사전·알레르기 매핑·안전/대체 규칙은 사람이 검증해야 하므로 여기 넣지 않는다
-- =====================================================================

-- 도구 분류 ------------------------------------------------------------
INSERT INTO tool_category (code, name_ko, sort_order) VALUES
  ('measure',     '계량',      1),
  ('cut',         '자르기',    2),
  ('heat_source', '가열 기기', 3),
  ('vessel',      '조리 용기', 4);

-- 도구 선택지 (icon_key 는 assets/source/images/manifest.json 의 키) ------
INSERT INTO tool_variant (code, category_id, name_ko, sub_label_ko, icon_key, heat_source_type, sort_order)
SELECT v.code, c.id, v.name_ko, v.sub_label_ko, 'tool.' || v.code, v.heat_source_type, v.sort_order
FROM (VALUES
  ('spoon_rice',          'measure',     '밥숟가락',          NULL,            NULL,          1),
  ('teaspoon',            'measure',     '티스푼',            '커피 스푼',     NULL,          2),
  ('paper_cup_small',     'measure',     '작은 종이컵',       '자판기 컵',     NULL,          3),
  ('paper_cup_large',     'measure',     '큰 종이컵',         '테이크아웃 컵', NULL,          4),
  ('glass_cup',           'measure',     '유리컵',            NULL,            NULL,          5),
  ('knife',               'cut',         '칼',                NULL,            NULL,          1),
  ('scissors',            'cut',         '가위',              NULL,            NULL,          2),
  ('gas_stove',           'heat_source', '가스레인지',        NULL,            'GAS',         1),
  ('induction',           'heat_source', '인덕션',            NULL,            'INDUCTION',   2),
  ('microwave',           'heat_source', '전자레인지',        NULL,            'MICROWAVE',   3),
  ('air_fryer',           'heat_source', '에어프라이어',      NULL,            'AIR_FRYER',   4),
  ('rice_cooker',         'heat_source', '전기밥솥',          NULL,            'RICE_COOKER', 5),
  ('pot',                 'vessel',      '냄비',              NULL,            NULL,          1),
  ('frying_pan',          'vessel',      '프라이팬',          NULL,            NULL,          2),
  ('microwave_container', 'vessel',      '전자레인지용 용기', NULL,            NULL,          3)
) AS v(code, cat, name_ko, sub_label_ko, heat_source_type, sort_order)
JOIN tool_category c ON c.code = v.cat;

-- 숟가락 양 그림 목록 (그림은 아직 없음: manifest 의 missing) -------------
INSERT INTO fill_level (tool_variant_id, code, content_type, fraction, is_heaped, image_key)
SELECT t.id, f.code, f.content_type, f.fraction, f.is_heaped, 'fill.' || t.code || '.' || f.code
FROM (VALUES
  ('spoon_rice', 'powder.heaped', 'POWDER', 1.0000, TRUE),
  ('spoon_rice', 'powder.full',   'POWDER', 1.0000, FALSE),
  ('spoon_rice', 'powder.2_3',    'POWDER', 0.6667, FALSE),
  ('spoon_rice', 'powder.1_2',    'POWDER', 0.5000, FALSE),
  ('spoon_rice', 'powder.1_3',    'POWDER', 0.3333, FALSE),
  ('spoon_rice', 'liquid.full',   'LIQUID', 1.0000, FALSE),
  ('spoon_rice', 'liquid.2_3',    'LIQUID', 0.6667, FALSE),
  ('spoon_rice', 'liquid.1_2',    'LIQUID', 0.5000, FALSE),
  ('spoon_rice', 'liquid.1_3',    'LIQUID', 0.3333, FALSE),
  ('teaspoon',   'powder.heaped', 'POWDER', 1.0000, TRUE),
  ('teaspoon',   'powder.full',   'POWDER', 1.0000, FALSE),
  ('teaspoon',   'powder.1_2',    'POWDER', 0.5000, FALSE),
  ('teaspoon',   'liquid.full',   'LIQUID', 1.0000, FALSE),
  ('teaspoon',   'liquid.1_2',    'LIQUID', 0.5000, FALSE)
) AS f(tool, code, content_type, fraction, is_heaped)
JOIN tool_variant t ON t.code = f.tool;

-- 알레르기 항목 (화면 소제목 순서대로) --------------------------------
-- 출시 전 식약처 최신 표시 대상 목록과 대조할 것
INSERT INTO allergen (code, name_ko, group_ko, sort_order) VALUES
  ('wheat',     '밀',       '곡물·콩',    1),
  ('buckwheat', '메밀',     '곡물·콩',    2),
  ('soybean',   '대두',     '곡물·콩',    3),
  ('peanut',    '땅콩',     '곡물·콩',    4),
  ('egg',       '난류',     '알·유제품',  5),
  ('milk',      '우유',     '알·유제품',  6),
  ('pork',      '돼지고기', '고기',       7),
  ('chicken',   '닭고기',   '고기',       8),
  ('beef',      '쇠고기',   '고기',       9),
  ('mackerel',  '고등어',   '해산물',    10),
  ('crab',      '게',       '해산물',    11),
  ('shrimp',    '새우',     '해산물',    12),
  ('squid',     '오징어',   '해산물',    13),
  ('shellfish', '조개류',   '해산물',    14),
  ('peach',     '복숭아',   '과일·견과', 15),
  ('tomato',    '토마토',   '과일·견과', 16),
  ('walnut',    '호두',     '과일·견과', 17),
  ('pine_nut',  '잣',       '과일·견과', 18),
  ('sulfite',   '아황산류', '기타',      19);
