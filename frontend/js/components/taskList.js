// ============================================================
// Tasks — Task List Component
// ============================================================
import { api } from '../api.js';
import {
  createElement, showToast, formatDate, isOverdue, isToday,
  getChipStyle, getPriorityLabel, getPriorityClass, formatReminder, REPEAT_LABELS,
} from '../utils.js';
import { TaskForm } from './taskForm.js';
import { toDateStr } from './dailyLog.js';
import { icon } from '../icons.js';

const TAG_TYPE_ORDER = ['client', 'project', 'via'];
// Tags named like this render as a colored strip on the card's left edge instead of a chip
const KIND_TAG_NAMES = ['issue', 'requirement', 'modification'];
const isKindTag = (tag) => KIND_TAG_NAMES.includes((tag.name || '').trim().toLowerCase());

export class TaskList {
  constructor({ onRefreshSidebar }) { this.tasks = []; this.filters = {}; this.view = 'all'; this.groupFilter = null; this.searchQuery = ''; this.taskForm = null; this.onRefreshSidebar = onRefreshSidebar; }
  setView(view) { this.view = view; this.groupFilter = null; this.filters = this._getFiltersForView(view); }
  setGroupFilter(group) { this.view = 'group'; this.groupFilter = group; this.filters = { group_id: group.id, completed: 'false' }; }
  setSearch(query, type = 'task', tagTypeId = null) {
    this.searchQuery = query;
    if (query) { this.filters.search = query; this.filters.search_type = type; if (tagTypeId) this.filters.search_tag_type = tagTypeId; else delete this.filters.search_tag_type; }
    else { delete this.filters.search; delete this.filters.search_type; delete this.filters.search_tag_type; }
  }
  setTagFilter(tagId) { if (tagId) this.filters.tag_id = tagId; else delete this.filters.tag_id; const dd = document.getElementById('tag-filter'); if (dd) dd.value = tagId || ''; }
  _getFiltersForView(view) {
    const today = new Date().toISOString().split('T')[0]; const nextWeek = new Date(Date.now() + 7 * 86400000).toISOString().split('T')[0];
    // Only active tasks by default; completed ones are shown via the Completed view or chip
    switch (view) { case 'today': return { date_from: today, date_to: today, completed: 'false' }; case 'upcoming': return { date_from: today, date_to: nextWeek, completed: 'false' }; case 'priority': return { completed: 'false' }; case 'completed': return { completed: 'true' }; default: return { completed: 'false' }; }
  }
  async loadTasks() { try { const data = await api.getTasks(this.filters); this.tasks = data.tasks || []; if (this.view === 'priority') this.tasks = this.tasks.filter(t => t.priority > 0); } catch (err) { showToast(err.message, 'error'); this.tasks = []; } }
  getViewTitle() { switch (this.view) { case 'all': return 'All Tasks'; case 'today': return 'Today'; case 'upcoming': return 'Upcoming'; case 'priority': return 'Priority'; case 'completed': return 'Completed'; case 'group': return this.groupFilter?.name || 'Group'; default: return 'Tasks'; } }
  render(container) {
    container.innerHTML = '';
    const fb = createElement('div', { className: 'filter-bar' },
      this._filterChip('All', this.view === 'all' && !this.groupFilter, () => { this.setView('all'); this.refresh(container); }),
      this._filterChip('Completed', this.filters.completed === 'true', () => { this.filters.completed = this.filters.completed === 'true' ? 'false' : 'true'; this.refresh(container); }),
      this._filterChip('Urgent', this.filters.priority === '2', () => { this.filters.priority = this.filters.priority === '2' ? '' : '2'; this.refresh(container); }),
      this._filterChip('High', this.filters.priority === '1', () => { this.filters.priority = this.filters.priority === '1' ? '' : '1'; this.refresh(container); }),
      ...(this.filters.tag_id ? [this._filterChip('Tag filter', true, () => { this.setTagFilter(''); this.refresh(container); }, icon('x', { size: 13 }))] : [])
    );
    container.appendChild(fb); const body = createElement('div', { className: 'content-body', id: 'task-list-body' }); container.appendChild(body); this._renderTaskList(body);
  }
  _renderTaskList(body) {
    body.innerHTML = '';
    const count = document.getElementById('view-count'); if (count) count.textContent = String(this.tasks.length);
    if (this.tasks.length === 0) { body.appendChild(this._renderEmptyState()); return; }
    if (this.view === 'all' && !this.groupFilter) {
      const g = this._groupTasksByGroup();
      if (g.priority.length > 0) body.appendChild(this._renderSection('Priority', g.priority, { color: 'var(--priority-urgent)' }));
      if (g.ungrouped.length > 0) body.appendChild(this._renderSection('Tasks', g.ungrouped, { color: 'var(--accent)' }));
      Object.entries(g.groups).forEach(([id, { group, tasks }]) => { body.appendChild(this._renderSection(group.name, tasks, group)); });
    } else { body.appendChild(createElement('div', { className: 'task-list' }, ...this.tasks.map((t, i) => this._renderTaskCard(t, i)))); }
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
    const color = group?.color || 'var(--accent)';
    return createElement('div', { className: 'task-group-section' },
      createElement('div', { className: 'task-group-header' },
        createElement('span', { className: 'group-indicator', style: { background: color } }),
        createElement('h3', {}, title),
        createElement('span', { className: 'group-count' }, String(tasks.length))
      ),
      createElement('div', { className: 'task-list' }, ...tasks.map((t, i) => this._renderTaskCard(t, i)))
    );
  }

  _renderTaskCard(task, index = 0) {
    const card = createElement('div', {
      className: `task-card priority-${task.priority}${task.is_completed ? ' completed' : ''}`,
      onClick: (e) => { if (e.target.closest('.task-checkbox') || e.target.closest('.task-action-btn') || e.target.closest('.selected-tag-clickable')) return; this._editTask(task); },
    },
      createElement('label', { className: 'task-checkbox', title: task.is_completed ? 'Mark as not done' : 'Mark as done', onClick: (e) => e.stopPropagation() },
        createElement('input', { type: 'checkbox', 'aria-label': `Complete ${task.title}`, ...(task.is_completed ? { checked: 'true' } : {}), onChange: () => this._toggleTask(task) }),
        createElement('span', { className: 'checkmark' })
      ),
      createElement('div', { className: 'task-card-body' },
        createElement('div', { className: 'task-title-row' },
          createElement('div', { className: 'task-title' }, task.title),
          createElement('div', { className: 'task-card-actions' },
            createElement('button', { className: 'task-action-btn', title: 'Edit', 'aria-label': 'Edit task', onClick: (e) => { e.stopPropagation(); this._editTask(task); } }, icon('pencil', { size: 15 })),
            createElement('button', { className: 'task-action-btn delete', title: 'Delete', 'aria-label': 'Delete task', onClick: (e) => { e.stopPropagation(); this._deleteTask(task); } }, icon('trash', { size: 15 })),
          )
        ),
        task.details ? createElement('div', { className: 'task-details' }, task.details) : null,
        this._renderMeta(task),
        (task.subtasks || []).length > 0 ? this._renderSubtasks(task) : null,
      )
    );
    card.style.animationDelay = `${Math.min(index, 12) * 25}ms`;
    return card;
  }

  // Priority, group, date, reminder and tags on one wrapping line
  _renderMeta(task) {
    const items = [];
    if (task.priority > 0) {
      items.push(createElement('span', { className: `priority-badge ${getPriorityClass(task.priority)}` }, icon('flag'), getPriorityLabel(task.priority)));
    }
    if (task.group && this.view !== 'group') {
      items.push(createElement('span', { className: 'tag-chip group-tag', style: getChipStyle(task.group) }, task.group.name));
    }
    if (task.date) {
      const dc = isOverdue(task.date) && !task.is_completed ? ' overdue' : (isToday(task.date) ? ' today' : '');
      items.push(createElement('span', { className: `meta-item${dc}`, title: 'Date' }, icon('calendar'), formatDate(task.date)));
    }
    if (task.reminder) {
      const repeat = REPEAT_LABELS[task.reminder_repeat];
      items.push(createElement('span', { className: 'meta-item', title: repeat ? `Reminder, repeats ${repeat.toLowerCase()}` : 'Reminder' },
        icon('bell'), formatReminder(task.reminder),
        ...(repeat ? [icon('repeat'), repeat] : [])
      ));
    }
    const tags = this._renderTags(task.tags);
    if (tags) items.push(tags);
    if (items.length === 0) return null;
    return createElement('div', { className: 'task-meta' }, ...items);
  }

  _renderTags(tags) {
    if (!tags || tags.length === 0) return null;
    // Client first, then the kind chip (Issue/Requirement/Modification), then Project, Via, then any other tag types
    const ORDER = ['client', 'kind', 'project', 'via'];
    const rank = (tag) => { const i = ORDER.indexOf(isKindTag(tag) ? 'kind' : (tag.type_name || '').toLowerCase()); return i === -1 ? ORDER.length : i; };
    const sorted = [...tags].sort((a, b) => rank(a) - rank(b));
    return createElement('div', { className: 'tag-list' },
      ...sorted.map(tag => {
        const kind = isKindTag(tag);
        const style = kind
          ? getChipStyle({ color: tag.color, fg_color: tag.fg_color, has_bg: true, type_color: tag.type_color, type_fg_color: tag.type_fg_color })
          : getChipStyle({ color: tag.color, fg_color: tag.fg_color, has_bg: tag.has_bg, type_color: tag.type_color, type_fg_color: tag.type_fg_color, type_has_bg: tag.type_has_bg });
        return createElement('span', {
          className: `tag-chip selected-tag-clickable${kind ? ' kind-chip' : ''}`,
          style,
          title: `Filter by ${tag.name}`,
          onClick: (e) => { e.stopPropagation(); this.setTagFilter(tag.id); const b = document.getElementById('task-list-body'); if (b) this.refresh(b.parentElement); }
        },
          // Client, Project and Via tags read as just their name; other types keep the "Type:" label
          ...(kind || TAG_TYPE_ORDER.includes((tag.type_name || '').toLowerCase())
            ? [tag.name]
            : [createElement('span', { className: 'tag-type-label' }, `${tag.type_name || 'Tag'}:`), ` ${tag.name}`])
        );
      })
    );
  }

  _renderSubtasks(task) {
    const subtasks = task.subtasks || []; const completed = subtasks.filter(s => s.is_completed).length; const total = subtasks.length; const pct = total > 0 ? (completed / total) * 100 : 0;
    const container = createElement('div', { className: 'subtask-preview' },
      createElement('div', { className: 'subtask-progress' },
        createElement('div', { className: 'subtask-progress-bar' }, createElement('div', { className: 'subtask-progress-fill', style: { width: `${pct}%` } })),
        createElement('span', { className: 'subtask-progress-text' }, `${completed}/${total}`)
      )
    );
    subtasks.forEach(s => {
      container.appendChild(createElement('div', { className: `subtask-item${s.is_completed ? ' completed' : ''}` },
        createElement('label', { className: 'task-checkbox sm', onClick: (e) => e.stopPropagation() },
          createElement('input', { type: 'checkbox', 'aria-label': `Complete ${s.title}`, ...(s.is_completed ? { checked: 'true' } : {}), onChange: () => this._toggleSubtask(s) }),
          createElement('span', { className: 'checkmark' })
        ),
        createElement('span', { className: 'subtask-title' }, s.title)
      ));
    });
    return container;
  }
  _filterChip(label, active, onClick, trailing = null) { return createElement('button', { className: `filter-chip${active ? ' active' : ''}`, 'aria-pressed': String(active), onClick }, label, trailing); }
  _renderEmptyState() {
    const searching = !!this.filters.search || !!this.filters.tag_id;
    const msgs = {
      all: ['inbox', 'No tasks', 'Create a task to get started.'],
      today: ['calendarCheck', 'Nothing due today', 'Tasks dated today will show up here.'],
      upcoming: ['clock', 'Nothing upcoming', 'Tasks dated in the next 7 days will show up here.'],
      priority: ['flag', 'No priority tasks', 'High and urgent tasks will show up here.'],
      completed: ['circleCheck', 'No completed tasks', 'Tasks you finish will show up here.'],
    };
    const [ic, title, text] = searching ? ['search', 'No matching tasks', 'Try a different search or clear the filter.'] : (msgs[this.view] || msgs.all);
    return createElement('div', { className: 'empty-state' },
      createElement('div', { className: 'empty-icon' }, icon(ic, { size: 20 })),
      createElement('h3', {}, title),
      createElement('p', {}, text)
    );
  }
  async _toggleTask(t) {
    try {
      await api.toggleTask(t.id); t.is_completed = !t.is_completed;
      const b = document.getElementById('task-list-body'); if (b) { await this.loadTasks(); this._renderTaskList(b); } this.onRefreshSidebar?.();
      const clients = (t.tags || []).filter(g => (g.type_name || '').toLowerCase() === 'client').map(g => g.name);
      const text = clients.length ? `${clients.join(', ')} - ${t.title}` : t.title;
      if (t.is_completed && await this._askAddToDaily(text)) {
        await api.createDailyLog({ date: toDateStr(new Date()), text });
        showToast('Added to Daily Tasks', 'success');
      }
    } catch (err) { showToast(err.message, 'error'); }
  }
  _askAddToDaily(text) {
    return new Promise(resolve => {
      const close = (answer) => { document.removeEventListener('keydown', onKey); overlay.remove(); resolve(answer); };
      const onKey = (e) => { if (e.key === 'Escape') close(false); };
      const overlay = createElement('div', { className: 'modal-overlay', onClick: (e) => { if (e.target === overlay) close(false); } },
        createElement('div', { className: 'modal modal-sm' },
          createElement('div', { className: 'modal-header' }, createElement('h3', {}, 'Add to Daily Tasks?')),
          createElement('div', { className: 'modal-body' }, createElement('div', {}, `Log "${text}" as a daily task entry for today?`)),
          createElement('div', { className: 'modal-footer' },
            createElement('button', { className: 'btn btn-secondary', onClick: () => close(false) }, 'No'),
            createElement('button', { className: 'btn btn-primary', onClick: () => close(true) }, 'Add entry')
          )
        )
      );
      document.addEventListener('keydown', onKey);
      document.body.appendChild(overlay);
    });
  }
  async _toggleSubtask(s) { try { await api.toggleSubtask(s.id); const b = document.getElementById('task-list-body'); if (b) { await this.loadTasks(); this._renderTaskList(b); } } catch (err) { showToast(err.message, 'error'); } }
  _editTask(t) { if (!this.taskForm) this.taskForm = new TaskForm({ onSave: async () => { const b = document.getElementById('task-list-body'); if (b) { await this.loadTasks(); this._renderTaskList(b); } this.onRefreshSidebar?.(); }, onClose: () => { } }); this.taskForm.open(t); }
  async _deleteTask(t) { if (!confirm(`Delete "${t.title}"?`)) return; try { await api.deleteTask(t.id); showToast('Deleted', 'success'); const b = document.getElementById('task-list-body'); if (b) { await this.loadTasks(); this._renderTaskList(b); } this.onRefreshSidebar?.(); } catch (err) { showToast(err.message, 'error'); } }
  async refresh(c) { await this.loadTasks(); if (c) this.render(c); else { const b = document.getElementById('task-list-body'); if (b) this._renderTaskList(b); } }
  openNewTaskForm() { if (!this.taskForm) this.taskForm = new TaskForm({ onSave: async () => { const b = document.getElementById('task-list-body'); if (b) { await this.loadTasks(); this._renderTaskList(b); } this.onRefreshSidebar?.(); }, onClose: () => { } }); this.taskForm.open(null); }
}
