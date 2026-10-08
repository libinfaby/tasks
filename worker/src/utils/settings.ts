/** Settings the apps share, keyed in the settings table. */
export const DEFAULT_GROUP_KEY = 'default_group_id';
/** Groups whose tasks stay out of All tasks, Today and Upcoming (a JSON array of ids). */
export const HIDDEN_GROUPS_KEY = 'hidden_group_ids';

/**
 * Reads a default group id from a request body: a positive integer, or null to clear it.
 * Anything else is invalid (undefined).
 */
export function parseGroupId(value: unknown): number | null | undefined {
  if (value === null) return null;
  const n = typeof value === 'string' && value.trim() !== '' ? Number(value) : value;
  return typeof n === 'number' && Number.isInteger(n) && n > 0 ? n : undefined;
}

/** Reads a list of group ids from a request body, without repeats. Anything but an array of ids is invalid (undefined). */
export function parseGroupIds(value: unknown): number[] | undefined {
  if (!Array.isArray(value)) return undefined;
  const ids = value.map(parseGroupId);
  if (ids.some(id => id == null)) return undefined;
  return [...new Set(ids as number[])];
}

/** The stored hidden group ids; a missing or malformed value reads as none. */
export function decodeGroupIds(stored: string | null | undefined): number[] {
  if (!stored) return [];
  try {
    return parseGroupIds(JSON.parse(stored)) ?? [];
  } catch {
    return [];
  }
}
