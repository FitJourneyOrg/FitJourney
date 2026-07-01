CREATE TABLE exercises (
    id          UUID          PRIMARY KEY,
    name        VARCHAR(200)  NOT NULL,
    category    VARCHAR(32)   NOT NULL,
    description TEXT,
    video_ref   VARCHAR(300)  NOT NULL,
    thumb_ref   VARCHAR(300)  NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_exercises_category ON exercises(category);