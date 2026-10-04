// ============================================================
// Tasks — Utility Functions
// ============================================================

/**
 * Create a DOM element with attributes and children
 */
export function createElement(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  
  for (const [key, value] of Object.entries(attrs)) {
    if (key === 'className') {
      el.className = value;
    } else if (key === 'style' && typeof value === 'object') {
      Object.assign(el.style, value);
    } else if (key.startsWith('on') && typeof value === 'function') {
      el.addEventListener(key.slice(2).toLowerCase(), value);
    } else if (key === 'dataset') {
      Object.assign(el.dataset, value);
    } else if (key === 'innerHTML') {
      el.innerHTML = value;
    } else {
      el.setAttribute(key, value);
    }
  }
  
  for (const child of children) {
    if (typeof child === 'string' || typeof child === 'number') {
      el.appendChild(document.createTextNode(child));
    } else if (child instanceof Node) {
      el.appendChild(child);
    } else if (Array.isArray(child)) {
      child.forEach(c => {
        if (c instanceof Node) el.appendChild(c);
      });
    }
  }
  
  return el;
}

export const $ = (sel, ctx = document) => ctx.querySelector(sel);
export const $$ = (sel, ctx = document) => [...ctx.querySelectorAll(sel)];

/**
 * Format date for display
 */
export function formatDate(dateStr) {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const tomorrow = new Date(today);
  tomorrow.setDate(tomorrow.getDate() + 1);
  const taskDate = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  
  if (taskDate.getTime() === today.getTime()) return 'Today';
  if (taskDate.getTime() === tomorrow.getTime()) return 'Tomorrow';
  
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);
  if (taskDate.getTime() === yesterday.getTime()) return 'Yesterday';
  
  const options = { month: 'short', day: 'numeric' };
  if (date.getFullYear() !== now.getFullYear()) {
    options.year = 'numeric';
  }
  return date.toLocaleDateString('en-US', options);
}

/**
 * Format date for input fields (YYYY-MM-DD)
 */
export function formatDateInput(dateStr) {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return date.toISOString().split('T')[0];
}

/**
 * Format datetime for local input fields (YYYY-MM-DDTHH:MM)
 */
export function formatDatetimeLocal(dateStr) {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  return `${year}-${month}-${day}T${hours}:${minutes}`;
}

/**
 * Check if a date is overdue
 */
export function isOverdue(dateStr) {
  if (!dateStr) return false;
  const date = new Date(dateStr);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return date < today;
}

/**
 * Check if date is today
 */
export function isToday(dateStr) {
  if (!dateStr) return false;
  const date = new Date(dateStr);
  const today = new Date();
  return date.toDateString() === today.toDateString();
}

/**
 * Debounce function
 */
export function debounce(fn, ms = 300) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn.apply(this, args), ms);
  };
}

/**
 * Show toast notification
 */
export function showToast(message, type = 'info', duration = 3000) {
  const container = document.getElementById('toast-container');
  const toast = createElement('div', { className: `toast ${type}` }, message);
  container.appendChild(toast);
  
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(10px)';
    toast.style.transition = 'all 300ms ease';
    setTimeout(() => toast.remove(), 300);
  }, duration);
}

/**
 * Get tag chip background color from tag color
 */
export function getTagBg(color, solid = false) {
  if (!color) return 'rgba(99, 102, 241, 0.1)';
  if (solid) return color;
  // Convert hex to rgba with low opacity
  const hex = color.replace('#', '');
  if (hex.length !== 6) return color;
  const r = parseInt(hex.substring(0, 2), 16);
  const g = parseInt(hex.substring(2, 4), 16);
  const b = parseInt(hex.substring(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, 0.12)`;
}

/**
 * Get chip style object based on color, fg_color and has_bg.
 * Unfilled chips use the colour as text, falling back to a neutral text colour
 * when it would be unreadable on the current theme (e.g. white text in light mode).
 */
export function getChipStyle(config) {
  const chipBg = config.color || config.type_color || '#6366f1';
  const chipFg = config.fg_color || config.type_fg_color || '#ffffff';
  // Check both tag levels and group levels if applicable
  const hasBg = config.has_bg !== undefined ? !!config.has_bg :
               (config.type_has_bg !== undefined ? !!config.type_has_bg : true);

  if (hasBg) {
    return { background: chipBg, color: chipFg };
  }
  return { background: 'transparent', color: readableOnSurface(chipBg) };
}

// Card surfaces per theme (keep in sync with --bg-card in styles.css)
const SURFACES = { light: '#ffffff', dark: '#18181b' };

function relativeLuminance(hex) {
  let h = hex.replace('#', '');
  if (h.length === 3) h = h.split('').map(c => c + c).join('');
  if (!/^[0-9a-f]{6}$/i.test(h)) return null;
  const [r, g, b] = [0, 2, 4].map(i => {
    const c = parseInt(h.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function readableOnSurface(color) {
  const fg = relativeLuminance(color);
  const bg = relativeLuminance(SURFACES[getEffectiveTheme()]);
  if (fg === null) return color;
  const contrast = (Math.max(fg, bg) + 0.05) / (Math.min(fg, bg) + 0.05);
  return contrast >= 2.2 ? color : 'var(--text-secondary)';
}

/**
 * Generate a color from string (for auto-coloring)
 */
export function stringToColor(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash);
  }
  const colors = ['#6366f1', '#8b5cf6', '#ec4899', '#ef4444', '#f59e0b', '#10b981', '#3b82f6', '#14b8a6', '#f97316', '#84cc16'];
  return colors[Math.abs(hash) % colors.length];
}

// Tasks are normal (0) or urgent (2). High (1) was retired; any raised priority reads as urgent.
export const PRIORITY_URGENT = 2;

/**
 * Priority label
 */
export function getPriorityLabel(priority) {
  return priority > 0 ? 'Urgent' : 'Normal';
}

/**
 * Priority CSS class
 */
export function getPriorityClass(priority) {
  return priority > 0 ? 'urgent' : '';
}

// Recurring reminder rules accepted by the API (tasks.reminder_repeat)
export const REPEAT_LABELS = { daily: 'Daily', weekdays: 'Weekdays', weekly: 'Weekly', monthly: 'Monthly' };

/**
 * Format reminder string to show date and local time (e.g. Today at 1:30 PM)
 */
export function formatReminder(dateStr) {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  const dateFormatted = formatDate(dateStr);
  
  let hours = date.getHours();
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const ampm = hours >= 12 ? 'PM' : 'AM';
  hours = hours % 12;
  hours = hours ? hours : 12;
  const timeFormatted = `${hours}:${minutes} ${ampm}`;
  
  return `${dateFormatted} at ${timeFormatted}`;
}

/**
 * Theme: 'light' | 'dark' saved per browser; unset follows the OS.
 * The saved value is applied before first paint by an inline script in index.html.
 */
export function getEffectiveTheme() {
  const saved = document.documentElement.dataset.theme;
  if (saved === 'light' || saved === 'dark') return saved;
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function setTheme(theme) {
  document.documentElement.dataset.theme = theme;
  try { localStorage.setItem('tasks_theme', theme); } catch { /* storage unavailable */ }
  // Chip colours are computed per theme, so views re-render on change
  window.dispatchEvent(new CustomEvent('theme:change'));
}
