// ============================================================
// Tasks — Daily Tasks Component (Entry + Report)
// ============================================================

import { api } from '../api.js';
import { createElement, showToast } from '../utils.js';

// Local (not UTC) YYYY-MM-DD, so "today" matches the user's calendar day
function toDateStr(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function addDays(dateStr, delta) {
  const [y, m, d] = dateStr.split('-').map(Number);
  return toDateStr(new Date(y, m - 1, d + delta));
}

function prettyDate(dateStr) {
  const [y, m, d] = dateStr.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('en-US', {
    weekday: 'short', month: 'short', day: 'numeric', year: 'numeric',
  });
}

async function copyLines(entries) {
  const text = entries.map(e => e.text).join('\n');
  if (!text) {
    showToast('Nothing to copy', 'info');
    return;
  }
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    // Fallback for browsers/contexts without the async clipboard API
    const ta = createElement('textarea', { style: { position: 'fixed', opacity: '0' } });
    ta.value = text;
    document.body.appendChild(ta);
    ta.select();
    document.execCommand('copy');
    ta.remove();
  }
  showToast(`Copied ${entries.length} task${entries.length === 1 ? '' : 's'}`, 'success');
}

export class DailyLog {
  constructor() {
    this.entryDate = toDateStr(new Date());
    this.reportFrom = addDays(toDateStr(new Date()), -6);
    this.reportTo = toDateStr(new Date());
  }

  // ==================== Entry ====================
  async renderEntry(container) {
    container.innerHTML = '';

    const input = createElement('input', {
      type: 'text',
      className: 'form-input',
      placeholder: 'What did you get done? Press Enter to add',
      maxlength: '500',
      onKeydown: (e) => { if (e.key === 'Enter') add(); },
    });

    const listEl = createElement('div', { className: 'daily-list' });
    const countEl = createElement('span', { className: 'daily-count' });
    let entries = [];

    const dateInput = createElement('input', {
      type: 'date',
      className: 'form-input daily-date',
      value: this.entryDate,
      onChange: (e) => {
        if (!e.target.value) { e.target.value = this.entryDate; return; }
        this.entryDate = e.target.value;
        load();
      },
    });

    const copyBtn = createElement('button', {
      className: 'btn btn-secondary',
      onClick: () => copyLines(entries),
    }, 'Copy tasks');

    const draw = () => {
      countEl.textContent = `${entries.length} task${entries.length === 1 ? '' : 's'}`;
      listEl.innerHTML = '';
      if (entries.length === 0) {
        listEl.appendChild(createElement('div', { className: 'daily-empty' }, 'Nothing logged for this day yet.'));
        return;
      }
      entries.forEach(e => listEl.appendChild(this._entryRow(e, load)));
    };

    // Only the most recent load may draw, so out-of-order responses can't show a stale list
    let loadSeq = 0;
    const load = async () => {
      const seq = ++loadSeq;
      let result = [];
      try {
        const data = await api.getDailyLogs({ date: this.entryDate });
        result = data.logs || [];
      } catch (err) {
        showToast(err.message, 'error');
      }
      if (seq !== loadSeq) return;
      entries = result;
      draw();
    };

    // The input stays enabled and is cleared right away, so quick consecutive entries aren't lost
    const add = async () => {
      const text = input.value.trim();
      if (!text) return;
      input.value = '';
      try {
        await api.createDailyLog({ date: this.entryDate, text });
        await load();
      } catch (err) {
        showToast(err.message, 'error');
        if (!input.value) input.value = text;
      }
    };

    container.appendChild(createElement('div', { className: 'daily-wrap' },
      createElement('div', { className: 'daily-toolbar' },
        createElement('div', { className: 'daily-date-group' },
          createElement('button', { className: 'btn btn-secondary btn-icon', title: 'Previous day', onClick: () => shift(-1) }, '‹'),
          dateInput,
          createElement('button', { className: 'btn btn-secondary btn-icon', title: 'Next day', onClick: () => shift(1) }, '›'),
        ),
        createElement('div', { className: 'daily-toolbar-right' }, countEl, copyBtn)
      ),
      createElement('div', { className: 'daily-add' },
        input,
        createElement('button', { className: 'btn btn-primary', onClick: add }, 'Add')
      ),
      listEl
    ));

    const shift = (delta) => {
      this.entryDate = addDays(this.entryDate, delta);
      dateInput.value = this.entryDate;
      load();
    };

    await load();
    input.focus();
  }

  _entryRow(entry, reload) {
    const textEl = createElement('span', { className: 'daily-text' }, entry.text);
    const row = createElement('div', { className: 'daily-item' },
      createElement('span', { className: 'daily-bullet' }),
      textEl,
      createElement('div', { className: 'daily-actions' },
        createElement('button', { className: 'task-action-btn', onClick: () => startEdit() }, 'Edit'),
        createElement('button', { className: 'task-action-btn delete', onClick: () => remove() }, 'Delete'),
      )
    );

    const startEdit = () => {
      const edit = createElement('input', {
        type: 'text',
        className: 'form-input daily-edit',
        value: entry.text,
        maxlength: '500',
      });
      let done = false;
      const save = async () => {
        if (done) return;
        done = true;
        const text = edit.value.trim();
        if (!text || text === entry.text) { reload(); return; }
        try {
          await api.updateDailyLog(entry.id, { text });
        } catch (err) {
          showToast(err.message, 'error');
        }
        reload();
      };
      edit.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') save();
        if (e.key === 'Escape') { done = true; reload(); }
      });
      edit.addEventListener('blur', save);
      textEl.replaceWith(edit);
      edit.focus();
      edit.select();
    };

    const remove = async () => {
      try {
        await api.deleteDailyLog(entry.id);
        reload();
      } catch (err) {
        showToast(err.message, 'error');
      }
    };

    return row;
  }

  // ==================== Report ====================
  async renderReport(container) {
    container.innerHTML = '';

    const listEl = createElement('div', { className: 'daily-report-list' });

    const fromInput = createElement('input', {
      type: 'date', className: 'form-input daily-date', value: this.reportFrom,
      onChange: (e) => { if (e.target.value) { this.reportFrom = e.target.value; load(); } else e.target.value = this.reportFrom; },
    });
    const toInput = createElement('input', {
      type: 'date', className: 'form-input daily-date', value: this.reportTo,
      onChange: (e) => { if (e.target.value) { this.reportTo = e.target.value; load(); } else e.target.value = this.reportTo; },
    });

    let loadSeq = 0;
    const load = async () => {
      const seq = ++loadSeq;
      if (this.reportFrom > this.reportTo) {
        listEl.innerHTML = '';
        listEl.appendChild(createElement('div', { className: 'daily-empty' }, '"From" is after "To".'));
        return;
      }
      let logs = [];
      try {
        const data = await api.getDailyLogs({ from: this.reportFrom, to: this.reportTo });
        logs = data.logs || [];
      } catch (err) {
        showToast(err.message, 'error');
      }
      if (seq !== loadSeq) return;

      // Group by day (API returns newest day first)
      const byDay = new Map();
      logs.forEach(l => {
        if (!byDay.has(l.log_date)) byDay.set(l.log_date, []);
        byDay.get(l.log_date).push(l);
      });

      listEl.innerHTML = '';
      if (byDay.size === 0) {
        listEl.appendChild(createElement('div', { className: 'daily-empty' }, 'No tasks logged in this range.'));
        return;
      }
      byDay.forEach((entries, date) => {
        listEl.appendChild(createElement('section', { className: 'daily-day' },
          createElement('div', { className: 'daily-day-header' },
            createElement('h3', {}, prettyDate(date)),
            createElement('span', { className: 'daily-count' }, `${entries.length} task${entries.length === 1 ? '' : 's'}`),
            createElement('button', { className: 'btn btn-secondary daily-copy', onClick: () => copyLines(entries) }, 'Copy tasks')
          ),
          createElement('ul', { className: 'daily-day-list' },
            ...entries.map(e => createElement('li', {}, e.text))
          )
        ));
      });
    };

    const setRange = (days) => {
      const today = toDateStr(new Date());
      this.reportTo = today;
      this.reportFrom = addDays(today, -(days - 1));
      fromInput.value = this.reportFrom;
      toInput.value = this.reportTo;
      load();
    };

    container.appendChild(createElement('div', { className: 'daily-wrap' },
      createElement('div', { className: 'daily-toolbar' },
        createElement('div', { className: 'daily-date-group' },
          createElement('label', { className: 'daily-label' }, 'From'), fromInput,
          createElement('label', { className: 'daily-label' }, 'To'), toInput,
        ),
        createElement('div', { className: 'daily-toolbar-right' },
          createElement('button', { className: 'btn btn-secondary', onClick: () => setRange(7) }, 'Last 7 days'),
          createElement('button', { className: 'btn btn-secondary', onClick: () => setRange(30) }, 'Last 30 days'),
        )
      ),
      listEl
    ));

    await load();
  }
}
