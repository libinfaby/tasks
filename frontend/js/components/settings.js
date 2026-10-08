// ============================================================
// Tasks — Settings: theme, default and hidden groups, notifications, sign out
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
  function defaultGroupRow() {
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
      onClick: () => openMenu(row, [
        { label: 'No group', icon: 'block', checked: !current, onClick: () => pick(null) },
        ...store.groups.map(g => ({
          label: g.name,
          iconEl: groupDot(g),
          checked: g.id === current?.id,
          // New tasks would vanish from the view they were added in
          disabled: store.isHidden(g.id),
          supporting: store.isHidden(g.id) ? 'Hidden, so it can’t be the default' : null,
          onClick: () => pick(g.id),
        })),
      ]),
    });
    return row;
  }

  /** Groups whose tasks only show inside the group, not in All tasks, Today or Upcoming. */
  function hiddenGroupsRow() {
    const hidden = store.groups.filter(g => store.isHidden(g.id));
    const row = citem(listRow({
      title: 'Hidden groups',
      supporting: hidden.length
        ? `${hidden.map(g => g.name).join(', ')} only show${hidden.length === 1 ? 's' : ''} inside ${hidden.length === 1 ? 'its' : 'their'} group`
        : 'Every group’s tasks show in All tasks, Today and Upcoming',
      icon: 'visibilityOff',
      tile: 'primary',
      trailing: [h('span', { className: 'label-large' }, hidden.length ? String(hidden.length) : 'None'), icon('dropDown')],
    }), { onClick: () => openHiddenMenu(row) });
    return row;
  }

  function openHiddenMenu(row) {
    if (!store.groups.length) { showToast('No groups yet'); return; }
    openMenu(row, store.groups.map(g => {
      const isDefault = g.id === store.defaultGroupId();
      return {
        label: g.name,
        iconEl: groupDot(g),
        checked: store.isHidden(g.id),
        disabled: isDefault,
        supporting: isDefault ? 'Default group, so it can’t be hidden' : null,
        // Stay open so several groups can be ticked in a row
        onClick: async () => {
          try { await store.setGroupHidden(g.id, !store.isHidden(g.id)); } catch (err) { showToast(err.message); }
          const fresh = groupSlot.lastElementChild;
          if (fresh?.isConnected) openHiddenMenu(fresh);
        },
      };
    }));
  }

  const groupDot = (g) => h('span', { className: 'group-dot', style: { background: g.color, width: '12px', height: '12px' } });

  function renderGroups() {
    setChildren(groupSlot, defaultGroupRow(), hiddenGroupsRow());
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

  renderGroups();
  renderNotifications();
  const off = store.on(renderGroups);
  store.loadSettings();
  return { el, destroy: off };
}
