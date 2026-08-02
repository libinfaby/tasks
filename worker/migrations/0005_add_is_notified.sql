-- Migration: add_is_notified_column
ALTER TABLE tasks ADD COLUMN is_notified INTEGER DEFAULT 0;
