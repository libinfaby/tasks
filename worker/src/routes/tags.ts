import { Hono } from 'hono';
import type { Env } from '../index';

type Variables = { userId: string };

export const tagRoutes = new Hono<{ Bindings: Env; Variables: Variables }>();

// ==================== TAG TYPES ====================

// GET /tag-types — List all tag types with their tags
tagRoutes.get('/', async (c) => {
  const db = c.env.DB;

  try {
    const { results: tagTypes } = await db.prepare(
      'SELECT * FROM tag_types ORDER BY name ASC'
    ).all();

    const enriched = await Promise.all(
      (tagTypes || []).map(async (type: any) => {
        const { results: tags } = await db.prepare(
          'SELECT * FROM tags WHERE tag_type_id = ? ORDER BY name ASC'
        ).bind(type.id).all();
        return { ...type, tags: tags || [] };
      })
    );

    return c.json({ tag_types: enriched });
  } catch (error) {
    console.error('Error fetching tag types:', error);
    return c.json({ error: 'Failed to fetch tag types' }, 500);
  }
});

// POST /tag-types — Create tag type
tagRoutes.post('/', async (c) => {
  const db = c.env.DB;

  try {
    const body = await c.req.json();
    const { name, color, icon } = body;

    if (!name || name.trim() === '') {
      return c.json({ error: 'Name is required' }, 400);
    }

    const result = await db.prepare(
      'INSERT INTO tag_types (name, color, icon) VALUES (?, ?, ?)'
    ).bind(name.trim(), color || '#6366f1', icon || null).run();

    return c.json({ id: result.meta.last_row_id, message: 'Tag type created' }, 201);
  } catch (error: any) {
    if (error?.message?.includes('UNIQUE')) {
      return c.json({ error: 'Tag type already exists' }, 409);
    }
    console.error('Error creating tag type:', error);
    return c.json({ error: 'Failed to create tag type' }, 500);
  }
});

// PUT /tag-types/:id — Update tag type
tagRoutes.put('/:id', async (c) => {
  const db = c.env.DB;
  const id = parseInt(c.req.param('id'));

  try {
    const body = await c.req.json();
    const { name, color, icon } = body;

    const result = await db.prepare(
      `UPDATE tag_types SET 
        name = COALESCE(?, name),
        color = COALESCE(?, color),
        icon = COALESCE(?, icon)
       WHERE id = ?`
    ).bind(name?.trim() || null, color || null, icon || null, id).run();

    if (result.meta.changes === 0) {
      return c.json({ error: 'Tag type not found' }, 404);
    }

    return c.json({ message: 'Tag type updated' });
  } catch (error) {
    console.error('Error updating tag type:', error);
    return c.json({ error: 'Failed to update tag type' }, 500);
  }
});

// DELETE /tag-types/:id — Delete tag type
tagRoutes.delete('/:id', async (c) => {
  const db = c.env.DB;
  const id = parseInt(c.req.param('id'));

  try {
    const result = await db.prepare('DELETE FROM tag_types WHERE id = ?').bind(id).run();
    if (result.meta.changes === 0) {
      return c.json({ error: 'Tag type not found' }, 404);
    }
    return c.json({ message: 'Tag type deleted' });
  } catch (error) {
    console.error('Error deleting tag type:', error);
    return c.json({ error: 'Failed to delete tag type' }, 500);
  }
});

// ==================== TAGS ====================

// GET /tags — List all tags (flat list with type info)
tagRoutes.get('/tags', async (c) => {
  const db = c.env.DB;

  try {
    const { results: tags } = await db.prepare(
      `SELECT t.*, tt.name as type_name, tt.color as type_color, tt.icon as type_icon
       FROM tags t LEFT JOIN tag_types tt ON t.tag_type_id = tt.id
       ORDER BY tt.name ASC, t.name ASC`
    ).all();

    return c.json({ tags: tags || [] });
  } catch (error) {
    console.error('Error fetching tags:', error);
    return c.json({ error: 'Failed to fetch tags' }, 500);
  }
});

// POST /tags/create — Create tag
tagRoutes.post('/tags', async (c) => {
  const db = c.env.DB;

  try {
    const body = await c.req.json();
    const { name, tag_type_id, color } = body;

    if (!name || name.trim() === '' || !tag_type_id) {
      return c.json({ error: 'Name and tag_type_id are required' }, 400);
    }

    // Verify tag type exists
    const { results: typeCheck } = await db.prepare(
      'SELECT id FROM tag_types WHERE id = ?'
    ).bind(tag_type_id).all();
    if (!typeCheck || typeCheck.length === 0) {
      return c.json({ error: 'Tag type not found' }, 404);
    }

    const result = await db.prepare(
      'INSERT INTO tags (name, tag_type_id, color) VALUES (?, ?, ?)'
    ).bind(name.trim(), tag_type_id, color || null).run();

    return c.json({ id: result.meta.last_row_id, message: 'Tag created' }, 201);
  } catch (error: any) {
    if (error?.message?.includes('UNIQUE')) {
      return c.json({ error: 'Tag already exists under this type' }, 409);
    }
    console.error('Error creating tag:', error);
    return c.json({ error: 'Failed to create tag' }, 500);
  }
});

// PUT /tags/:id — Update tag
tagRoutes.put('/tags/:id', async (c) => {
  const db = c.env.DB;
  const id = parseInt(c.req.param('id'));

  try {
    const body = await c.req.json();
    const { name, tag_type_id, color } = body;

    const result = await db.prepare(
      `UPDATE tags SET 
        name = COALESCE(?, name),
        tag_type_id = COALESCE(?, tag_type_id),
        color = COALESCE(?, color)
       WHERE id = ?`
    ).bind(name?.trim() || null, tag_type_id || null, color || null, id).run();

    if (result.meta.changes === 0) {
      return c.json({ error: 'Tag not found' }, 404);
    }

    return c.json({ message: 'Tag updated' });
  } catch (error) {
    console.error('Error updating tag:', error);
    return c.json({ error: 'Failed to update tag' }, 500);
  }
});

// DELETE /tags/:id — Delete tag
tagRoutes.delete('/tags/:id', async (c) => {
  const db = c.env.DB;
  const id = parseInt(c.req.param('id'));

  try {
    const result = await db.prepare('DELETE FROM tags WHERE id = ?').bind(id).run();
    if (result.meta.changes === 0) {
      return c.json({ error: 'Tag not found' }, 404);
    }
    return c.json({ message: 'Tag deleted' });
  } catch (error) {
    console.error('Error deleting tag:', error);
    return c.json({ error: 'Failed to delete tag' }, 500);
  }
});
