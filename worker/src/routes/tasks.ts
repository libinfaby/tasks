import { Hono } from 'hono';
import type { Env } from '../index';

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
    } else {
      query += ` AND (t.title LIKE ? OR t.details LIKE ?)`;
      params.push(`%${search}%`, `%${search}%`);
    }
  }

  query += ` ORDER BY t.is_completed ASC, t.priority DESC, t.position ASC, t.created_at DESC`;

  try {
    const { results: tasks } = await db.prepare(query).bind(...params).all();

    // Fetch subtasks and tags for each task
    const enrichedTasks = await Promise.all(
      (tasks || []).map(async (task: any) => {
        const { results: subtasks } = await db.prepare(
          `SELECT s.*, 
            (SELECT json_group_array(json_object('id', t.id, 'name', t.name, 'tag_type_id', t.tag_type_id, 'color', t.color,
              'type_name', (SELECT tt.name FROM tag_types tt WHERE tt.id = t.tag_type_id),
              'type_color', (SELECT tt.color FROM tag_types tt WHERE tt.id = t.tag_type_id)
            ))
            FROM subtask_tags st JOIN tags t ON st.tag_id = t.id WHERE st.subtask_id = s.id) as tags
           FROM subtasks s WHERE s.task_id = ? ORDER BY s.position ASC, s.created_at ASC`
        ).bind(task.id).all();

        const { results: tags } = await db.prepare(
          `SELECT t.*, tt.name as type_name, tt.color as type_color 
           FROM task_tags tg JOIN tags t ON tg.tag_id = t.id 
           LEFT JOIN tag_types tt ON t.tag_type_id = tt.id
           WHERE tg.task_id = ?`
        ).bind(task.id).all();

        const { results: group } = task.group_id
          ? await db.prepare(`SELECT * FROM task_groups WHERE id = ?`).bind(task.group_id).all()
          : { results: [] };

        return {
          ...task,
          subtasks: (subtasks || []).map((s: any) => ({
            ...s,
            tags: s.tags ? JSON.parse(s.tags).filter((t: any) => t.id !== null) : [],
          })),
          tags: tags || [],
          group: group?.[0] || null,
        };
      })
    );

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

    const task: any = tasks[0];

    const { results: subtasks } = await db.prepare(
      `SELECT s.*,
        (SELECT json_group_array(json_object('id', t.id, 'name', t.name, 'tag_type_id', t.tag_type_id, 'color', t.color,
          'type_name', (SELECT tt.name FROM tag_types tt WHERE tt.id = t.tag_type_id),
          'type_color', (SELECT tt.color FROM tag_types tt WHERE tt.id = t.tag_type_id)
        ))
        FROM subtask_tags st JOIN tags t ON st.tag_id = t.id WHERE st.subtask_id = s.id) as tags
       FROM subtasks s WHERE s.task_id = ? ORDER BY s.position ASC`
    ).bind(id).all();

    const { results: tags } = await db.prepare(
      `SELECT t.*, tt.name as type_name, tt.color as type_color 
       FROM task_tags tg JOIN tags t ON tg.tag_id = t.id 
       LEFT JOIN tag_types tt ON t.tag_type_id = tt.id
       WHERE tg.task_id = ?`
    ).bind(id).all();

    const { results: group } = task.group_id
      ? await db.prepare('SELECT * FROM task_groups WHERE id = ?').bind(task.group_id).all()
      : { results: [] };

    return c.json({
      task: {
        ...task,
        subtasks: (subtasks || []).map((s: any) => ({
          ...s,
          tags: s.tags ? JSON.parse(s.tags).filter((t: any) => t.id !== null) : [],
        })),
        tags: tags || [],
        group: group?.[0] || null,
      },
    });
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
    const { title, details, priority, date, deadline, group_id, subtasks, tag_ids } = body;

    if (!title || title.trim() === '') {
      return c.json({ error: 'Title is required' }, 400);
    }

    // Get next position
    const { results: posResult } = await db.prepare(
      'SELECT COALESCE(MAX(position), 0) + 1 as next_pos FROM tasks'
    ).all();
    const nextPos = (posResult as any)?.[0]?.next_pos || 0;

    const result = await db.prepare(
      `INSERT INTO tasks (title, details, priority, date, deadline, group_id, position) 
       VALUES (?, ?, ?, ?, ?, ?, ?)`
    ).bind(
      title.trim(),
      details?.trim() || null,
      priority || 0,
      date || null,
      deadline || null,
      group_id || null,
      nextPos
    ).run();

    const taskId = result.meta.last_row_id;

    // Add subtasks
    if (subtasks && Array.isArray(subtasks)) {
      for (let i = 0; i < subtasks.length; i++) {
        const sub = subtasks[i];
        const subResult = await db.prepare(
          'INSERT INTO subtasks (task_id, title, position) VALUES (?, ?, ?)'
        ).bind(taskId, sub.title.trim(), i).run();

        // Add subtask tags
        if (sub.tag_ids && Array.isArray(sub.tag_ids)) {
          for (const tagId of sub.tag_ids) {
            await db.prepare(
              'INSERT OR IGNORE INTO subtask_tags (subtask_id, tag_id) VALUES (?, ?)'
            ).bind(subResult.meta.last_row_id, tagId).run();
          }
        }
      }
    }

    // Add task tags
    if (tag_ids && Array.isArray(tag_ids)) {
      for (const tagId of tag_ids) {
        await db.prepare(
          'INSERT OR IGNORE INTO task_tags (task_id, tag_id) VALUES (?, ?)'
        ).bind(taskId, tagId).run();
      }
    }

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
    const { title, details, priority, date, deadline, group_id, position, tag_ids } = body;

    // Check task exists
    const { results: existing } = await db.prepare('SELECT id FROM tasks WHERE id = ?').bind(id).all();
    if (!existing || existing.length === 0) {
      return c.json({ error: 'Task not found' }, 404);
    }

    await db.prepare(
      `UPDATE tasks SET 
        title = COALESCE(?, title),
        details = ?,
        priority = COALESCE(?, priority),
        date = ?,
        deadline = ?,
        group_id = ?,
        position = COALESCE(?, position),
        updated_at = datetime('now')
       WHERE id = ?`
    ).bind(
      title?.trim() || null,
      details !== undefined ? (details?.trim() || null) : null,
      priority !== undefined ? priority : null,
      date !== undefined ? (date || null) : null,
      deadline !== undefined ? (deadline || null) : null,
      group_id !== undefined ? (group_id || null) : null,
      position !== undefined ? position : null,
      id
    ).run();

    // Update task tags if provided
    if (tag_ids !== undefined && Array.isArray(tag_ids)) {
      await db.prepare('DELETE FROM task_tags WHERE task_id = ?').bind(id).run();
      for (const tagId of tag_ids) {
        await db.prepare(
          'INSERT OR IGNORE INTO task_tags (task_id, tag_id) VALUES (?, ?)'
        ).bind(id, tagId).run();
      }
    }

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
