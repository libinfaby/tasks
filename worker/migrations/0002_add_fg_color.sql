-- Add fg_color to tag_types, tags, and task_groups
ALTER TABLE tag_types ADD COLUMN fg_color TEXT NOT NULL DEFAULT '#ffffff';
ALTER TABLE tags ADD COLUMN fg_color TEXT;
ALTER TABLE task_groups ADD COLUMN fg_color TEXT NOT NULL DEFAULT '#ffffff';
