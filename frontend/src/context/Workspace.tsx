import { createContext, use, useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'

export type Center = { id: number; name: string; teacherCount: number }
export type Session = {
  id: number
  reference: string
  centerId: number
  centerName: string
  type: string
  /**
   * The year whose candidates sit it, stated by the server.
   *
   * <p>Never worked out from the type here. A candidats libres rattrapage is a
   * first-year session held in the middle of the second-year season, and the
   * copy of that rule that used to live in the frontend got exactly that case
   * wrong.
   */
  level: string
}

/**
 * Which centre and which session every screen is talking about.
 *
 * <p>Held in one place on purpose. Each screen used to remember its own choice
 * — the teacher list one centre, the planning grid one session — and nothing
 * kept the two in agreement, so it was possible to read one centre's pool
 * beside another centre's timetable with no hint that they did not belong
 * together.
 *
 * <p>There is only ever one centre. An administrator runs their own
 * establishment and nobody else's, so the centre is a fact about the
 * installation rather than a choice to be offered — later it will come from the
 * account. Sessions are the thing there are several of: 1BAC in June, 2BAC in
 * June, the rattrapage in July.
 */
type Workspace = {
  centerId: number | null
  sessionId: number | null
  /** False only before the centre has been set up for the first time. */
  hasCenter: boolean
  sessions: Session[]
  /** Sessions of this centre — what the session picker offers. */
  sessionsHere: Session[]
  center: Center | undefined
  session: Session | undefined
  isLoading: boolean
  chooseSession: (id: number | null) => void
}

const SESSION_KEY = 'bacsurv-session'

const WorkspaceContext = createContext<Workspace | null>(null)

function stored(key: string): number | null {
  const saved = Number(localStorage.getItem(key))
  return Number.isFinite(saved) && saved > 0 ? saved : null
}

function remember(key: string, value: number | null) {
  if (value === null) localStorage.removeItem(key)
  else localStorage.setItem(key, String(value))
}

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const [sessionId, setSessionId] = useState<number | null>(() => stored(SESSION_KEY))

  const centers = useQuery({
    queryKey: ['centers'],
    queryFn: () => api.get<Center[]>('/centers'),
  })

  const sessions = useQuery({
    queryKey: ['operations'],
    queryFn: () => api.get<Session[]>('/operations'),
  })

  const centerList = useMemo(() => centers.data ?? [], [centers.data])
  const sessionList = useMemo(() => sessions.data ?? [], [sessions.data])

  // the one centre this installation is for
  const center = centerList[0]
  const centerId = center?.id ?? null

  const chooseSession = useCallback(
    (id: number | null) => {
      setSessionId(id)
      remember(SESSION_KEY, id)
    },
    [],
  )

  useEffect(() => {
    if (!sessions.isSuccess || centerId === null) return
    const here = sessionList.filter((session) => session.centerId === centerId)
    const known = here.some((session) => session.id === sessionId)
    if (!known) {
      const first = here[0]?.id ?? null
      setSessionId(first)
      remember(SESSION_KEY, first)
    }
  }, [sessions.isSuccess, sessionList, centerId, sessionId])

  const value = useMemo<Workspace>(() => {
    const sessionsHere = sessionList.filter((session) => session.centerId === centerId)
    return {
      centerId,
      sessionId,
      hasCenter: center !== undefined,
      sessions: sessionList,
      sessionsHere,
      center,
      session: sessionsHere.find((session) => session.id === sessionId),
      isLoading: centers.isPending || sessions.isPending,
      chooseSession,
    }
  }, [centerId, sessionId, center, sessionList, centers.isPending, sessions.isPending, chooseSession])

  return <WorkspaceContext value={value}>{children}</WorkspaceContext>
}

export function useWorkspace(): Workspace {
  const workspace = use(WorkspaceContext)
  if (workspace === null) throw new Error('useWorkspace outside WorkspaceProvider')
  return workspace
}
