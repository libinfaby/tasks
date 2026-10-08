import { Hono } from 'hono';
import type { Env } from '../index';
import { DEFAULT_GROUP_KEY, HIDDEN_GROUPS_KEY, decodeGroupIds, parseGroupId, parseGroupIds } from '../utils/settings';

type Variables = { userId: string };

export const settingsRoutes = new Hono<{ Bindings: Env; Variables: Variables }>();

/** The shared settings, reading groups that no longer exist as unset. */
async function readSettings(db: D1Database) {
  const [{ results: rows }, { results: groups }] = await Promise.all([
    db.prepare('SELECT key, value FROM settings WHERE key IN (?, ?)').bind(DEFAULT_GROUP_KEY, HIDDEN_GROUPS_KEY).all<{ key: string; value: string }>(),
    db.prepare('SELECT id FROM task_groups').all<{ id: number }>(),
  ]);
  const exists = new Set((groups || []).map(g => g.id));
  const value = (key: string) => (rows || []).find(r => r.key === key)?.value;
  const defaultGroupId = Number(value(DEFAULT_GROUP_KEY));
  return {
    default_group_id: exists.has(defaultGroupId) ? defaultGroupId : null,
    hidden_group_ids: decodeGroupIds(value(HIDDEN_GROUPS_KEY)).filter(id => exists.has(id)),
  };
}

// GET /settings — Shared preferences. Groups that no longer exist read as unset.
settingsRoutes.get('/', async (c) => {
  try {
    return c.json({ settings: await readSettings(c.env.DB) });
  } catch (error) {
    console.error('Error fetching settings:', error);
    return c.json({ error: 'Failed to fetch settings' }, 500);
  }
});

// PUT /settings — Update preferences. { default_group_id?: number | null, hidden_group_ids?: number[] }
// The default group can't also be hidden, or new tasks would vanish from the view they were added in.
settingsRoutes.put('/', async (c) => {
  const db = c.env.DB;

  try {
    const body = await c.req.json();
    const current = await readSettings(db);
    let defaultGroupId = current.default_group_id;
    let hiddenGroupIds = current.hidden_group_ids;

    if ('default_group_id' in body) {
      const groupId = parseGroupId(body.default_group_id);
      if (groupId === undefined) {
        return c.json({ error: 'default_group_id must be a group id or null' }, 400);
      }
      defaultGroupId = groupId;
    }
    if ('hidden_group_ids' in body) {
      const ids = parseGroupIds(body.hidden_group_ids);
      if (ids === undefined) {
        return c.json({ error: 'hidden_group_ids must be a list of group ids' }, 400);
      }
      hiddenGroupIds = ids;
    }

    const wanted = [...hiddenGroupIds, ...(defaultGroupId ? [defaultGroupId] : [])];
    if (wanted.length) {
      const { results } = await db.prepare(
        `SELECT id FROM task_groups WHERE id IN (${wanted.map(() => '?').join(', ')})`
      ).bind(...wanted).all<{ id: number }>();
      if (new Set((results || []).map(g => g.id)).size !== new Set(wanted).size) {
        return c.json({ error: 'Group not found' }, 404);
      }
    }
    if (defaultGroupId && hiddenGroupIds.includes(defaultGroupId)) {
      return c.json({ error: 'The default group can’t be hidden' }, 400);
    }

    const upsert = 'INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value';
    await db.batch([
      defaultGroupId === null
        ? db.prepare('DELETE FROM settings WHERE key = ?').bind(DEFAULT_GROUP_KEY)
        : db.prepare(upsert).bind(DEFAULT_GROUP_KEY, String(defaultGroupId)),
      hiddenGroupIds.length === 0
        ? db.prepare('DELETE FROM settings WHERE key = ?').bind(HIDDEN_GROUPS_KEY)
        : db.prepare(upsert).bind(HIDDEN_GROUPS_KEY, JSON.stringify(hiddenGroupIds)),
    ]);

    return c.json({ message: 'Settings updated', settings: { default_group_id: defaultGroupId, hidden_group_ids: hiddenGroupIds } });
  } catch (error) {
    console.error('Error updating settings:', error);
    return c.json({ error: 'Failed to update settings' }, 500);
  }
});
