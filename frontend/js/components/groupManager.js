// ============================================================
// Tasks — Group Manager Component
// ============================================================

import { api } from '../api.js';
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

    body.appendChild(createElement('div', { style: { marginBottom: 'var(--space-lg)', display: 'flex', justifyContent: 'flex-end' } },
      createElement('button', { className: 'btn btn-primary btn-sm', onClick: () => this._showAddGroup(container) }, '+ New Group')
    ));

    if (this.groups.length === 0) {
      body.appendChild(createElement('div', { className: 'empty-state' },
        createElement('div', { className: 'empty-icon' }, ''),
        createElement('h3', {}, 'No groups yet'),
        createElement('p', {}, 'Create groups to organize related tasks together.')
      ));
    } else {
      const list = createElement('div', { className: 'task-list' });
      this.groups.forEach((group, i) => {
        const card = createElement('div', {
          className: 'task-card',
          style: { animationDelay: `${i * 60}ms` },
        },
          createElement('div', { className: 'task-card-header' },
            createElement('div', {
              style: { width: '12px', height: '12px', borderRadius: '50%', background: group.color, border: 'none', marginRight: '8px', flexShrink: '0' }
            }),
            createElement('div', { className: 'task-card-body' },
              createElement('div', { className: 'task-title' }, group.name),
              createElement('div', { className: 'task-meta', style: { marginTop: '4px' } },
                createElement('span', { className: 'task-date' }, `${group.task_count || 0} tasks`),
                createElement('span', { className: 'task-date' }, `${group.active_task_count || 0} active`)
              )
            ),
            createElement('div', { className: 'task-card-actions', style: { opacity: '1' } },
              createElement('button', { className: 'task-action-btn', title: 'Edit', onClick: () => this._editGroup(group, container) }, 'Edit'),
              createElement('button', { className: 'task-action-btn delete', title: 'Delete', onClick: () => this._deleteGroup(group, container) }, 'Delete')
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
    const preview = createElement('span', { className: 'tag-chip' }, 'Group Preview');
    
    const updatePreview = () => {
      const name = nameInput.value || 'Group Preview';
      const style = getChipStyle({
        color: colorInput.value,
        fg_color: fgColorInput.value,
        has_bg: hasBgCheck.checked
      });
      preview.textContent = name;
      Object.assign(preview.style, style);
      preview.style.marginBottom = '12px';
      preview.style.display = 'inline-block';
    };

    const nameInput = createElement('input', { type: 'text', className: 'form-input', placeholder: 'Group name...', onInput: updatePreview });
    const colorInput = createElement('input', { type: 'color', className: 'form-input', value: '#8b5cf6', style: { height: '40px', padding: '4px', cursor: 'pointer' }, onInput: updatePreview });
    const fgColorInput = createElement('input', { type: 'color', className: 'form-input', value: '#ffffff', style: { height: '40px', padding: '4px', cursor: 'pointer' }, onInput: updatePreview });
    const hasBgCheck = createElement('input', { type: 'checkbox', checked: true, onChange: updatePreview });

    this._modal('New Group', [
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Preview'), preview),
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Name'), nameInput),
      createElement('div', { className: 'form-row' },
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Background'), colorInput),
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Foreground'), fgColorInput),
        createElement('div', { className: 'form-group' }, createElement('label', { style: { display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' } }, hasBgCheck, ' Show background')))
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
    const preview = createElement('span', { className: 'tag-chip' }, group.name);
    
    const updatePreview = () => {
      const name = nameInput.value || group.name;
      const style = getChipStyle({
        color: colorInput.value,
        fg_color: fgColorInput.value,
        has_bg: hasBgCheck.checked
      });
      preview.textContent = name;
      Object.assign(preview.style, style);
      preview.style.marginBottom = '12px';
      preview.style.display = 'inline-block';
    };

    const nameInput = createElement('input', { type: 'text', className: 'form-input', value: group.name, onInput: updatePreview });
    const colorInput = createElement('input', { type: 'color', className: 'form-input', value: group.color, style: { height: '40px', padding: '4px', cursor: 'pointer' }, onInput: updatePreview });
    const fgColorInput = createElement('input', { type: 'color', className: 'form-input', value: group.fg_color || '#ffffff', style: { height: '40px', padding: '4px', cursor: 'pointer' }, onInput: updatePreview });
    const hasBgCheck = createElement('input', { type: 'checkbox', checked: group.has_bg !== undefined ? !!group.has_bg : true, onChange: updatePreview });

    this._modal('Edit Group', [
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Preview'), preview),
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Name'), nameInput),
      createElement('div', { className: 'form-row' },
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Background'), colorInput),
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Foreground'), fgColorInput),
        createElement('div', { className: 'form-group' }, createElement('label', { style: { display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' } }, hasBgCheck, ' Show background')))
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
      createElement('div', { className: 'modal', style: { maxWidth: '400px' } },
        createElement('div', { className: 'modal-header' },
          createElement('h3', {}, title),
          createElement('button', { className: 'modal-close', onClick: () => overlay.remove() }, 'Close')
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
