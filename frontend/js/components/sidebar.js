// ============================================================
// Tasks — Sidebar Component
// ============================================================

import { api } from '../api.js';
import { createElement, showToast } from '../utils.js';

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
        createElement('h1', {}, 'Tasks')
      ),

      // Navigation
      createElement('nav', { className: 'sidebar-nav', id: 'sidebar-nav' },
        // Main nav
        createElement('div', { className: 'nav-section' },
          createElement('div', { className: 'nav-section-title' }, 'Tasks'),
          this._navItem('all', '', 'All Tasks'),
          this._navItem('today', '', 'Today'),
          this._navItem('upcoming', '', 'Upcoming'),
          this._navItem('priority', '', 'Priority'),
          this._navItem('completed', '', 'Completed'),
        ),

        // Groups
        createElement('div', { className: 'nav-section', id: 'sidebar-groups-section' },
          createElement('div', {
            className: 'nav-section-title',
            style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between' }
          },
            createElement('span', {}, 'Groups'),
            createElement('button', {
              className: 'btn-ghost',
              style: { fontSize: '1rem', padding: '0 4px' },
              onClick: () => this._showAddGroup(),
              title: 'Add Group',
            }, '+')
          ),
          createElement('div', { id: 'sidebar-groups-list' },
            ...this.groups.map(g => this._groupItem(g))
          )
        ),

        // Management
        createElement('div', { className: 'nav-section' },
          createElement('div', { className: 'nav-section-title' }, 'Manage'),
          this._navItem('tags', '', 'Tags'),
          this._navItem('groups-manage', '', 'Groups'),
        )
      ),

      // Footer
      createElement('div', { className: 'sidebar-footer' },
        createElement('button', {
          onClick: () => {
            api.logout();
            window.dispatchEvent(new CustomEvent('auth:logout'));
          }
        }, 'Sign Out')
      )
    );

    this.element = sidebar;
    return sidebar;
  }

  _navItem(view, icon, label, badge = null) {
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
      createElement('span', { className: 'nav-icon' }, icon),
      createElement('span', {}, label),
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
      createElement('span', {
        className: 'group-dot',
        style: { background: group.color }
      }),
      createElement('span', {}, group.name),
      createElement('span', { className: 'nav-badge' }, String(group.active_task_count || 0))
    );
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
      placeholder: 'Group name...',
      style: { fontSize: '0.825rem', margin: '4px 12px 4px 28px', width: 'calc(100% - 40px)' },
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
