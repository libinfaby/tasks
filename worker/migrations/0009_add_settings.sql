-- Migration: add_settings
-- App preferences shared by the web and Android apps (single user): one row per key
CREATE TABLE IF NOT EXISTS settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);
