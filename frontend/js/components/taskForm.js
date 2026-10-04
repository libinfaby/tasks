// ============================================================
// Tasks — Task Form Component (Create/Edit Modal)
// ============================================================

import { api } from '../api.js';
import { createElement, showToast, formatDateInput, formatDatetimeLocal, getChipStyle, readableOnSurface, REPEAT_LABELS, PRIORITY_URGENT } from '../utils.js';
import { icon } from '../icons.js';

// Unselected options are outlined in the tag's colour; selected ones are filled (see .tag-option)
function tagOptionStyle(chip, isSelected) {
  const primary = chip.background === 'transparent' ? chip.color : readableOnSurface(chip.background);
  return isSelected
    ? { background: chip.background, color: chip.color }
    : { background: 'transparent', color: primary };
}

export class TaskForm {
  constructor({ onSave, onClose }) {
    this.onSave = onSave;
    this.onClose = onClose;
    this.task = null; 
    this.tagTypes = [];
    this.groups = [];
    this.subtasks = [];
    this.selectedTagIds = new Set();
    this.subtaskTags = new Map();
  }

  async open(task = null) {
    this.task = task; this.subtasks = []; this.selectedTagIds = new Set(); this.subtaskTags = new Map();
    try {
      const [tagData, groupData] = await Promise.all([api.getTagTypes(), api.getGroups()]);
      this.tagTypes = tagData.tag_types || [];
      this.groups = groupData.groups || [];
    } catch (err) { console.error('Failed to load form data:', err); }
    if (task) {
      this.subtasks = (task.subtasks || []).map(s => ({ id: s.id, title: s.title, tag_ids: (s.tags || []).map(t => t.id) }));
      this.selectedTagIds = new Set((task.tags || []).map(t => t.id));
      this.subtasks.forEach((s, i) => { this.subtaskTags.set(i, new Set(s.tag_ids)); });
    }
    this._render();
  }

  _render() {
    document.querySelector('.modal-overlay')?.remove();
    const isEdit = !!this.task;
    const today = new Date();
    const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    const overlay = createElement('div', {
      className: 'modal-overlay', id: 'task-modal-overlay',
      onClick: (e) => { if (e.target === overlay) this.close(); }
    },
      createElement('div', { className: 'modal', id: 'task-modal' },
        createElement('div', { className: 'modal-header' },
          createElement('h3', {}, isEdit ? 'Edit Task' : 'New Task'),
          createElement('button', { className: 'modal-close', title: 'Close', 'aria-label': 'Close', onClick: () => this.close() }, icon('x', { size: 18 }))
        ),
        createElement('div', { className: 'modal-body' },
          createElement('div', { className: 'form-group' },
            createElement('label', { for: 'task-title' }, 'Title'),
            createElement('input', { type: 'text', id: 'task-title', className: 'form-input', placeholder: 'What needs to be done?', value: this.task?.title || '', autofocus: 'true' }),
            createElement('div', { id: 'selected-tags-preview', className: 'selected-tags-preview' })
          ),
          createElement('div', { className: 'form-group' },
            createElement('label', { for: 'task-details' }, 'Details'),
            createElement('textarea', { id: 'task-details', className: 'form-input', placeholder: 'Add more details…', rows: '3' }, this.task?.details || '')
          ),
          createElement('div', { className: 'form-row' },
            createElement('div', { className: 'form-group' },
              createElement('label', { for: 'task-date' }, 'Date'),
              createElement('input', { type: 'date', id: 'task-date', className: 'form-input', value: isEdit ? (formatDateInput(this.task?.date) || '') : todayStr })
            ),
            createElement('div', { className: 'form-group' },
              createElement('label', { for: 'task-reminder' }, 'Reminder'),
              createElement('input', { type: 'datetime-local', id: 'task-reminder', className: 'form-input', value: formatDatetimeLocal(this.task?.reminder) || '' })
            ),
            createElement('div', { className: 'form-group' },
              createElement('label', { for: 'task-repeat' }, 'Repeat'),
              createElement('select', { id: 'task-repeat', className: 'form-select' },
                createElement('option', { value: '' }, 'Does not repeat'),
                ...Object.entries(REPEAT_LABELS).map(([value, label]) => createElement('option', { value }, label))
              )
            )
          ),
          createElement('div', { className: 'form-row' },
            createElement('div', { className: 'form-group' },
              createElement('label', { for: 'task-priority' }, 'Priority'),
              createElement('select', { id: 'task-priority', className: 'form-select' },
                createElement('option', { value: '0' }, 'Normal'),
                createElement('option', { value: String(PRIORITY_URGENT) }, 'Urgent')
              )
            ),
            createElement('div', { className: 'form-group' },
              createElement('label', { for: 'task-group' }, 'Group'),
              createElement('select', { id: 'task-group', className: 'form-select' },
                createElement('option', { value: '' }, 'No Group'),
                ...this.groups.map(g => createElement('option', { value: String(g.id) }, g.name))
              )
            )
          ),
          createElement('div', { className: 'form-group' },
            createElement('label', {}, 'Tags'),
            this._renderTagSelector('task-tags', this.selectedTagIds, (tagId) => {
              if (this.selectedTagIds.has(tagId)) this.selectedTagIds.delete(tagId); else this.selectedTagIds.add(tagId);
              this._refreshTagSelector('task-tags', this.selectedTagIds);
            })
          ),
          createElement('div', { className: 'form-group' },
            createElement('label', {}, 'Subtasks'),
            createElement('div', { className: 'subtask-editor', id: 'subtask-editor' },
              ...this.subtasks.map((s, i) => this._renderSubtaskItem(s, i)),
              createElement('button', { type: 'button', className: 'add-subtask-btn', onClick: () => this._addSubtask() }, icon('plus', { size: 15 }), 'Add subtask')
            )
          )
        ),
        createElement('div', { className: 'modal-footer' },
          isEdit ? createElement('button', { className: 'btn btn-danger footer-start', onClick: () => this._deleteTask() }, icon('trash'), 'Delete') : null,
          createElement('button', { className: 'btn btn-secondary', onClick: () => this.close() }, 'Cancel'),
          createElement('button', { className: 'btn btn-primary', id: 'task-save-btn', onClick: () => this._save() }, isEdit ? 'Save changes' : 'Create task')
        )
      )
    );
    document.body.appendChild(overlay);
    setTimeout(() => {
      document.getElementById('task-title')?.focus();
      this._renderTagsPreview();
      const p = document.getElementById('task-priority'); if (p && this.task) p.value = String(this.task.priority > 0 ? PRIORITY_URGENT : 0);
      const g = document.getElementById('task-group'); if (g && this.task) g.value = String(this.task.group_id || '');
      const r = document.getElementById('task-repeat'); if (r && this.task) r.value = this.task.reminder_repeat || '';
    }, 100);
  }

  _renderTagSelector(containerId, selectedIds, onToggle) {
    const container = createElement('div', { className: 'tag-selector', id: containerId });
    if (this.tagTypes.length === 0) {
      container.appendChild(createElement('div', { className: 'tag-selector-empty' },
        createElement('p', {}, 'No tag types yet.'),
        createElement('button', {
          type: 'button', className: 'add-tag-inline',
          onClick: () => this._showInlineCreateTagType(containerId, selectedIds, onToggle)
        }, icon('plus'), 'Create tag type')
      ));
      return container;
    }
    this.tagTypes.forEach(type => {
      const hasTags = type.tags && type.tags.length > 0;
      const group = createElement('div', { className: 'tag-selector-group' },
        createElement('div', { className: 'tag-selector-group-header' },
          createElement('div', { className: 'tag-selector-group-title' }, `${type.name}`),
          createElement('button', {
            type: 'button', className: 'tag-selector-add-btn',
            onClick: () => this._showInlineCreateTag(type, containerId, selectedIds, onToggle)
          }, '+ New')
        ),
        createElement('div', { className: 'tag-selector-options' },
          ...(hasTags ? type.tags.map(tag => {
            const isSelected = selectedIds.has(tag.id);
            const style = getChipStyle({ color: tag.color, fg_color: tag.fg_color, has_bg: tag.has_bg, type_color: type.color, type_fg_color: type.fg_color, type_has_bg: type.has_bg });
            return createElement('button', {
              type: 'button', className: `tag-option${isSelected ? ' selected' : ''}`, dataset: { tagId: tag.id },
              'aria-pressed': String(isSelected),
              style: tagOptionStyle(style, isSelected),
              onClick: () => onToggle(tag.id),
            }, tag.name);
          }) : [createElement('span', { className: 'tag-selector-none' }, 'No tags yet — use + New to add one')])
        )
      );
      container.appendChild(group);
    });
    return container;
  }

  _showInlineCreateTag(type, containerId, selectedIds, onToggle) {
    const container = document.getElementById(containerId);
    if (!container) return;
    // Remove any existing inline form
    container.querySelector('.inline-tag-form')?.remove();

    const nameInput = createElement('input', {
      type: 'text', className: 'form-input', placeholder: `New ${type.name} tag name…`,
    });
    const saveBtn = createElement('button', {
      type: 'button', className: 'btn btn-primary btn-sm',
      onClick: async () => {
        const name = nameInput.value.trim();
        if (!name) { nameInput.focus(); return; }
        saveBtn.textContent = '...'; saveBtn.disabled = true;
        try {
          const result = await api.createTag({ name, tag_type_id: type.id, color: type.color, fg_color: type.fg_color || '#ffffff', has_bg: type.has_bg !== undefined ? type.has_bg : true });
          showToast(`Tag "${name}" created`, 'success');
          // Reload tag types and re-render selector in place
          await this._reloadAndRefreshTagSelector(containerId, selectedIds, onToggle);
        } catch (err) {
          showToast(err.message, 'error');
          saveBtn.textContent = 'Add'; saveBtn.disabled = false;
        }
      }
    }, 'Add');
    const cancelBtn = createElement('button', {
      type: 'button', className: 'btn btn-ghost btn-sm',
      onClick: () => form.remove()
    }, 'Cancel');

    const form = createElement('div', { className: 'inline-tag-form' },
      createElement('div', { className: 'inline-tag-form-title' }, `Add tag to ${type.name}`),
      createElement('div', { className: 'inline-tag-form-row' }, nameInput, saveBtn, cancelBtn)
    );

    // Insert the form at the top of the container
    container.insertBefore(form, container.firstChild);
    setTimeout(() => nameInput.focus(), 50);

    // Support Enter key
    nameInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') { e.preventDefault(); saveBtn.click(); }
      if (e.key === 'Escape') { form.remove(); }
    });
  }

  _showInlineCreateTagType(containerId, selectedIds, onToggle) {
    const container = document.getElementById(containerId);
    if (!container) return;
    container.querySelector('.inline-tag-form')?.remove();

    const nameInput = createElement('input', {
      type: 'text', className: 'form-input', placeholder: 'Tag type name (e.g. Project, Client)…',
    });
    const colorInput = createElement('input', { type: 'color', className: 'form-input', value: '#6366f1', title: 'Colour' });
    const saveBtn = createElement('button', {
      type: 'button', className: 'btn btn-primary btn-sm',
      onClick: async () => {
        const name = nameInput.value.trim();
        if (!name) { nameInput.focus(); return; }
        saveBtn.textContent = '...'; saveBtn.disabled = true;
        try {
          await api.createTagType({ name, color: colorInput.value, fg_color: '#ffffff', has_bg: true, icon: '' });
          showToast(`Tag type "${name}" created`, 'success');
          await this._reloadAndRefreshTagSelector(containerId, selectedIds, onToggle);
        } catch (err) {
          showToast(err.message, 'error');
          saveBtn.textContent = 'Create'; saveBtn.disabled = false;
        }
      }
    }, 'Create');
    const cancelBtn = createElement('button', {
      type: 'button', className: 'btn btn-ghost btn-sm',
      onClick: () => form.remove()
    }, 'Cancel');

    const form = createElement('div', { className: 'inline-tag-form' },
      createElement('div', { className: 'inline-tag-form-title' }, 'Create a new tag type'),
      createElement('div', { className: 'inline-tag-form-row' }, nameInput, colorInput, saveBtn, cancelBtn)
    );

    container.innerHTML = '';
    container.appendChild(form);
    setTimeout(() => nameInput.focus(), 50);

    nameInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') { e.preventDefault(); saveBtn.click(); }
      if (e.key === 'Escape') { form.remove(); }
    });
  }

  async _reloadAndRefreshTagSelector(containerId, selectedIds, onToggle) {
    try {
      const tagData = await api.getTagTypes();
      this.tagTypes = tagData.tag_types || [];
    } catch (err) { console.error('Failed to reload tags:', err); }
    const container = document.getElementById(containerId);
    if (!container) return;
    const newSelector = this._renderTagSelector(containerId, selectedIds, onToggle);
    container.replaceWith(newSelector);
    // Also refresh the preview
    if (containerId === 'task-tags') this._renderTagsPreview();
  }

  _refreshTagSelector(containerId, selectedIds) {
    const container = document.getElementById(containerId); if (!container) return;
    container.querySelectorAll('.tag-option').forEach(el => {
      const tagId = parseInt(el.dataset.tagId); const isSelected = selectedIds.has(tagId);
      el.classList.toggle('selected', isSelected);
      let tagData = null; let typeData = null;
      for (const type of this.tagTypes) {
        const tag = type.tags?.find(t => t.id === tagId);
        if (tag) { tagData = tag; typeData = type; break; }
      }
      if (tagData && typeData) {
        const style = getChipStyle({ color: tagData.color, fg_color: tagData.fg_color, has_bg: tagData.has_bg, type_color: typeData.color, type_fg_color: typeData.fg_color, type_has_bg: typeData.has_bg });
        Object.assign(el.style, tagOptionStyle(style, isSelected));
        el.setAttribute('aria-pressed', String(isSelected));
      }
    });
    if (containerId === 'task-tags') this._renderTagsPreview();
  }

  _renderTagsPreview() {
    const container = document.getElementById('selected-tags-preview'); if (!container) return;
    container.innerHTML = '';
    this.selectedTagIds.forEach(id => {
      let ft = null; let fty = null;
      for(const type of this.tagTypes) {
        const tag = type.tags?.find(t => t.id === id);
        if (tag) { ft = tag; fty = type; break; }
      }
      if (ft && fty) {
        const s = getChipStyle({ color: ft.color, fg_color: ft.fg_color, has_bg: ft.has_bg, type_color: fty.color, type_fg_color: fty.fg_color, type_has_bg: fty.type_has_bg });
        container.appendChild(createElement('span', { className: 'tag-chip', style: s }, createElement('span', { className: 'tag-type-label' }, `${fty.name}:`), ` ${ft.name}`));
      }
    });
  }

  _renderSubtaskItem(subtask, index) {
    const item = createElement('div', { className: 'subtask-editor-item', dataset: { subtaskIndex: index } },
      createElement('input', { type: 'text', className: 'subtask-title-input', placeholder: 'Subtask title…', value: subtask.title || '', dataset: { subtaskIndex: index } }),
      createElement('button', { type: 'button', className: 'icon-btn', title: 'Tags', 'aria-label': 'Subtask tags', onClick: () => this._toggleSubtaskTags(index) }, icon('tag', { size: 15 })),
      createElement('button', { type: 'button', className: 'icon-btn danger', title: 'Remove', 'aria-label': 'Remove subtask', onClick: () => this._removeSubtask(index) }, icon('x', { size: 15 }))
    );
    const tagsContainer = createElement('div', { className: 'subtask-tags-selector hidden', id: `subtask-tags-${index}` });
    if (!this.subtaskTags.has(index)) this.subtaskTags.set(index, new Set());
    return createElement('div', {}, item, tagsContainer);
  }

  _toggleSubtaskTags(index) {
    const c = document.getElementById(`subtask-tags-${index}`); if (!c) return;
    if (c.classList.contains('hidden')) {
      c.classList.remove('hidden'); c.innerHTML = '';
      const ids = this.subtaskTags.get(index) || new Set();
      const sel = this._renderTagSelector(`subtask-tag-sel-${index}`, ids, (tid) => {
        const cur = this.subtaskTags.get(index) || new Set();
        if (cur.has(tid)) cur.delete(tid); else cur.add(tid);
        this.subtaskTags.set(index, cur); this._refreshTagSelector(`subtask-tag-sel-${index}`, cur);
      });
      c.appendChild(sel);
    } else { c.classList.add('hidden'); }
  }

  _addSubtask() {
    const ed = document.getElementById('subtask-editor'); if (!ed) return;
    const i = this.subtasks.length; this.subtasks.push({ title: '' }); this.subtaskTags.set(i, new Set());
    const btn = ed.querySelector('.add-subtask-btn'); const item = this._renderSubtaskItem({ title: '' }, i); ed.insertBefore(item, btn);
    setTimeout(() => { item.querySelector('input')?.focus(); }, 50);
  }

  _removeSubtask(index) {
    const ed = document.getElementById('subtask-editor'); if (!ed) return;
    const items = ed.querySelectorAll(`[data-subtask-index="${index}"]`);
    items.forEach(el => { const w = el.closest('.subtask-editor-item')?.parentElement; if (w) w.remove(); });
    this.subtasks[index] = null; this.subtaskTags.delete(index);
  }

  async _save() {
    const title = document.getElementById('task-title')?.value?.trim();
    const details = document.getElementById('task-details')?.value?.trim();
    const date = document.getElementById('task-date')?.value || null;
    const reminderVal = document.getElementById('task-reminder')?.value || null;
    const reminder = reminderVal ? new Date(reminderVal).toISOString() : null;
    // A repeat rule only means something alongside a reminder
    const reminderRepeat = reminder ? (document.getElementById('task-repeat')?.value || null) : null;
    const priority = parseInt(document.getElementById('task-priority')?.value || '0');
    const groupId = document.getElementById('task-group')?.value || null;
    if (!title) { showToast('Title is required', 'error'); document.getElementById('task-title')?.focus(); return; }
    const subtaskInputs = document.querySelectorAll('.subtask-title-input');
    const subtasks = [];
    subtaskInputs.forEach((input) => {
      const idx = parseInt(input.dataset.subtaskIndex); const stTitle = input.value.trim();
      if (stTitle) {
        const tids = this.subtaskTags.get(idx);
        subtasks.push({ title: stTitle, tag_ids: tids ? [...tids] : [], ...(this.subtasks[idx]?.id ? { id: this.subtasks[idx].id } : {}) });
      }
    });
    const saveBtn = document.getElementById('task-save-btn');
    if (saveBtn) { saveBtn.textContent = 'Saving…'; saveBtn.disabled = true; }
    try {
      const taskData = { title, details: details || null, date, reminder, reminder_repeat: reminderRepeat, priority, group_id: groupId ? parseInt(groupId) : null, tag_ids: [...this.selectedTagIds] };
      if (this.task) {
        await api.updateTask(this.task.id, taskData);
        const oldIds = (this.task.subtasks || []).map(s => s.id); const newIds = subtasks.filter(s => s.id).map(s => s.id);
        for (const id of oldIds) { if (!newIds.includes(id)) await api.deleteSubtask(id); }
        for (const sub of subtasks) {
          if (sub.id) await api.updateSubtask(sub.id, { title: sub.title, tag_ids: sub.tag_ids });
          else await api.createSubtask({ task_id: this.task.id, title: sub.title, tag_ids: sub.tag_ids });
        }
        showToast('Task updated', 'success');
      } else { taskData.subtasks = subtasks; await api.createTask(taskData); showToast('Task created', 'success'); }
      this.close(); this.onSave();
    } catch (err) {
      showToast(err.message, 'error');
      if (saveBtn) { saveBtn.textContent = this.task ? 'Save changes' : 'Create task'; saveBtn.disabled = false; }
    }
  }

  async _deleteTask() {
    if (!this.task) return; if (!confirm('Delete this task?')) return;
    try { await api.deleteTask(this.task.id); showToast('Task deleted', 'success'); this.close(); this.onSave(); } catch (err) { showToast(err.message, 'error'); }
  }

  close() { document.getElementById('task-modal-overlay')?.remove(); this.onClose?.(); }
}
