CREATE TABLE profiles (
    user_id              UUID         PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    goal                 VARCHAR(32)  NOT NULL,
    level                VARCHAR(32)  NOT NULL,
    days_per_week        INT          NOT NULL,
    focus_areas          TEXT         NOT NULL DEFAULT '[]',
    weight_kg            DOUBLE PRECISION,
    height_cm            DOUBLE PRECISION,
    onboarding_completed BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);