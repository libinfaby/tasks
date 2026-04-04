import { Hono } from 'hono';
import type { Env } from '../index';
import { createToken, verifyToken } from '../utils/jwt';

type Variables = {
  userId: string;
};

export const authRoutes = new Hono<{ Bindings: Env; Variables: Variables }>();

// Simple password verification using timing-safe comparison
async function verifyPassword(input: string, hash: string): Promise<boolean> {
  // The PASSWORD_HASH is stored as a simple SHA-256 hash for single-user simplicity
  // Hash the input and compare
  const encoder = new TextEncoder();
  const data = encoder.encode(input);
  const hashBuffer = await crypto.subtle.digest('SHA-256', data);
  const hashArray = new Uint8Array(hashBuffer);
  const inputHash = Array.from(hashArray).map(b => b.toString(16).padStart(2, '0')).join('');
  
  // Timing-safe comparison
  if (inputHash.length !== hash.length) return false;
  let result = 0;
  for (let i = 0; i < inputHash.length; i++) {
    result |= inputHash.charCodeAt(i) ^ hash.charCodeAt(i);
  }
  return result === 0;
}

// POST /auth/login
authRoutes.post('/login', async (c) => {
  try {
    const body = await c.req.json();
    const { username, password } = body;

    if (!username || !password) {
      return c.json({ error: 'Username and password are required' }, 400);
    }

    // Single user check - username must be 'admin' (or configurable)
    if (username !== 'admin') {
      return c.json({ error: 'Invalid credentials' }, 401);
    }

    const isValid = await verifyPassword(password, c.env.PASSWORD_HASH);
    if (!isValid) {
      return c.json({ error: 'Invalid credentials' }, 401);
    }

    const token = await createToken(
      { sub: 'admin', username: 'admin' },
      c.env.JWT_SECRET,
      c.env.JWT_EXPIRY || '7d'
    );

    return c.json({
      token,
      user: { username: 'admin' },
    });
  } catch (error) {
    console.error('Login error:', error);
    return c.json({ error: 'Login failed' }, 500);
  }
});

// GET /auth/me
authRoutes.get('/me', async (c) => {
  const authHeader = c.req.header('Authorization');
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return c.json({ error: 'Not authenticated' }, 401);
  }

  const token = authHeader.substring(7);
  try {
    const payload = await verifyToken(token, c.env.JWT_SECRET);
    if (!payload) {
      return c.json({ error: 'Invalid token' }, 401);
    }
    return c.json({ user: { username: payload.username } });
  } catch {
    return c.json({ error: 'Invalid token' }, 401);
  }
});

// POST /auth/refresh
authRoutes.post('/refresh', async (c) => {
  const authHeader = c.req.header('Authorization');
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return c.json({ error: 'Not authenticated' }, 401);
  }

  const token = authHeader.substring(7);
  try {
    const payload = await verifyToken(token, c.env.JWT_SECRET);
    if (!payload) {
      return c.json({ error: 'Invalid token' }, 401);
    }

    const newToken = await createToken(
      { sub: payload.sub, username: payload.username },
      c.env.JWT_SECRET,
      c.env.JWT_EXPIRY || '7d'
    );

    return c.json({ token: newToken });
  } catch {
    return c.json({ error: 'Invalid token' }, 401);
  }
});
