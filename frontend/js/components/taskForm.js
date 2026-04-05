// ============================================================
// Tasks — Task Form Component (Create/Edit Modal)
// ============================================================

import { api } from '../api.js';
import { createElement, showToast, formatDateInput, getChipStyle } from '../utils.js';

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
    const overlay = createElement('div', {
      className: 'modal-overlay', id: 'task-modal-overlay',
      onClick: (e) => { if (e.target === overlay) this.close(); }
    },
      createElement('div', { className: 'modal', id: 'task-modal' },
        createElement('div', { className: 'modal-header' },
          createElement('h3', {}, isEdit ? 'Edit Task' : 'New Task'),
          createElement('button', { className: 'modal-close', onClick: () => this.close() }, 'Close')
        ),
        createElement('div', { className: 'modal-body' },
          createElement('div', { className: 'form-group' },
            createElement('label', { for: 'task-title' }, 'Title'),
            createElement('input', { type: 'text', id: 'task-title', className: 'form-input', placeholder: 'What needs to be done?', value: this.task?.title || '', autofocus: 'true' }),
            createElement('div', { id: 'selected-tags-preview', className: 'selected-tags-preview', style: { display: 'none', marginTop: '8px' } })
          ),
          createElement('div', { className: 'form-group' },
            createElement('label', { for: 'task-details' }, 'Details'),
            createElement('textarea', { id: 'task-details', className: 'form-input', placeholder: 'Add more details...', rows: '3' }, this.task?.details || '')
          ),
          createElement('div', { className: 'form-row' },
            createElement('div', { className: 'form-group' },
              createElement('label', { for: 'task-date' }, 'Date'),
              createElement('input', { type: 'date', id: 'task-date', className: 'form-input', value: formatDateInput(this.task?.date) || '' })
            ),
            createElement('div', { className: 'form-group' },
              createElement('label', { for: 'task-deadline' }, 'Deadline'),
              createElement('input', { type: 'date', id: 'task-deadline', className: 'form-input', value: formatDateInput(this.task?.deadline) || '' })
            )
          ),
          createElement('div', { className: 'form-row' },
            createElement('div', { className: 'form-group' },
              createElement('label', { for: 'task-priority' }, 'Priority'),
              createElement('select', { id: 'task-priority', className: 'form-select' },
                createElement('option', { value: '0' }, 'Normal'),
                createElement('option', { value: '1' }, 'High'),
                createElement('option', { value: '2' }, 'Urgent')
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
              createElement('button', { className: 'add-subtask-btn', onClick: () => this._addSubtask() }, '+ Add subtask')
            )
          )
        ),
        createElement('div', { className: 'modal-footer' },
          isEdit ? createElement('button', { className: 'btn btn-danger btn-sm', onClick: () => this._deleteTask(), style: { marginRight: 'auto' } }, 'Delete') : null,
          createElement('button', { className: 'btn btn-secondary', onClick: () => this.close() }, 'Cancel'),
          createElement('button', { className: 'btn btn-primary', id: 'task-save-btn', onClick: () => this._save() }, isEdit ? 'Update' : 'Create Task')
        )
      )
    );
    document.body.appendChild(overlay);
    setTimeout(() => {
      document.getElementById('task-title')?.focus();
      this._renderTagsPreview();
      const p = document.getElementById('task-priority'); if (p && this.task) p.value = String(this.task.priority || 0);
      const g = document.getElementById('task-group'); if (g && this.task) g.value = String(this.task.group_id || '');
    }, 100);
  }

  _renderTagSelector(containerId, selectedIds, onToggle) {
    const container = createElement('div', { className: 'tag-selector', id: containerId });
    if (this.tagTypes.length === 0) {
      container.appendChild(createElement('p', { style: { fontSize: '0.8rem', color: 'var(--text-tertiary)' } }, 'No tags yet.'));
      return container;
    }
    this.tagTypes.forEach(type => {
      if (!type.tags || type.tags.length === 0) return;
      const group = createElement('div', { className: 'tag-selector-group' },
        createElement('div', { className: 'tag-selector-group-title' }, `${type.name}`),
        createElement('div', { className: 'tag-selector-options' },
          ...type.tags.map(tag => {
            const isSelected = selectedIds.has(tag.id);
            const style = getChipStyle({ color: tag.color, fg_color: tag.fg_color, has_bg: tag.has_bg, type_color: type.color, type_fg_color: type.fg_color, type_has_bg: type.has_bg });
            return createElement('button', {
              type: 'button', className: `tag-option${isSelected ? ' selected' : ''}`, dataset: { tagId: tag.id },
              style: {
                background: isSelected ? style.background : 'transparent',
                color: isSelected ? style.color : (style.background === 'transparent' ? style.color : style.background),
                border: isSelected ? 'none' : `1px solid ${style.background === 'transparent' ? style.color : style.background}`,
                padding: '4px 10px', margin: '2px', borderRadius: '0', fontSize: '0.75rem', cursor: 'pointer', opacity: isSelected ? '1' : '0.4'
              },
              onClick: () => onToggle(tag.id),
            }, tag.name);
          })
        )
      );
      container.appendChild(group);
    });
    return container;
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
        const primaryColor = (style.background === 'transparent' ? style.color : style.background);
        el.style.background = isSelected ? style.background : 'transparent';
        el.style.color = isSelected ? style.color : primaryColor;
        el.style.border = isSelected ? 'none' : `1px solid ${primaryColor}`;
        el.style.opacity = isSelected ? '1' : '0.4';
      }
    });
    if (containerId === 'task-tags') this._renderTagsPreview();
  }

  _renderTagsPreview() {
    const container = document.getElementById('selected-tags-preview'); if (!container) return;
    container.innerHTML = ''; if (this.selectedTagIds.size === 0) { container.style.display = 'none'; return; }
    container.style.display = 'flex'; container.style.flexWrap = 'wrap'; container.style.gap = '6px';
    this.selectedTagIds.forEach(id => {
      let ft = null; let fty = null;
      for(const type of this.tagTypes) {
        const tag = type.tags?.find(t => t.id === id);
        if (tag) { ft = tag; fty = type; break; }
      }
      if (ft && fty) {
        const s = getChipStyle({ color: ft.color, fg_color: ft.fg_color, has_bg: ft.has_bg, type_color: fty.color, type_fg_color: fty.fg_color, type_has_bg: fty.type_has_bg });
        container.appendChild(createElement('span', { className: 'tag-chip', style: { ...s, fontSize: '0.7rem', padding: '2px 8px', border: 'none' } }, `${fty.name}: ${ft.name}`));
      }
    });
  }

  _renderSubtaskItem(subtask, index) {
    const item = createElement('div', { className: 'subtask-editor-item', dataset: { subtaskIndex: index } },
      createElement('span', { style: { color: 'var(--text-tertiary)', fontSize: '0.8rem' } }, '-'),
      createElement('input', { type: 'text', className: 'subtask-title-input', placeholder: 'Subtask title...', value: subtask.title || '', dataset: { subtaskIndex: index } }),
      createElement('button', { className: 'btn-ghost', style: { fontSize: '0.75rem' }, onClick: () => this._toggleSubtaskTags(index) }, 'Tag'),
      createElement('button', { className: 'remove-subtask', onClick: () => this._removeSubtask(index) }, 'X')
    );
    const tagsContainer = createElement('div', { className: 'subtask-tags-selector', id: `subtask-tags-${index}`, style: { display: 'none', paddingLeft: '28px', paddingBottom: '8px' } });
    if (!this.subtaskTags.has(index)) this.subtaskTags.set(index, new Set());
    return createElement('div', {}, item, tagsContainer);
  }

  _toggleSubtaskTags(index) {
    const c = document.getElementById(`subtask-tags-${index}`); if (!c) return;
    if (c.style.display === 'none') {
      c.style.display = 'block'; c.innerHTML = '';
      const ids = this.subtaskTags.get(index) || new Set();
      const sel = this._renderTagSelector(`subtask-tag-sel-${index}`, ids, (tid) => {
        const cur = this.subtaskTags.get(index) || new Set();
        if (cur.has(tid)) cur.delete(tid); else cur.add(tid);
        this.subtaskTags.set(index, cur); this._refreshTagSelector(`subtask-tag-sel-${index}`, cur);
      });
      c.appendChild(sel);
    } else { c.style.display = 'none'; }
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
    const deadline = document.getElementById('task-deadline')?.value || null;
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
    if (saveBtn) { saveBtn.textContent = 'Saving...'; saveBtn.disabled = true; }
    try {
      const taskData = { title, details: details || null, date, deadline, priority, group_id: groupId ? parseInt(groupId) : null, tag_ids: [...this.selectedTagIds] };
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
      if (saveBtn) { saveBtn.textContent = this.task ? 'Update' : 'Create Task'; saveBtn.disabled = false; }
    }
  }

  async _deleteTask() {
    if (!this.task) return; if (!confirm('Delete this task?')) return;
    try { await api.deleteTask(this.task.id); showToast('Task deleted', 'success'); this.close(); this.onSave(); } catch (err) { showToast(err.message, 'error'); }
  }

  close() { document.getElementById('task-modal-overlay')?.remove(); this.onClose?.(); }
}
