// ============================================================
// Tasks — Daily log: a day's entries, or a report over a range (DailyScreen on Android)
// ============================================================

import { api } from '../api.js';
import { createElement as h, setChildren, showSnackbar, showToast, todayStr, addDays, dateLabel, longLabel } from '../utils.js';
import { icon } from '../icons.js';
import { citem, confirmDialog, emptyState, nameDialog, sectionHeader, spinner, toggleGroup } from '../ui.js';

const bullets = (entries) => entries.map(e => `• ${e.text}`).join('\n');

async function copyEntries(entries) {
  if (!entries.length) { showSnackbar('Nothing to copy'); return; }
  const text = bullets(entries);
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    // Fallback for browsers/contexts without the async clipboard API
    const ta = h('textarea', { style: { position: 'fixed', opacity: '0' } });
    ta.value = text;
    document.body.appendChild(ta);
    ta.select();
    document.execCommand('copy');
    ta.remove();
  }
  showSnackbar(`Copied ${entries.length} task${entries.length === 1 ? '' : 's'}`);
}

export function createDailyPage() {
  const state = { report: false, date: todayStr(), from: addDays(todayStr(), -6), to: todayStr(), entries: [], loading: true };
  let seq = 0;

  const title = h('div', { className: 'daily-title' });
  const controls = h('div');
  const list = h('div');
  const inner = h('div', { className: 'page-inner' },
    title,
    toggleGroup([
      { value: false, label: 'Day', icon: 'today' },
      { value: true, label: 'Report', icon: 'report' },
    ], false, (v) => { state.report = v; renderControls(); load(); }),
    controls,
    list,
  );
  const el = h('div', { className: 'page root' }, h('div', { className: 'page-scroll' }, inner));

  async function load() {
    const mine = ++seq;
    state.loading = true;
    renderList();
    try {
      const params = state.report ? { from: state.from, to: state.to } : { date: state.date };
      const data = await api.getDailyLogs(params);
      if (mine !== seq) return;
      state.entries = data.logs || [];
    } catch (err) {
      if (mine !== seq) return;
      state.entries = [];
      showToast(err.message);
    }
    state.loading = false;
    renderTitle();
    renderList();
  }

  function renderTitle() {
    setChildren(title,
      h('h1', { className: 'display-small' }, 'Daily log'),
      state.report ? null : h('button', { type: 'button', className: 'icon-btn interactive', 'aria-label': "Copy the day's entries", onClick: () => copyEntries(state.entries) }, icon('copy')),
    );
  }

  /** A pill holding a date button; the native date picker sits over the label. */
  function dateButton(value, label, onPick) {
    const input = h('input', { type: 'date', value, 'aria-label': label });
    input.addEventListener('change', () => { if (input.value) onPick(input.value); });
    return h('label', { className: 'date-btn interactive' }, h('span', {}, dateLabelFor(value)), input);
  }
  const dateLabelFor = (d) => (d === todayStr() && !state.report ? 'Today' : state.report ? dateLabel(d) : longLabel(d));

  function renderControls() {
    renderTitle();
    if (!state.report) {
      // Previous day / the date (opens a picker) / next day, as one pill
      const shift = (n) => { state.date = addDays(state.date, n); renderControls(); load(); };
      const input = h('input', { type: 'text', placeholder: 'What did you get done?', 'aria-label': 'New entry', maxlength: '500', enterkeyhint: 'done', autocapitalize: 'sentences' });
      // The input stays put and clears right away, so quick consecutive entries aren't lost
      const add = async () => {
        const text = input.value.trim();
        if (!text) return;
        input.value = '';
        try { await api.createDailyLog({ date: state.date, text }); load(); } catch (err) { showToast(err.message); input.value = text; }
      };
      input.addEventListener('keydown', (e) => { if (e.key === 'Enter') add(); });
      setChildren(controls,
        h('div', { className: 'pill-bar' },
          h('button', { type: 'button', className: 'icon-btn interactive', 'aria-label': 'Previous day', onClick: () => shift(-1) }, icon('chevronLeft')),
          dateButton(state.date, 'Pick a day', (d) => { state.date = d; renderControls(); load(); }),
          h('button', { type: 'button', className: 'icon-btn interactive', 'aria-label': 'Next day', onClick: () => shift(1) }, icon('chevronRight')),
        ),
        h('div', { className: 'add-row' }, input,
          h('button', { type: 'button', className: 'icon-btn filled interactive', 'aria-label': 'Add entry', onClick: add }, icon('add'))),
      );
    } else {
      const today = todayStr();
      const setRange = (from, to) => { state.from = from; state.to = to; renderControls(); load(); };
      const preset = (days, label) => {
        const selected = state.to === today && state.from === addDays(today, -(days - 1));
        return h('button', {
          type: 'button', className: 'pick-chip interactive', 'aria-pressed': String(selected),
          onClick: () => setRange(addDays(today, -(days - 1)), today),
        }, selected ? icon('check', { size: 18 }) : null, label);
      };
      setChildren(controls,
        h('div', { className: 'pill-bar' },
          dateButton(state.from, 'From', (d) => setRange(d, d > state.to ? d : state.to)),
          icon('chevronRight', { className: 'muted' }),
          dateButton(state.to, 'To', (d) => setRange(d < state.from ? d : state.from, d)),
        ),
        h('div', { className: 'range-chips' }, preset(7, 'Last 7 days'), preset(30, 'Last 30 days')),
      );
    }
  }

  function entryRows(entries) {
    return h('div', { className: 'connected' }, ...entries.map(e => citem(
      h('div', { className: 'entry-row' },
        h('span', { className: 'entry-dot' }),
        h('span', { className: 'entry-text' }, e.text),
        h('button', {
          type: 'button', className: 'icon-btn interactive', 'aria-label': 'Delete entry',
          onClick: (ev) => {
            ev.stopPropagation();
            confirmDialog('Delete entry?', e.text, 'Delete', async () => {
              try { await api.deleteDailyLog(e.id); load(); } catch (err) { showToast(err.message); }
            });
          },
        }, icon('delete', { size: 20 })),
      ),
      {
        onClick: () => nameDialog('Edit entry', 'Entry', e.text, async (text) => {
          try { await api.updateDailyLog(e.id, { text }); load(); } catch (err) { showToast(err.message); }
        }),
      },
    )));
  }

  function renderList() {
    if (state.loading) return setChildren(list, spinner());
    if (!state.entries.length) {
      return setChildren(list, state.report
        ? emptyState('report', 'Nothing in this range', 'Entries you log will add up here.')
        : emptyState('dailyLogFilled', 'Nothing logged yet', 'Finish a task and tap Log it, or add one above.'));
    }
    if (!state.report) return setChildren(list, h('div', { style: { height: '20px' } }), entryRows(state.entries));
    // The API returns newest day first
    const byDay = new Map();
    state.entries.forEach(e => { if (!byDay.has(e.log_date)) byDay.set(e.log_date, []); byDay.get(e.log_date).push(e); });
    setChildren(list, ...[...byDay].flatMap(([day, entries]) => [
      h('div', { style: { display: 'flex', alignItems: 'center' } },
        h('div', { style: { flex: '1', minWidth: '0' } }, sectionHeader({ title: longLabel(day), icon: 'calendar', tile: 'primary square', trailing: String(entries.length) })),
        h('button', { type: 'button', className: 'icon-btn interactive', 'aria-label': 'Copy this day', style: { marginTop: '10px' }, onClick: () => copyEntries(entries) }, icon('copy', { size: 20 })),
      ),
      entryRows(entries),
    ]));
  }

  renderControls();
  load();
  return { el, refresh: load, onShow: load };
}
