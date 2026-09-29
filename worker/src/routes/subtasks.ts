import { Hono } from 'hono';
import type { Env } from '../index';
import { linkTagsStatement } from '../utils/tasks';

type Variables = { userId: string };

export const subtaskRoutes = new Hono<{ Bindings: Env; Variables: Variables }>();

// POST /subtasks — Create subtask (expects task_id in body)
subtaskRoutes.post('/', async (c) => {
  const db = c.env.DB;

  try {
    const body = await c.req.json();
    const { task_id, title, tag_ids } = body;

    if (!task_id || !title || title.trim() === '') {
      return c.json({ error: 'task_id and title are required' }, 400);
    }

    // Verify task exists
    const { results: taskCheck } = await db.prepare('SELECT id FROM tasks WHERE id = ?').bind(task_id).all();
    if (!taskCheck || taskCheck.length === 0) {
      return c.json({ error: 'Task not found' }, 404);
    }

    // Insert subtask and its tags atomically
    const stmts: D1PreparedStatement[] = [
      db.prepare(
        `INSERT INTO subtasks (task_id, title, position)
         VALUES (?, ?, (SELECT COALESCE(MAX(position), 0) + 1 FROM subtasks WHERE task_id = ?))`
      ).bind(task_id, title.trim(), task_id),
    ];
    if (tag_ids && Array.isArray(tag_ids) && tag_ids.length > 0) {
      stmts.push(linkTagsStatement(db, 'subtask_tags', 'SELECT MAX(id) FROM subtasks', [], tag_ids));
    }
    const batchResults = await db.batch(stmts);
    const subtaskId = batchResults[0].meta.last_row_id;

    return c.json({ id: subtaskId, message: 'Subtask created' }, 201);
  } catch (error) {
    console.error('Error creating subtask:', error);
    return c.json({ error: 'Failed to create subtask' }, 500);
  }
});

// PUT /subtasks/:id — Update subtask
subtaskRoutes.put('/:id', async (c) => {
  const db = c.env.DB;
  const id = parseInt(c.req.param('id'));

  try {
    const body = await c.req.json();
    const { title, position, tag_ids } = body;

    const { results: existing } = await db.prepare('SELECT id FROM subtasks WHERE id = ?').bind(id).all();
    if (!existing || existing.length === 0) {
      return c.json({ error: 'Subtask not found' }, 404);
    }

    const stmts: D1PreparedStatement[] = [
      db.prepare(
        `UPDATE subtasks SET
          title = COALESCE(?, title),
          position = COALESCE(?, position)
         WHERE id = ?`
      ).bind(title?.trim() || null, position !== undefined ? position : null, id),
    ];

    // Replace tags if provided
    if (tag_ids !== undefined && Array.isArray(tag_ids)) {
      stmts.push(db.prepare('DELETE FROM subtask_tags WHERE subtask_id = ?').bind(id));
      if (tag_ids.length > 0) {
        stmts.push(linkTagsStatement(db, 'subtask_tags', '?', [id], tag_ids));
      }
    }

    await db.batch(stmts);

    return c.json({ message: 'Subtask updated' });
  } catch (error) {
    console.error('Error updating subtask:', error);
    return c.json({ error: 'Failed to update subtask' }, 500);
  }
});

// DELETE /subtasks/:id — Delete subtask
subtaskRoutes.delete('/:id', async (c) => {
  const db = c.env.DB;
  const id = parseInt(c.req.param('id'));

  try {
    const result = await db.prepare('DELETE FROM subtasks WHERE id = ?').bind(id).run();
    if (result.meta.changes === 0) {
      return c.json({ error: 'Subtask not found' }, 404);
    }
    return c.json({ message: 'Subtask deleted' });
  } catch (error) {
    console.error('Error deleting subtask:', error);
    return c.json({ error: 'Failed to delete subtask' }, 500);
  }
});

// PATCH /subtasks/:id/toggle — Toggle subtask completion
subtaskRoutes.patch('/:id/toggle', async (c) => {
  const db = c.env.DB;
  const id = parseInt(c.req.param('id'));

  try {
    const result = await db.prepare(
      'UPDATE subtasks SET is_completed = NOT is_completed WHERE id = ?'
    ).bind(id).run();

    if (result.meta.changes === 0) {
      return c.json({ error: 'Subtask not found' }, 404);
    }

    return c.json({ message: 'Subtask toggled' });
  } catch (error) {
    console.error('Error toggling subtask:', error);
    return c.json({ error: 'Failed to toggle subtask' }, 500);
  }
});
