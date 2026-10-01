import { Hono } from 'hono';
import type { Env } from '../index';
import { enrichTasks, linkTagsStatement } from '../utils/tasks';

type Variables = { userId: string };

export const taskRoutes = new Hono<{ Bindings: Env; Variables: Variables }>();

// GET /tasks — List tasks with filters
taskRoutes.get('/', async (c) => {
  const db = c.env.DB;
  const completed = c.req.query('completed');
  const priority = c.req.query('priority');
  const groupId = c.req.query('group_id');
  const tagId = c.req.query('tag_id');
  const dateFrom = c.req.query('date_from');
  const dateTo = c.req.query('date_to');
  const search = c.req.query('search');
  const searchType = c.req.query('search_type') || 'task';
  const searchTagTypeId = c.req.query('search_tag_type');

  let query = `
    SELECT DISTINCT t.* FROM tasks t
    LEFT JOIN task_tags tt ON t.id = tt.task_id
    WHERE 1=1
  `;
  const params: any[] = [];

  if (completed !== undefined && completed !== '') {
    query += ` AND t.is_completed = ?`;
    params.push(completed === 'true' ? 1 : 0);
  }

  if (priority !== undefined && priority !== '') {
    query += ` AND t.priority = ?`;
    params.push(parseInt(priority));
  }

  if (groupId) {
    query += ` AND t.group_id = ?`;
    params.push(parseInt(groupId));
  }

  if (tagId) {
    query += ` AND tt.tag_id = ?`;
    params.push(parseInt(tagId));
  }

  if (dateFrom) {
    query += ` AND t.date >= ?`;
    params.push(dateFrom);
  }

  if (dateTo) {
    query += ` AND t.date <= ?`;
    params.push(dateTo);
  }

  if (search) {
    if (searchType === 'tag') {
      let tagSubQuery = `
        EXISTS (
          SELECT 1 FROM task_tags tt_search 
          JOIN tags tg_search ON tt_search.tag_id = tg_search.id
          WHERE tt_search.task_id = t.id AND tg_search.name LIKE ?
      `;
      params.push(`%${search}%`);
      
      if (searchTagTypeId) {
        tagSubQuery += ` AND tg_search.tag_type_id = ?`;
        params.push(parseInt(searchTagTypeId));
      }
      
      tagSubQuery += `)`;
      query += ` AND ${tagSubQuery}`;
    } else if (searchType === 'date') {
      query += ` AND (t.date LIKE ? OR t.reminder LIKE ?)`;
      params.push(`%${search}%`, `%${search}%`);
    } else {
      query += ` AND (t.title LIKE ? OR t.details LIKE ?)`;
      params.push(`%${search}%`, `%${search}%`);
    }
  }

  query += ` ORDER BY t.is_completed ASC, t.priority DESC, t.created_at DESC, t.id DESC`;

  try {
    const { results: tasks } = await db.prepare(query).bind(...params).all();

    const enrichedTasks = await enrichTasks(db, (tasks || []) as any[]);

    return c.json({ tasks: enrichedTasks });
  } catch (error) {
    console.error('Error fetching tasks:', error);
    return c.json({ error: 'Failed to fetch tasks' }, 500);
  }
});

// GET /tasks/:id — Get single task
taskRoutes.get('/:id', async (c) => {
  const db = c.env.DB;
  const id = parseInt(c.req.param('id'));

  try {
    const { results: tasks } = await db.prepare('SELECT * FROM tasks WHERE id = ?').bind(id).all();
    if (!tasks || tasks.length === 0) {
      return c.json({ error: 'Task not found' }, 404);
    }

    const [task] = await enrichTasks(db, [tasks[0]]);

    return c.json({ task });
  } catch (error) {
    console.error('Error fetching task:', error);
    return c.json({ error: 'Failed to fetch task' }, 500);
  }
});

// POST /tasks — Create task
taskRoutes.post('/', async (c) => {
  const db = c.env.DB;

  try {
    const body = await c.req.json();
    const { title, details, priority, date, reminder, group_id, subtasks, tag_ids } = body;

    if (!title || title.trim() === '') {
      return c.json({ error: 'Title is required' }, 400);
    }

    // Everything is applied in one atomic batch. Later statements reference the
    // rows just inserted via MAX(id), which is safe inside the batch transaction.
    const stmts: D1PreparedStatement[] = [
      db.prepare(
        `INSERT INTO tasks (title, details, priority, date, reminder, group_id, position)
         VALUES (?, ?, ?, ?, ?, ?, (SELECT COALESCE(MAX(position), 0) + 1 FROM tasks))`
      ).bind(
        title.trim(),
        details?.trim() || null,
        priority || 0,
        date || null,
        reminder || null,
        group_id || null
      ),
    ];

    if (subtasks && Array.isArray(subtasks)) {
      subtasks.forEach((sub: any, i: number) => {
        stmts.push(
          db.prepare(
            'INSERT INTO subtasks (task_id, title, position) VALUES ((SELECT MAX(id) FROM tasks), ?, ?)'
          ).bind(sub.title.trim(), i)
        );
        if (sub.tag_ids && Array.isArray(sub.tag_ids) && sub.tag_ids.length > 0) {
          stmts.push(
            linkTagsStatement(db, 'subtask_tags', 'SELECT MAX(id) FROM subtasks', [], sub.tag_ids)
          );
        }
      });
    }

    if (tag_ids && Array.isArray(tag_ids) && tag_ids.length > 0) {
      stmts.push(linkTagsStatement(db, 'task_tags', 'SELECT MAX(id) FROM tasks', [], tag_ids));
    }

    const batchResults = await db.batch(stmts);
    const taskId = batchResults[0].meta.last_row_id;

    return c.json({ id: taskId, message: 'Task created' }, 201);
  } catch (error) {
    console.error('Error creating task:', error);
    return c.json({ error: 'Failed to create task' }, 500);
  }
});

// PUT /tasks/:id — Update task
taskRoutes.put('/:id', async (c) => {
  const db = c.env.DB;
  const id = parseInt(c.req.param('id'));

  try {
    const body = await c.req.json();
    const { title, details, priority, date, reminder, group_id, position, tag_ids } = body;

    // Check task exists
    const { results: existing } = await db.prepare('SELECT id, reminder, is_notified FROM tasks WHERE id = ?').bind(id).all();
    if (!existing || existing.length === 0) {
      return c.json({ error: 'Task not found' }, 404);
    }
    const current = existing[0] as any;
    let newIsNotified = current.is_notified;
    if (reminder !== undefined && reminder !== current.reminder) {
      newIsNotified = 0;
    }

    const stmts: D1PreparedStatement[] = [
      db.prepare(
        `UPDATE tasks SET
          title = COALESCE(?, title),
          details = ?,
          priority = COALESCE(?, priority),
          date = ?,
          reminder = ?,
          is_notified = ?,
          group_id = ?,
          position = COALESCE(?, position),
          updated_at = datetime('now')
         WHERE id = ?`
      ).bind(
        title?.trim() || null,
        details !== undefined ? (details?.trim() || null) : null,
        priority !== undefined ? priority : null,
        date !== undefined ? (date || null) : null,
        reminder !== undefined ? (reminder || null) : null,
        newIsNotified,
        group_id !== undefined ? (group_id || null) : null,
        position !== undefined ? position : null,
        id
      ),
    ];

    // Replace task tags if provided
    if (tag_ids !== undefined && Array.isArray(tag_ids)) {
      stmts.push(db.prepare('DELETE FROM task_tags WHERE task_id = ?').bind(id));
      if (tag_ids.length > 0) {
        stmts.push(linkTagsStatement(db, 'task_tags', '?', [id], tag_ids));
      }
    }

    await db.batch(stmts);

    return c.json({ message: 'Task updated' });
  } catch (error) {
    console.error('Error updating task:', error);
    return c.json({ error: 'Failed to update task' }, 500);
  }
});

// DELETE /tasks/:id — Delete task
taskRoutes.delete('/:id', async (c) => {
  const db = c.env.DB;
  const id = parseInt(c.req.param('id'));

  try {
    const result = await db.prepare('DELETE FROM tasks WHERE id = ?').bind(id).run();
    if (result.meta.changes === 0) {
      return c.json({ error: 'Task not found' }, 404);
    }
    return c.json({ message: 'Task deleted' });
  } catch (error) {
    console.error('Error deleting task:', error);
    return c.json({ error: 'Failed to delete task' }, 500);
  }
});

// PATCH /tasks/:id/toggle — Toggle completion
taskRoutes.patch('/:id/toggle', async (c) => {
  const db = c.env.DB;
  const id = parseInt(c.req.param('id'));

  try {
    const result = await db.prepare(
      `UPDATE tasks SET is_completed = NOT is_completed, updated_at = datetime('now') WHERE id = ?`
    ).bind(id).run();

    if (result.meta.changes === 0) {
      return c.json({ error: 'Task not found' }, 404);
    }

    return c.json({ message: 'Task toggled' });
  } catch (error) {
    console.error('Error toggling task:', error);
    return c.json({ error: 'Failed to toggle task' }, 500);
  }
});

// POST /tasks/subscribe — Save a push subscription
taskRoutes.post('/subscribe', async (c) => {
  const db = c.env.DB;
  try {
    const { endpoint, keys } = await c.req.json();
    if (!endpoint || !keys || !keys.p256dh || !keys.auth) {
      return c.json({ error: 'Invalid subscription object' }, 400);
    }
    await db.prepare(
      'INSERT OR REPLACE INTO push_subscriptions (endpoint, p256dh, auth) VALUES (?, ?, ?)'
    ).bind(endpoint, keys.p256dh, keys.auth).run();
    return c.json({ message: 'Subscribed successfully' });
  } catch (error: any) {
    console.error('Error subscribing:', error);
    return c.json({ error: error.message || 'Failed to subscribe' }, 500);
  }
});

// POST /tasks/unsubscribe — Remove a push subscription
taskRoutes.post('/unsubscribe', async (c) => {
  const db = c.env.DB;
  try {
    const { endpoint } = await c.req.json();
    if (!endpoint) {
      return c.json({ error: 'Endpoint is required' }, 400);
    }
    await db.prepare('DELETE FROM push_subscriptions WHERE endpoint = ?').bind(endpoint).run();
    return c.json({ message: 'Unsubscribed successfully' });
  } catch (error: any) {
    console.error('Error unsubscribing:', error);
    return c.json({ error: error.message || 'Failed to unsubscribe' }, 500);
  }
});
