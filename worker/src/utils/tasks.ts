// Shared query helpers for tasks / subtasks / tags.
// D1 allows at most 100 bound parameters per statement, so id lists are chunked.

const CHUNK_SIZE = 90;

function chunk<T>(items: T[], size = CHUNK_SIZE): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < items.length; i += size) out.push(items.slice(i, i + size));
  return out;
}

const placeholders = (n: number) => new Array(n).fill('?').join(',');

// Attach subtasks (with their tags), tags and group to each task using a fixed
// number of queries, regardless of how many tasks there are.
export async function enrichTasks(db: D1Database, tasks: any[]): Promise<any[]> {
  if (tasks.length === 0) return [];

  const ids = tasks.map((t) => t.id);
  const subtasksByTask = new Map<number, any[]>();
  const tagsByTask = new Map<number, any[]>();

  const stmts: D1PreparedStatement[] = [];
  for (const part of chunk(ids)) {
    stmts.push(
      db.prepare(
        `SELECT s.*,
          (SELECT json_group_array(json_object('id', t.id, 'name', t.name, 'tag_type_id', t.tag_type_id, 'color', t.color,
            'type_name', (SELECT tt.name FROM tag_types tt WHERE tt.id = t.tag_type_id),
            'type_color', (SELECT tt.color FROM tag_types tt WHERE tt.id = t.tag_type_id)
          ))
          FROM subtask_tags st JOIN tags t ON st.tag_id = t.id WHERE st.subtask_id = s.id) as tags
         FROM subtasks s WHERE s.task_id IN (${placeholders(part.length)})
         ORDER BY s.position ASC, s.created_at ASC`
      ).bind(...part),
      db.prepare(
        `SELECT tg.task_id as task_id, t.*, tt.name as type_name, tt.color as type_color
         FROM task_tags tg JOIN tags t ON tg.tag_id = t.id
         LEFT JOIN tag_types tt ON t.tag_type_id = tt.id
         WHERE tg.task_id IN (${placeholders(part.length)})`
      ).bind(...part)
    );
  }
  stmts.push(db.prepare('SELECT * FROM task_groups'));

  const results = await db.batch(stmts);

  for (let i = 0; i < results.length - 1; i += 2) {
    for (const s of (results[i].results || []) as any[]) {
      const list = subtasksByTask.get(s.task_id) || [];
      list.push({
        ...s,
        tags: s.tags ? JSON.parse(s.tags).filter((t: any) => t.id !== null) : [],
      });
      subtasksByTask.set(s.task_id, list);
    }
    for (const t of (results[i + 1].results || []) as any[]) {
      const { task_id, ...tag } = t;
      const list = tagsByTask.get(task_id) || [];
      list.push(tag);
      tagsByTask.set(task_id, list);
    }
  }

  const groups = new Map<number, any>();
  for (const g of (results[results.length - 1].results || []) as any[]) groups.set(g.id, g);

  return tasks.map((task) => ({
    ...task,
    subtasks: subtasksByTask.get(task.id) || [],
    tags: tagsByTask.get(task.id) || [],
    group: task.group_id ? groups.get(task.group_id) || null : null,
  }));
}

// One statement that links every id in `tagIds` to the row that `ownerSql` yields.
// `join` is e.g. 'task_tags' / 'subtask_tags'.
export function linkTagsStatement(
  db: D1Database,
  join: 'task_tags' | 'subtask_tags',
  ownerSql: string,
  ownerParams: any[],
  tagIds: any[]
): D1PreparedStatement {
  const ownerCol = join === 'task_tags' ? 'task_id' : 'subtask_id';
  return db
    .prepare(
      `INSERT OR IGNORE INTO ${join} (${ownerCol}, tag_id)
       SELECT (${ownerSql}), value FROM json_each(?)`
    )
    .bind(...ownerParams, JSON.stringify(tagIds));
}
