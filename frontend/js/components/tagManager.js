// ============================================================
// Tasks — Tag Manager Component
// ============================================================

import { api } from '../api.js';
import { createElement, showToast, getTagBg, getChipStyle } from '../utils.js';

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
          createElement('span', { className: 'type-color', style: { background: type.color, border: 'none' } })
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
    const style = getChipStyle({
      color: tag.color,
      fg_color: tag.fg_color,
      has_bg: tag.has_bg,
      type_color: type.color,
      type_fg_color: type.fg_color,
      type_has_bg: type.has_bg
    });
    return createElement('span', {
      className: 'tag-chip tag-chip-editable', style,
      onClick: () => this._editTag(tag, type, root)
    },
      tag.name,
      createElement('button', { className: 'tag-remove', style: { color: style.color }, onClick: async (e) => {
        e.stopPropagation();
        if (confirm(`Delete tag "${tag.name}"?`)) {
          try { await api.deleteTag(tag.id); showToast('Tag deleted', 'success'); this.render(root); }
          catch (err) { showToast(err.message, 'error'); }
        }
      }}, ' X')
    );
  }

  _editTag(tag, type, root) {
    const preview = createElement('span', { className: 'tag-chip' }, tag.name);

    const updatePreview = () => {
      const name = nameInput.value || tag.name;
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

    const nameInput = createElement('input', { type: 'text', className: 'form-input', value: tag.name, onInput: updatePreview });
    const colorInput = createElement('input', { type: 'color', className: 'form-input', value: tag.color || type.color, style: { height: '40px', padding: '4px', cursor: 'pointer' }, onInput: updatePreview });
    const fgColorInput = createElement('input', { type: 'color', className: 'form-input', value: tag.fg_color || type.fg_color || '#ffffff', style: { height: '40px', padding: '4px', cursor: 'pointer' }, onInput: updatePreview });
    const hasBgCheck = createElement('input', { type: 'checkbox', checked: tag.has_bg !== undefined ? !!tag.has_bg : (type.has_bg !== undefined ? !!type.has_bg : true), onChange: updatePreview });

    const body = [
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Preview'), preview),
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Tag Name'), nameInput),
      createElement('div', { className: 'form-row' },
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Background'), colorInput),
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Foreground'), fgColorInput),
        createElement('div', { className: 'form-group' }, createElement('label', { style: { display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' } }, hasBgCheck, ' Show background')))
    ];
    this._modal('Edit Tag', body, async () => {
      const name = nameInput.value.trim();
      if (!name) { showToast('Name required', 'error'); throw 'stop'; }
      try {
        await api.updateTag(tag.id, {
          name,
          color: colorInput.value,
          fg_color: fgColorInput.value,
          has_bg: hasBgCheck.checked
        });
        showToast('Tag updated', 'success');
        this.render(root);
      }
      catch (err) { showToast(err.message, 'error'); }
    });
    setTimeout(() => {
      nameInput.focus();
      updatePreview();
    }, 100);
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
    const preview = createElement('span', { className: 'tag-chip', style: { background: '#6366f1', color: '#ffffff', marginBottom: '12px', display: 'inline-block' } }, 'Preview Tag');
    
    const updatePreview = () => {
      const name = nameInput.value || 'Preview Tag';
      const style = getChipStyle({
        color: colorInput.value,
        fg_color: fgColorInput.value,
        has_bg: hasBgCheck.checked
      });
      preview.textContent = name;
      Object.assign(preview.style, style);
    };

    const nameInput = createElement('input', { type: 'text', className: 'form-input', placeholder: 'e.g., Project, Client...', onInput: updatePreview });
    const colorInput = createElement('input', { type: 'color', className: 'form-input', value: '#6366f1', style: { height: '40px', padding: '4px', cursor: 'pointer' }, onInput: updatePreview });
    const fgColorInput = createElement('input', { type: 'color', className: 'form-input', value: '#ffffff', style: { height: '40px', padding: '4px', cursor: 'pointer' }, onInput: updatePreview });
    const hasBgCheck = createElement('input', { type: 'checkbox', checked: true, onChange: updatePreview });
    const iconInput = createElement('input', { type: 'text', className: 'form-input', placeholder: 'Tag', maxlength: '4' });

    const body = [
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Preview'), preview),
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Name'), nameInput),
      createElement('div', { className: 'form-row' },
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Background'), colorInput),
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Foreground'), fgColorInput),
        createElement('div', { className: 'form-group' }, createElement('label', { style: { display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' } }, hasBgCheck, ' Show background'))),
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Icon'), iconInput)
    ];
    this._modal('New Tag Type', body, async () => {
      const name = nameInput.value.trim();
      if (!name) { showToast('Name required', 'error'); throw 'stop'; }
      try { 
        await api.createTagType({ 
          name, 
          color: colorInput.value, 
          fg_color: fgColorInput.value,
          has_bg: hasBgCheck.checked,
          icon: iconInput.value.trim() 
        }); 
        showToast('Created', 'success'); 
        this.render(root); 
      }
      catch (err) { showToast(err.message, 'error'); }
    });
    setTimeout(() => {
      nameInput.focus();
      updatePreview();
    }, 100);
  }

  _showAddTag(type, root) {
    const preview = createElement('span', { className: 'tag-chip' }, 'Preview Tag');
    
    const updatePreview = () => {
      const name = nameInput.value || 'Preview Tag';
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

    const nameInput = createElement('input', { type: 'text', className: 'form-input', placeholder: 'Tag name...', onInput: updatePreview });
    const colorInput = createElement('input', { type: 'color', className: 'form-input', value: type.color, style: { height: '40px', padding: '4px', cursor: 'pointer' }, onInput: updatePreview });
    const fgColorInput = createElement('input', { type: 'color', className: 'form-input', value: (type.fg_color || '#ffffff'), style: { height: '40px', padding: '4px', cursor: 'pointer' }, onInput: updatePreview });
    const hasBgCheck = createElement('input', { type: 'checkbox', checked: type.has_bg !== undefined ? !!type.has_bg : true, onChange: updatePreview });

    const body = [
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Preview'), preview),
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Tag Name'), nameInput),
      createElement('div', { className: 'form-row' },
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Background'), colorInput),
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Foreground'), fgColorInput),
        createElement('div', { className: 'form-group' }, createElement('label', { style: { display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' } }, hasBgCheck, ' Show background')))
    ];
    this._modal(`Add ${type.name} Tag`, body, async () => {
      const name = nameInput.value.trim();
      if (!name) { showToast('Name required', 'error'); throw 'stop'; }
      try { 
        await api.createTag({ 
          name, 
          tag_type_id: type.id, 
          color: colorInput.value,
          fg_color: fgColorInput.value,
          has_bg: hasBgCheck.checked
        }); 
        showToast('Created', 'success'); 
        this.render(root); 
      }
      catch (err) { showToast(err.message, 'error'); }
    });
    setTimeout(() => {
      nameInput.focus();
      updatePreview();
    }, 100);
  }

  _editType(type, root) {
    const preview = createElement('span', { className: 'tag-chip' }, type.name);
    
    const updatePreview = () => {
      const name = nameInput.value || type.name;
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

    const nameInput = createElement('input', { type: 'text', className: 'form-input', value: type.name, onInput: updatePreview });
    const colorInput = createElement('input', { type: 'color', className: 'form-input', value: type.color, style: { height: '40px', padding: '4px', cursor: 'pointer' }, onInput: updatePreview });
    const fgColorInput = createElement('input', { type: 'color', className: 'form-input', value: (type.fg_color || '#ffffff'), style: { height: '40px', padding: '4px', cursor: 'pointer' }, onInput: updatePreview });
    const hasBgCheck = createElement('input', { type: 'checkbox', checked: type.has_bg !== undefined ? !!type.has_bg : true, onChange: updatePreview });
    const iconInput = createElement('input', { type: 'text', className: 'form-input', value: type.icon || '', maxlength: '4' });

    const body = [
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Preview'), preview),
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Name'), nameInput),
      createElement('div', { className: 'form-row' },
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Background'), colorInput),
        createElement('div', { className: 'form-group' }, createElement('label', {}, 'Foreground'), fgColorInput),
        createElement('div', { className: 'form-group' }, createElement('label', { style: { display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' } }, hasBgCheck, ' Show background'))),
      createElement('div', { className: 'form-group' }, createElement('label', {}, 'Icon'), iconInput)
    ];
    this._modal('Edit Tag Type', body, async () => {
      const name = nameInput.value.trim();
      if (!name) { showToast('Name required', 'error'); throw 'stop'; }
      try { 
        await api.updateTagType(type.id, { 
          name, 
          color: colorInput.value, 
          fg_color: fgColorInput.value,
          has_bg: hasBgCheck.checked,
          icon: iconInput.value.trim() 
        }); 
        showToast('Updated', 'success'); 
        this.render(root); 
      }
      catch (err) { showToast(err.message, 'error'); }
    });
    setTimeout(() => {
      nameInput.focus();
      updatePreview();
    }, 100);
  }

  async _deleteType(type, root) {
    if (!confirm(`Delete "${type.name}" and all its tags?`)) return;
    try { await api.deleteTagType(type.id); showToast('Deleted', 'success'); this.render(root); }
    catch (err) { showToast(err.message, 'error'); }
  }
}
