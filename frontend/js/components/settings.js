// ============================================================
// Tasks — Settings: theme, default group, notifications, sign out
// ============================================================

import { api } from '../api.js';
import { createElement as h, setChildren, getThemeSetting, setTheme, showToast, tonal } from '../utils.js';
import { icon } from '../icons.js';
import { citem, listRow, openMenu, toggleGroup, topBar } from '../ui.js';
import { store } from '../store.js';

export function createSettingsPage({ nav }) {
  const groupSlot = h('div');
  const notifySlot = h('div');
  const inner = h('div', { className: 'page-inner' },
    h('section', { className: 'settings-section' },
      h('span', { className: 'field-label', style: { paddingLeft: '4px' } }, 'Appearance'),
      toggleGroup([
        { value: 'system', label: 'System', icon: 'autoMode' },
        { value: 'light', label: 'Light', icon: 'lightMode' },
        { value: 'dark', label: 'Dark', icon: 'darkMode' },
      ], getThemeSetting(), setTheme),
    ),
    h('section', { className: 'settings-section' },
      h('span', { className: 'field-label', style: { paddingLeft: '4px' } }, 'Tasks'),
      groupSlot,
    ),
    h('section', { className: 'settings-section' },
      h('span', { className: 'field-label', style: { paddingLeft: '4px' } }, 'Reminders'),
      notifySlot,
      h('p', { className: 'settings-tip' }, 'Tip: if the Android app is on your phone, turn browser notifications off there to avoid duplicates.'),
    ),
    h('section', { className: 'settings-section' },
      h('button', {
        type: 'button',
        className: 'btn btn-error btn-lg btn-block interactive',
        onClick: () => { api.logout(); window.dispatchEvent(new CustomEvent('auth:logout')); },
      }, icon('logout'), 'Sign out'),
    ),
  );
  const el = h('div', { className: 'page' }, h('div', { className: 'page-scroll' }, topBar('Settings', nav.back), inner));

  /** The group every new task starts in, shared with the Android app. A deleted group reads as none. */
  function renderDefaultGroup() {
    const current = store.groups.find(g => g.id === store.defaultGroupId());
    const t = current ? tonal(current.color) : null;
    const row = citem(listRow({
      title: 'Default group',
      supporting: current ? `New tasks start in ${current.name}` : 'New tasks start without a group',
      icon: 'groupFilled',
      tile: current ? `group-shape tile-tonal ${t.className}` : 'group-shape primary',
      tileStyle: t?.style,
      trailing: [h('span', { className: 'label-large' }, current?.name || 'None'), icon('dropDown')],
    }), {
      className: 'solo',
      onClick: () => openMenu(row, [
        { label: 'No group', icon: 'block', checked: !current, onClick: () => pick(null) },
        ...store.groups.map(g => ({
          label: g.name,
          iconEl: h('span', { className: 'group-dot', style: { background: g.color, width: '12px', height: '12px' } }),
          checked: g.id === current?.id,
          onClick: () => pick(g.id),
        })),
      ]),
    });
    setChildren(groupSlot, row);
  }

  async function pick(id) {
    try { await store.setDefaultGroup(id); } catch (err) { showToast(err.message); }
  }

  /** Browser notifications for reminders: a check when allowed, an action when not. */
  function renderNotifications() {
    const supported = 'Notification' in window;
    const perm = supported ? Notification.permission : 'unsupported';
    const ok = perm === 'granted';
    const detail = ok ? 'Allowed'
      : perm === 'denied' ? 'Blocked — allow them in the browser’s site settings'
        : perm === 'unsupported' ? 'Not supported in this browser'
          : 'Off, so reminders can’t be shown';
    setChildren(notifySlot, citem(listRow({
      title: 'Notifications',
      supporting: detail,
      icon: 'notifications',
      tile: ok ? '' : 'primary',
      trailing: ok ? icon('check', { className: 'muted' })
        : perm === 'default' ? h('button', {
          type: 'button', className: 'btn btn-tonal interactive',
          onClick: async () => { await Notification.requestPermission(); renderNotifications(); },
        }, 'Allow') : null,
    }), { className: 'solo' }));
  }

  renderDefaultGroup();
  renderNotifications();
  const off = store.on(renderDefaultGroup);
  store.loadSettings();
  return { el, destroy: off };
}
