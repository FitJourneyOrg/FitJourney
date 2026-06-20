CREATE TABLE users (
    id           UUID         PRIMARY KEY,
    firebase_uid VARCHAR(128) NOT NULL UNIQUE,
    email        VARCHAR(320),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);