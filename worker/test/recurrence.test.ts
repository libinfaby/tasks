import { test } from 'vitest';
import assert from 'node:assert/strict';
import { nextOccurrence, isRepeatRule } from '../src/utils/recurrence';

// Shared cases — the Android app's RecurrenceTest mirrors these.
const IST = 'Asia/Kolkata';
const NY = 'America/New_York';

test('daily keeps local wall-clock time', () => {
  // 09:00 IST = 03:30Z
  assert.equal(
    nextOccurrence('2026-10-05T03:30:00.000Z', 'daily', IST, new Date('2026-10-05T03:30:00.000Z')),
    '2026-10-06T03:30:00.000Z',
  );
});

test('weekdays skips the weekend', () => {
  // Fri 2026-10-09 09:00 IST → Mon 2026-10-12
  assert.equal(
    nextOccurrence('2026-10-09T03:30:00.000Z', 'weekdays', IST, new Date('2026-10-09T03:30:00.000Z')),
    '2026-10-12T03:30:00.000Z',
  );
});

test('weekdays from a weekend lands on Monday', () => {
  // Sat 2026-10-10 → Mon 2026-10-12
  assert.equal(
    nextOccurrence('2026-10-10T03:30:00.000Z', 'weekdays', IST, new Date('2026-10-10T03:30:00.000Z')),
    '2026-10-12T03:30:00.000Z',
  );
});

test('weekly adds seven days', () => {
  assert.equal(
    nextOccurrence('2026-10-05T03:30:00.000Z', 'weekly', IST, new Date('2026-10-05T03:30:00.000Z')),
    '2026-10-12T03:30:00.000Z',
  );
});

test('monthly clamps Jan 31 to the end of February', () => {
  assert.equal(
    nextOccurrence('2027-01-31T03:30:00.000Z', 'monthly', IST, new Date('2027-01-31T03:30:00.000Z')),
    '2027-02-28T03:30:00.000Z',
  );
});

test('monthly rolls over the year', () => {
  assert.equal(
    nextOccurrence('2026-12-15T03:30:00.000Z', 'monthly', IST, new Date('2026-12-15T03:30:00.000Z')),
    '2027-01-15T03:30:00.000Z',
  );
});

test('missed occurrences are skipped, not replayed', () => {
  // Reminder was 10 days ago; next is the first daily slot after now
  assert.equal(
    nextOccurrence('2026-09-24T03:30:00.000Z', 'daily', IST, new Date('2026-10-04T10:00:00.000Z')),
    '2026-10-05T03:30:00.000Z',
  );
});

test('local time survives a DST change', () => {
  // 09:00 New York: EDT (13:00Z) on Oct 31 2026, EST (14:00Z) on Nov 1 after clocks fall back
  assert.equal(
    nextOccurrence('2026-10-31T13:00:00.000Z', 'daily', NY, new Date('2026-10-31T13:00:00.000Z')),
    '2026-11-01T14:00:00.000Z',
  );
});

test('isRepeatRule only accepts known rules', () => {
  assert.equal(isRepeatRule('daily'), true);
  assert.equal(isRepeatRule('yearly'), false);
  assert.equal(isRepeatRule(null), false);
});
