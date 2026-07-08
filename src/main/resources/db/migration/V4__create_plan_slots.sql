CREATE TABLE plan_slots (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    week_start  DATE        NOT NULL,
    day_index   SMALLINT    NOT NULL CHECK (day_index BETWEEN 0 AND 6),
    slot_index  SMALLINT    NOT NULL CHECK (slot_index >= 0),
    image_id    UUID        NOT NULL REFERENCES images(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, week_start, day_index, slot_index)
);

CREATE INDEX idx_plan_slots_user_week ON plan_slots(user_id, week_start);
