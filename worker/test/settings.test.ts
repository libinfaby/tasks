import { test } from 'vitest';
import assert from 'node:assert/strict';
import { parseGroupId } from '../src/utils/settings';

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
