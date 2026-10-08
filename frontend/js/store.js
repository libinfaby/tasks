// ============================================================
// Tasks — Shared state: groups, tag types and the shared settings
// Screens subscribe so the drawer, menu and editor stay in step after an edit anywhere.
// ============================================================

import { api } from './api.js';
import { showToast } from './utils.js';

const listeners = new Set();

export const store = {
  groups: [],
  tagTypes: [],
  /** Shared with the Android app (GET/PUT /settings). */
  settings: { default_group_id: null, hidden_group_ids: [] },

  on(fn) { listeners.add(fn); return () => listeners.delete(fn); },
  emit() { listeners.forEach(fn => fn()); },

  async loadGroups() {
    try { this.groups = (await api.getGroups()).groups || []; } catch (err) { showToast(err.message); }
    this.emit();
  },

  async loadTagTypes() {
    try { this.tagTypes = (await api.getTagTypes()).tag_types || []; } catch (err) { showToast(err.message); }
    this.emit();
  },

  async loadSettings() {
    // An older API without /settings simply has no default group and nothing hidden
    try { this.settings = { default_group_id: null, hidden_group_ids: [], ...(await api.getSettings()).settings }; } catch { /* keep defaults */ }
    this.emit();
  },

  async loadAll() {
    await Promise.all([this.loadGroups(), this.loadTagTypes(), this.loadSettings()]);
  },

  async setDefaultGroup(id) {
    await api.updateSettings({ default_group_id: id });
    this.settings = { ...this.settings, default_group_id: id };
    this.emit();
  },

  async setGroupHidden(id, hidden) {
    // A group deleted since settings loaded would be refused, so only send ones that still exist
    const ids = this.settings.hidden_group_ids.filter(g => g !== id && this.groups.some(x => x.id === g));
    if (hidden) ids.push(id);
    await api.updateSettings({ hidden_group_ids: ids });
    this.settings = { ...this.settings, hidden_group_ids: ids };
    this.emit();
  },

  /** Whether a group's tasks stay out of All tasks, Today and Upcoming (they still show in the group). */
  isHidden(groupId) {
    return groupId != null && this.settings.hidden_group_ids.includes(groupId);
  },

  /** The default group if it still exists. */
  defaultGroupId() {
    const id = this.settings.default_group_id;
    return this.groups.some(g => g.id === id) ? id : null;
  },
};
