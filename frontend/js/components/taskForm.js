// ============================================================
// Tasks — Task editor (new / edit), the web counterpart of TaskEditorSheet on Android
// ============================================================

import { api } from '../api.js';
import { createElement as h, setChildren, showToast, todayStr, addDays, dateLabel, formatTime, toDateStr, toDatetimeLocal, tonal,
  REPEAT_LABELS, PRIORITY_URGENT,
} from '../utils.js';
import { icon } from '../icons.js';
import { autosize, citem, confirmDialog, listRow, nameDialog, openMenu, sheet, switchEl, textField, toggleGroup } from '../ui.js';
import { store } from '../store.js';

let nextKey = 0;

export function openTaskEditor(task, { onSaved }) {
  const isEdit = !!task;
  const d = isEdit ? {
    title: task.title,
    details: task.details || '',
    date: task.date ? task.date.slice(0, 10) : null,
    reminder: task.reminder ? new Date(task.reminder) : null,
    repeat: task.reminder_repeat || null,
    priority: task.priority > 0 ? PRIORITY_URGENT : 0,
    groupId: task.group_id ?? null,
    tagIds: new Set((task.tags || []).map(t => t.id)),
    subtasks: (task.subtasks || []).map(s => ({ key: nextKey++, id: s.id, title: s.title, tagIds: new Set((s.tags || []).map(t => t.id)) })),
  } : {
    // New tasks default to today and to the default group from Settings
    title: '', details: '', date: todayStr(), reminder: null, repeat: null, priority: 0,
    groupId: store.defaultGroupId(), tagIds: new Set(), subtasks: [],
  };
  let saving = false;

  // ---------- Header ----------
  const groupSlot = h('div');
  const renderGroup = () => {
    const g = store.groups.find(x => x.id === d.groupId);
    const t = g ? tonal(g.color) : null;
    const btn = h('button', {
      type: 'button',
      className: `group-picker interactive ${g ? `tile-tonal ${t.className}` : 'none'}`,
      style: t?.style,
      'aria-label': `Group: ${g?.name || 'none'}. Change group`,
      onClick: () => openMenu(btn, [
        { label: 'No group', icon: 'block', checked: !g, onClick: () => { d.groupId = null; renderGroup(); } },
        ...store.groups.map(x => ({
          label: x.name,
          iconEl: h('span', { className: 'group-dot', style: { background: x.color, width: '12px', height: '12px' } }),
          checked: x.id === d.groupId,
          onClick: () => { d.groupId = x.id; renderGroup(); },
        })),
      ]),
    },
      g ? h('span', { className: 'group-dot', style: { background: g.color, width: '12px', height: '12px' } }) : icon('group', { size: 18, className: 'small' }),
      h('span', { className: 'text' }, g?.name || 'No group'),
      icon('dropDown', { size: 24 }),
    );
    setChildren(groupSlot, btn);
  };

  // ---------- Title & details ----------
  const titleInput = h('textarea', { className: 'bare-input editor-title', rows: '1', placeholder: 'What needs doing?', 'aria-label': 'Title', autocapitalize: 'sentences' });
  titleInput.value = d.title;
  titleInput.addEventListener('input', () => { d.title = titleInput.value; clearError(); });
  // Enter in the title saves, like a single-line field
  titleInput.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); save(); } });
  // One line of notes under the title, growing with each line written
  const detailsInput = h('textarea', { className: 'bare-input', rows: '1', placeholder: 'Add details', 'aria-label': 'Details', autocapitalize: 'sentences' });
  detailsInput.value = d.details;
  detailsInput.addEventListener('input', () => { d.details = detailsInput.value; });

  // ---------- When ----------
  const whenSlot = h('div', { className: 'chip-row' });
  const datePicker = h('input', { type: 'date', className: 'hidden-picker', tabindex: '-1', 'aria-hidden': 'true' });
  datePicker.addEventListener('change', () => { if (datePicker.value) { d.date = datePicker.value; renderWhen(); } });
  const renderWhen = () => {
    const today = todayStr();
    const presets = { today, tomorrow: addDays(today, 1) };
    const selected = !d.date ? 'none' : d.date === presets.today ? 'today' : d.date === presets.tomorrow ? 'tomorrow' : 'custom';
    const option = (key, label, iconName, onClick) => h('button', {
      type: 'button', className: 'pick-chip interactive', 'aria-pressed': String(selected === key), onClick,
    }, selected === key ? icon('check', { size: 18 }) : iconName ? icon(iconName, { size: 18 }) : null, label);
    setChildren(whenSlot,
      option('none', 'No date', 'eventBusy', () => { d.date = null; renderWhen(); }),
      option('today', 'Today', null, () => { d.date = presets.today; renderWhen(); }),
      option('tomorrow', 'Tomorrow', null, () => { d.date = presets.tomorrow; renderWhen(); }),
      option('custom', selected === 'custom' ? dateLabel(d.date) : 'Pick date', 'editCalendar', () => {
        datePicker.value = d.date || today;
        try { datePicker.showPicker(); } catch { datePicker.click(); }
      }),
      datePicker,
    );
  };

  // ---------- Reminder ----------
  const reminderSlot = h('div', { className: 'connected' });
  const renderReminder = () => {
    const on = !!d.reminder;
    const label = on ? `${dateLabel(toDateStr(d.reminder))} · ${formatTime(d.reminder)}` : 'Off';
    const enable = () => {
      const day = d.date || todayStr();
      const now = new Date();
      // An hour from now (on the hour) for today, otherwise 9 AM
      if (day === todayStr()) { const t = new Date(now); t.setHours(now.getHours() + 1, 0, 0, 0); d.reminder = t; }
      else { const [y, m, dd] = day.split('-').map(Number); d.reminder = new Date(y, m - 1, dd, 9, 0); }
      renderReminder();
    };
    const toggle = switchEl(on, (v) => { if (v) enable(); else { d.reminder = null; d.repeat = null; renderReminder(); } }, 'Reminder');
    const rows = [citem(listRow({ title: 'Reminder', supporting: label, icon: 'alarm', tile: 'primary', trailing: toggle }), {
      onClick: () => (on ? reminderSlot.querySelector('input')?.focus() : enable()),
    })];
    if (on) {
      const local = toDatetimeLocal(d.reminder.toISOString());
      const { el: dateField, input: dateIn } = textField({ label: 'Date', type: 'date', value: local.slice(0, 10) });
      const { el: timeField, input: timeIn } = textField({ label: 'Time', type: 'time', value: local.slice(11, 16) });
      const apply = () => {
        if (!dateIn.value || !timeIn.value) return;
        const [y, m, dd] = dateIn.value.split('-').map(Number);
        const [hh, mm] = timeIn.value.split(':').map(Number);
        d.reminder = new Date(y, m - 1, dd, hh, mm);
        rows[0].querySelector('.row-supporting').textContent = `${dateLabel(toDateStr(d.reminder))} · ${formatTime(d.reminder)}`;
      };
      dateIn.addEventListener('change', apply);
      timeIn.addEventListener('change', apply);
      rows.push(citem(h('div', { className: 'reminder-inputs' }, dateField, timeField)));
      const repeatRow = citem(listRow({ title: 'Repeat', supporting: REPEAT_LABELS[d.repeat] || 'Does not repeat', icon: 'repeat', tile: 'circle', trailing: icon('dropDown') }), {
        onClick: () => openMenu(repeatRow, [
          { label: 'Does not repeat', checked: !d.repeat, onClick: () => { d.repeat = null; renderReminder(); } },
          ...Object.entries(REPEAT_LABELS).map(([v, l]) => ({ label: l, checked: d.repeat === v, onClick: () => { d.repeat = v; renderReminder(); } })),
        ]),
      });
      rows.push(repeatRow);
    }
    setChildren(reminderSlot, ...rows);
  };

  // ---------- Priority ----------
  const priority = toggleGroup([
    { value: 0, label: 'Normal', icon: 'block' },
    { value: PRIORITY_URGENT, label: 'Urgent', icon: (sel) => icon(sel ? 'fireFilled' : 'fire', { size: 20 }), className: 'error' },
  ], d.priority, (v) => { d.priority = v; }, { tall: true });

  // ---------- Tags ----------
  const tagsSlot = h('div', { className: 'selected-tags' });
  const findTag = (id) => {
    for (const type of store.tagTypes) {
      const tag = (type.tags || []).find(t => t.id === id);
      if (tag) return { tag, type };
    }
    return null;
  };
  // The task's tags as removable chips, with an Add tag button on its own line below
  const renderTags = () => {
    const chosen = [...d.tagIds].map(findTag).filter(Boolean);
    setChildren(tagsSlot,
      chosen.length ? h('div', { className: 'chip-row' }, ...chosen.map(({ tag, type }) => {
        const t = tonal(tag.color, type.color);
        return h('span', { className: `removable-chip ${t.className}`, style: t.style }, tag.name,
          h('button', { type: 'button', className: 'icon-btn interactive', 'aria-label': `Remove tag ${tag.name}`, onClick: () => { d.tagIds.delete(tag.id); renderTags(); } }, icon('close', { size: 18 })));
      })) : null,
      h('button', { type: 'button', className: 'add-tags-btn interactive', onClick: openTagPicker }, icon('add', { size: 18 }), chosen.length ? 'Add tag' : 'Add tags'),
    );
  };
  const openTagPicker = () => {
    const selector = tagSelector(d.tagIds, () => renderTags(), { canCreate: true });
    const s = sheet({
      label: 'Tags',
      body: [
        h('div', { className: 'editor-head' }, h('h2', { className: 'headline-small' }, 'Tags'),
          h('button', { type: 'button', className: 'btn btn-filled interactive', onClick: () => s.close() }, 'Done')),
        selector,
      ],
    });
  };

  // ---------- Subtasks ----------
  const subtasksSlot = h('div', { className: 'connected' });
  let taggingKey = null;
  const renderSubtasks = () => {
    const rows = d.subtasks.map(row => {
      const input = h('input', { type: 'text', placeholder: 'Subtask', value: row.title, 'aria-label': 'Subtask', autocapitalize: 'sentences' });
      input.addEventListener('input', () => { row.title = input.value; });
      input.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); addSubtask(); } });
      const hasTags = row.tagIds.size > 0;
      return citem(h('div', {},
        h('div', { className: 'subtask-item' },
          h('span', { className: 'bullet' }),
          input,
          store.tagTypes.length ? h('button', {
            type: 'button', className: `icon-btn interactive${hasTags ? ' on' : ''}`, 'aria-label': 'Subtask tags',
            onClick: () => { taggingKey = taggingKey === row.key ? null : row.key; renderSubtasks(); },
          }, icon(hasTags ? 'tagFilled' : 'tag', { size: 20 })) : null,
          h('button', { type: 'button', className: 'icon-btn interactive', 'aria-label': 'Remove subtask', onClick: () => { d.subtasks = d.subtasks.filter(s => s !== row); renderSubtasks(); } }, icon('close', { size: 20 })),
        ),
        taggingKey === row.key ? h('div', { className: 'subtask-tags' }, tagSelector(row.tagIds, () => renderSubtasks())) : null,
      ));
    });
    // Sized like the Reminder card above it: an icon tile and a full-size label
    rows.push(citem(listRow({ title: 'Add subtask', icon: 'add', tile: 'primary', className: 'tall' }), { onClick: addSubtask }));
    setChildren(subtasksSlot, ...rows);
  };
  const addSubtask = () => {
    d.subtasks.push({ key: nextKey++, id: null, title: '', tagIds: new Set() });
    renderSubtasks();
    const inputs = subtasksSlot.querySelectorAll('.subtask-item input');
    inputs[inputs.length - 1]?.focus();
  };

  // ---------- Footer ----------
  const errorEl = h('div', { className: 'editor-error hidden', role: 'alert' });
  const clearError = () => errorEl.classList.add('hidden');
  const showError = (msg) => { errorEl.textContent = msg; errorEl.classList.remove('hidden'); };
  const saveBtn = h('button', { type: 'button', className: 'btn btn-filled btn-lg interactive', onClick: () => save() },
    icon('check', { size: 22 }), h('span', {}, isEdit ? 'Save' : 'Add task'));
  const footer = [
    isEdit ? h('button', { type: 'button', className: 'icon-btn error-tonal interactive', 'aria-label': 'Delete task', onClick: () => remove() }, icon('delete')) : null,
    h('span', { className: 'spacer' }),
    h('button', { type: 'button', className: 'btn btn-text btn-lg interactive', onClick: () => s.close() }, 'Cancel'),
    saveBtn,
  ];

  const s = sheet({
    label: isEdit ? 'Edit task' : 'New task',
    body: [
      h('div', { className: 'editor-head' }, h('h2', { className: 'headline-small' }, isEdit ? 'Edit task' : 'New task'), groupSlot),
      h('div', { className: 'editor-title-block' }, titleInput, h('div', { className: 'details-row' }, icon('notes'), detailsInput)),
      h('div', {}, h('span', { className: 'field-label' }, 'When'), whenSlot),
      reminderSlot,
      h('div', {}, h('span', { className: 'field-label' }, 'Priority'), priority),
      h('div', {}, h('span', { className: 'field-label' }, 'Tags'), tagsSlot),
      h('div', {}, h('span', { className: 'field-label' }, 'Subtasks'), subtasksSlot),
      errorEl,
    ],
    footer,
  });

  renderGroup(); renderWhen(); renderReminder(); renderTags(); renderSubtasks();
  autosize(titleInput);
  autosize(detailsInput);
  if (!isEdit) setTimeout(() => titleInput.focus(), 80);
  // Groups or tag types edited elsewhere show up while the editor is open
  const off = store.on(() => { renderGroup(); renderTags(); });
  const observer = new MutationObserver(() => { if (!s.box.isConnected) { off(); observer.disconnect(); } });
  observer.observe(document.body, { childList: true });

  async function save() {
    if (saving) return;
    const title = d.title.trim();
    if (!title) { showError('Title is required'); titleInput.focus(); return; }
    const reminder = d.reminder ? d.reminder.toISOString() : null;
    const data = {
      title,
      details: d.details.trim() || null,
      date: d.date,
      reminder,
      // A repeat rule only means something alongside a reminder
      reminder_repeat: reminder ? d.repeat : null,
      priority: d.priority,
      group_id: d.groupId,
      tag_ids: [...d.tagIds],
    };
    const subtasks = d.subtasks.filter(st => st.title.trim()).map(st => ({ id: st.id, title: st.title.trim(), tag_ids: [...st.tagIds] }));
    saving = true;
    saveBtn.disabled = true;
    saveBtn.lastChild.textContent = 'Saving…';
    try {
      if (isEdit) {
        await api.updateTask(task.id, data);
        const keep = subtasks.filter(st => st.id).map(st => st.id);
        for (const old of task.subtasks || []) if (!keep.includes(old.id)) await api.deleteSubtask(old.id);
        for (const st of subtasks) {
          if (st.id) await api.updateSubtask(st.id, { title: st.title, tag_ids: st.tag_ids });
          else await api.createSubtask({ task_id: task.id, title: st.title, tag_ids: st.tag_ids });
        }
      } else {
        await api.createTask({ ...data, subtasks: subtasks.map(({ title: t, tag_ids }) => ({ title: t, tag_ids })) });
      }
      s.close();
      onSaved();
    } catch (err) {
      saving = false;
      saveBtn.disabled = false;
      saveBtn.lastChild.textContent = isEdit ? 'Save' : 'Add task';
      showError(err.message);
    }
  }

  function remove() {
    confirmDialog('Delete task?', `"${d.title || task.title}" will be deleted.`, 'Delete', async () => {
      try {
        await api.deleteTask(task.id);
        s.close();
        onSaved();
      } catch (err) { showToast(err.message); }
    });
  }
}

/** Tag options grouped by type: outlined when off, a tonal chip in the tag's colour when on. */
function tagSelector(selected, onChange, { canCreate = false } = {}) {
  const root = h('div', { className: 'tag-selector' });
  const render = () => {
    if (!store.tagTypes.length) {
      setChildren(root, h('p', { className: 'body-medium muted' }, 'No tag types yet. Create them from the menu, under Tags.'));
      return;
    }
    setChildren(root, ...store.tagTypes.map(type => h('div', { className: 'tag-type-block' },
      h('div', { className: 'head' },
        h('span', { className: 'title-small' }, type.name),
        canCreate ? h('button', { type: 'button', className: 'btn btn-text interactive', onClick: () => createIn(type) }, icon('add', { size: 18 }), 'New') : null,
      ),
      (type.tags || []).length
        ? h('div', { className: 'chip-row', style: { marginTop: '4px' } }, ...type.tags.map(tag => {
          const on = selected.has(tag.id);
          const t = tonal(tag.color, type.color);
          return h('button', {
            type: 'button',
            className: `pick-chip interactive ${t.className}`,
            style: t.style,
            'aria-pressed': String(on),
            onClick: () => { if (on) selected.delete(tag.id); else selected.add(tag.id); render(); onChange(); },
          }, on ? icon('check', { size: 18 }) : null, tag.name);
        }))
        : h('p', { className: 'body-small muted', style: { marginTop: '4px' } }, 'No tags yet'),
    )));
  };
  // Creates a tag in [type] (inheriting its colours) and selects it
  const createIn = (type) => nameDialog(`New ${type.name} tag`, 'Tag name', '', async (name) => {
    try {
      const { id } = await api.createTag({ name, tag_type_id: type.id, color: type.color, fg_color: type.fg_color || '#ffffff', has_bg: type.has_bg ?? true });
      await store.loadTagTypes();
      selected.add(id);
      render();
      onChange();
    } catch (err) { showToast(err.message); }
  });
  render();
  return root;
}
