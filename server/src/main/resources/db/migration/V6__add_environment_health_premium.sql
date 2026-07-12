ALTER TABLE profiles ADD COLUMN environment VARCHAR(32);
ALTER TABLE profiles ADD COLUMN health_screening TEXT;
ALTER TABLE users ADD COLUMN is_premium BOOLEAN NOT NULL DEFAULT false;