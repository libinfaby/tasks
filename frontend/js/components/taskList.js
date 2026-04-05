// ============================================================
// Tasks — Task List Component
// ============================================================
import { api } from '../api.js';
import {
  createElement, showToast, formatDate, isOverdue, isToday,
  getChipStyle, getPriorityLabel, getPriorityClass,
} from '../utils.js';
import { TaskForm } from './taskForm.js';

export class TaskList {
  constructor({ onRefreshSidebar }) { this.tasks = []; this.filters = {}; this.view = 'all'; this.groupFilter = null; this.searchQuery = ''; this.taskForm = null; this.onRefreshSidebar = onRefreshSidebar; }
  setView(view) { this.view = view; this.groupFilter = null; this.filters = this._getFiltersForView(view); }
  setGroupFilter(group) { this.view = 'group'; this.groupFilter = group; this.filters = { group_id: group.id }; }
  setSearch(query, type = 'task', tagTypeId = null) {
    this.searchQuery = query;
    if (query) { this.filters.search = query; this.filters.search_type = type; if (tagTypeId) this.filters.search_tag_type = tagTypeId; else delete this.filters.search_tag_type; }
    else { delete this.filters.search; delete this.filters.search_type; delete this.filters.search_tag_type; }
  }
  setTagFilter(tagId) { if (tagId) this.filters.tag_id = tagId; else delete this.filters.tag_id; const dd = document.getElementById('tag-filter'); if (dd) dd.value = tagId || ''; }
  _getFiltersForView(view) {
    const today = new Date().toISOString().split('T')[0]; const nextWeek = new Date(Date.now() + 7 * 86400000).toISOString().split('T')[0];
    switch (view) { case 'today': return { date_from: today, date_to: today }; case 'upcoming': return { date_from: today, date_to: nextWeek, completed: 'false' }; case 'priority': return { completed: 'false' }; case 'completed': return { completed: 'true' }; default: return {}; }
  }
  async loadTasks() { try { const data = await api.getTasks(this.filters); this.tasks = data.tasks || []; if (this.view === 'priority') this.tasks = this.tasks.filter(t => t.priority > 0); } catch (err) { showToast(err.message, 'error'); this.tasks = []; } }
  getViewTitle() { switch (this.view) { case 'all': return 'All Tasks'; case 'today': return 'Today'; case 'upcoming': return 'Upcoming'; case 'priority': return 'Priority'; case 'completed': return 'Completed'; case 'group': return this.groupFilter?.name || 'Group'; default: return 'Tasks'; } }
  render(container) {
    container.innerHTML = '';
    const fb = createElement('div', { className: 'filter-bar' },
      this._filterChip('All', this.view === 'all' && !this.groupFilter, () => { this.setView('all'); this.refresh(container); }),
      this._filterChip('Active', this.filters.completed === 'false', () => { this.filters.completed = this.filters.completed === 'false' ? '' : 'false'; this.refresh(container); }),
      this._filterChip('Urgent', this.filters.priority === '2', () => { this.filters.priority = this.filters.priority === '2' ? '' : '2'; this.refresh(container); }),
      this._filterChip('High', this.filters.priority === '1', () => { this.filters.priority = this.filters.priority === '1' ? '' : '1'; this.refresh(container); }),
      ...(this.filters.tag_id ? [this._filterChip('Tag Filter Active', true, () => { this.setTagFilter(''); this.refresh(container); })] : [])
    );
    container.appendChild(fb); const body = createElement('div', { className: 'content-body', id: 'task-list-body' }); container.appendChild(body); this._renderTaskList(body);
  }
  _renderTaskList(body) {
    body.innerHTML = ''; if (this.tasks.length === 0) { body.appendChild(this._renderEmptyState()); return; }
    if (this.view === 'all' && !this.groupFilter) {
      const g = this._groupTasksByGroup();
      if (g.priority.length > 0) body.appendChild(this._renderSection('Priority', g.priority, { color: '#ef4444', has_bg: true }));
      if (g.ungrouped.length > 0) body.appendChild(this._renderSection('Tasks', g.ungrouped, { color: '#6366f1', has_bg: true }));
      Object.entries(g.groups).forEach(([id, { group, tasks }]) => { body.appendChild(this._renderSection(group.name, tasks, group)); });
    } else { const list = createElement('div', { className: 'task-list' }); this.tasks.forEach((t, i) => { const c = this._renderTaskCard(t); c.style.animationDelay = `${i * 50}ms`; list.appendChild(c); }); body.appendChild(list); }
  }
  _groupTasksByGroup() {
    const res = { priority: [], ungrouped: [], groups: {} };
    this.tasks.forEach(t => {
      if (t.priority > 0 && !t.is_completed) res.priority.push(t);
      if (t.group_id && t.group) { if (!res.groups[t.group_id]) res.groups[t.group_id] = { group: t.group, tasks: [] }; res.groups[t.group_id].tasks.push(t); }
      else if (t.priority === 0 || t.is_completed) res.ungrouped.push(t);
    });
    return res;
  }
  _renderSection(title, tasks, group) {
    const color = group?.color || '#6366f1';
    return createElement('div', { className: 'task-group-section' },
      createElement('div', { className: 'task-group-header' }, createElement('div', { className: 'group-indicator', style: { background: color, border: 'none' } }), createElement('h3', { style: { color: group?.has_bg ? (group.fg_color || '#ffffff') : color } }, title), createElement('span', { className: 'group-count' }, `(${tasks.length})`)),
      createElement('div', { className: 'task-list' }, ...tasks.map((t, i) => { const c = this._renderTaskCard(t); c.style.animationDelay = `${i * 50}ms`; return c; }))
    );
  }

  _renderTaskCard(task) {
    const totalSubtasks = (task.subtasks || []).length;
    return createElement('div', {
      className: `task-card priority-${task.priority}${task.is_completed ? ' completed' : ''}`,
      onClick: (e) => { if (e.target.closest('.task-checkbox') || e.target.closest('.task-action-btn') || e.target.closest('.subtask-item .task-checkbox')) return; this._editTask(task); },
    },
      createElement('div', { className: 'task-card-header', style: { padding: '12px 16px', display: 'flex', gap: '12px', alignItems: 'flex-start' } },
        // Checkbox Container
        createElement('div', { style: { width: '18px', display: 'flex', flexDirection: 'column', alignItems: 'center', flexShrink: '0', marginRight: '12px' } },
          createElement('label', {
            className: 'task-checkbox',
            onClick: (e) => e.stopPropagation(),
            style: { marginTop: '26px' }
          },
            createElement('input', { type: 'checkbox', ...(task.is_completed ? { checked: 'true' } : {}), onChange: () => this._toggleTask(task) }),
            createElement('span', { className: 'checkmark' })
          )
        ),

        // Body
        createElement('div', { className: 'task-card-body', style: { flex: 1, minWidth: 0 } },
          // Top Row: Priority, Group and Actions
          this._renderTopMetaRow(task),

          // Title Row: Title + Date/Deadline
          createElement('div', {
            className: 'task-title-row',
            style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', minHeight: '24px' }
          },
            createElement('div', { className: 'task-title', style: { flex: 1 } }, task.title),
            this._renderDateMeta(task)
          ),

          task.details ? createElement('div', { className: 'task-details', style: { marginTop: '4px' } }, task.details) : null,
          this._renderTags(task.tags),
          totalSubtasks > 0 ? this._renderSubtasks(task) : null,
        )
      )
    );
  }

  _renderTopMetaRow(task) {
    const leftItems = [];
    if (task.priority > 0) {
      const cls = getPriorityClass(task.priority);
      leftItems.push(createElement('span', {
        className: `priority-badge ${cls}`,
        style: { border: 'none', fontWeight: '600' }
      }, getPriorityLabel(task.priority)));
    }
    if (task.group && this.view !== 'group') {
      const style = getChipStyle(task.group);
      leftItems.push(createElement('span', {
        className: 'tag-chip group-tag',
        style: { ...style, border: 'none' } // Solid from getChipStyle, border radius from CSS
      }, task.group.name));
    }

    const actions = createElement('div', {
      className: 'task-card-actions',
      style: { marginLeft: 'auto', display: 'flex', alignItems: 'center', height: '22px' }
    },
      createElement('button', { className: 'task-action-btn', onClick: (e) => { e.stopPropagation(); this._editTask(task); } }, 'Edit'),
      createElement('button', { className: 'task-action-btn delete', onClick: (e) => { e.stopPropagation(); this._deleteTask(task); } }, 'Delete'),
    );

    return createElement('div', {
      style: { display: 'flex', alignItems: 'center', marginBottom: '4px', gap: '8px' }
    }, ...leftItems, actions);
  }

  _renderDateMeta(task) {
    const items = [];
    if (task.date) {
      const dc = isOverdue(task.date) ? 'overdue' : (isToday(task.date) ? 'today' : '');
      items.push(createElement('span', { className: `task-date ${dc}`, style: { color: 'inherit', fontSize: '0.75rem', whiteSpace: 'nowrap' } }, formatDate(task.date)));
    }
    if (task.deadline) {
      items.push(createElement('span', { className: 'task-deadline', style: { color: 'inherit', fontSize: '0.75rem', whiteSpace: 'nowrap' } }, `• ${formatDate(task.deadline)}`));
    }
    if (items.length === 0) return null;
    return createElement('div', { style: { display: 'flex', gap: '8px', alignItems: 'center', height: '24px', flexShrink: '0' } }, ...items);
  }

  _renderTags(tags) {
    if (!tags || tags.length === 0) return null;
    return createElement('div', { className: 'tag-list', style: { marginTop: '8px', display: 'flex', flexWrap: 'wrap', gap: '6px' } },
      ...tags.map(tag => {
        const style = getChipStyle({ color: tag.color, fg_color: tag.fg_color, has_bg: tag.has_bg, type_color: tag.type_color, type_fg_color: tag.type_fg_color, type_has_bg: tag.type_has_bg });
        return createElement('span', {
          className: 'tag-chip selected-tag-clickable',
          style: { ...style, cursor: 'pointer', border: 'none', fontSize: '0.65rem' },
          onClick: (e) => { e.stopPropagation(); this.setTagFilter(tag.id); const b = document.getElementById('task-list-body'); if (b) this.refresh(b.parentElement); }
        },
          createElement('span', { className: 'tag-type-label' }, `${tag.type_name || 'Tag'}:`), ` ${tag.name}`
        );
      })
    );
  }

  _renderSubtasks(task) {
    const subtasks = task.subtasks || []; const completed = subtasks.filter(s => s.is_completed).length; const total = subtasks.length; const pct = total > 0 ? (completed / total) * 100 : 0;
    const container = createElement('div', { className: 'subtask-preview', style: { marginTop: '12px', borderLeft: '2px solid var(--border-color)', paddingLeft: '12px' } },
      createElement('div', { className: 'subtask-progress', style: { marginBottom: '8px' } }, createElement('div', { className: 'subtask-progress-bar' }, createElement('div', { className: 'subtask-progress-fill', style: { width: `${pct}%` } })), createElement('span', { className: 'subtask-progress-text' }, `${completed}/${total}`)));
    subtasks.forEach(s => {
      const item = createElement('div', { className: `subtask-item${s.is_completed ? ' completed' : ''}`, style: { display: 'flex', alignItems: 'center', gap: '8px', padding: '2px 0' } },
        createElement('label', { className: 'task-checkbox', style: { transform: 'scale(0.8)', flexShrink: '0' }, onClick: (e) => e.stopPropagation() }, createElement('input', { type: 'checkbox', ...(s.is_completed ? { checked: 'true' } : {}), onChange: () => this._toggleSubtask(s) }), createElement('span', { className: 'checkmark' })),
        createElement('span', { className: 'subtask-title', style: { fontSize: '0.75rem' } }, s.title)
      );
      container.appendChild(item);
    });
    return container;
  }
  _filterChip(label, active, onClick) { return createElement('button', { className: `filter-chip${active ? ' active' : ''}`, onClick }, label); }
  _renderEmptyState() { const msgs = { all: { title: 'No tasks' } }; const msg = msgs[this.view] || msgs.all; return createElement('div', { className: 'empty-state' }, createElement('h3', {}, msg.title)); }
  async _toggleTask(t) { try { await api.toggleTask(t.id); t.is_completed = !t.is_completed; const b = document.getElementById('task-list-body'); if (b) { await this.loadTasks(); this._renderTaskList(b); } this.onRefreshSidebar?.(); } catch (err) { showToast(err.message, 'error'); } }
  async _toggleSubtask(s) { try { await api.toggleSubtask(s.id); const b = document.getElementById('task-list-body'); if (b) { await this.loadTasks(); this._renderTaskList(b); } } catch (err) { showToast(err.message, 'error'); } }
  _editTask(t) { if (!this.taskForm) this.taskForm = new TaskForm({ onSave: async () => { const b = document.getElementById('task-list-body'); if (b) { await this.loadTasks(); this._renderTaskList(b); } this.onRefreshSidebar?.(); }, onClose: () => { } }); this.taskForm.open(t); }
  async _deleteTask(t) { if (!confirm(`Delete?`)) return; try { await api.deleteTask(t.id); showToast('Deleted', 'success'); const b = document.getElementById('task-list-body'); if (b) { await this.loadTasks(); this._renderTaskList(b); } this.onRefreshSidebar?.(); } catch (err) { showToast(err.message, 'error'); } }
  async refresh(c) { await this.loadTasks(); if (c) this.render(c); else { const b = document.getElementById('task-list-body'); if (b) this._renderTaskList(b); } }
  openNewTaskForm() { if (!this.taskForm) this.taskForm = new TaskForm({ onSave: async () => { const b = document.getElementById('task-list-body'); if (b) { await this.loadTasks(); this._renderTaskList(b); } this.onRefreshSidebar?.(); }, onClose: () => { } }); this.taskForm.open(null); }
}
