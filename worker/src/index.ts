import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { authRoutes } from './routes/auth';
import { taskRoutes } from './routes/tasks';
import { subtaskRoutes } from './routes/subtasks';
import { tagRoutes } from './routes/tags';
import { groupRoutes } from './routes/groups';
import { authMiddleware } from './middleware/auth';

export type Env = {
  DB: D1Database;
  ALLOWED_ORIGIN: string;
  JWT_SECRET: string;
  PASSWORD_HASH: string;
  JWT_EXPIRY: string;
};

type Variables = {
  userId: string;
};

const app = new Hono<{ Bindings: Env; Variables: Variables }>();

// CORS middleware
app.use('*', async (c, next) => {
  const corsMiddleware = cors({
    origin: [c.env.ALLOWED_ORIGIN, 'http://localhost:5500', 'http://127.0.0.1:5500', 'http://localhost:3000'],
    allowMethods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    allowHeaders: ['Content-Type', 'Authorization'],
    exposeHeaders: ['Content-Length'],
    maxAge: 86400,
    credentials: true,
  });
  return corsMiddleware(c, next);
});

// Health check
app.get('/health', (c) => c.json({ status: 'ok', timestamp: new Date().toISOString() }));

// Auth routes (no auth middleware)
app.route('/auth', authRoutes);

// Protected routes
app.use('/tasks/*', authMiddleware);
app.use('/subtasks/*', authMiddleware);
app.use('/tags/*', authMiddleware);
app.use('/tag-types/*', authMiddleware);
app.use('/groups/*', authMiddleware);

app.route('/tasks', taskRoutes);
app.route('/subtasks', subtaskRoutes);
app.route('/tags', tagRoutes);
app.route('/tag-types', tagRoutes);
app.route('/groups', groupRoutes);

// 404 fallback
app.notFound((c) => c.json({ error: 'Not found' }, 404));

// Error handler
app.onError((err, c) => {
  console.error('Unhandled error:', err);
  return c.json({ error: 'Internal server error' }, 500);
});

export default app;
