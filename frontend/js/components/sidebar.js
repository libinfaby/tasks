// ============================================================
// Tasks — Sidebar Component
// ============================================================

import { api } from '../api.js';
import { createElement, showToast, getEffectiveTheme, setTheme } from '../utils.js';
import { icon } from '../icons.js';

export class Sidebar {
  constructor({ onNavigate, onGroupSelect }) {
    this.onNavigate = onNavigate;
    this.onGroupSelect = onGroupSelect;
    this.groups = [];
    this.activeView = 'all';
    this.activeGroupId = null;
    this.element = null;
  }

  async loadGroups() {
    try {
      const data = await api.getGroups();
      this.groups = data.groups || [];
    } catch (err) {
      console.error('Failed to load groups:', err);
    }
  }

  render() {
    const sidebar = createElement('aside', { className: 'sidebar', id: 'sidebar' },
      // Header
      createElement('div', { className: 'sidebar-header' },
        createElement('div', { className: 'brand' },
          createElement('span', { className: 'brand-mark' }, icon('listChecks', { size: 16 })),
          'Tasks'
        )
      ),

      // Navigation
      createElement('nav', { className: 'sidebar-nav', id: 'sidebar-nav' },
        // Main nav
        createElement('div', { className: 'nav-section' },
          createElement('div', { className: 'nav-section-title' }, 'Tasks'),
          this._navItem('all', 'inbox', 'All Tasks'),
          this._navItem('today', 'calendarCheck', 'Today'),
          this._navItem('upcoming', 'clock', 'Upcoming'),
          this._navItem('priority', 'flag', 'Urgent'),
          this._navItem('completed', 'circleCheck', 'Completed'),
        ),

        // Daily log
        createElement('div', { className: 'nav-section' },
          createElement('div', { className: 'nav-section-title' }, 'Daily Tasks'),
          this._navItem('daily-entry', 'clipboard', 'Entry'),
          this._navItem('daily-report', 'fileText', 'Report'),
        ),

        // Groups
        createElement('div', { className: 'nav-section', id: 'sidebar-groups-section' },
          createElement('div', { className: 'nav-section-title' },
            createElement('span', {}, 'Groups'),
            createElement('button', {
              className: 'icon-btn',
              onClick: () => this._showAddGroup(),
              title: 'Add group',
              'aria-label': 'Add group',
            }, icon('plus', { size: 14 }))
          ),
          createElement('div', { id: 'sidebar-groups-list' },
            ...this.groups.map(g => this._groupItem(g))
          )
        ),

        // Management
        createElement('div', { className: 'nav-section' },
          createElement('div', { className: 'nav-section-title' }, 'Manage'),
          this._navItem('tags', 'tag', 'Tags'),
          this._navItem('groups-manage', 'layers', 'Groups'),
        )
      ),

      // Footer
      createElement('div', { className: 'sidebar-footer' },
        createElement('button', {
          className: 'nav-item',
          onClick: () => {
            api.logout();
            window.dispatchEvent(new CustomEvent('auth:logout'));
          }
        },
          createElement('span', { className: 'nav-icon' }, icon('logOut')),
          createElement('span', { className: 'nav-label' }, 'Sign out')
        ),
        this._themeToggle()
      )
    );

    this.element = sidebar;
    return sidebar;
  }

  _navItem(view, iconName, label, badge = null) {
    const isActive = this.activeView === view && !this.activeGroupId;
    const item = createElement('button', {
      className: `nav-item${isActive ? ' active' : ''}`,
      dataset: { view },
      onClick: () => {
        this.activeView = view;
        this.activeGroupId = null;
        this.onNavigate(view);
        this._updateActiveState();
      }
    },
      createElement('span', { className: 'nav-icon' }, icon(iconName)),
      createElement('span', { className: 'nav-label' }, label),
    );

    if (badge !== null) {
      item.appendChild(createElement('span', { className: 'nav-badge' }, String(badge)));
    }

    return item;
  }

  _groupItem(group) {
    const isActive = this.activeGroupId === group.id;
    return createElement('button', {
      className: `nav-group-item${isActive ? ' active' : ''}`,
      dataset: { groupId: group.id },
      onClick: () => {
        this.activeView = 'group';
        this.activeGroupId = group.id;
        this.onGroupSelect(group);
        this._updateActiveState();
      }
    },
      createElement('span', { className: 'group-dot', style: { background: group.color } }),
      createElement('span', { className: 'nav-label' }, group.name),
      createElement('span', { className: 'nav-badge' }, String(group.active_task_count || 0))
    );
  }

  _themeToggle() {
    const btn = createElement('button', { className: 'icon-btn' });
    const paint = () => {
      const dark = getEffectiveTheme() === 'dark';
      btn.replaceChildren(icon(dark ? 'sun' : 'moon'));
      btn.title = dark ? 'Switch to light theme' : 'Switch to dark theme';
      btn.setAttribute('aria-label', btn.title);
    };
    btn.addEventListener('click', () => {
      setTheme(getEffectiveTheme() === 'dark' ? 'light' : 'dark');
      paint();
    });
    paint();
    return btn;
  }

  _updateActiveState() {
    if (!this.element) return;
    
    // Clear all active states
    this.element.querySelectorAll('.nav-item, .nav-group-item').forEach(el => {
      el.classList.remove('active');
    });

    // Set active
    if (this.activeGroupId) {
      const groupEl = this.element.querySelector(`[data-group-id="${this.activeGroupId}"]`);
      if (groupEl) groupEl.classList.add('active');
    } else {
      const viewEl = this.element.querySelector(`[data-view="${this.activeView}"]`);
      if (viewEl) viewEl.classList.add('active');
    }
  }

  async refreshGroups() {
    await this.loadGroups();
    const list = document.getElementById('sidebar-groups-list');
    if (list) {
      list.innerHTML = '';
      this.groups.forEach(g => {
        list.appendChild(this._groupItem(g));
      });
    }
  }

  _showAddGroup() {
    const list = document.getElementById('sidebar-groups-list');
    if (!list || list.querySelector('.inline-add')) return;

    const input = createElement('input', {
      type: 'text',
      className: 'form-input',
      placeholder: 'Group name…',
    });

    const wrapper = createElement('div', { className: 'inline-add' }, input);
    list.appendChild(wrapper);
    input.focus();

    const save = async () => {
      const name = input.value.trim();
      if (name) {
        try {
          await api.createGroup({ name });
          showToast('Group created', 'success');
          await this.refreshGroups();
        } catch (err) {
          showToast(err.message, 'error');
        }
      } else {
        wrapper.remove();
      }
    };

    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') save();
      if (e.key === 'Escape') wrapper.remove();
    });
    input.addEventListener('blur', save);
  }

  closeMobile() {
    this.element?.classList.remove('open');
    document.getElementById('sidebar-backdrop')?.classList.remove('visible');
  }

  openMobile() {
    this.element?.classList.add('open');
    document.getElementById('sidebar-backdrop')?.classList.add('visible');
  }
}
