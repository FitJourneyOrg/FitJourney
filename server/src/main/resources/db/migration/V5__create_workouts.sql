CREATE TABLE workouts (
    id          UUID          PRIMARY KEY,
    user_id     UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(200)  NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workout_exercises (
    id           UUID  PRIMARY KEY,
    workout_id   UUID  NOT NULL REFERENCES workouts(id) ON DELETE CASCADE,
    exercise_id  UUID  NOT NULL REFERENCES exercises(id),
    order_index  INT   NOT NULL
);

CREATE TABLE workout_sets (
    id                   UUID  PRIMARY KEY,
    workout_exercise_id  UUID  NOT NULL REFERENCES workout_exercises(id) ON DELETE CASCADE,
    reps                 INT   NOT NULL,
    order_index          INT   NOT NULL
);

CREATE INDEX idx_workouts_user ON workouts(user_id);
CREATE INDEX idx_workout_exercises_workout ON workout_exercises(workout_id);
CREATE INDEX idx_workout_sets_we ON workout_sets(workout_exercise_id);