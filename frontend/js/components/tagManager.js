// ============================================================
// Tasks — Tag Manager Component
// ============================================================

import { api } from '../api.js';
import { createElement, showToast, getTagBg } from '../utils.js';

export class TagManager {
  constructor() {
    this.tagTypes = [];
  }

  async loadData() {
    try {
      const data = await api.getTagTypes();
      this.tagTypes = data.tag_types || [];
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  async render(container) {
    await this.loadData();
    container.innerHTML = '';
    const body = createElement('div', { className: 'content-body' });
    body.appendChild(createElement('div', { style: { marginBottom: 'var(--space-lg)', display: 'flex', justifyContent: 'flex-end' } },
      createElement('button', { className: 'btn btn-primary btn-sm', onClick: () => this._showAddTagType(container) }, '+ New Tag')
    ));
    const manager = createElement('div', { className: 'tag-manager' });
    if (this.tagTypes.length === 0) {
      manager.appendChild(createElement('div', { className: 'empty-state' },
        createElement('div', { className: 'empty-icon' }, ''),
        createElement('h3', {}, 'No tag types yet'),
        createElement('p', {}, 'Create tag types like "Project", "Client" to organize tasks.')
      ));
    } else {
      this.tagTypes.forEach((type, i) => {
        const card = this._renderCard(type, container);
        card.style.animationDelay = `${i * 80}ms`;
        manager.appendChild(card);
      });
    }
    body.appendChild(manager);
    container.appendChild(body);
  }

  _renderCard(type, root) {
    return createElement('div', { className: 'tag-type-card' },
      createElement('div', { className: 'tag-type-header' },
        createElement('div', { className: 'tag-type-header-left' },
          createElement('span', { className: 'type-icon' }, type.icon || ''),
          createElement('span', { className: 'type-name' }, type.name),
          createElement('span', { className: 'type-color', style: { background: type.color } })
        ),
        createElement('div', { style: { display: 'flex', gap: '4px' } },
          createElement('button', { className: 'task-action-btn', title: 'Edit', onClick: () => this._editType(type, root) }, 'Edit'),
          createElement('button', { className: 'task-action-btn delete', title: 'Delete', onClick: () => this._deleteType(type, root) }, 'Delete')
        )
      ),
      createElement('div', { className: 'tag-type-body' },
        ...(type.tags || []).map(tag => this._chip(tag, type, root)),
        createElement('button', { className: 'add-tag-inline', onClick: () => this._showAddTag(type, root) }, '+ Add')
      )
    );
  }

  _chip(tag, type, root) {
    const color = tag.color || type.color;
    return createElement('span', { className: 'tag-chip', style: { background: getTagBg(color), color, borderColor: color + '22' } },
      tag.name,
      createElement('button', { className: 'tag-remove', onClick: async (e) => {
        e.stopPropagation();
        if (confirm(`Delete tag "${tag.name}"?`)) {
          try { await api.deleteTag(tag.id); showToast('Tag deleted', 'success'); this.render(root); }
          catch (err) { showToast(err.message, 'error'); }
        }
      }}, ' X')
    );
  }

  _modal(title, bodyContent, onSave, onClose) {
    const overlay = createElement('div', { className: 'modal-overlay', onClick: (e) => { if (e.target === overlay) overlay.remove(); } },
      createElement('div', { className: 'modal', style: { maxWidth: '420px' } },
        createElement('div', { className: 'modal-header' },
          createElement('h3', {}, title),
          createElement('button', { className: 'modal-close', onClick: () => overlay.remove() }, 'Close')
        ),
        createElement('div', { className: 'modal-body' }, ...bodyContent),
        createElement('div', { className: 'modal-footer' },
          createElement('button', { className: 'btn btn-secondary', onClick: () => overlay.remove() }, 'Cancel'),
          createElement('button', { className: 'btn btn-primary', onClick: async () => { await onSave(); overlay.remove(); } }, 'Save')
        )
      )
    );
    document.body.appendChild(overlay);
    return overlay;
  }

  _showAddTagType(root) {
    const body = [
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Name'),
        createElement('input', { type: 'text', id: 'new-type-name', className: 'form-input', placeholder: 'e.g., Project, Client...' })),
      createElement('div', { className: 'form-row' },
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Color'),
          createElement('input', { type: 'color', id: 'new-type-color', className: 'form-input', value: '#6366f1', style: { height: '40px', padding: '4px', cursor: 'pointer' } })),
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Icon'),
          createElement('input', { type: 'text', id: 'new-type-icon', className: 'form-input', placeholder: 'Tag', maxlength: '4' })))
    ];
    this._modal('New Tag', body, async () => {
      const name = document.getElementById('new-type-name')?.value?.trim();
      if (!name) { showToast('Name required', 'error'); throw 'stop'; }
      try { await api.createTagType({ name, color: document.getElementById('new-type-color')?.value, icon: document.getElementById('new-type-icon')?.value?.trim() }); showToast('Created', 'success'); this.render(root); }
      catch (err) { showToast(err.message, 'error'); }
    });
    setTimeout(() => document.getElementById('new-type-name')?.focus(), 100);
  }

  _showAddTag(type, root) {
    const body = [
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Tag Name'),
        createElement('input', { type: 'text', id: 'new-tag-name', className: 'form-input', placeholder: 'Tag name...' })),
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Color'),
        createElement('input', { type: 'color', id: 'new-tag-color', className: 'form-input', value: type.color, style: { height: '40px', padding: '4px', cursor: 'pointer' } }))
    ];
    this._modal(`Add ${type.name} Tag`, body, async () => {
      const name = document.getElementById('new-tag-name')?.value?.trim();
      if (!name) { showToast('Name required', 'error'); throw 'stop'; }
      try { await api.createTag({ name, tag_type_id: type.id, color: document.getElementById('new-tag-color')?.value }); showToast('Created', 'success'); this.render(root); }
      catch (err) { showToast(err.message, 'error'); }
    });
    setTimeout(() => document.getElementById('new-tag-name')?.focus(), 100);
  }

  _editType(type, root) {
    const body = [
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Name'),
        createElement('input', { type: 'text', id: 'edit-type-name', className: 'form-input', value: type.name })),
      createElement('div', { className: 'form-row' },
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Color'),
          createElement('input', { type: 'color', id: 'edit-type-color', className: 'form-input', value: type.color, style: { height: '40px', padding: '4px', cursor: 'pointer' } })),
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Icon'),
          createElement('input', { type: 'text', id: 'edit-type-icon', className: 'form-input', value: type.icon || '', maxlength: '4' })))
    ];
    this._modal('Edit Tag Type', body, async () => {
      const name = document.getElementById('edit-type-name')?.value?.trim();
      if (!name) { showToast('Name required', 'error'); throw 'stop'; }
      try { await api.updateTagType(type.id, { name, color: document.getElementById('edit-type-color')?.value, icon: document.getElementById('edit-type-icon')?.value?.trim() }); showToast('Updated', 'success'); this.render(root); }
      catch (err) { showToast(err.message, 'error'); }
    });
  }

  async _deleteType(type, root) {
    if (!confirm(`Delete "${type.name}" and all its tags?`)) return;
    try { await api.deleteTagType(type.id); showToast('Deleted', 'success'); this.render(root); }
    catch (err) { showToast(err.message, 'error'); }
  }
}
