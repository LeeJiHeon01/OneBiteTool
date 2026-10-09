-- =====================================================================
-- V6: 알레르기 매핑 검토 등급 (2026-10-09)
--  - HUMAN_VERIFIED: 사람이 근거를 보고 확인
--  - AI_RESEARCHED : AI가 공개 자료로 조사한 보수적 매핑 (사람이 확인한 것이 아님)
--  - NULL          : 초안 또는 미조사 → "알레르기 확인 필요"
--  1차에서는 HUMAN_VERIFIED 와 AI_RESEARCHED 를 확인된 매핑으로 쓴다 (CLAUDE.md 규칙 13-1).
--  알레르기가 하나도 없는 재료도 "조사됨"을 표시해야 하므로 등급은 재료 테이블에 둔다.
--  - 알레르기 항목 표시 이름: 식약처 표기에 맞춰 '난류' → '알류' (코드 egg 는 그대로)
-- =====================================================================

ALTER TABLE ingredient_allergen ADD COLUMN review_level VARCHAR(16)
    CHECK (review_level IN ('HUMAN_VERIFIED','AI_RESEARCHED'));
ALTER TABLE ingredient ADD COLUMN allergen_review_level VARCHAR(16)
    CHECK (allergen_review_level IN ('HUMAN_VERIFIED','AI_RESEARCHED'));
ALTER TABLE ingredient ADD COLUMN allergen_product_varies BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ingredient_allergen.review_level IS '이 매핑의 검토 등급: HUMAN_VERIFIED(사람 확인) / AI_RESEARCHED(자료 기반 AI 조사) / NULL(초안)';
COMMENT ON COLUMN ingredient.allergen_review_level IS '재료 단위 검토 등급. allergens가 비어 있어도 조사 완료면 값이 있음. NULL이면 "알레르기 확인 필요"';
COMMENT ON COLUMN ingredient.allergen_product_varies IS 'TRUE면 제품마다 성분이 다른 가공 재료(간장·카레·햄 등). 경고 옆에 "제품마다 달라요" 표시';

UPDATE allergen SET name_ko = '알류' WHERE code = 'egg';
