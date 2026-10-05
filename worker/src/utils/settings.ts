/** Settings the apps share, keyed in the settings table. */
export const DEFAULT_GROUP_KEY = 'default_group_id';

/**
 * Reads a default group id from a request body: a positive integer, or null to clear it.
 * Anything else is invalid (undefined).
 */
export function parseGroupId(value: unknown): number | null | undefined {
  if (value === null) return null;
  const n = typeof value === 'string' && value.trim() !== '' ? Number(value) : value;
  return typeof n === 'number' && Number.isInteger(n) && n > 0 ? n : undefined;
}
