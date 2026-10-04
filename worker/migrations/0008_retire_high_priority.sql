-- High priority (1) is retired: tasks are normal (0) or urgent (2). Existing high tasks become urgent.
UPDATE tasks SET priority = 2 WHERE priority = 1;
