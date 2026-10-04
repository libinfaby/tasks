// Recurring reminders. Rules step in local wall-clock time (via an IANA zone),
// so a 09:00 reminder stays at 09:00 local. Mirrored in the Android app —
// keep the two implementations and their test cases in sync.

export const REPEAT_RULES = ['daily', 'weekdays', 'weekly', 'monthly'] as const;
export type RepeatRule = (typeof REPEAT_RULES)[number];

export function isRepeatRule(value: unknown): value is RepeatRule {
  return typeof value === 'string' && (REPEAT_RULES as readonly string[]).includes(value);
}

type LocalTime = { y: number; mo: number; d: number; h: number; mi: number; s: number; ms: number };

const formatters = new Map<string, Intl.DateTimeFormat>();

function formatter(tz: string): Intl.DateTimeFormat {
  let f = formatters.get(tz);
  if (!f) {
    f = new Intl.DateTimeFormat('en-US', {
      timeZone: tz, hourCycle: 'h23',
      year: 'numeric', month: 'numeric', day: 'numeric',
      hour: 'numeric', minute: 'numeric', second: 'numeric',
    });
    formatters.set(tz, f);
  }
  return f;
}

function toLocal(instantMs: number, tz: string): LocalTime {
  const parts: Record<string, number> = {};
  for (const p of formatter(tz).formatToParts(new Date(instantMs))) {
    if (p.type !== 'literal') parts[p.type] = parseInt(p.value, 10);
  }
  return {
    y: parts.year, mo: parts.month - 1, d: parts.day,
    h: parts.hour, mi: parts.minute, s: parts.second,
    ms: ((instantMs % 1000) + 1000) % 1000,
  };
}

// Offset of `tz` from UTC at the given instant, in ms
function offsetAt(instantMs: number, tz: string): number {
  const l = toLocal(instantMs, tz);
  return Date.UTC(l.y, l.mo, l.d, l.h, l.mi, l.s, l.ms) - instantMs;
}

function toInstant(l: LocalTime, tz: string): number {
  const asUtc = Date.UTC(l.y, l.mo, l.d, l.h, l.mi, l.s, l.ms);
  const first = asUtc - offsetAt(asUtc, tz);
  const second = asUtc - offsetAt(first, tz);
  return second;
}

const daysInMonth = (y: number, mo: number) => new Date(Date.UTC(y, mo + 1, 0)).getUTCDate();

function addDays(l: LocalTime, n: number): LocalTime {
  const t = new Date(Date.UTC(l.y, l.mo, l.d + n));
  return { ...l, y: t.getUTCFullYear(), mo: t.getUTCMonth(), d: t.getUTCDate() };
}

// One step of the rule. Monthly keeps the day-of-month, clamped to shorter
// months (Jan 31 → Feb 28); later steps start from the clamped day.
function step(l: LocalTime, rule: RepeatRule): LocalTime {
  switch (rule) {
    case 'daily':
      return addDays(l, 1);
    case 'weekly':
      return addDays(l, 7);
    case 'weekdays': {
      let next = addDays(l, 1);
      while ([0, 6].includes(new Date(Date.UTC(next.y, next.mo, next.d)).getUTCDay())) {
        next = addDays(next, 1);
      }
      return next;
    }
    case 'monthly': {
      const mo = (l.mo + 1) % 12;
      const y = l.y + (l.mo === 11 ? 1 : 0);
      return { ...l, y, mo, d: Math.min(l.d, daysInMonth(y, mo)) };
    }
  }
}

// Next occurrence strictly after `now`. Missed occurrences are skipped, not replayed.
export function nextOccurrence(reminderIso: string, rule: RepeatRule, tz: string, now: Date = new Date()): string {
  let local = toLocal(new Date(reminderIso).getTime(), tz);
  let instant: number;
  do {
    local = step(local, rule);
    instant = toInstant(local, tz);
  } while (instant <= now.getTime());
  return new Date(instant).toISOString();
}
