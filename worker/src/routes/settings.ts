import { Hono } from 'hono';
import type { Env } from '../index';
import { DEFAULT_GROUP_KEY, parseGroupId } from '../utils/settings';

type Variables = { userId: string };

export const settingsRoutes = new Hono<{ Bindings: Env; Variables: Variables }>();

// GET /settings — Shared preferences. A default group that no longer exists reads as none.
settingsRoutes.get('/', async (c) => {
  const db = c.env.DB;

  try {
    const row = await db.prepare(
      `SELECT g.id FROM settings s JOIN task_groups g ON g.id = CAST(s.value AS INTEGER) WHERE s.key = ?`
    ).bind(DEFAULT_GROUP_KEY).first<{ id: number }>();

    return c.json({ settings: { default_group_id: row?.id ?? null } });
  } catch (error) {
    console.error('Error fetching settings:', error);
    return c.json({ error: 'Failed to fetch settings' }, 500);
  }
});

// PUT /settings — Update preferences. { default_group_id: number | null }
settingsRoutes.put('/', async (c) => {
  const db = c.env.DB;

  try {
    const body = await c.req.json();
    if ('default_group_id' in body) {
      const groupId = parseGroupId(body.default_group_id);
      if (groupId === undefined) {
        return c.json({ error: 'default_group_id must be a group id or null' }, 400);
      }
      if (groupId === null) {
        await db.prepare('DELETE FROM settings WHERE key = ?').bind(DEFAULT_GROUP_KEY).run();
      } else {
        const group = await db.prepare('SELECT id FROM task_groups WHERE id = ?').bind(groupId).first();
        if (!group) {
          return c.json({ error: 'Group not found' }, 404);
        }
        await db.prepare(
          'INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value'
        ).bind(DEFAULT_GROUP_KEY, String(groupId)).run();
      }
    }

    return c.json({ message: 'Settings updated' });
  } catch (error) {
    console.error('Error updating settings:', error);
    return c.json({ error: 'Failed to update settings' }, 500);
  }
});
