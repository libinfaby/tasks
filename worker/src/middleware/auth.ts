import { Context, Next } from 'hono';
import type { Env } from '../index';
import { verifyToken } from '../utils/jwt';

type Variables = {
  userId: string;
};

export async function authMiddleware(c: Context<{ Bindings: Env; Variables: Variables }>, next: Next) {
  const authHeader = c.req.header('Authorization');

  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return c.json({ error: 'Missing or invalid authorization header' }, 401);
  }

  const token = authHeader.substring(7);

  try {
    const payload = await verifyToken(token, c.env.JWT_SECRET);
    if (!payload || !payload.sub) {
      return c.json({ error: 'Invalid token' }, 401);
    }
    c.set('userId', payload.sub as string);
    await next();
  } catch (error) {
    return c.json({ error: 'Invalid or expired token' }, 401);
  }
}
