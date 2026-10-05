// ============================================================
// Tasks — Utility Functions
// ============================================================

import { icon } from './icons.js';

/**
 * Create a DOM element with attributes and children
 */
export function createElement(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);

  for (const [key, value] of Object.entries(attrs)) {
    if (value === null || value === undefined || value === false) continue;
    if (key === 'className') {
      el.className = value;
    } else if (key === 'style' && typeof value === 'object') {
      for (const [prop, v] of Object.entries(value)) {
        // Custom properties (--hue) need setProperty
        if (prop.startsWith('--')) el.style.setProperty(prop, v); else el.style[prop] = v;
      }
    } else if (key.startsWith('on') && typeof value === 'function') {
      el.addEventListener(key.slice(2).toLowerCase(), value);
    } else if (key === 'dataset') {
      Object.assign(el.dataset, value);
    } else if (key === 'value' && ('value' in el)) {
      el.value = value;
    } else if (value === true) {
      el.setAttribute(key, '');
    } else {
      el.setAttribute(key, value);
    }
  }

  for (const child of children.flat(Infinity)) {
    if (typeof child === 'string' || typeof child === 'number') {
      el.appendChild(document.createTextNode(child));
    } else if (child instanceof Node) {
      el.appendChild(child);
    }
  }

  return el;
}

/** Replaces [el]'s children, skipping null/false entries (which replaceChildren would print). */
export function setChildren(el, ...children) {
  el.replaceChildren(...children.flat(Infinity).filter(c => c !== null && c !== undefined && c !== false));
  return el;
}

export const $ = (sel, ctx = document) => ctx.querySelector(sel);
export const $$ = (sel, ctx = document) => [...ctx.querySelectorAll(sel)];

// ==================== Dates ====================
// Task dates are local YYYY-MM-DD strings; reminders are UTC ISO instants (same as the Android app).

/** Local (not UTC) YYYY-MM-DD, so "today" matches the user's calendar day. */
export function toDateStr(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export const todayStr = () => toDateStr(new Date());

/** A YYYY-MM-DD string (or the date part of an ISO string) as a local Date, or null. */
export function parseDate(value) {
  if (!value) return null;
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  return m ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])) : null;
}

export function addDays(dateStr, delta) {
  const d = parseDate(dateStr);
  return toDateStr(new Date(d.getFullYear(), d.getMonth(), d.getDate() + delta));
}

const fmt = (opts) => new Intl.DateTimeFormat('en-US', opts);
const SHORT = fmt({ month: 'short', day: 'numeric' });
const SHORT_YEAR = fmt({ month: 'short', day: 'numeric', year: 'numeric' });
const LONG = fmt({ weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
const WEEKDAY = fmt({ weekday: 'long', month: 'short', day: 'numeric' });
const FULL = fmt({ weekday: 'long', month: 'long', day: 'numeric' });
const TIME = fmt({ hour: 'numeric', minute: '2-digit' });

/** "Today", "Tomorrow", "Yesterday", else "Oct 7" (with the year when it differs). */
export function dateLabel(dateStr) {
  const d = parseDate(dateStr);
  if (!d) return '';
  const today = todayStr();
  if (dateStr.slice(0, 10) === today) return 'Today';
  if (dateStr.slice(0, 10) === addDays(today, 1)) return 'Tomorrow';
  if (dateStr.slice(0, 10) === addDays(today, -1)) return 'Yesterday';
  return d.getFullYear() === new Date().getFullYear() ? SHORT.format(d) : SHORT_YEAR.format(d);
}

/** "Mon, Oct 5, 2026" (daily log). */
export const longLabel = (dateStr) => LONG.format(parseDate(dateStr));

/** Upcoming section titles: "Tomorrow", then "Wednesday, Oct 7". */
export const dayLabel = (dateStr) => (dateStr === addDays(todayStr(), 1) ? 'Tomorrow' : WEEKDAY.format(parseDate(dateStr)));

/** The Today header: "Monday, October 5". */
export const fullLabel = (dateStr) => FULL.format(parseDate(dateStr));

export const formatTime = (date) => TIME.format(date);

/** "Today at 9:00 AM". */
export function formatReminder(iso) {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  return `${dateLabel(toDateStr(date))} at ${formatTime(date)}`;
}

export const isOverdue = (dateStr) => !!dateStr && dateStr.slice(0, 10) < todayStr();
export const isToday = (dateStr) => !!dateStr && dateStr.slice(0, 10) === todayStr();

/** Local datetime-local input value (YYYY-MM-DDTHH:MM) for an ISO instant. */
export function toDatetimeLocal(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const pad = (n) => String(n).padStart(2, '0');
  return `${toDateStr(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function debounce(fn, ms = 300) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), ms);
  };
}

// ==================== Colour ====================

const HEX = /^#?([0-9a-f]{3}|[0-9a-f]{6})$/i;

export const isHex = (value) => HEX.test(value || '');

function rgb(hex) {
  if (!isHex(hex)) return null;
  let h = hex.replace('#', '');
  if (h.length === 3) h = h.split('').map(c => c + c).join('');
  return [0, 2, 4].map(i => parseInt(h.slice(i, i + 2), 16) / 255);
}

/** No real hue (greys, white, black): such colours read as the theme's neutral chip. */
function isNeutral(hex) {
  const c = rgb(hex);
  return !c || Math.max(...c) - Math.min(...c) < 0.12;
}

/**
 * Material tonal colours for a tag, group or tile: a soft container in the colour's hue with darker
 * (light theme) or lighter (dark theme) content of the same hue — the CSS does the mixing per theme
 * (.tonal). Neutral colours use the theme's own neutral pair, so only real colours draw the eye.
 * Same rules as tagColors/tonalColors in the Android app.
 */
export function tonal(color, fallback) {
  const hue = isHex(color) ? color : (isHex(fallback) ? fallback : '#6f6f78');
  if (isNeutral(hue)) return { className: 'neutral', style: {} };
  return { className: 'tonal', style: { '--hue': hue.startsWith('#') ? hue : `#${hue}` } };
}

// ==================== Tasks ====================

// Tasks are normal (0) or urgent (2). High (1) was retired; any raised priority reads as urgent.
export const PRIORITY_URGENT = 2;

// Recurring reminder rules accepted by the API (tasks.reminder_repeat)
export const REPEAT_LABELS = { daily: 'Daily', weekdays: 'Weekdays', weekly: 'Weekly', monthly: 'Monthly' };

// ==================== Theme ====================

/**
 * Theme: 'system' | 'light' | 'dark', saved per browser (system = no saved value).
 * The saved value is applied before first paint by an inline script in index.html.
 */
export function getThemeSetting() {
  const t = document.documentElement.dataset.theme;
  return t === 'light' || t === 'dark' ? t : 'system';
}

export function setTheme(theme) {
  if (theme === 'light' || theme === 'dark') document.documentElement.dataset.theme = theme;
  else delete document.documentElement.dataset.theme;
  try {
    if (theme === 'light' || theme === 'dark') localStorage.setItem('tasks_theme', theme);
    else localStorage.removeItem('tasks_theme');
  } catch { /* storage unavailable */ }
}

// ==================== Snackbars ====================

/**
 * A Material snackbar at the bottom of the screen. [action] = { label, onClick } adds a button;
 * [dismissible] adds a close button. Returns a function that hides it.
 */
export function showSnackbar(message, { action = null, dismissible = false, duration = 4000 } = {}) {
  const host = document.getElementById('snackbar-host');
  if (!host) return () => {};
  host.querySelectorAll('.snackbar').forEach(s => s.remove()); // one at a time, like Material
  const hide = () => {
    bar.classList.add('leaving');
    setTimeout(() => bar.remove(), 200);
  };
  const bar = createElement('div', { className: 'snackbar', role: 'status' },
    createElement('span', { className: 'snackbar-text' }, message),
    action ? createElement('button', { className: 'snackbar-action', onClick: () => { hide(); action.onClick(); } }, action.label) : null,
    dismissible ? createElement('button', { className: 'snackbar-close', 'aria-label': 'Dismiss', onClick: hide }, icon('close', { size: 20 })) : null,
  );
  host.appendChild(bar);
  if (duration) setTimeout(hide, duration);
  return hide;
}

/** Errors and confirmations from API calls. */
export const showToast = (message) => showSnackbar(message);
