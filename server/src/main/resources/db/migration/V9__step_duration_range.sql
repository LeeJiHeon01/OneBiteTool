-- =====================================================================
-- V9: 범위 시간("2~3분")의 긴 값을 함께 저장한다 (결정 2026-10-09, 규칙 19-1)
--  - duration_seconds 는 짧은 값, duration_max_seconds 는 긴 값. 범위가 아니면 NULL
--  - 타이머 카드에서 사용자가 둘 중 하나를 고른다. 중간값은 만들지 않는다
-- =====================================================================

ALTER TABLE recipe_step
    ADD COLUMN duration_max_seconds INT
        CHECK (duration_max_seconds IS NULL OR duration_max_seconds > 0);

ALTER TABLE recipe_step
    ADD CONSTRAINT ck_recipe_step_duration_range
        CHECK (duration_max_seconds IS NULL
               OR (duration_seconds IS NOT NULL AND duration_max_seconds > duration_seconds));

COMMENT ON COLUMN recipe_step.duration_max_seconds IS '범위 시간의 긴 값("2~3분"→180). 범위가 아니면 NULL';
