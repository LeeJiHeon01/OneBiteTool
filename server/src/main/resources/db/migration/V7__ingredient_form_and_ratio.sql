-- =====================================================================
-- V7: 재료의 형태(form)와 민감도 3단계 (2026-10-09)
--  - category 는 분류 이름(양념, 채소, 기름 ...)이 되어 형태를 알 수 없다
--    (같은 '양념' 안에 간장=액체, 소금=가루, 된장=걸쭉한 양념이 섞여 있음)
--    → 계량에 쓰는 형태를 form 으로 따로 둔다. 고체는 컵·그릇의 부피로 바꾸지 않는다.
--  - 민감도에 RATIO(비율이 중요: 밀가루·전분·쌀) 추가. 허용 오차 10% (data/seed/units.json)
-- =====================================================================

ALTER TABLE ingredient
    ADD COLUMN form VARCHAR(8) NOT NULL DEFAULT 'SOLID'
        CHECK (form IN ('LIQUID','POWDER','PASTE','SOLID'));

ALTER TABLE ingredient DROP CONSTRAINT ingredient_default_sensitivity_check;
ALTER TABLE ingredient ADD CONSTRAINT ingredient_default_sensitivity_check
    CHECK (default_sensitivity IN ('HIGH','RATIO','NORMAL'));

COMMENT ON COLUMN ingredient.form IS '계량에 쓰는 형태: LIQUID(액체) / POWDER(가루) / PASTE(걸쭉한 양념) / SOLID(고체, 컵·그릇 부피로 바꾸지 않음)';
COMMENT ON COLUMN ingredient.category IS '분류 이름 (양념, 채소, 기름 ...). 화면·정리용이며 계량 형태는 form 이 정한다';
COMMENT ON COLUMN ingredient.default_sensitivity IS '민감도: HIGH(간을 정함, 많아지게 반올림하지 않음) / RATIO(비율이 중요, 허용 오차 10%) / NORMAL(허용 오차 25%)';
