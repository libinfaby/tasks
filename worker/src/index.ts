import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { authRoutes } from './routes/auth';
import { taskRoutes } from './routes/tasks';
import { subtaskRoutes } from './routes/subtasks';
import { tagRoutes } from './routes/tags';
import { groupRoutes } from './routes/groups';
import { authMiddleware } from './middleware/auth';
import { sendPushNotification } from './utils/webpush';

export type Env = {
  DB: D1Database;
  ALLOWED_ORIGIN: string;
  JWT_SECRET: string;
  PASSWORD_HASH: string;
  JWT_EXPIRY: string;
  VAPID_PUBLIC_KEY: string;
  VAPID_PRIVATE_KEY: string;
  VAPID_SUBJECT: string;
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

export default {
  fetch: app.fetch,
  async scheduled(event: any, env: Env, ctx: any) {
    const db = env.DB;
    const nowStr = new Date().toISOString();
    console.log(`[CRON] Running at ${nowStr}`);

    try {
      // Find active tasks with reminders that are due and not yet notified
      const { results: tasksToNotify } = await db.prepare(
        'SELECT * FROM tasks WHERE is_completed = 0 AND reminder IS NOT NULL AND is_notified = 0 AND reminder <= ?'
      ).bind(nowStr).all();

      if (!tasksToNotify || tasksToNotify.length === 0) {
        console.log('[CRON] No tasks to notify');
        return;
      }
      console.log(`[CRON] Found ${tasksToNotify.length} task(s) to notify`);

      // Get all push subscriptions
      const { results: subscriptions } = await db.prepare('SELECT * FROM push_subscriptions').all();
      if (!subscriptions || subscriptions.length === 0) {
        console.log('[CRON] No push subscriptions registered');
        return;
      }
      console.log(`[CRON] Sending to ${subscriptions.length} subscription(s)`);

      for (const task of tasksToNotify as any[]) {
        const payload = JSON.stringify({
          title: `Reminder: ${task.title}`,
          body: task.details || 'Task reminder!'
        });

        const sendPromises = (subscriptions as any[]).map(async (sub) => {
          const pushSubscription = {
            endpoint: sub.endpoint,
            keys: { p256dh: sub.p256dh, auth: sub.auth }
          };

          try {
            const response = await sendPushNotification(
              pushSubscription,
              payload,
              env.VAPID_SUBJECT,
              env.VAPID_PUBLIC_KEY,
              env.VAPID_PRIVATE_KEY
            );

            if (!response.ok) {
              const body = await response.text();
              console.error(`[CRON] Push failed (${response.status}):`, body);
              // Delete subscription if expired/gone
              if (response.status === 410 || response.status === 404) {
                await db.prepare('DELETE FROM push_subscriptions WHERE endpoint = ?').bind(sub.endpoint).run();
              }
            } else {
              console.log(`[CRON] Push sent successfully for task ${task.id}`);
            }
          } catch (err: any) {
            console.error('[CRON] Failed to send push:', err.message || err);
          }
        });

        await Promise.all(sendPromises);

        // Mark task as notified
        await db.prepare('UPDATE tasks SET is_notified = 1 WHERE id = ?').bind(task.id).run();
      }
    } catch (err) {
      console.error('[CRON] Scheduled trigger failed:', err);
    }
  }
};
