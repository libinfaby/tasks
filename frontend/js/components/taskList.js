// ============================================================
// Tasks — Task List Component
// ============================================================
import { api } from '../api.js';
import {
  createElement, showToast, formatDate, isOverdue, isToday,
  getTagBg, getPriorityLabel, getPriorityClass,
} from '../utils.js';
import { TaskForm } from './taskForm.js';

export class TaskList {
  constructor({ onRefreshSidebar }) {
    this.tasks = [];
    this.filters = {};
    this.view = 'all';
    this.groupFilter = null;
    this.searchQuery = '';
    this.taskForm = null;
    this.onRefreshSidebar = onRefreshSidebar;
  }

  setView(view) {
    this.view = view;
    this.groupFilter = null;
    this.filters = this._getFiltersForView(view);
  }

  setGroupFilter(group) {
    this.view = 'group';
    this.groupFilter = group;
    this.filters = { group_id: group.id };
  }

  setSearch(query, type = 'task', tagTypeId = null) {
    this.searchQuery = query;
    if (query) {
      this.filters.search = query;
      this.filters.search_type = type;
      if (tagTypeId) {
        this.filters.search_tag_type = tagTypeId;
      } else {
        delete this.filters.search_tag_type;
      }
    } else {
      delete this.filters.search;
      delete this.filters.search_type;
      delete this.filters.search_tag_type;
    }
  }

  setTagFilter(tagId) {
    if (tagId) {
      this.filters.tag_id = tagId;
    } else {
      delete this.filters.tag_id;
    }
    // Sync header dropdown if it exists
    const dd = document.getElementById('tag-filter');
    if (dd) dd.value = tagId || '';
  }

  _getFiltersForView(view) {
    const today = new Date().toISOString().split('T')[0];
    const nextWeek = new Date(Date.now() + 7 * 86400000).toISOString().split('T')[0];

    switch (view) {
      case 'today':
        return { date_from: today, date_to: today };
      case 'upcoming':
        return { date_from: today, date_to: nextWeek, completed: 'false' };
      case 'priority':
        return { completed: 'false' };
      case 'completed':
        return { completed: 'true' };
      default:
        return {};
    }
  }

  async loadTasks() {
    try {
      const data = await api.getTasks(this.filters);
      this.tasks = data.tasks || [];

      // Additional client-side filtering for priority view
      if (this.view === 'priority') {
        this.tasks = this.tasks.filter(t => t.priority > 0);
      }
    } catch (err) {
      showToast(err.message, 'error');
      this.tasks = [];
    }
  }

  getViewTitle() {
    switch (this.view) {
      case 'all': return 'All Tasks';
      case 'today': return 'Today';
      case 'upcoming': return 'Upcoming';
      case 'priority': return 'Priority';
      case 'completed': return 'Completed';
      case 'group': return this.groupFilter?.name || 'Group';
      default: return 'Tasks';
    }
  }

  render(container) {
    container.innerHTML = '';

    // Filter bar
    const filterBar = createElement('div', { className: 'filter-bar' },
      this._filterChip('All', this.view === 'all' && !this.groupFilter, () => {
        this.setView('all');
        this.refresh(container);
      }),
      this._filterChip('Active', this.filters.completed === 'false', () => {
        this.filters.completed = this.filters.completed === 'false' ? '' : 'false';
        this.refresh(container);
      }),
      this._filterChip('Urgent', this.filters.priority === '2', () => {
        this.filters.priority = this.filters.priority === '2' ? '' : '2';
        this.refresh(container);
      }),
      this._filterChip('High', this.filters.priority === '1', () => {
        this.filters.priority = this.filters.priority === '1' ? '' : '1';
        this.refresh(container);
      }),
      ...(this.filters.tag_id ? [this._filterChip('Tag Filter Active', true, () => {
        this.setTagFilter('');
        this.refresh(container);
      })] : [])
    );
    container.appendChild(filterBar);

    // Task list content
    const body = createElement('div', { className: 'content-body', id: 'task-list-body' });
    container.appendChild(body);

    this._renderTaskList(body);
  }

  _renderTaskList(body) {
    body.innerHTML = '';

    if (this.tasks.length === 0) {
      body.appendChild(this._renderEmptyState());
      return;
    }

    // Group tasks by group if in 'all' view, otherwise flat list
    if (this.view === 'all' && !this.groupFilter) {
      const grouped = this._groupTasksByGroup();
      
      // Priority (ungrouped) first
      if (grouped.priority.length > 0) {
        body.appendChild(this._renderSection('Priority', grouped.priority, '#ef4444'));
      }

      // Ungrouped
      if (grouped.ungrouped.length > 0) {
        body.appendChild(this._renderSection('Tasks', grouped.ungrouped, '#6366f1'));
      }

      // By group
      Object.entries(grouped.groups).forEach(([groupId, { group, tasks }]) => {
        body.appendChild(this._renderSection(
          group.name,
          tasks,
          group.color
        ));
      });
    } else {
      const list = createElement('div', { className: 'task-list' });
      this.tasks.forEach((task, i) => {
        const card = this._renderTaskCard(task);
        card.style.animationDelay = `${i * 50}ms`;
        list.appendChild(card);
      });
      body.appendChild(list);
    }
  }

  _groupTasksByGroup() {
    const result = {
      priority: [],
      ungrouped: [],
      groups: {},
    };

    this.tasks.forEach(task => {
      if (task.priority > 0 && !task.is_completed) {
        result.priority.push(task);
      }

      if (task.group_id && task.group) {
        if (!result.groups[task.group_id]) {
          result.groups[task.group_id] = { group: task.group, tasks: [] };
        }
        result.groups[task.group_id].tasks.push(task);
      } else {
        // Don't add priority tasks again to ungrouped unless they're completed
        if (task.priority === 0 || task.is_completed) {
          result.ungrouped.push(task);
        }
      }
    });

    return result;
  }

  _renderSection(title, tasks, color) {
    const section = createElement('div', { className: 'task-group-section' },
      createElement('div', { className: 'task-group-header' },
        createElement('div', {
          className: 'group-indicator',
          style: { background: color },
        }),
        createElement('h3', {}, title),
        createElement('span', { className: 'group-count' }, `(${tasks.length})`)
      ),
      createElement('div', { className: 'task-list' },
        ...tasks.map((task, i) => {
          const card = this._renderTaskCard(task);
          card.style.animationDelay = `${i * 50}ms`;
          return card;
        })
      )
    );
    return section;
  }

  _renderTaskCard(task) {
    const completedSubtasks = (task.subtasks || []).filter(s => s.is_completed).length;
    const totalSubtasks = (task.subtasks || []).length;

    const card = createElement('div', {
      className: `task-card priority-${task.priority}${task.is_completed ? ' completed' : ''}`,
      onClick: (e) => {
        // Don't open editor when clicking checkbox
        if (e.target.closest('.task-checkbox') || e.target.closest('.task-action-btn') || e.target.closest('.subtask-item .task-checkbox')) return;
        this._editTask(task);
      },
    },
      // Header: checkbox + title + actions
      createElement('div', { className: 'task-card-header' },
        // Checkbox
        createElement('label', { className: 'task-checkbox', onClick: (e) => e.stopPropagation() },
          createElement('input', {
            type: 'checkbox',
            ...(task.is_completed ? { checked: 'true' } : {}),
            onChange: () => this._toggleTask(task),
          }),
          createElement('span', { className: 'checkmark' })
        ),

        // Body
        createElement('div', { className: 'task-card-body' },
          createElement('div', { className: 'task-title' }, task.title),
          task.details ? createElement('div', { className: 'task-details' }, task.details) : null,

          // Meta (date, deadline, priority)
          this._renderMeta(task),

          // Tags
          this._renderTags(task.tags),

          // Subtasks
          totalSubtasks > 0 ? this._renderSubtasks(task) : null,
        ),

        // Actions
        createElement('div', { className: 'task-card-actions' },
          createElement('button', {
            className: 'task-action-btn',
            title: 'Edit',
            onClick: (e) => { e.stopPropagation(); this._editTask(task); },
          }, 'Edit'),
          createElement('button', {
            className: 'task-action-btn delete',
            title: 'Delete',
            onClick: (e) => { e.stopPropagation(); this._deleteTask(task); },
          }, 'Delete'),
        )
      )
    );

    return card;
  }

  _renderMeta(task) {
    const items = [];

    if (task.priority > 0) {
      const cls = getPriorityClass(task.priority);
      items.push(createElement('span', {
        className: `priority-badge ${cls}`,
      }, getPriorityLabel(task.priority)));
    }

    if (task.date) {
      const dateClass = isOverdue(task.date) ? 'overdue' : (isToday(task.date) ? 'today' : '');
      items.push(createElement('span', {
        className: `task-date ${dateClass}`,
      }, 
        // createElement('span', { 
        //   style: { 
        //     // background: 'var(--primary, #0f62fe)', 
        //     color: 'white', 
        //     padding: '2px 6px', 
        //     borderRadius: '4px', 
        //     marginRight: '6px',
        //     fontSize: '0.75rem',
        //     fontWeight: '600'
        //   } 
        // }, 'Date:'),
        `${formatDate(task.date)}`
      ));
    }

    if (task.deadline) {
      items.push(createElement('span', {
        className: 'task-deadline',
        style: { display: 'flex', alignItems: 'center' }
      }, 
        // createElement('span', { 
        //   style: { 
        //     // background: 'var(--text-secondary, #525252)', 
        //     color: 'white', 
        //     padding: '2px 6px', 
        //     borderRadius: '4px', 
        //     marginRight: '6px',
        //     fontSize: '0.75rem',
        //     fontWeight: '600'
        //   } 
        // }, 'Deadline:'),
        `${formatDate(task.deadline)}`
      ));
    }

    if (task.group && this.view !== 'group') {
      items.push(createElement('span', {
        className: 'tag-chip',
        style: {
          background: getTagBg(task.group.color),
          color: task.group.color,
          borderColor: task.group.color + '33',
        }},
        // createElement('span', { 
        //   style: { 
        //     // background: 'var(--text-secondary, #525252)', 
        //     color: 'white', 
        //     padding: '2px 6px', 
        //     borderRadius: '4px', 
        //     marginRight: '6px',
        //     fontSize: '0.75rem',
        //     fontWeight: '600'
        //   } 
        // }, ''),        
       `${task.group.name}`));
    }

    if (items.length === 0) return null;

    return createElement('div', { className: 'task-meta' }, ...items);
  }

  _renderTags(tags) {
    if (!tags || tags.length === 0) return null;

    return createElement('div', { className: 'tag-list' },
      ...tags.map(tag => {
        const color = tag.color || tag.type_color || '#6366f1';
        return createElement('span', {
          className: 'tag-chip selected-tag-clickable',
          style: {
            background: getTagBg(color),
            color: color,
            borderColor: color + '22',
            cursor: 'pointer'
          },
          onClick: (e) => {
            e.stopPropagation();
            this.setTagFilter(tag.id);
            // Refresh body directly if we don't have container reference
            const body = document.getElementById('task-list-body');
            if (body) {
              this.refresh(body.parentElement);
            }
          }
        },
          createElement('span', { className: 'tag-type-label' },
            `${tag.type_name || 'Tag'}:`
          ),
          ` ${tag.name}`
        );
      })
    );
  }

  _renderSubtasks(task) {
    const subtasks = task.subtasks || [];
    const completed = subtasks.filter(s => s.is_completed).length;
    const total = subtasks.length;
    const pct = total > 0 ? (completed / total) * 100 : 0;

    const container = createElement('div', { className: 'subtask-preview' },
      // Progress bar
      createElement('div', { className: 'subtask-progress' },
        createElement('div', { className: 'subtask-progress-bar' },
          createElement('div', {
            className: 'subtask-progress-fill',
            style: { width: `${pct}%` },
          })
        ),
        createElement('span', { className: 'subtask-progress-text' }, `${completed}/${total}`)
      )
    );

    // Show subtask items
    subtasks.forEach(sub => {
      const item = createElement('div', {
        className: `subtask-item${sub.is_completed ? ' completed' : ''}`,
      },
        createElement('label', {
          className: 'task-checkbox',
          style: { transform: 'scale(0.85)' },
          onClick: (e) => e.stopPropagation(),
        },
          createElement('input', {
            type: 'checkbox',
            ...(sub.is_completed ? { checked: 'true' } : {}),
            onChange: () => this._toggleSubtask(sub),
          }),
          createElement('span', { className: 'checkmark' })
        ),
        createElement('span', { className: 'subtask-title' }, sub.title),
      );
      container.appendChild(item);

      // Subtask tags
      if (sub.tags && sub.tags.length > 0) {
        const tagsRow = createElement('div', { className: 'subtask-tags' },
          ...sub.tags.map(tag => {
            const color = tag.color || tag.type_color || '#6366f1';
            return createElement('span', {
              className: 'tag-chip',
              style: {
                background: getTagBg(color),
                color: color,
                borderColor: color + '22',
                fontSize: '0.65rem',
                padding: '1px 8px',
              },
            },
              `${tag.type_name}: ${tag.name}`
            );
          })
        );
        container.appendChild(tagsRow);
      }
    });

    return container;
  }

  _filterChip(label, active, onClick) {
    return createElement('button', {
      className: `filter-chip${active ? ' active' : ''}`,
      onClick,
    }, label);
  }

  _renderEmptyState() {
    const messages = {
      all: { icon: '', title: 'No tasks yet', desc: 'Click the + button to create your first task.' },
      today: { icon: '', title: 'Nothing for today', desc: 'Enjoy your free time or plan new tasks.' },
      upcoming: { icon: '', title: 'Nothing upcoming', desc: 'No tasks scheduled for the next 7 days.' },
      priority: { icon: '', title: 'No priority tasks', desc: 'Mark tasks as high or urgent to see them here.' },
      completed: { icon: '', title: 'No completed tasks', desc: 'Complete some tasks to see them here.' },
      group: { icon: '', title: 'Empty group', desc: 'Add tasks to this group to see them here.' },
    };

    const msg = messages[this.view] || messages.all;

    return createElement('div', { className: 'empty-state' },
      createElement('div', { className: 'empty-icon' }, msg.icon),
      createElement('h3', {}, msg.title),
      createElement('p', {}, msg.desc),
    );
  }

  async _toggleTask(task) {
    try {
      await api.toggleTask(task.id);
      task.is_completed = !task.is_completed;
      const body = document.getElementById('task-list-body');
      if (body) {
        await this.loadTasks();
        this._renderTaskList(body);
      }
      this.onRefreshSidebar?.();
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  async _toggleSubtask(subtask) {
    try {
      await api.toggleSubtask(subtask.id);
      const body = document.getElementById('task-list-body');
      if (body) {
        await this.loadTasks();
        this._renderTaskList(body);
      }
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  _editTask(task) {
    if (!this.taskForm) {
      this.taskForm = new TaskForm({
        onSave: async () => {
          const body = document.getElementById('task-list-body');
          if (body) {
            await this.loadTasks();
            this._renderTaskList(body);
          }
          this.onRefreshSidebar?.();
        },
        onClose: () => {},
      });
    }
    this.taskForm.open(task);
  }

  async _deleteTask(task) {
    if (!confirm(`Delete "${task.title}"?`)) return;
    try {
      await api.deleteTask(task.id);
      showToast('Task deleted', 'success');
      const body = document.getElementById('task-list-body');
      if (body) {
        await this.loadTasks();
        this._renderTaskList(body);
      }
      this.onRefreshSidebar?.();
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  async refresh(container) {
    await this.loadTasks();
    if (container) {
      this.render(container);
    } else {
      const body = document.getElementById('task-list-body');
      if (body) this._renderTaskList(body);
    }
  }

  openNewTaskForm() {
    if (!this.taskForm) {
      this.taskForm = new TaskForm({
        onSave: async () => {
          const body = document.getElementById('task-list-body');
          if (body) {
            await this.loadTasks();
            this._renderTaskList(body);
          }
          this.onRefreshSidebar?.();
        },
        onClose: () => {},
      });
    }

    // Pre-set group if we're in a group view
    const defaults = {};
    if (this.groupFilter) {
      defaults.group_id = this.groupFilter.id;
    }

    this.taskForm.open(null);
  }
}
