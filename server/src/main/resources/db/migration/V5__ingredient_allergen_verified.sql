-- =====================================================================
-- V5: 재료의 알레르기 정보를 사람이 확인했는지 기록 (2026-10-09)
--  - 확인되지 않은 재료는 알려진 알레르기 경고는 그대로 보여 주되,
--    "알레르기 없음"으로 다루지 않고 "확인 필요"로 표시한다 (CLAUDE.md 규칙 13)
--  - 값은 data/seed/allergen-mapping.json 의 verifiedBy / verifiedAt 에서 온다
--    (알레르기가 없는 재료도 allergens 를 빈 배열로 두고 verifiedBy 를 채우면 확인된 것으로 본다)
-- =====================================================================

ALTER TABLE ingredient
    ADD COLUMN allergen_verified_by  VARCHAR(64),
    ADD COLUMN allergen_verified_at  TIMESTAMPTZ;

COMMENT ON COLUMN ingredient.allergen_verified_by IS '이 재료의 알레르기 정보를 확인한 사람. NULL이면 미확인: 알려진 경고는 보여 주되 "확인 필요"로 표시';
COMMENT ON COLUMN ingredient.allergen_verified_at IS '알레르기 정보를 확인한 시각';
