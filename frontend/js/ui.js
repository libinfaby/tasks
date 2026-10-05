// ============================================================
// Tasks — Material 3 Expressive building blocks
// The web counterparts of android/.../ui/components (Expressive.kt, Forms.kt, Chips.kt).
// ============================================================

import { createElement as h, setChildren, tonal } from './utils.js';
import { icon } from './icons.js';

// ==================== Shapes ====================

function closedPath(radiusAt, steps = 180) {
  let d = '';
  for (let i = 0; i <= steps; i++) {
    const a = (i * 2 * Math.PI) / steps;
    const r = radiusAt(a);
    d += `${i === 0 ? 'M' : 'L'}${(50 + r * Math.cos(a)).toFixed(2)} ${(50 + r * Math.sin(a)).toFixed(2)}`;
  }
  return `${d}Z`;
}

// M3 Expressive's soft-burst "cookie": a circle with 8 gentle bumps (ScallopShape on Android)
const SCALLOP = (() => {
  const lobes = 8, depth = 0.12, radius = 50 / (1 + depth);
  return closedPath(a => radius + radius * depth * Math.cos(lobes * (a - Math.PI / 2 + Math.PI / 2)), 192);
})();

// An uneven, hand-drawn circle: a few slow swells in the radius (PebbleShape on Android)
const PEBBLE = (() => {
  const swells = [[2, 0.05, 0.6], [3, 0.035, 1.9], [5, 0.015, 0.3]];
  const wobble = a => 1 + swells.reduce((s, [n, amp, ph]) => s + amp * Math.cos(n * a + ph), 0);
  let peak = 0;
  for (let i = 0; i < 180; i++) peak = Math.max(peak, wobble((i * 2 * Math.PI) / 180));
  return closedPath(a => (50 / peak) * wobble(a));
})();

function shapeSvg(d) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 100 100');
  svg.setAttribute('class', 'shape');
  svg.setAttribute('aria-hidden', 'true');
  const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  path.setAttribute('d', d);
  path.setAttribute('fill', 'currentColor');
  svg.appendChild(path);
  return svg;
}

export const scallop = () => shapeSvg(SCALLOP);

/** The app mark: a checklist in a primary pebble. */
export function brandMark(size = 40) {
  return h('span', { className: 'brand-mark', style: { width: `${size}px`, height: `${size}px` } },
    shapeSvg(PEBBLE), icon('checklist', { size: Math.round(size * 0.5) }));
}

// ==================== Lists ====================

/**
 * One item of a connected list (.connected). Clickable when [onClick] is given — as a focusable div
 * rather than a button, since rows can hold their own buttons and switches.
 */
export function citem(content, { onClick = null, className = '', label = null } = {}) {
  if (!onClick) return h('div', { className: `citem ${className}` }, content);
  const el = h('div', {
    className: `citem interactive ${className}`,
    role: 'button',
    tabindex: '0',
    'aria-label': label,
    onClick,
    onKeydown: (e) => { if ((e.key === 'Enter' || e.key === ' ') && e.target === el) { e.preventDefault(); onClick(e); } },
  }, content);
  return el;
}

/** The usual settings-style row: icon tile, two lines, trailing slot. */
export function listRow({ title, supporting = null, icon: iconName = null, tile = '', tileStyle = null, tileContent = null, trailing = null, className = '' }) {
  return h('div', { className: `list-row ${className}` },
    iconName || tileContent ? h('span', { className: `row-tile ${tile}`, style: tileStyle }, tileContent || icon(iconName, { size: 22 })) : null,
    h('div', { className: 'row-text' },
      h('div', { className: 'row-title' }, title),
      supporting ? h('div', { className: 'row-supporting' }, supporting) : null,
    ),
    trailing ? h('div', { className: 'row-trailing' }, trailing) : null,
  );
}

/** A section title with an icon tile and a trailing count; a button when [onClick] is given. */
export function sectionHeader({ title, icon: iconName, tile = 'secondary', tileStyle = null, trailing = null, onClick = null, label = null }) {
  const parts = [
    h('span', { className: `tile ${tile}`, style: tileStyle }, icon(iconName, { size: 18 })),
    h('span', { className: 'title' }, title),
    trailing ? h('span', { className: 'label-medium muted' }, trailing) : null,
    onClick ? icon('chevronRight', { size: 20, className: 'chev' }) : null,
  ];
  return onClick
    ? h('button', { type: 'button', className: 'section-header clickable interactive', onClick, 'aria-label': label }, parts)
    : h('div', { className: 'section-header' }, parts);
}

/** Friendly empty state: a tilted icon tile, a headline and a line of help. */
export function emptyState(iconName, title, text) {
  return h('div', { className: 'empty-state' },
    h('div', { className: 'empty-tile' }, icon(iconName, { size: 44 })),
    h('h3', { className: 'title-large' }, title),
    h('p', { className: 'body-medium' }, text),
  );
}

export const spinner = () => h('div', { className: 'loading' }, h('div', { className: 'spinner', role: 'progressbar', 'aria-label': 'Loading' }));

/** Top app bar with a back arrow. */
export function topBar(title, onBack, ...actions) {
  return h('div', { className: 'top-bar' },
    h('button', { type: 'button', className: 'icon-btn interactive', 'aria-label': 'Back', onClick: onBack }, icon('back')),
    h('span', { className: 'title-large' }, title),
    ...actions,
  );
}

/** Extended FAB that collapses to its icon once [scroller] scrolls. */
export function fab(label, iconName, onClick, scroller) {
  const btn = h('button', { type: 'button', className: 'fab interactive', onClick, 'aria-label': label },
    icon(iconName, { size: 26 }), h('span', { className: 'fab-label' }, label));
  scroller?.addEventListener('scroll', () => btn.classList.toggle('collapsed', scroller.scrollTop > 24), { passive: true });
  return btn;
}

// ==================== Controls ====================

/** M3 switch; a check rides on the thumb when on. */
export function switchEl(checked, onChange, label) {
  const el = h('button', { type: 'button', className: 'switch', role: 'switch', 'aria-checked': String(checked), 'aria-label': label },
    h('span', { className: 'thumb' }, icon('check', { size: 16 })));
  el.addEventListener('click', (e) => {
    e.stopPropagation();
    const next = el.getAttribute('aria-checked') !== 'true';
    el.setAttribute('aria-checked', String(next));
    onChange(next);
  });
  return el;
}

/** Connected toggle group: options [{ value, label, icon, className }]. */
export function toggleGroup(options, selected, onSelect, { tall = false } = {}) {
  const group = h('div', { className: `toggle-group${tall ? ' tall' : ''}`, role: 'group' });
  const paint = (value) => group.querySelectorAll('button').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.value === String(value))));
  options.forEach(o => {
    group.appendChild(h('button', {
      type: 'button',
      className: `interactive ${o.className || ''}`,
      dataset: { value: String(o.value) },
      onClick: () => { paint(o.value); onSelect(o.value); },
    }, o.icon ? (typeof o.icon === 'function' ? o.icon(o.value === selected) : icon(o.icon, { size: 20 })) : null, o.label));
  });
  paint(selected);
  if (options.some(o => typeof o.icon === 'function')) {
    // Icons that change with selection (Urgent's flame fills in) repaint on select
    group.addEventListener('click', () => requestAnimationFrame(() => {
      group.querySelectorAll('button').forEach((b, i) => {
        const o = options[i];
        if (typeof o.icon !== 'function') return;
        b.querySelector('svg')?.replaceWith(o.icon(b.getAttribute('aria-pressed') === 'true'));
      });
    }));
  }
  return group;
}

/** Tag chip in the tag's tonal colours; [label] prefixes the tag type. */
export function chip(text, color, { fallback = null, label = null, onClick = null, title = null, large = false } = {}) {
  const t = tonal(color, fallback);
  return h(onClick ? 'button' : 'span', {
    type: onClick ? 'button' : null,
    className: `chip ${t.className}${large ? ' lg' : ''}${onClick ? ' interactive' : ''}`,
    style: t.style,
    title,
    onClick: onClick ? (e) => { e.stopPropagation(); onClick(e); } : null,
  }, label ? h('span', { className: 'chip-label' }, label) : null, text);
}

/** Outlined text field with a notched label. Returns { el, input }. */
export function textField({ label, value = '', type = 'text', icon: iconName = null, placeholder = '', attrs = {}, multiline = false }) {
  const input = h(multiline ? 'textarea' : 'input', { type: multiline ? null : type, value, placeholder, ...attrs });
  const el = h('div', { className: `text-field${iconName ? ' with-icon' : ''}` },
    iconName ? h('span', { className: 'lead' }, icon(iconName)) : null,
    input,
    label ? h('label', {}, label) : null,
  );
  return { el, input };
}

/** Grows a textarea with its content, starting at one line. */
export function autosize(textarea) {
  const fit = () => { textarea.style.height = 'auto'; textarea.style.height = `${textarea.scrollHeight}px`; };
  textarea.addEventListener('input', fit);
  requestAnimationFrame(fit);
  return fit;
}

// ==================== Menus ====================

let openMenuEl = null;

/** A dropdown menu under [anchor]: items [{ label, icon, iconEl, checked, onClick }]. */
export function openMenu(anchor, items) {
  closeMenu();
  const menu = h('div', { className: 'menu-popover', role: 'menu' },
    ...items.map(item => h('button', {
      type: 'button',
      className: 'menu-item interactive',
      role: 'menuitem',
      onClick: () => { closeMenu(); item.onClick(); },
    },
      item.iconEl || item.icon ? h('span', { className: 'lead' }, item.iconEl || icon(item.icon, { size: 20 })) : null,
      h('span', { className: 'text' }, item.label),
      item.checked ? h('span', { className: 'trail' }, icon('check', { size: 20 })) : null,
    )),
  );
  document.body.appendChild(menu);
  const r = anchor.getBoundingClientRect();
  const mw = menu.offsetWidth, mh = menu.offsetHeight;
  let left = Math.min(r.right - mw, window.innerWidth - mw - 16);
  if (left < 16) left = Math.max(16, Math.min(r.left, window.innerWidth - mw - 16));
  let top = r.bottom + 4;
  if (top + mh > window.innerHeight - 16) top = Math.max(16, r.top - mh - 4);
  Object.assign(menu.style, { left: `${left}px`, top: `${top}px` });
  openMenuEl = menu;
  setTimeout(() => {
    document.addEventListener('pointerdown', outside, true);
    document.addEventListener('keydown', escape, true);
  });
  menu.querySelector('button')?.focus({ preventScroll: true });
}

function outside(e) { if (openMenuEl && !openMenuEl.contains(e.target)) { e.stopPropagation(); closeMenu(); } }
function escape(e) { if (e.key === 'Escape') { e.stopPropagation(); closeMenu(); } }

export function closeMenu() {
  openMenuEl?.remove();
  openMenuEl = null;
  document.removeEventListener('pointerdown', outside, true);
  document.removeEventListener('keydown', escape, true);
}

// ==================== Dialogs & sheets ====================

const layers = [];

function overlay(scrimClass, content, onDismiss) {
  const scrim = h('div', { className: `scrim ${scrimClass}` }, content);
  let closed = false;
  const close = () => {
    if (closed) return;
    closed = true;
    layers.splice(layers.indexOf(close), 1);
    scrim.classList.add('leaving');
    setTimeout(() => scrim.remove(), 200);
  };
  scrim.addEventListener('pointerdown', (e) => { scrim.dataset.downOnScrim = String(e.target === scrim); });
  scrim.addEventListener('click', (e) => { if (e.target === scrim && scrim.dataset.downOnScrim === 'true') { onDismiss?.(); close(); } });
  document.body.appendChild(scrim);
  layers.push(close);
  return close;
}

// Escape closes the top dialog or sheet
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && layers.length && !openMenuEl) layers[layers.length - 1]();
});

/** A basic dialog. actions: [{ label, onClick, kind: 'text'|'danger', start, disabled }]; onClick returning false keeps it open. */
export function dialog({ title, body = [], actions = [], small = false }) {
  let close;
  const actionEls = actions.map(a => {
    const btn = h('button', {
      type: 'button',
      className: `btn btn-text interactive${a.kind === 'danger' ? ' danger' : ''}${a.start ? ' start' : ''}`,
      onClick: async () => { if ((await a.onClick?.()) !== false) close(); },
    }, a.label);
    if (a.disabled) btn.disabled = true;
    return btn;
  });
  const box = h('div', { className: `dialog${small ? ' sm' : ''}`, role: 'dialog', 'aria-modal': 'true', 'aria-label': title },
    h('h2', { className: 'dialog-title headline-small' }, title),
    h('div', { className: 'dialog-body' }, body),
    h('div', { className: 'dialog-actions' }, actionEls),
  );
  close = overlay('', box);
  return { close, box, actions: actionEls };
}

export function confirmDialog(title, text, confirmLabel, onConfirm) {
  return dialog({
    title,
    small: true,
    body: [h('p', {}, text)],
    actions: [{ label: 'Cancel' }, { label: confirmLabel, kind: 'danger', onClick: onConfirm }],
  });
}

/** Small dialog asking for a single line of text. */
export function nameDialog(title, label, initial, onSave) {
  const { el, input } = textField({ label, value: initial || '', attrs: { autocapitalize: 'sentences' } });
  const d = dialog({
    title,
    small: true,
    body: [el],
    actions: [{ label: 'Cancel' }, { label: 'Save', onClick: () => { const v = input.value.trim(); if (!v) return false; onSave(v); } }],
  });
  const save = d.actions[1];
  const sync = () => { save.disabled = !input.value.trim(); };
  input.addEventListener('input', sync);
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter' && input.value.trim()) save.click(); });
  sync();
  setTimeout(() => input.focus(), 50);
  return d;
}

/** Bottom sheet on phones, a centred dialog on wider screens. */
export function sheet({ body, footer = null, label, onDismiss = null }) {
  const box = h('div', { className: 'sheet', role: 'dialog', 'aria-modal': 'true', 'aria-label': label },
    h('div', { className: 'sheet-handle' }),
    h('div', { className: 'sheet-body' }, body),
    footer ? h('div', { className: 'sheet-footer' }, footer) : null,
  );
  const close = overlay('sheet-scrim', box, onDismiss);
  return { close, box };
}

// ==================== Colour field ====================

// Twelve hues around the wheel at a Material tone-40 strength, plus a neutral (same as Android)
export const SWATCHES = [
  ['#b3261e', 'Red'], ['#a04100', 'Orange'], ['#7d5700', 'Amber'], ['#5b6300', 'Olive'],
  ['#2e6b30', 'Green'], ['#006a60', 'Teal'], ['#006782', 'Cyan'], ['#275ea8', 'Blue'],
  ['#4a5ba8', 'Indigo'], ['#6750a4', 'Purple'], ['#8a3f8a', 'Magenta'], ['#a23a5f', 'Pink'],
  ['#6f6f78', 'Neutral'],
];

/**
 * Colour choice as Material swatches: each circle shows the tone a tag or group takes, with a dot of
 * its text tone; the picked one gets a check and a ring. The last swatch opens a hex field.
 */
export function colorField(label, value, onChange) {
  let hex = value;
  let custom = !SWATCHES.some(([v]) => v.toLowerCase() === (value || '').toLowerCase());
  const row = h('div', { className: 'swatches', role: 'radiogroup', 'aria-label': label });
  const { el: hexField, input: hexInput } = textField({ label: 'Hex', value: custom ? value : '', attrs: { spellcheck: 'false' } });
  const support = h('div', { className: 'support' });
  hexField.appendChild(support);
  hexField.style.marginTop = '10px';

  const paint = () => {
    setChildren(row,
      ...SWATCHES.map(([v, name]) => swatch(v, name, !custom && v.toLowerCase() === hex.toLowerCase(), null, () => { custom = false; hex = v; onChange(v); paint(); })),
      swatch(custom ? hex : null, 'Custom colour', custom, 'palette', () => { custom = true; paint(); setTimeout(() => hexInput.focus(), 0); }),
    );
    hexField.classList.toggle('hidden', !custom);
  };
  hexInput.addEventListener('input', () => {
    const v = hexInput.value.trim();
    const ok = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.test(v);
    hexField.classList.toggle('error', !ok);
    support.textContent = ok ? '' : 'Use a hex colour like #4a5ba8';
    if (ok) { hex = v; onChange(v); paint(); }
  });
  paint();
  return h('div', {}, h('span', { className: 'field-label' }, label), row, hexField);
}

function swatch(color, name, selected, iconName, onClick) {
  const t = color ? tonal(color) : { className: 'neutral', style: {} };
  const face = h('span', { className: `face ${t.className}`, style: t.style },
    selected ? icon('check', { size: 20 }) : iconName ? icon(iconName, { size: 20 }) : h('span', { className: 'dot' }));
  const btn = h('button', { type: 'button', className: `swatch interactive${selected ? ' selected' : ''}`, role: 'radio', 'aria-checked': String(selected), 'aria-label': name, title: name, onClick }, face);
  // The ring takes the swatch's text tone
  if (selected) requestAnimationFrame(() => btn.style.setProperty('--swatch-ring', getComputedStyle(face).color));
  return btn;
}
