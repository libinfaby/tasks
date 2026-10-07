// ============================================================
// Tasks — Task list page (All tasks, Today, Upcoming, a group)
// Same views, sections and cards as the Android TaskListScreen.
// ============================================================

import { api } from '../api.js';
import { createElement as h, setChildren, debounce, showSnackbar, showToast, todayStr, addDays, dateLabel, dayLabel, fullLabel,
  formatReminder, isOverdue, isToday, tonal, REPEAT_LABELS,
} from '../utils.js';
import { icon } from '../icons.js';
import { chip, emptyState, fab, sectionHeader, spinner, topBar } from '../ui.js';
import { store } from '../store.js';

const TITLES = { all: 'All tasks', today: 'Today', upcoming: 'Upcoming' };
// Tags named like this are the task's kind; they sort right after the client
const KIND_TAG_NAMES = ['issue', 'requirement', 'modification'];
const TAG_ORDER = ['client', 'kind', 'project', 'via'];
// How long a task ticked off stays (shown done) so the animation plays before it leaves
const LINGER_MS = 1200;

const isKind = (tag) => KIND_TAG_NAMES.includes((tag.name || '').trim().toLowerCase());
const tagRank = (tag) => {
  const i = TAG_ORDER.indexOf(isKind(tag) ? 'kind' : (tag.type_name || '').toLowerCase());
  return i === -1 ? TAG_ORDER.length : i;
};

/** The daily log line for a task: "Client: Title(subtask 1, subtask 2)", without the parts it doesn't have. */
function dailyLogEntry(task) {
  const clients = (task.tags || []).filter(t => (t.type_name || '').toLowerCase() === 'client').map(t => t.name);
  const subtasks = (task.subtasks || []).map(s => s.title.trim()).filter(Boolean);
  const title = subtasks.length ? `${task.title}(${subtasks.join(', ')})` : task.title;
  return clients.length ? `${clients.join(', ')}: ${title}` : title;
}

async function addToDailyLog(task) {
  try {
    await api.createDailyLog({ date: todayStr(), text: dailyLogEntry(task) });
    showSnackbar("Added to today's daily log");
  } catch (err) { showToast(err.message); }
}

export function createTaskListPage({ view, group = null, nav }) {
  const root = view !== 'group';
  const state = {
    filters: { completed: false, urgent: false, tagId: null, tagName: null },
    search: { text: '', type: 'task', tagTypeId: null },
    searching: false,
    tasks: [],
    loading: true,
    error: null,
    held: new Set(), // ticked off a moment ago: keeps its place until it leaves
  };
  let loadSeq = 0;

  const scroller = h('div', { className: 'page-scroll' });
  const inner = h('div', { className: 'page-inner' });
  const searchSlot = h('div');
  const header = h('div', { className: 'title-header' });
  const filterBar = h('div', { className: 'filter-bar' });
  const body = h('div');
  if (root) inner.append(searchSlot, header);
  inner.append(filterBar, body);
  if (!root) scroller.append(topBar(group.name, nav.back));
  scroller.append(inner);
  const el = h('div', { className: `page${root ? ' root' : ''}` }, scroller, fab('New task', 'add', nav.newTask, scroller));

  // ==================== Data ====================

  const needsServerView = () => state.filters.completed || !!state.search.text.trim();

  /** Same filters the Android app uses: Today carries anything overdue, unless searching or showing done. */
  function query() {
    const q = { completed: String(state.filters.completed) };
    const today = todayStr();
    const server = needsServerView();
    if (view === 'today') { if (server) q.date_from = today; q.date_to = today; }
    if (view === 'upcoming') { q.date_from = addDays(today, 1); q.date_to = addDays(today, 7); }
    if (view === 'group') q.group_id = group.id;
    if (state.filters.tagId) q.tag_id = state.filters.tagId;
    const text = state.search.text.trim();
    if (text) {
      q.search = text;
      q.search_type = state.search.type;
      if (state.search.tagTypeId) q.search_tag_type = state.search.tagTypeId;
    }
    return q;
  }

  async function load({ quiet = false } = {}) {
    const seq = ++loadSeq;
    if (!quiet) { state.loading = true; render(); }
    try {
      let tasks = (await api.getTasks(query())).tasks || [];
      if (state.filters.urgent) tasks = tasks.filter(t => t.priority > 0);
      if (seq !== loadSeq) return;
      // A task still lingering after being ticked off keeps its place, shown done
      const lingering = state.tasks.filter(t => state.held.has(t.id) && !tasks.some(n => n.id === t.id));
      state.tasks = [...tasks, ...lingering];
      state.error = null;
    } catch (err) {
      if (seq !== loadSeq) return;
      state.error = err.message;
    }
    state.loading = false;
    render();
  }

  /**
   * All: Urgent / Tasks / one section per group. Today: Overdue first, then the same split.
   * Upcoming: one section per day. Groups, searches and done tasks are flat.
   */
  function sections() {
    const tasks = state.tasks;
    const settledDone = (t) => t.is_completed && !state.held.has(t.id);
    const split = (ts) => {
      const urgent = ts.filter(t => t.priority > 0 && !settledDone(t));
      const ungrouped = ts.filter(t => !t.group && (t.priority === 0 || settledDone(t)));
      const groups = new Map();
      // As before, a grouped urgent task shows under Urgent and under its group
      ts.filter(t => t.group).forEach(t => {
        if (!groups.has(t.group.id)) groups.set(t.group.id, { title: t.group.name, kind: 'group', group: t.group, tasks: [] });
        groups.get(t.group.id).tasks.push(t);
      });
      return [
        ...(urgent.length ? [{ title: 'Urgent', kind: 'urgent', tasks: urgent }] : []),
        ...(ungrouped.length ? [{ title: 'Tasks', kind: 'tasks', tasks: ungrouped }] : []),
        ...groups.values(),
      ];
    };
    if (!tasks.length) return [];
    if (needsServerView() || view === 'group') return [{ title: '', kind: 'plain', tasks }];
    if (view === 'today') {
      const today = todayStr();
      const overdue = tasks.filter(t => t.date && t.date.slice(0, 10) < today);
      const rest = tasks.filter(t => !overdue.includes(t));
      return [...(overdue.length ? [{ title: 'Overdue', kind: 'overdue', tasks: overdue }] : []), ...split(rest)];
    }
    if (view === 'upcoming') {
      const byDay = new Map();
      [...tasks].sort((a, b) => a.date.localeCompare(b.date)).forEach(t => {
        const day = t.date.slice(0, 10);
        if (!byDay.has(day)) byDay.set(day, { title: dayLabel(day), kind: 'day', tasks: [] });
        byDay.get(day).tasks.push(t);
      });
      return [...byDay.values()];
    }
    return split(tasks);
  }

  // ==================== Rendering ====================

  function render() {
    if (root) renderSearch();
    renderHeader();
    renderFilters();
    if (state.loading) return setChildren(body, spinner());
    if (state.error) {
      return setChildren(body, h('div', { className: 'empty-state' },
        emptyState('refresh', "Couldn't load tasks", state.error),
        h('button', { type: 'button', className: 'btn btn-tonal interactive', onClick: () => load() }, 'Try again')));
    }
    const secs = sections();
    if (!secs.length) return setChildren(body, emptyFor());
    setChildren(body, ...secs.flatMap(sec => [sectionTitle(sec), taskListFor(sec)]));
  }

  function renderHeader() {
    if (!root) return;
    const count = state.tasks.filter(t => !(t.is_completed && !state.held.has(t.id))).length;
    const sub = view === 'today' ? `${fullLabel(todayStr())} · ${count} open`
      : view === 'upcoming' ? `Next 7 days · ${count} open`
        : `${count} open`;
    setChildren(header,
      h('h1', { className: 'display-small' }, TITLES[view]),
      h('p', { className: 'subtitle body-medium muted', style: { visibility: state.loading ? 'hidden' : 'visible' } }, sub),
    );
  }

  function renderFilters() {
    const f = state.filters;
    const any = f.completed || f.urgent || f.tagId;
    const pill = (label, selected, onClick, { iconName = null, iconClass = '', clearable = false } = {}) => h('button', {
      type: 'button',
      className: `filter-chip interactive${selected ? ' selected' : ''}${clearable ? ' clearable' : ''}`,
      'aria-pressed': String(selected),
      onClick,
    },
      selected ? icon('check', { size: 18 }) : iconName ? icon(iconName, { size: 18, className: iconClass }) : null,
      label,
      clearable ? icon('close', { size: 18 }) : null,
    );
    setChildren(filterBar,
      pill('All', !any, () => { state.filters = { completed: false, urgent: false, tagId: null, tagName: null }; load(); }),
      pill('Urgent', f.urgent, () => { f.urgent = !f.urgent; load(); }, { iconName: 'fireFilled', iconClass: 'urgent-icon' }),
      pill('Done', f.completed, () => { f.completed = !f.completed; load(); }, { iconName: 'taskAlt', iconClass: 'done-icon' }),
      f.tagId ? pill(f.tagName || 'Tag', true, () => { f.tagId = null; f.tagName = null; load(); }, { clearable: true }) : null,
    );
  }

  function sectionTitle(sec) {
    if (!sec.title) return h('div', { style: { height: '16px' } });
    const open = sec.tasks.filter(t => !t.is_completed).length;
    const trailing = open === 0 ? 'All done' : `${open} left`;
    switch (sec.kind) {
      case 'overdue': return sectionHeader({ title: sec.title, icon: 'alarm', tile: 'error square', trailing });
      case 'urgent': return sectionHeader({ title: sec.title, icon: 'fireFilled', tile: 'error square', trailing });
      case 'day': return sectionHeader({ title: sec.title, icon: 'calendar', tile: 'primary square', trailing });
      case 'group': {
        // A group's header opens that group on its own
        const t = tonal(sec.group.color);
        return sectionHeader({
          title: sec.title, icon: 'groupFilled', tile: `group-shape tile-tonal ${t.className}`, tileStyle: t.style, trailing,
          onClick: () => nav.go({ name: 'group', group: sec.group }), label: `Open group ${sec.group.name}`,
        });
      }
      default: return sectionHeader({ title: sec.title, icon: 'taskAlt', tile: 'secondary', trailing });
    }
  }

  function taskListFor(sec) {
    const showDate = sec.kind === 'day' ? false : view === 'today' ? sec.kind === 'overdue' : true;
    const showGroup = sec.kind !== 'group' && view !== 'group';
    return h('div', { className: 'connected tasks' }, ...sec.tasks.map(t => taskCard(t, { showDate, showGroup })));
  }

  function taskCard(task, { showDate, showGroup }) {
    const done = !!task.is_completed;
    // A div rather than a button, so the checkbox and chips inside can be real buttons
    const card = h('div', {
      className: `citem interactive task-card${done ? ' done' : ''}`,
      role: 'button',
      tabindex: '0',
      'aria-label': `Open ${task.title}`,
      onClick: () => nav.openTask(task),
      onKeydown: (e) => { if ((e.key === 'Enter' || e.key === ' ') && e.target === card) { e.preventDefault(); nav.openTask(task); } },
    },
      checkbox(task, done),
      h('div', { className: 'task-body' },
        h('div', { className: 'task-title' }, task.title),
        task.details?.trim() ? h('div', { className: 'task-details' }, task.details.trim()) : null,
        meta(task, { showDate, showGroup }),
        task.subtasks?.length ? subtaskPreview(task.subtasks) : null,
      ),
      // A done task can be logged any time, not only from the snackbar
      done ? h('button', {
        type: 'button',
        className: 'icon-btn sm log-btn interactive',
        'aria-label': `Add ${task.title} to today's daily log`,
        title: "Add to today's daily log",
        onClick: (e) => { e.stopPropagation(); addToDailyLog(task); },
      }, icon('dailyLog')) : null,
    );
    return card;
  }

  /** Round checkbox whose ring takes the priority colour; checking pops a filled circle and a burst. */
  function checkbox(task, done) {
    const btn = h('button', {
      type: 'button',
      className: `task-check interactive${task.priority > 0 ? ' urgent' : ''}${done ? ' checked' : ''}`,
      role: 'checkbox',
      'aria-checked': String(done),
      'aria-label': done ? `Mark ${task.title} not done` : `Complete ${task.title}`,
    },
      h('span', { className: 'ring' }),
      h('span', { className: 'scallop' }, icon('check', { size: 20 })),
    );
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      toggle(task, btn);
    });
    return btn;
  }

  function burst(btn) {
    const b = h('span', { className: 'burst' });
    const colors = ['var(--primary)', 'var(--error)', 'var(--secondary)', 'var(--outline)'];
    for (let i = 0; i < 10; i++) {
      const a = (i / 10) * Math.PI * 2 + Math.random() * 0.4;
      const d = 22 + Math.random() * 12;
      b.appendChild(h('i', { style: { background: colors[i % colors.length], '--dx': `${Math.cos(a) * d}px`, '--dy': `${Math.sin(a) * d}px` } }));
    }
    btn.appendChild(b);
    setTimeout(() => b.remove(), 800);
  }

  async function toggle(task, btn) {
    const completing = !task.is_completed;
    if (completing) {
      state.held.add(task.id);
      btn.classList.add('checked', 'popping');
      btn.closest('.task-card')?.classList.add('done');
      burst(btn);
      navigator.vibrate?.(10);
    }
    try {
      await api.toggleTask(task.id);
      task.is_completed = completing ? 1 : 0;
      if (completing) {
        offerDailyLog(task);
        setTimeout(() => { state.held.delete(task.id); load({ quiet: true }); store.loadGroups(); }, LINGER_MS);
      } else {
        load({ quiet: true });
        store.loadGroups();
      }
    } catch (err) {
      state.held.delete(task.id);
      showToast(err.message);
      render();
    }
  }

  // Finishing a task offers to log it, without stopping the flow with a dialog
  function offerDailyLog(task) {
    showSnackbar('Done! Add it to your daily log?', {
      dismissible: true,
      duration: 6000,
      action: { label: 'Log it', onClick: () => addToDailyLog(task) },
    });
  }

  function meta(task, { showDate, showGroup }) {
    const items = [];
    // Urgency is spelled out too, so it never rests on the ring colour alone
    if (task.priority > 0) items.push(h('span', { className: 'meta-item urgent' }, icon('fireFilled', { size: 16 }), 'Urgent'));
    if (task.date && showDate) {
      const cls = isOverdue(task.date) && !task.is_completed ? ' overdue' : isToday(task.date) ? ' today' : '';
      items.push(h('span', { className: `meta-item${cls}` }, icon('calendar', { size: 16 }), dateLabel(task.date)));
    }
    if (task.reminder) {
      items.push(h('span', { className: 'meta-item' }, icon('alarm', { size: 16 }), formatReminder(task.reminder)));
      const repeat = REPEAT_LABELS[task.reminder_repeat];
      if (repeat) items.push(h('span', { className: 'meta-item' }, icon('repeat', { size: 16 }), repeat));
    }
    if (task.group && showGroup) {
      items.push(h('span', { className: 'meta-item' }, h('span', { className: 'group-dot', style: { background: task.group.color } }), task.group.name));
    }
    [...(task.tags || [])].sort((a, b) => tagRank(a) - tagRank(b)).forEach(tag => {
      items.push(chip(tag.name, tag.color, {
        fallback: tag.type_color,
        title: `Show only ${tag.name}`,
        onClick: () => { state.filters.tagId = tag.id; state.filters.tagName = tag.name; load(); },
      }));
    });
    return items.length ? h('div', { className: 'task-meta' }, ...items) : null;
  }

  function subtaskPreview(subtasks) {
    const done = subtasks.filter(s => s.is_completed).length;
    const pct = (done / subtasks.length) * 100;
    return h('div', { className: 'subtask-preview' },
      h('div', { className: 'subtask-progress' },
        h('div', { className: 'progress-track' },
          done > 0 ? h('span', { className: 'fill', style: { flexBasis: `${pct}%` } }) : null,
          done < subtasks.length ? h('span', { className: 'rest' }) : null,
        ),
        h('span', { className: 'label-medium muted' }, `${done}/${subtasks.length}`),
      ),
      ...subtasks.map(s => {
        const checked = !!s.is_completed;
        return h('div', { className: `subtask-row${checked ? ' done' : ''}` },
          h('button', {
            type: 'button',
            className: `sub-check interactive${checked ? ' checked' : ''}`,
            role: 'checkbox',
            'aria-checked': String(checked),
            'aria-label': checked ? `Mark ${s.title} not done` : `Complete ${s.title}`,
            onClick: async (e) => {
              e.stopPropagation();
              try { await api.toggleSubtask(s.id); load({ quiet: true }); } catch (err) { showToast(err.message); }
            },
          }, h('span', { className: 'box' }, checked ? icon('check', { size: 16 }) : null)),
          h('span', { className: 'sub-text' }, s.title),
        );
      }),
    );
  }

  function emptyFor() {
    const filtered = !!state.search.text.trim() || !!state.filters.tagId || state.filters.urgent;
    if (filtered) return emptyState('search404', 'No matches', 'Try a different search, or clear the filter.');
    if (state.filters.completed) return emptyState('taskAlt', 'Nothing finished yet', 'Tasks you complete will show up here.');
    if (view === 'today') return emptyState('sunny', 'Nothing due today', 'Enjoy the calm, or tap New task to plan something.');
    if (view === 'upcoming') return emptyState('upcoming', 'Nothing coming up', 'Tasks dated in the next 7 days land here.');
    if (view === 'group') return emptyState('groupFilled', 'This group is empty', 'Tap New task to add one.');
    return emptyState('beach', 'All clear', 'Tap New task to add one.');
  }

  // ==================== Search ====================

  const runSearch = debounce(() => load({ quiet: true }), 300);

  /**
   * The search bar: a pill that opens into a field with a type menu (task name / any tag / date / a
   * specific tag type). The button on the right opens the menu (phones; wide screens have the drawer).
   */
  function renderSearch() {
    if (searchSlot.dataset.mode === (state.searching ? 'open' : 'closed') && searchSlot.firstChild) return;
    searchSlot.dataset.mode = state.searching ? 'open' : 'closed';
    if (!state.searching) {
      setChildren(searchSlot, h('div', { className: 'search-pill' },
        h('button', { type: 'button', className: 'search-open interactive', onClick: () => { state.searching = true; renderSearch(); } },
          icon('search'), h('span', { className: 'body-large' }, 'Search tasks and tags')),
        h('button', { type: 'button', className: 'icon-btn menu-btn interactive', 'aria-label': 'Menu: groups, tags and settings', onClick: nav.openMenu }, icon('menuSteps')),
      ));
      return;
    }
    const input = h('input', { className: 'search-input', type: state.search.type === 'date' ? 'date' : 'text', placeholder: 'Search…', 'aria-label': 'Search', value: state.search.text, enterkeyhint: 'search' });
    const select = h('select', { className: 'search-type', 'aria-label': 'Search by' },
      h('option', { value: 'task' }, 'Task name'),
      h('option', { value: 'tag' }, 'Any tag'),
      h('option', { value: 'date' }, 'Date'),
      ...store.tagTypes.map(t => h('option', { value: `type-${t.id}` }, t.name)),
    );
    select.value = state.search.tagTypeId ? `type-${state.search.tagTypeId}` : state.search.type;
    select.addEventListener('change', () => {
      const v = select.value;
      const wasDate = state.search.type === 'date';
      state.search.type = v.startsWith('type-') ? 'tag' : v;
      state.search.tagTypeId = v.startsWith('type-') ? Number(v.slice(5)) : null;
      // A date search uses the date picker; other searches use free text
      if ((state.search.type === 'date') !== wasDate) { state.search.text = ''; input.value = ''; input.type = state.search.type === 'date' ? 'date' : 'text'; }
      input.focus();
      if (state.search.text) runSearch();
    });
    input.addEventListener('input', () => { state.search.text = input.value; runSearch(); });
    input.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });
    const close = () => {
      state.searching = false;
      const had = !!state.search.text;
      state.search = { text: '', type: 'task', tagTypeId: null };
      renderSearch();
      if (had) load({ quiet: true });
    };
    setChildren(searchSlot, h('div', { className: 'search-pill' },
      h('div', { className: 'search-field' }, select, input,
        h('button', { type: 'button', className: 'icon-btn interactive', 'aria-label': 'Close search', onClick: close }, icon('close'))),
    ));
    setTimeout(() => input.focus(), 0);
  }

  const off = store.on(() => { if (state.searching && root) { searchSlot.dataset.mode = ''; renderSearch(); } });

  render();
  load();

  return {
    el,
    refresh: () => load({ quiet: true }),
    onShow: () => load({ quiet: true }),
    destroy: off,
  };
}
