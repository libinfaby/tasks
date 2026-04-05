-- Migration: add_has_bg_column
ALTER TABLE tag_types ADD COLUMN has_bg INTEGER DEFAULT 1;
ALTER TABLE tags ADD COLUMN has_bg INTEGER DEFAULT 1;
ALTER TABLE task_groups ADD COLUMN has_bg INTEGER DEFAULT 1;
