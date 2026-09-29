import { Hono } from 'hono';
import type { Env } from '../index';

type Variables = { userId: string };

export const dailyRoutes = new Hono<{ Bindings: Env; Variables: Variables }>();

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

// GET /daily?date=YYYY-MM-DD  or  ?from=YYYY-MM-DD&to=YYYY-MM-DD — list log entries
dailyRoutes.get('/', async (c) => {
  const db = c.env.DB;
  const date = c.req.query('date');
  const from = c.req.query('from');
  const to = c.req.query('to');

  for (const d of [date, from, to]) {
    if (d && !DATE_RE.test(d)) return c.json({ error: 'Dates must be YYYY-MM-DD' }, 400);
  }

  let query = 'SELECT * FROM daily_logs WHERE 1=1';
  const params: any[] = [];
  if (date) {
    query += ' AND log_date = ?';
    params.push(date);
  }
  if (from) {
    query += ' AND log_date >= ?';
    params.push(from);
  }
  if (to) {
    query += ' AND log_date <= ?';
    params.push(to);
  }
  query += ' ORDER BY log_date DESC, id ASC';

  try {
    const { results } = await db.prepare(query).bind(...params).all();
    return c.json({ logs: results || [] });
  } catch (error) {
    console.error('Error fetching daily logs:', error);
    return c.json({ error: 'Failed to fetch daily logs' }, 500);
  }
});

// POST /daily — Add an entry { date, text }
dailyRoutes.post('/', async (c) => {
  const db = c.env.DB;

  try {
    const { date, text } = await c.req.json();

    if (!date || !DATE_RE.test(date)) {
      return c.json({ error: 'A valid date (YYYY-MM-DD) is required' }, 400);
    }
    if (!text || typeof text !== 'string' || text.trim() === '') {
      return c.json({ error: 'Text is required' }, 400);
    }

    const result = await db.prepare(
      'INSERT INTO daily_logs (log_date, text) VALUES (?, ?)'
    ).bind(date, text.trim()).run();

    return c.json({ id: result.meta.last_row_id, message: 'Entry added' }, 201);
  } catch (error) {
    console.error('Error creating daily log:', error);
    return c.json({ error: 'Failed to add entry' }, 500);
  }
});

// PUT /daily/:id — Edit an entry { text }
dailyRoutes.put('/:id', async (c) => {
  const db = c.env.DB;
  const id = parseInt(c.req.param('id'));

  try {
    const { text } = await c.req.json();
    if (!text || typeof text !== 'string' || text.trim() === '') {
      return c.json({ error: 'Text is required' }, 400);
    }

    const result = await db.prepare(
      'UPDATE daily_logs SET text = ? WHERE id = ?'
    ).bind(text.trim(), id).run();

    if (result.meta.changes === 0) {
      return c.json({ error: 'Entry not found' }, 404);
    }
    return c.json({ message: 'Entry updated' });
  } catch (error) {
    console.error('Error updating daily log:', error);
    return c.json({ error: 'Failed to update entry' }, 500);
  }
});

// DELETE /daily/:id — Delete an entry
dailyRoutes.delete('/:id', async (c) => {
  const db = c.env.DB;
  const id = parseInt(c.req.param('id'));

  try {
    const result = await db.prepare('DELETE FROM daily_logs WHERE id = ?').bind(id).run();
    if (result.meta.changes === 0) {
      return c.json({ error: 'Entry not found' }, 404);
    }
    return c.json({ message: 'Entry deleted' });
  } catch (error) {
    console.error('Error deleting daily log:', error);
    return c.json({ error: 'Failed to delete entry' }, 500);
  }
});
