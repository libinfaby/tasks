import { test } from 'vitest';
import assert from 'node:assert/strict';
import { normalizePriority, PRIORITY_URGENT } from '../src/utils/tasks';

test('high and urgent are stored as urgent', () => {
  assert.equal(normalizePriority(1), PRIORITY_URGENT);
  assert.equal(normalizePriority(2), PRIORITY_URGENT);
  assert.equal(normalizePriority('2'), PRIORITY_URGENT);
});

test('missing or zero priority is normal', () => {
  assert.equal(normalizePriority(0), 0);
  assert.equal(normalizePriority(undefined), 0);
  assert.equal(normalizePriority(null), 0);
});
