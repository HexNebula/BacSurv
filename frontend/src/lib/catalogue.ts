/**
 * The year a filière belongs to.
 *
 * <p>These two values are all that survives of `lib/levels.ts`. What that file
 * also held — working a session's level out from its type — is gone: it was a
 * second copy of a server rule, and it broke the day a fourth type existed,
 * because a candidats libres rattrapage examines first-year papers in the
 * middle of the second-year season. A session's level is read from the session,
 * never derived. These are only the two values a filière can be filed under.
 */
export const LEVELS = ['BAC1', 'BAC2'] as const

export type Level = (typeof LEVELS)[number]
