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
 * <p>A session belongs to a centre, so the two move together: picking a session
 * moves the centre to its own, and picking a centre drops a session that
 * belongs elsewhere.
 */
type Workspace = {
  centerId: number | null
  sessionId: number | null
  centers: Center[]
  sessions: Session[]
  /** Sessions of the chosen centre — what a session picker should offer. */
  sessionsHere: Session[]
  center: Center | undefined
  session: Session | undefined
  isLoading: boolean
  chooseCenter: (id: number | null) => void
  chooseSession: (id: number | null) => void
}

const CENTER_KEY = 'bacsurv-center'
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
  const [centerId, setCenterId] = useState<number | null>(() => stored(CENTER_KEY))
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

  const chooseCenter = useCallback(
    (id: number | null) => {
      setCenterId(id)
      remember(CENTER_KEY, id)
      // a session of another centre would now be showing beside it
      setSessionId((current) => {
        const kept = sessionList.find((session) => session.id === current)
        const valid = kept !== undefined && kept.centerId === id
        if (!valid) remember(SESSION_KEY, null)
        return valid ? current : null
      })
    },
    [sessionList],
  )

  const chooseSession = useCallback(
    (id: number | null) => {
      setSessionId(id)
      remember(SESSION_KEY, id)
      const session = sessionList.find((candidate) => candidate.id === id)
      if (session) {
        setCenterId(session.centerId)
        remember(CENTER_KEY, session.centerId)
      }
    },
    [sessionList],
  )

  // land on something rather than on an empty chooser, and drop a remembered
  // choice that no longer exists
  useEffect(() => {
    if (!centers.isSuccess) return
    const known = centerList.some((center) => center.id === centerId)
    if (!known) {
      const first = centerList[0]?.id ?? null
      setCenterId(first)
      remember(CENTER_KEY, first)
    }
  }, [centers.isSuccess, centerList, centerId])

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
      centers: centerList,
      sessions: sessionList,
      sessionsHere,
      center: centerList.find((center) => center.id === centerId),
      session: sessionsHere.find((session) => session.id === sessionId),
      isLoading: centers.isPending || sessions.isPending,
      chooseCenter,
      chooseSession,
    }
  }, [
    centerId,
    sessionId,
    centerList,
    sessionList,
    centers.isPending,
    sessions.isPending,
    chooseCenter,
    chooseSession,
  ])

  return <WorkspaceContext value={value}>{children}</WorkspaceContext>
}

export function useWorkspace(): Workspace {
  const workspace = use(WorkspaceContext)
  if (workspace === null) throw new Error('useWorkspace outside WorkspaceProvider')
  return workspace
}
