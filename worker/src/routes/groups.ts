import { Hono } from 'hono';
import type { Env } from '../index';

type Variables = { userId: string };

export const groupRoutes = new Hono<{ Bindings: Env; Variables: Variables }>();

// GET /groups — List all groups with task counts
groupRoutes.get('/', async (c) => {
  const db = c.env.DB;

  try {
    const { results: groups } = await db.prepare(
      `SELECT g.*, 
        (SELECT COUNT(*) FROM tasks t WHERE t.group_id = g.id) as task_count,
        (SELECT COUNT(*) FROM tasks t WHERE t.group_id = g.id AND t.is_completed = 0) as active_task_count
       FROM task_groups g
       ORDER BY g.position ASC, g.name ASC`
    ).all();

    return c.json({ groups: groups || [] });
  } catch (error) {
    console.error('Error fetching groups:', error);
    return c.json({ error: 'Failed to fetch groups' }, 500);
  }
});

// GET /groups/:id — Get single group with its tasks
groupRoutes.get('/:id', async (c) => {
  const db = c.env.DB;
  const id = parseInt(c.req.param('id'));

  try {
    const { results: groups } = await db.prepare(
      'SELECT * FROM task_groups WHERE id = ?'
    ).bind(id).all();

    if (!groups || groups.length === 0) {
      return c.json({ error: 'Group not found' }, 404);
    }

    return c.json({ group: groups[0] });
  } catch (error) {
    console.error('Error fetching group:', error);
    return c.json({ error: 'Failed to fetch group' }, 500);
  }
});

// POST /groups — Create group
groupRoutes.post('/', async (c) => {
  const db = c.env.DB;

  try {
    const body = await c.req.json();
    const { name, color } = body;

    if (!name || name.trim() === '') {
      return c.json({ error: 'Name is required' }, 400);
    }

    // Get next position
    const { results: posResult } = await db.prepare(
      'SELECT COALESCE(MAX(position), 0) + 1 as next_pos FROM task_groups'
    ).all();
    const nextPos = (posResult as any)?.[0]?.next_pos || 0;

    const result = await db.prepare(
      'INSERT INTO task_groups (name, color, fg_color, position, has_bg) VALUES (?, ?, ?, ?, ?)'
    ).bind(name.trim(), color || '#8b5cf6', body.fg_color || '#ffffff', nextPos, body.has_bg !== undefined ? (body.has_bg ? 1 : 0) : 1).run();

    return c.json({ id: result.meta.last_row_id, message: 'Group created' }, 201);
  } catch (error) {
    console.error('Error creating group:', error);
    return c.json({ error: 'Failed to create group' }, 500);
  }
});

// PUT /groups/:id — Update group
groupRoutes.put('/:id', async (c) => {
  const db = c.env.DB;
  const id = parseInt(c.req.param('id'));

  try {
    const body = await c.req.json();
    const { name, color, position } = body;

    const result = await db.prepare(
      `UPDATE task_groups SET 
        name = COALESCE(?, name),
        color = COALESCE(?, color),
        fg_color = COALESCE(?, fg_color),
        position = COALESCE(?, position),
        has_bg = COALESCE(?, has_bg)
       WHERE id = ?`
    ).bind(name?.trim() || null, color || null, body.fg_color || null, position !== undefined ? position : null, body.has_bg !== undefined ? (body.has_bg ? 1 : 0) : null, id).run();

    if (result.meta.changes === 0) {
      return c.json({ error: 'Group not found' }, 404);
    }

    return c.json({ message: 'Group updated' });
  } catch (error) {
    console.error('Error updating group:', error);
    return c.json({ error: 'Failed to update group' }, 500);
  }
});

// DELETE /groups/:id — Delete group (tasks are ungrouped, not deleted)
groupRoutes.delete('/:id', async (c) => {
  const db = c.env.DB;
  const id = parseInt(c.req.param('id'));

  try {
    // Ungroup tasks first
    await db.prepare('UPDATE tasks SET group_id = NULL WHERE group_id = ?').bind(id).run();

    const result = await db.prepare('DELETE FROM task_groups WHERE id = ?').bind(id).run();
    if (result.meta.changes === 0) {
      return c.json({ error: 'Group not found' }, 404);
    }

    return c.json({ message: 'Group deleted' });
  } catch (error) {
    console.error('Error deleting group:', error);
    return c.json({ error: 'Failed to delete group' }, 500);
  }
});
