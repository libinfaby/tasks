// ============================================================
// Tasks — Group Manager Component
// ============================================================

import { api } from '../api.js';
import { icon } from '../icons.js';
import { createElement, showToast, getChipStyle } from '../utils.js';

export class GroupManager {
  constructor({ onRefreshSidebar }) {
    this.groups = [];
    this.onRefreshSidebar = onRefreshSidebar;
  }

  async loadData() {
    try {
      const data = await api.getGroups();
      this.groups = data.groups || [];
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  async render(container) {
    await this.loadData();
    container.innerHTML = '';
    const body = createElement('div', { className: 'content-body' });

    body.appendChild(createElement('div', { className: 'page-actions' },
      createElement('button', { className: 'btn btn-primary', onClick: () => this._showAddGroup(container) }, icon('plus'), 'New group')
    ));

    if (this.groups.length === 0) {
      body.appendChild(createElement('div', { className: 'empty-state' },
        createElement('div', { className: 'empty-icon' }, icon('layers', { size: 20 })),
        createElement('h3', {}, 'No groups yet'),
        createElement('p', {}, 'Create groups to organize related tasks together.')
      ));
    } else {
      const list = createElement('div', { className: 'task-list' });
      this.groups.forEach((group, i) => {
        const card = createElement('div', {
          className: 'task-card static',
          style: { animationDelay: `${i * 25}ms` },
        },
          createElement('span', { className: 'group-swatch', style: { background: group.color } }),
          createElement('div', { className: 'task-card-body' },
            createElement('div', { className: 'task-title-row' },
              createElement('div', { className: 'task-title' }, group.name),
              createElement('div', { className: 'row-actions' },
                createElement('button', { className: 'task-action-btn', title: 'Edit', 'aria-label': 'Edit', onClick: () => this._editGroup(group, container) }, icon('pencil', { size: 15 })),
                createElement('button', { className: 'task-action-btn delete', title: 'Delete', 'aria-label': 'Delete', onClick: () => this._deleteGroup(group, container) }, icon('trash', { size: 15 }))
              )
            ),
            createElement('div', { className: 'task-meta' },
              createElement('span', { className: 'meta-item' }, `${group.task_count || 0} tasks`),
              createElement('span', { className: 'meta-item' }, `${group.active_task_count || 0} active`)
            )
          )
        );
        list.appendChild(card);
      });
      body.appendChild(list);
    }

    container.appendChild(body);
  }

  _showAddGroup(root) {
    const preview = createElement('span', { className: 'tag-chip chip-preview' }, 'Group Preview');
    
    const updatePreview = () => {
      const name = nameInput.value || 'Group Preview';
      const style = getChipStyle({
        color: colorInput.value,
        fg_color: fgColorInput.value,
        has_bg: hasBgCheck.checked
      });
      preview.textContent = name;
      Object.assign(preview.style, style);
    };

    const nameInput = createElement('input', { type: 'text', className: 'form-input', placeholder: 'Group name...', onInput: updatePreview });
    const colorInput = createElement('input', { type: 'color', className: 'form-input', value: '#8b5cf6', onInput: updatePreview });
    const fgColorInput = createElement('input', { type: 'color', className: 'form-input', value: '#ffffff', onInput: updatePreview });
    const hasBgCheck = createElement('input', { type: 'checkbox', checked: true, onChange: updatePreview });

    this._modal('New Group', [
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Preview'), preview),
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Name'), nameInput),
      createElement('div', { className: 'form-row' },
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Colour'), colorInput),
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Text colour'), fgColorInput),
        createElement('div', { className: 'form-group' }, createElement('span', { className: 'form-label' }, 'Background'), createElement('label', { className: 'form-check' }, hasBgCheck, 'Filled')))
    ], async () => {
      const name = nameInput.value.trim();
      if (!name) { showToast('Name required', 'error'); return false; }
      try { 
        await api.createGroup({ 
          name, 
          color: colorInput.value,
          fg_color: fgColorInput.value,
          has_bg: hasBgCheck.checked
        }); 
        showToast('Group created', 'success'); 
        this.render(root); 
        this.onRefreshSidebar?.(); 
        return true; 
      }
      catch (err) { showToast(err.message, 'error'); return false; }
    });
    setTimeout(() => {
        nameInput.focus();
        updatePreview();
    }, 100);
  }

  _editGroup(group, root) {
    const preview = createElement('span', { className: 'tag-chip chip-preview' }, group.name);
    
    const updatePreview = () => {
      const name = nameInput.value || group.name;
      const style = getChipStyle({
        color: colorInput.value,
        fg_color: fgColorInput.value,
        has_bg: hasBgCheck.checked
      });
      preview.textContent = name;
      Object.assign(preview.style, style);
    };

    const nameInput = createElement('input', { type: 'text', className: 'form-input', value: group.name, onInput: updatePreview });
    const colorInput = createElement('input', { type: 'color', className: 'form-input', value: group.color, onInput: updatePreview });
    const fgColorInput = createElement('input', { type: 'color', className: 'form-input', value: group.fg_color || '#ffffff', onInput: updatePreview });
    const hasBgCheck = createElement('input', { type: 'checkbox', checked: group.has_bg !== undefined ? !!group.has_bg : true, onChange: updatePreview });

    this._modal('Edit Group', [
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Preview'), preview),
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Name'), nameInput),
      createElement('div', { className: 'form-row' },
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Colour'), colorInput),
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Text colour'), fgColorInput),
        createElement('div', { className: 'form-group' }, createElement('span', { className: 'form-label' }, 'Background'), createElement('label', { className: 'form-check' }, hasBgCheck, 'Filled')))
    ], async () => {
      const name = nameInput.value.trim();
      if (!name) { showToast('Name required', 'error'); return false; }
      try { 
        await api.updateGroup(group.id, { 
          name, 
          color: colorInput.value,
          fg_color: fgColorInput.value,
          has_bg: hasBgCheck.checked
        }); 
        showToast('Updated', 'success'); 
        this.render(root); 
        this.onRefreshSidebar?.(); 
        return true; 
      }
      catch (err) { showToast(err.message, 'error'); return false; }
    });
    setTimeout(() => {
        nameInput.focus();
        updatePreview();
    }, 100);
  }

  async _deleteGroup(group, root) {
    if (!confirm(`Delete "${group.name}"? Tasks will be ungrouped.`)) return;
    try { await api.deleteGroup(group.id); showToast('Group deleted', 'success'); this.render(root); this.onRefreshSidebar?.(); }
    catch (err) { showToast(err.message, 'error'); }
  }

  _modal(title, bodyContent, onSave) {
    const overlay = createElement('div', { className: 'modal-overlay', onClick: (e) => { if (e.target === overlay) overlay.remove(); } },
      createElement('div', { className: 'modal modal-sm' },
        createElement('div', { className: 'modal-header' },
          createElement('h3', {}, title),
          createElement('button', { className: 'modal-close', title: 'Close', 'aria-label': 'Close', onClick: () => overlay.remove() }, icon('x', { size: 18 }))
        ),
        createElement('div', { className: 'modal-body' }, ...bodyContent),
        createElement('div', { className: 'modal-footer' },
          createElement('button', { className: 'btn btn-secondary', onClick: () => overlay.remove() }, 'Cancel'),
          createElement('button', { className: 'btn btn-primary', onClick: async () => { const ok = await onSave(); if (ok !== false) overlay.remove(); } }, 'Save')
        )
      )
    );
    document.body.appendChild(overlay);
    return overlay;
  }
}
