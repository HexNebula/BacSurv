/**
 * The two levels a filière can belong to, and which one a session examines.
 *
 * <p>Only the régional is 1BAC; the national and its rattrapage are both 2BAC.
 * The server holds the same rule — a session's type is what decides which
 * filières it may run — and this is the screen's copy of it, so a picker can be
 * filtered before anything is sent.
 */
export const LEVELS = ['BAC1', 'BAC2'] as const

export type Level = (typeof LEVELS)[number]

export function levelOf(operationType: string | undefined): Level | null {
  if (!operationType) return null
  return operationType === 'REGIONAL_1BAC' ? 'BAC1' : 'BAC2'
}
