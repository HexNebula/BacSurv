import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'

/**
 * What a session is worth, and what acting on it would cost.
 *
 * <p>`/operations` — the list the header picker reads — does not carry the
 * state, so a screen that needs to know whether it may still edit asks here.
 * The counts come with it, which is the whole point of the endpoint: a
 * confirmation should name what is at stake rather than say "êtes-vous sûr ?".
 */
export type Impact = {
  sessionId: number
  reference: string
  state: 'DRAFT' | 'SETTLED'
  streamCount: number
  examCount: number
  solveCount: number
  dutyCount: number
  teacherCount: number
  deletable: boolean
}

export function impactKey(sessionId: number | null) {
  return ['impact', sessionId] as const
}

/**
 * The state of one session.
 *
 * <p>Every screen that can write to a session calls this: a settled session's
 * planning is the planning the convocations were printed from, and the server
 * refuses to move it. Finding that out from a refusal is worse than being told
 * before typing, so the screens ask first and say so.
 */
export function useSessionState(sessionId: number | null) {
  const impact = useQuery({
    queryKey: impactKey(sessionId),
    queryFn: () => api.get<Impact>(`/sessions/${sessionId}/impact`),
    enabled: sessionId !== null,
  })

  return {
    impact: impact.data,
    // unknown until it answers: a screen must not flash "locked" at a draft,
    // nor let a settled session look editable for a moment
    isSettled: impact.data?.state === 'SETTLED',
    isKnown: impact.isSuccess,
  }
}

/**
 * Dropping the caches a lifecycle act invalidates.
 *
 * <p>Settling, reopening and deleting all move more than one list: the centre's
 * page holds the sessions, the header picker reads `/operations`, readiness
 * counts the settled step, and the year's own figures change with it. Forgetting
 * one is how a session comes back settled on one screen and a draft on another.
 */
export function useLifecycleRefresh() {
  const queryClient = useQueryClient()

  return (centerId: number | null, sessionId: number | null) => {
    for (const key of [
      ['center', centerId],
      ['operations'],
      ['years', centerId],
      impactKey(sessionId),
      ['readiness', sessionId],
      ['jobs'],
    ]) {
      void queryClient.invalidateQueries({ queryKey: key })
    }
  }
}
