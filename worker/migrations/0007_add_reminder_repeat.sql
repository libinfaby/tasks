-- Migration: add_reminder_repeat
-- Recurring reminders: NULL | 'daily' | 'weekdays' | 'weekly' | 'monthly'
ALTER TABLE tasks ADD COLUMN reminder_repeat TEXT;
