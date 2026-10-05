// ============================================================
// Tasks — Tags page: tag types as cards of tonal chips
// ============================================================

import { api } from '../api.js';
import { createElement as h, setChildren, showToast, tonal } from '../utils.js';
import { icon } from '../icons.js';
import { chip, colorField, confirmDialog, dialog, emptyState, fab, textField, topBar } from '../ui.js';
import { store } from '../store.js';

export function createTagsPage({ nav }) {
  const inner = h('div', { className: 'page-inner stack' });
  const scroller = h('div', { className: 'page-scroll' }, topBar('Tags', nav.back), inner);
  const el = h('div', { className: 'page' }, scroller, fab('New tag type', 'add', () => editType(null), scroller));

  const render = () => {
    if (!store.tagTypes.length) {
      setChildren(inner, emptyState('tagFilled', 'No tag types yet', 'Create tag types like "Project" or "Client" to organise tasks.'));
      return;
    }
    setChildren(inner, ...store.tagTypes.map(type => {
      const tags = type.tags || [];
      const t = tonal(type.color);
      return h('div', { className: 'type-card' },
        h('div', { className: 'head' },
          h('span', { className: `type-tile tile-tonal ${t.className}`, style: t.style }, type.icon ? type.icon : icon('tagFilled', { size: 20 })),
          h('div', { className: 'head-text' },
            h('div', { className: 'title-medium' }, type.name),
            h('div', { className: 'body-small muted' }, `${tags.length} tag${tags.length === 1 ? '' : 's'}`),
          ),
          h('button', { type: 'button', className: 'icon-btn interactive', 'aria-label': `Edit ${type.name}`, onClick: () => editType(type) }, icon('edit', { size: 20 })),
          h('button', { type: 'button', className: 'icon-btn interactive', 'aria-label': `Delete ${type.name}`, onClick: () => deleteType(type) }, icon('delete', { size: 20 })),
        ),
        tags.length ? h('div', { className: 'chips' }, ...tags.map(tag => chip(tag.name, tag.color, { fallback: type.color, onClick: () => editTag(type, tag) }))) : null,
        // Always on its own line under the chips
        h('button', { type: 'button', className: 'add-inline interactive', onClick: () => editTag(type, null) }, icon('add', { size: 16 }), 'Add tag'),
      );
    }));
  };

  function editType(type) {
    styleDialog({
      title: type ? 'Edit tag type' : 'New tag type',
      name: type?.name || '',
      color: type?.color || '#6f6aa8',
      icon: type?.icon || '',
      withIcon: true,
      applyAllCount: type?.tags?.length || 0,
      onSave: async ({ name, color, icon: emoji, applyAll }) => {
        // Text colour and fill are kept as they are; only the colour is chosen here
        const data = { name, color, fg_color: type?.fg_color || '#ffffff', has_bg: type ? !!(type.has_bg ?? 1) : true, icon: emoji || null };
        if (type) await api.updateTagType(type.id, data); else await api.createTagType(data);
        if (type && applyAll) {
          for (const tag of type.tags || []) {
            await api.updateTag(tag.id, { name: tag.name, tag_type_id: type.id, color, fg_color: data.fg_color, has_bg: data.has_bg });
          }
        }
        showToast(type ? 'Updated' : 'Tag type created');
        await store.loadTagTypes();
      },
    });
  }

  function editTag(type, tag) {
    // New Client tags start white (as before); others take the type's colour
    const client = (type.name || '').toLowerCase() === 'client';
    styleDialog({
      title: tag ? 'Edit tag' : `Add ${type.name} tag`,
      name: tag?.name || '',
      color: tag?.color || (client ? '#ffffff' : type.color),
      onDelete: tag ? () => confirmDialog(`Delete tag "${tag.name}"?`, 'It will be removed from every task.', 'Delete', async () => {
        try { await api.deleteTag(tag.id); showToast('Tag deleted'); await store.loadTagTypes(); } catch (err) { showToast(err.message); }
      }) : null,
      onSave: async ({ name, color }) => {
        const data = {
          name,
          tag_type_id: type.id,
          color,
          fg_color: tag?.fg_color || (client ? '#000000' : (type.fg_color || '#ffffff')),
          has_bg: tag ? !!(tag.has_bg ?? 1) : (client ? true : !!(type.has_bg ?? 1)),
        };
        if (tag) await api.updateTag(tag.id, data); else await api.createTag(data);
        showToast(tag ? 'Tag updated' : 'Tag created');
        await store.loadTagTypes();
      },
    });
  }

  function deleteType(type) {
    confirmDialog(`Delete "${type.name}"?`, 'The tag type and all its tags will be deleted.', 'Delete', async () => {
      try { await api.deleteTagType(type.id); showToast('Deleted'); await store.loadTagTypes(); } catch (err) { showToast(err.message); }
    });
  }

  render();
  const off = store.on(render);
  store.loadTagTypes();
  return { el, destroy: off, onShow: () => store.loadTagTypes() };
}

/**
 * Name + colour (+ an emoji for tag types) with a live chip preview, like the Android StyleDialog.
 * onSave({ name, color, icon, applyAll }) may throw to keep the dialog open.
 */
export function styleDialog({ title, name, color, icon: emoji = '', withIcon = false, applyAllCount = 0, onDelete = null, onSave }) {
  const s = { name, color, icon: emoji, applyAll: false };
  const preview = h('div');
  const paintPreview = () => setChildren(preview, chip(s.name.trim() || 'Preview', s.color));
  const { el: nameField, input: nameInput } = textField({ label: 'Name', value: name, attrs: { autocapitalize: 'words' } });
  const parts = [preview, nameField, colorField('Colour', color, (c) => { s.color = c; paintPreview(); })];
  if (withIcon) {
    const { el: iconField, input: iconInput } = textField({ label: 'Icon (emoji)', value: emoji, attrs: { maxlength: '4' } });
    iconInput.addEventListener('input', () => { s.icon = iconInput.value.trim(); });
    parts.push(iconField);
  }
  if (applyAllCount > 0) {
    const box = h('input', { type: 'checkbox' });
    box.addEventListener('change', () => { s.applyAll = box.checked; });
    parts.push(h('label', { className: 'check-row' }, box, `Apply this colour to all ${applyAllCount} tag${applyAllCount === 1 ? '' : 's'}`));
  }
  const d = dialog({
    title,
    body: parts,
    actions: [
      ...(onDelete ? [{ label: 'Delete', kind: 'danger', start: true, onClick: () => { onDelete(); } }] : []),
      { label: 'Cancel' },
      {
        label: 'Save',
        onClick: async () => {
          const n = s.name.trim();
          if (!n) return false;
          try { await onSave({ ...s, name: n }); } catch (err) { showToast(err.message); return false; }
        },
      },
    ],
  });
  const save = d.actions[d.actions.length - 1];
  const sync = () => { s.name = nameInput.value; save.disabled = !s.name.trim(); paintPreview(); };
  nameInput.addEventListener('input', sync);
  nameInput.addEventListener('keydown', (e) => { if (e.key === 'Enter' && s.name.trim()) save.click(); });
  sync();
  setTimeout(() => nameInput.focus(), 50);
  return d;
}
