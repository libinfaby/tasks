// ============================================================
// Tasks — Task Form Component (Create/Edit Modal)
// ============================================================

import { api } from '../api.js';
import { createElement, showToast, formatDateInput, getTagBg } from '../utils.js';

export class TaskForm {
  constructor({ onSave, onClose }) {
    this.onSave = onSave;
    this.onClose = onClose;
    this.task = null; // null = create mode
    this.tagTypes = [];
    this.groups = [];
    this.subtasks = [];
    this.selectedTagIds = new Set();
    this.subtaskTags = new Map(); // subtaskIndex -> Set of tagIds
  }

  async open(task = null) {
    this.task = task;
    this.subtasks = [];
    this.selectedTagIds = new Set();
    this.subtaskTags = new Map();

    // Load tag types and groups
    try {
      const [tagData, groupData] = await Promise.all([
        api.getTagTypes(),
        api.getGroups(),
      ]);
      this.tagTypes = tagData.tag_types || [];
      this.groups = groupData.groups || [];
    } catch (err) {
      console.error('Failed to load form data:', err);
    }

    // If editing, populate from task
    if (task) {
      this.subtasks = (task.subtasks || []).map(s => ({
        id: s.id,
        title: s.title,
        tag_ids: (s.tags || []).map(t => t.id),
      }));
      this.selectedTagIds = new Set((task.tags || []).map(t => t.id));
      this.subtasks.forEach((s, i) => {
        this.subtaskTags.set(i, new Set(s.tag_ids));
      });
    }

    this._render();
  }

  _render() {
    // Remove existing modal
    document.querySelector('.modal-overlay')?.remove();

    const isEdit = !!this.task;

    const overlay = createElement('div', {
      className: 'modal-overlay',
      id: 'task-modal-overlay',
      onClick: (e) => {
        if (e.target === overlay) this.close();
      }
    },
      createElement('div', { className: 'modal', id: 'task-modal' },
        // Header
        createElement('div', { className: 'modal-header' },
          createElement('h3', {}, isEdit ? 'Edit Task' : 'New Task'),
          createElement('button', {
            className: 'modal-close',
            onClick: () => this.close(),
          }, 'Close')
        ),

        // Body
        createElement('div', { className: 'modal-body' },
          // Title
          createElement('div', { className: 'form-group' },
            createElement('label', { for: 'task-title' }, 'Title'),
            createElement('input', {
              type: 'text',
              id: 'task-title',
              className: 'form-input',
              placeholder: 'What needs to be done?',
              value: this.task?.title || '',
              autofocus: 'true',
            })
          ),

          // Details
          createElement('div', { className: 'form-group' },
            createElement('label', { for: 'task-details' }, 'Details'),
            createElement('textarea', {
              id: 'task-details',
              className: 'form-input',
              placeholder: 'Add more details...',
              rows: '3',
            }, this.task?.details || '')
          ),

          // Date and Deadline
          createElement('div', { className: 'form-row' },
            createElement('div', { className: 'form-group' },
              createElement('label', { for: 'task-date' }, 'Date'),
              createElement('input', {
                type: 'date',
                id: 'task-date',
                className: 'form-input',
                value: formatDateInput(this.task?.date) || '',
              })
            ),
            createElement('div', { className: 'form-group' },
              createElement('label', { for: 'task-deadline' }, 'Deadline'),
              createElement('input', {
                type: 'date',
                id: 'task-deadline',
                className: 'form-input',
                value: formatDateInput(this.task?.deadline) || '',
              })
            )
          ),

          // Priority and Group
          createElement('div', { className: 'form-row' },
            createElement('div', { className: 'form-group' },
              createElement('label', { for: 'task-priority' }, 'Priority'),
              createElement('select', {
                id: 'task-priority',
                className: 'form-select',
              },
                createElement('option', { value: '0', ...(this.task?.priority === 0 ? { selected: 'true' } : {}) }, 'Normal'),
                createElement('option', { value: '1', ...(this.task?.priority === 1 ? { selected: 'true' } : {}) }, 'High'),
                createElement('option', { value: '2', ...(this.task?.priority === 2 ? { selected: 'true' } : {}) }, 'Urgent')
              )
            ),
            createElement('div', { className: 'form-group' },
              createElement('label', { for: 'task-group' }, 'Group'),
              createElement('select', {
                id: 'task-group',
                className: 'form-select',
              },
                createElement('option', { value: '' }, 'No Group'),
                ...this.groups.map(g =>
                  createElement('option', {
                    value: String(g.id),
                    ...(this.task?.group_id === g.id ? { selected: 'true' } : {})
                  }, g.name)
                )
              )
            )
          ),

          // Tags
          createElement('div', { className: 'form-group' },
            createElement('label', {}, 'Tags'),
            this._renderTagSelector('task-tags', this.selectedTagIds, (tagId) => {
              if (this.selectedTagIds.has(tagId)) {
                this.selectedTagIds.delete(tagId);
              } else {
                this.selectedTagIds.add(tagId);
              }
              this._refreshTagSelector('task-tags', this.selectedTagIds);
            })
          ),

          // Subtasks
          createElement('div', { className: 'form-group' },
            createElement('label', {}, 'Subtasks'),
            createElement('div', { className: 'subtask-editor', id: 'subtask-editor' },
              ...this.subtasks.map((s, i) => this._renderSubtaskItem(s, i)),
              createElement('button', {
                className: 'add-subtask-btn',
                onClick: () => this._addSubtask(),
              }, '+ Add subtask')
            )
          )
        ),

        // Footer
        createElement('div', { className: 'modal-footer' },
          isEdit ? createElement('button', {
            className: 'btn btn-danger btn-sm',
            onClick: () => this._deleteTask(),
            style: { marginRight: 'auto' },
          }, 'Delete') : null,
          createElement('button', {
            className: 'btn btn-secondary',
            onClick: () => this.close(),
          }, 'Cancel'),
          createElement('button', {
            className: 'btn btn-primary',
            id: 'task-save-btn',
            onClick: () => this._save(),
          }, isEdit ? 'Update' : 'Create Task')
        )
      )
    );

    document.body.appendChild(overlay);

    // Focus title
    setTimeout(() => {
      document.getElementById('task-title')?.focus();
    }, 100);

    // Set priority value properly
    const prioritySelect = document.getElementById('task-priority');
    if (prioritySelect && this.task) {
      prioritySelect.value = String(this.task.priority || 0);
    }
  }

  _renderTagSelector(containerId, selectedIds, onToggle) {
    const container = createElement('div', { className: 'tag-selector', id: containerId });

    if (this.tagTypes.length === 0) {
      container.appendChild(
        createElement('p', {
          style: { fontSize: '0.8rem', color: 'var(--text-tertiary)' }
        }, 'No tags yet. Create some in Tags.')
      );
      return container;
    }

    this.tagTypes.forEach(type => {
      if (!type.tags || type.tags.length === 0) return;

      const group = createElement('div', { className: 'tag-selector-group' },
        createElement('div', { className: 'tag-selector-group-title' },
          `${type.name}`
        ),
        createElement('div', { className: 'tag-selector-options' },
          ...type.tags.map(tag => {
            const isSelected = selectedIds.has(tag.id);
            return createElement('button', {
              type: 'button',
              className: `tag-option${isSelected ? ' selected' : ''}`,
              dataset: { tagId: tag.id },
              style: {
                borderColor: isSelected ? (tag.color || type.color) : '',
                background: isSelected ? getTagBg(tag.color || type.color) : '',
                color: isSelected ? (tag.color || type.color) : '',
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
    const container = document.getElementById(containerId);
    if (!container) return;

    container.querySelectorAll('.tag-option').forEach(el => {
      const tagId = parseInt(el.dataset.tagId);
      const isSelected = selectedIds.has(tagId);
      el.classList.toggle('selected', isSelected);

      // Find the tag to get its color
      let tagColor = '';
      for (const type of this.tagTypes) {
        const tag = type.tags?.find(t => t.id === tagId);
        if (tag) {
          tagColor = tag.color || type.color;
          break;
        }
      }

      el.style.borderColor = isSelected ? tagColor : '';
      el.style.background = isSelected ? getTagBg(tagColor) : '';
      el.style.color = isSelected ? tagColor : '';
    });
  }

  _renderSubtaskItem(subtask, index) {
    const item = createElement('div', {
      className: 'subtask-editor-item',
      dataset: { subtaskIndex: index },
    },
      createElement('span', { style: { color: 'var(--text-tertiary)', fontSize: '0.8rem' } }, '-'),
      createElement('input', {
        type: 'text',
        className: 'subtask-title-input',
        placeholder: 'Subtask title...',
        value: subtask.title || '',
        dataset: { subtaskIndex: index },
      }),
      createElement('button', {
        className: 'btn-ghost',
        title: 'Tags',
        style: { fontSize: '0.75rem' },
        onClick: () => this._toggleSubtaskTags(index),
      }, 'Tag'),
      createElement('button', {
        className: 'remove-subtask',
        onClick: () => this._removeSubtask(index),
      }, 'X')
    );

    // Subtask tags container (hidden by default)
    const tagsContainer = createElement('div', {
      className: 'subtask-tags-selector',
      id: `subtask-tags-${index}`,
      style: { display: 'none', paddingLeft: '28px', paddingBottom: '8px' },
    });

    if (!this.subtaskTags.has(index)) {
      this.subtaskTags.set(index, new Set());
    }

    const wrapper = createElement('div', {}, item, tagsContainer);
    return wrapper;
  }

  _toggleSubtaskTags(index) {
    const container = document.getElementById(`subtask-tags-${index}`);
    if (!container) return;

    if (container.style.display === 'none') {
      container.style.display = 'block';
      container.innerHTML = '';
      const selectedIds = this.subtaskTags.get(index) || new Set();

      const selector = this._renderTagSelector(`subtask-tag-sel-${index}`, selectedIds, (tagId) => {
        const ids = this.subtaskTags.get(index) || new Set();
        if (ids.has(tagId)) {
          ids.delete(tagId);
        } else {
          ids.add(tagId);
        }
        this.subtaskTags.set(index, ids);
        this._refreshTagSelector(`subtask-tag-sel-${index}`, ids);
      });
      container.appendChild(selector);
    } else {
      container.style.display = 'none';
    }
  }

  _addSubtask() {
    const editor = document.getElementById('subtask-editor');
    if (!editor) return;

    const index = this.subtasks.length;
    this.subtasks.push({ title: '' });
    this.subtaskTags.set(index, new Set());

    // Insert before the add button
    const addBtn = editor.querySelector('.add-subtask-btn');
    const item = this._renderSubtaskItem({ title: '' }, index);
    editor.insertBefore(item, addBtn);

    // Focus the new input
    setTimeout(() => {
      item.querySelector('input')?.focus();
    }, 50);
  }

  _removeSubtask(index) {
    const editor = document.getElementById('subtask-editor');
    if (!editor) return;

    const items = editor.querySelectorAll(`[data-subtask-index="${index}"]`);
    // Remove the wrapper (parent of the item)
    items.forEach(el => {
      const wrapper = el.closest('.subtask-editor-item')?.parentElement;
      if (wrapper && wrapper.children.length <= 2) wrapper.remove();
    });

    this.subtasks[index] = null; // Mark as removed
    this.subtaskTags.delete(index);
  }

  async _save() {
    const title = document.getElementById('task-title')?.value?.trim();
    const details = document.getElementById('task-details')?.value?.trim();
    const date = document.getElementById('task-date')?.value || null;
    const deadline = document.getElementById('task-deadline')?.value || null;
    const priority = parseInt(document.getElementById('task-priority')?.value || '0');
    const groupId = document.getElementById('task-group')?.value || null;

    if (!title) {
      showToast('Title is required', 'error');
      document.getElementById('task-title')?.focus();
      return;
    }

    // Collect subtasks from inputs
    const subtaskInputs = document.querySelectorAll('.subtask-title-input');
    const subtasks = [];
    subtaskInputs.forEach((input, i) => {
      const idx = parseInt(input.dataset.subtaskIndex);
      const stTitle = input.value.trim();
      if (stTitle) {
        const tagIds = this.subtaskTags.get(idx);
        subtasks.push({
          title: stTitle,
          tag_ids: tagIds ? [...tagIds] : [],
          ...(this.subtasks[idx]?.id ? { id: this.subtasks[idx].id } : {}),
        });
      }
    });

    const saveBtn = document.getElementById('task-save-btn');
    if (saveBtn) {
      saveBtn.textContent = 'Saving...';
      saveBtn.disabled = true;
    }

    try {
      const taskData = {
        title,
        details: details || null,
        date,
        deadline,
        priority,
        group_id: groupId ? parseInt(groupId) : null,
        tag_ids: [...this.selectedTagIds],
      };

      if (this.task) {
        // Update existing task
        await api.updateTask(this.task.id, taskData);

        // Handle subtasks: delete removed, update existing, add new
        const existingSubtaskIds = (this.task.subtasks || []).map(s => s.id);
        const newSubtaskIds = subtasks.filter(s => s.id).map(s => s.id);

        // Delete removed subtasks
        for (const id of existingSubtaskIds) {
          if (!newSubtaskIds.includes(id)) {
            await api.deleteSubtask(id);
          }
        }

        // Update or create subtasks
        for (const sub of subtasks) {
          if (sub.id) {
            await api.updateSubtask(sub.id, { title: sub.title, tag_ids: sub.tag_ids });
          } else {
            await api.createSubtask({ task_id: this.task.id, title: sub.title, tag_ids: sub.tag_ids });
          }
        }

        showToast('Task updated', 'success');
      } else {
        // Create new task
        taskData.subtasks = subtasks;
        await api.createTask(taskData);
        showToast('Task created', 'success');
      }

      this.close();
      this.onSave();
    } catch (err) {
      showToast(err.message, 'error');
      if (saveBtn) {
        saveBtn.textContent = this.task ? 'Update' : 'Create Task';
        saveBtn.disabled = false;
      }
    }
  }

  async _deleteTask() {
    if (!this.task) return;
    if (!confirm('Delete this task? This cannot be undone.')) return;

    try {
      await api.deleteTask(this.task.id);
      showToast('Task deleted', 'success');
      this.close();
      this.onSave();
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  close() {
    document.getElementById('task-modal-overlay')?.remove();
    this.onClose?.();
  }
}
