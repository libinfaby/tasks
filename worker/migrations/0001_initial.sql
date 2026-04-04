-- Tasks Database Schema
-- Migration: 0001_initial

-- Tag types: project, client, bug, requirement, feature, etc.
CREATE TABLE IF NOT EXISTS tag_types (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT UNIQUE NOT NULL,
  color TEXT NOT NULL DEFAULT '#6366f1',
  icon TEXT,
  created_at TEXT DEFAULT (datetime('now'))
);

-- Tags: Finance, HRM, XYZ, etc. Each belongs to a tag_type
CREATE TABLE IF NOT EXISTS tags (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  tag_type_id INTEGER NOT NULL REFERENCES tag_types(id) ON DELETE CASCADE,
  color TEXT,
  created_at TEXT DEFAULT (datetime('now')),
  UNIQUE(name, tag_type_id)
);

-- Task groups for organizing similar tasks
CREATE TABLE IF NOT EXISTS task_groups (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  color TEXT NOT NULL DEFAULT '#8b5cf6',
  position INTEGER DEFAULT 0,
  created_at TEXT DEFAULT (datetime('now'))
);

-- Main tasks
CREATE TABLE IF NOT EXISTS tasks (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title TEXT NOT NULL,
  details TEXT,
  is_completed INTEGER NOT NULL DEFAULT 0,
  priority INTEGER NOT NULL DEFAULT 0,
  date TEXT,
  deadline TEXT,
  group_id INTEGER REFERENCES task_groups(id) ON DELETE SET NULL,
  position INTEGER NOT NULL DEFAULT 0,
  created_at TEXT DEFAULT (datetime('now')),
  updated_at TEXT DEFAULT (datetime('now'))
);

-- Subtasks (belong to a task)
CREATE TABLE IF NOT EXISTS subtasks (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  task_id INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  is_completed INTEGER NOT NULL DEFAULT 0,
  position INTEGER NOT NULL DEFAULT 0,
  created_at TEXT DEFAULT (datetime('now'))
);

-- Many-to-many: tasks <-> tags
CREATE TABLE IF NOT EXISTS task_tags (
  task_id INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
  PRIMARY KEY (task_id, tag_id)
);

-- Many-to-many: subtasks <-> tags
CREATE TABLE IF NOT EXISTS subtask_tags (
  subtask_id INTEGER NOT NULL REFERENCES subtasks(id) ON DELETE CASCADE,
  tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
  PRIMARY KEY (subtask_id, tag_id)
);

-- Performance indexes
CREATE INDEX IF NOT EXISTS idx_tasks_group ON tasks(group_id);
CREATE INDEX IF NOT EXISTS idx_tasks_priority ON tasks(priority DESC);
CREATE INDEX IF NOT EXISTS idx_tasks_date ON tasks(date);
CREATE INDEX IF NOT EXISTS idx_tasks_completed ON tasks(is_completed);
CREATE INDEX IF NOT EXISTS idx_tasks_position ON tasks(position);
CREATE INDEX IF NOT EXISTS idx_subtasks_task ON subtasks(task_id);
CREATE INDEX IF NOT EXISTS idx_subtasks_position ON subtasks(task_id, position);
CREATE INDEX IF NOT EXISTS idx_tags_type ON tags(tag_type_id);

-- Seed default tag types
INSERT OR IGNORE INTO tag_types (name, color, icon) VALUES
  ('Project', '#6366f1', '📁'),
  ('Client', '#f59e0b', '🏢'),
  ('Bug', '#ef4444', '🐛'),
  ('Requirement', '#10b981', '📋'),
  ('Feature', '#3b82f6', '✨');
