import { test } from 'vitest';
import assert from 'node:assert/strict';
import { decodeGroupIds, parseGroupId, parseGroupIds } from '../src/utils/settings';

test('a positive integer (or its string) is a group id', () => {
  assert.equal(parseGroupId(4), 4);
  assert.equal(parseGroupId('12'), 12);
});

test('null clears the default group', () => {
  assert.equal(parseGroupId(null), null);
});

test('anything else is invalid', () => {
  for (const v of [undefined, 0, -3, 1.5, '', 'abc', true, {}, []]) {
    assert.equal(parseGroupId(v), undefined, `expected ${JSON.stringify(v)} to be invalid`);
  }
});

test('a list of group ids is read without repeats', () => {
  assert.deepEqual(parseGroupIds([3, '5', 3]), [3, 5]);
  assert.deepEqual(parseGroupIds([]), []);
});

test('anything but a list of group ids is invalid', () => {
  for (const v of [null, undefined, 3, '3', [0], [null], ['x'], [1, -2]]) {
    assert.equal(parseGroupIds(v), undefined, `expected ${JSON.stringify(v)} to be invalid`);
  }
});

test('stored hidden groups decode, and bad values read as none', () => {
  assert.deepEqual(decodeGroupIds('[2,7]'), [2, 7]);
  for (const v of [null, undefined, '', 'nope', '{}', '[0]']) {
    assert.deepEqual(decodeGroupIds(v), [], `expected ${JSON.stringify(v)} to decode as none`);
  }
});
