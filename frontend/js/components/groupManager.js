// ============================================================
// Tasks — Groups page: create, rename and recolour groups
// ============================================================

import { api } from '../api.js';
import { createElement as h, setChildren, showToast, tonal } from '../utils.js';
import { icon } from '../icons.js';
import { citem, confirmDialog, emptyState, fab, listRow, topBar } from '../ui.js';
import { store } from '../store.js';
import { styleDialog } from './tagManager.js';

export function createGroupsPage({ nav }) {
  const inner = h('div', { className: 'page-inner' });
  const scroller = h('div', { className: 'page-scroll' }, topBar('Groups', nav.back), inner);
  const el = h('div', { className: 'page' }, scroller, fab('New group', 'add', () => edit(null), scroller));

  const render = () => {
    if (!store.groups.length) {
      setChildren(inner, emptyState('groupFilled', 'No groups yet', 'Create groups to keep related tasks together.'));
      return;
    }
    setChildren(inner, h('div', { className: 'connected' }, ...store.groups.map(g => {
      const t = tonal(g.color);
      return citem(listRow({
        title: g.name,
        supporting: `${g.task_count || 0} tasks · ${g.active_task_count || 0} active`,
        icon: 'groupFilled',
        tile: `group-shape tile-tonal ${t.className}`,
        tileStyle: t.style,
        trailing: h('button', {
          type: 'button',
          className: 'icon-btn interactive',
          'aria-label': `Delete ${g.name}`,
          onClick: (e) => { e.stopPropagation(); remove(g); },
        }, icon('delete')),
      }), { onClick: () => edit(g) });
    })));
  };

  function edit(group) {
    styleDialog({
      title: group ? 'Edit group' : 'New group',
      name: group?.name || '',
      color: group?.color || '#8c6d9e',
      onSave: async ({ name, color }) => {
        // Text colour and fill are kept as they are; only the colour is chosen here
        const data = { name, color, fg_color: group?.fg_color || '#ffffff', has_bg: group ? !!(group.has_bg ?? 1) : true };
        if (group) await api.updateGroup(group.id, data); else await api.createGroup(data);
        showToast(group ? 'Updated' : 'Group created');
        await store.loadGroups();
      },
    });
  }

  function remove(group) {
    confirmDialog(`Delete "${group.name}"?`, 'Tasks in it will be ungrouped.', 'Delete', async () => {
      try {
        await api.deleteGroup(group.id);
        showToast('Group deleted');
        // The server also clears it as the default group
        await Promise.all([store.loadGroups(), store.loadSettings()]);
      } catch (err) { showToast(err.message); }
    });
  }

  render();
  const off = store.on(render);
  store.loadGroups();
  return { el, destroy: off, onShow: () => store.loadGroups() };
}
