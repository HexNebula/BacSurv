import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  CalendarDays,
  Check,
  CircleSlash,
  ListChecks,
  Lock,
  Pencil,
  Play,
  RotateCw,
  TriangleAlert,
  Users,
} from 'lucide-react'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { useWorkspace } from '../context/Workspace'
import { useSessionState } from '../lib/session'
import { SettleSession } from '../components/SettleSession'
import { Page } from '../components/Page'
import { ChangeDuty } from '../components/ChangeDuty'
import {
  Badge,
  LoadBar,
  Button,
  Card,
  CardHead,
  CardRule,
  Empty,
  Failed,
  Notice,
  SegmentedTabs,
  SearchField,
  Skeleton,
  Table,
  Td,
  Th,
  Tr,
} from '../ui'

type Job = {
  id: number
  operationId: number
  operationName: string
  status: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'
  timeLimitSeconds: number
  feasible: boolean | null
  hardViolations: number | null
  unfilled: number | null
  error: string | null
  finishedAt: string | null
  /** The session moved after this was solved: what is shown is out of date. */
  stale: boolean
}

type Assignment = {
  dutyId: string
  slotId: string
  date: string
  start: string
  end: string
  role: 'SURVEILLANCE' | 'RESERVE' | 'PERMANENCE'
  examId: string | null
  subject: string | null
  stream: string | null
  roomId: string | null
  teacherMatricule: string | null
  teacherName: string | null
}

type Workload = {
  matricule: string
  name: string
  subject: string
  surveillance: number
  reserve: number
  permanence: number
  priorTotal: number
  total: number
}

type Schedule = {
  feasible: boolean
  hardViolations: number
  unfilled: number
  hardViolationDetails: string[]
  assignments: Assignment[]
  workload: Workload[]
}

const ROLE_TONE = {
  SURVEILLANCE: 'accent',
  RESERVE: 'plain',
  PERMANENCE: 'good',
} as const

/**
 * R1, R2, … R13 — not R1, R10, R11, R2.
 *
 * <p>Text order puts the tenth room second in a centre of thirteen, and nobody
 * reads a door list that way. The rooms without a reference at all — réserve and
 * permanence, which belong to the day rather than to a door — go last.
 */
function byRoom(a: string, b: string): number {
  const roomless = (key: string) => key.startsWith('__')
  if (roomless(a) !== roomless(b)) return roomless(a) ? 1 : -1
  const digits = (key: string) => Number(key.replace(/\D/g, '')) || 0
  const letters = (key: string) => key.replace(/\d/g, '')
  return letters(a).localeCompare(letters(b)) || digits(a) - digits(b) || a.localeCompare(b)
}

/** "08:00:00" from the server, "08:00" on a screen. */
function hhmm(value: string): string {
  return value.slice(0, 5)
}

/**
 * The distribution: who surveys what, and what nobody is covering.
 *
 * <p>Everything here comes from one solved job. It is shown even when the
 * solver could not fill every duty — a partial distribution with three duties
 * marked as nobody's is exactly the information an administrator needs, and
 * hiding it until it is perfect would leave them with a screen that says only
 * "impossible".
 *
 * <p>Three readings of the same rows, because the paperwork wants three: the
 * sheet pinned to a room's door, the convocation handed to a teacher, and the
 * list of holes to go and fix.
 */
export function ResultsPage() {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const { sessionId, centerId, sessionsHere, isLoading } = useWorkspace()
  const { impact, isSettled } = useSessionState(sessionId)
  const [view, setView] = useState('day')
  const [search, setSearch] = useState('')
  // the duty being reassigned by hand, if any
  const [editing, setEditing] = useState<string | null>(null)

  /** For the room labels: the schedule names rooms by reference, not by label. */
  const center = useQuery({
    queryKey: ['center', centerId],
    queryFn: () => api.get<{ rooms: { reference: string; label: string }[] }>(
      `/centers/${centerId}`,
    ),
    enabled: centerId !== null,
  })

  const jobs = useQuery({
    queryKey: ['jobs'],
    queryFn: () => api.get<Job[]>('/jobs'),
    // while one is running the answer changes on its own
    refetchInterval: (query) =>
      (query.state.data ?? []).some(
        (job) => job.status === 'PENDING' || job.status === 'RUNNING',
      )
        ? 1500
        : false,
  })

  /** The session's own latest attempt; other sessions' jobs are not this screen. */
  const job = useMemo(
    () => (jobs.data ?? []).find((one) => one.operationId === sessionId),
    [jobs.data, sessionId],
  )

  const running = job?.status === 'PENDING' || job?.status === 'RUNNING'

  const schedule = useQuery({
    queryKey: ['schedule', job?.id],
    queryFn: () => api.get<Schedule>(`/jobs/${job?.id}/schedule`),
    enabled: job !== undefined && job.status === 'DONE',
  })

  const solve = useApiMutation({
    run: () => api.post<Job>(`/operations/${sessionId}/solve`),
    invalidate: ['jobs'],
    onDone: () => {
      void queryClient.invalidateQueries({ queryKey: ['readiness', sessionId] })
    },
  })

  const rows = schedule.data?.assignments ?? []

  /**
   * The label of each room, by the reference the schedule names it with.
   *
   * <p>A duty carries `roomId` — `R1`, the centre's internal reference — while
   * the sheet pinned to a door has to say « Salle 1 », which is what the
   * administrator typed and what is written beside the room itself. The centre
   * is already in cache, so this costs nothing; a room removed since the solve
   * keeps its reference rather than disappearing.
   */
  const roomLabel = useMemo(() => {
    const labels = new Map((center.data?.rooms ?? []).map((room) => [room.reference, room.label]))
    return (reference: string | null) =>
      reference === null ? null : (labels.get(reference) ?? reference)
  }, [center.data])

  /** Grouped by day, then by room: the sheet that goes on a door. */
  const byDay = useMemo(() => {
    const days = new Map<string, Map<string, Assignment[]>>()
    for (const row of rows) {
      const rooms = days.get(row.date) ?? new Map<string, Assignment[]>()
      // réserve and permanence have no room; they belong to the day, not a door
      const key = row.roomId ?? `__${row.role}`
      rooms.set(key, [...(rooms.get(key) ?? []), row])
      days.set(row.date, rooms)
    }
    return [...days.entries()].sort(([a], [b]) => a.localeCompare(b))
  }, [rows])

  const unfilled = rows.filter((row) => row.teacherMatricule === null)

  /**
   * The workload read as a distribution rather than as a list.
   *
   * <p>Heaviest first, because the person worth looking at should be the first
   * row and not the thirty-first. The comparison is on surveillances alone:
   * that is the load a teacher feels, while réserve and permanence are hours
   * spent waiting and are counted in their own columns.
   *
   * <p>Only the teachers at the two ends carry their distance from the average.
   * Marking every row would be marking none of them, and the sentence above the
   * table already says what the range is.
   */
  const load = useMemo(() => {
    const all = schedule.data?.workload ?? []
    if (all.length === 0) return { rows: [], heaviest: 0, spread: null }

    const counts = all.map((row) => row.surveillance)
    const least = Math.min(...counts)
    const heaviest = Math.max(...counts)
    const average = counts.reduce((sum, one) => sum + one, 0) / counts.length

    const needle = search.trim().toLowerCase()
    // the extremes only, and only when they are actually away from the middle:
    // a "+0" badge is a mark that says nothing
    const distance = (count: number) => {
      if (heaviest === least) return null
      if (count !== heaviest && count !== least) return null
      const gap = Math.round(count - average)
      return gap === 0 ? null : gap
    }

    const rows = all
      .map((row) => ({ ...row, gap: distance(row.surveillance) }))
      .filter((row) =>
        needle === ''
          ? true
          : `${row.name} ${row.matricule} ${row.subject}`.toLowerCase().includes(needle),
      )
      .sort((a, b) => b.surveillance - a.surveillance || a.name.localeCompare(b.name))

    return {
      rows,
      heaviest,
      spread: { least, most: heaviest, average: average.toFixed(1).replace('.0', '') },
    }
  }, [schedule.data, search])

  const dayNames = useMemo(
    () => new Intl.DateTimeFormat(i18n.language, { weekday: 'long', day: 'numeric', month: 'long' }),
    [i18n.language],
  )

  if (!isLoading && sessionsHere.length === 0) {
    return (
      <Page title={t('results.title')}>
        <Card>
          <Empty icon={<ListChecks size={22} aria-hidden />}>{t('schedule.noSession')}</Empty>
        </Card>
      </Page>
    )
  }

  return (
    <Page
      title={t('results.title')}
      subtitle={t('results.subtitle')}
      tabs={
        schedule.data && (
          <SegmentedTabs
            value={view}
            onChange={setView}
            tabs={[
              {
                id: 'day',
                label: t('results.byDay'),
                icon: <CalendarDays size={15} aria-hidden />,
                count: byDay.length,
              },
              {
                id: 'teacher',
                label: t('results.byTeacher'),
                icon: <Users size={15} aria-hidden />,
                count: schedule.data.workload.length,
              },
              {
                id: 'unfilled',
                label: t('results.unfilled'),
                icon: <CircleSlash size={15} aria-hidden />,
                count: unfilled.length,
                flag: unfilled.length > 0,
              },
            ]}
          />
        )
      }
      actions={
        sessionId !== null && (
          <>
            {/* the act the whole model turns on, beside the one that produced
                what it settles */}
            {(isSettled || (job?.status === 'DONE' && !job.stale)) && centerId !== null && (
              <SettleSession
                sessionId={sessionId}
                centerId={centerId}
                impact={impact}
                onSolve={() => solve.mutate(undefined)}
                isSolving={solve.isPending || running}
              />
            )}
            {!isSettled && (
              <Button
                onPress={() => solve.mutate(undefined)}
                isPending={solve.isPending || running}
                isDisabled={running}
              >
                {job ? <RotateCw size={16} aria-hidden /> : <Play size={16} aria-hidden />}
                {running ? t('results.running') : job ? t('results.runAgain') : t('results.run')}
              </Button>
            )}
          </>
        )
      }
    >
      {/*
        A settled session is not a screen with a warning on it — it is the
        répartition that went out. It says what it is, and the way back is in
        the same line rather than somewhere else.
      */}
      {isSettled && (
        <div className="mb-5">
          <Notice tone="good" icon={<Lock size={16} aria-hidden />}>
            {t('lifecycle.settledResults')}
          </Notice>
        </div>
      )}

      {job?.status === 'FAILED' && (
        <div className="mb-5">
          <Notice tone="alarm" icon={<TriangleAlert size={16} aria-hidden />}>
            {job.error || t('results.failed')}
          </Notice>
        </div>
      )}

      {/*
        A distribution solved before the last change looks exactly like a fresh
        one, which is how somebody hands out a répartition that no longer
        accounts for an absence recorded yesterday. Said above everything else,
        because it decides whether the tables below are worth reading at all.
      */}
      {job?.stale && !running && (
        <div className="mb-5">
          <Notice
            tone="warn"
            icon={<TriangleAlert size={16} aria-hidden />}
            /* a settled session refuses to be re-solved, so offering it here
               would be offering a refusal */
            action={
              !isSettled && (
                <Button
                  size="sm"
                  variant="secondary"
                  isPending={solve.isPending}
                  onPress={() => solve.mutate(undefined)}
                >
                  <RotateCw size={15} aria-hidden />
                  {t('results.relaunch')}
                </Button>
              )
            }
          >
            {isSettled ? t('lifecycle.staleSettled') : t('results.stale')}
          </Notice>
        </div>
      )}

      {/* what the solver made of it, said before the tables rather than left
          to be inferred from a missing name three screens down */}
      {schedule.data && (
        <div className="mb-5 space-y-2">
          {schedule.data.unfilled > 0 && (
            <Notice tone="warn" icon={<TriangleAlert size={16} aria-hidden />}>
              {t('results.unfilledSome', { count: schedule.data.unfilled })} ·{' '}
              {t('results.showPartial')}
            </Notice>
          )}
          {schedule.data.hardViolations > 0 && (
            <Notice tone="alarm" icon={<TriangleAlert size={16} aria-hidden />}>
              {t('results.broken', { count: schedule.data.hardViolations })}
            </Notice>
          )}
          {schedule.data.unfilled === 0 && schedule.data.hardViolations === 0 && (
            <Notice tone="good" icon={<Check size={16} aria-hidden />}>
              {t('results.ok')}
            </Notice>
          )}
        </div>
      )}

      {running && (
        <Card>
          <div className="px-5 py-6">
            <p className="text-[14px] font-medium">{t('results.running')}</p>
            <p className="mt-1 text-[12.5px] text-[var(--color-quiet)]">
              {t('results.runningHint', { seconds: job?.timeLimitSeconds ?? 30 })}
            </p>
          </div>
          <CardRule />
          <Skeleton rows={5} />
        </Card>
      )}

      {!job && !running && (
        <Card>
          <Empty
            icon={<ListChecks size={22} aria-hidden />}
            title={t('results.none')}
            action={
              sessionId !== null && (
                <Button onPress={() => solve.mutate(undefined)} isPending={solve.isPending}>
                  <Play size={16} aria-hidden />
                  {t('results.run')}
                </Button>
              )
            }
          >
            {t('results.noneHint')}
          </Empty>
        </Card>
      )}

      {schedule.isPending && job?.status === 'DONE' && (
        <Card>
          <Skeleton rows={6} />
        </Card>
      )}
      {schedule.isError && (
        <Card>
          <Failed error={schedule.error as Error} onRetry={() => void schedule.refetch()} />
        </Card>
      )}

      {schedule.data && view === 'day' && (
        <div className="space-y-6">
          {byDay.map(([date, rooms]) => (
            <Card key={date}>
              <CardHead
                title={dayNames.format(new Date(`${date}T00:00:00`))}
                count={[...rooms.values()].flat().length}
              />
              <CardRule />
              <Table>
                <thead>
                  <tr>
                    <Th width="150px">{t('results.room')}</Th>
                    <Th width="150px">{t('schedule.subject')}</Th>
                    <Th width="140px">{t('results.role')}</Th>
                    <Th width="140px">{t('teachers.matricule')}</Th>
                    <Th>{t('results.teacher')}</Th>
                    <Th width="150px">{t('schedule.from')}</Th>
                    <Th width="120px" className="no-print" />
                  </tr>
                </thead>
                <tbody>
                  {[...rooms.entries()]
                    .sort(([a], [b]) => byRoom(a, b))
                    .flatMap(([, duties]) =>
                      duties.map((duty) => (
                        <Tr key={duty.dutyId}>
                          <Td className="font-medium">
                            {roomLabel(duty.roomId) ?? (
                              <span className="text-[var(--color-faint)]">—</span>
                            )}
                          </Td>
                          <Td className="text-[var(--color-quiet)]">{duty.subject ?? '—'}</Td>
                          <Td>
                            <Badge tone={ROLE_TONE[duty.role]}>
                              {t(`results.role${duty.role}`)}
                            </Badge>
                          </Td>
                          <Td className="numeric text-[12.5px] text-[var(--color-quiet)]">
                            {duty.teacherMatricule ?? '—'}
                          </Td>
                          <Td className={duty.teacherName ? 'font-medium' : ''}>
                            {duty.teacherName ?? (
                              <Badge tone="warn">{t('results.nobody')}</Badge>
                            )}
                          </Td>
                          {/* a range reads left to right in both languages */}
                          <Td>
                            <bdi dir="ltr" className="numeric text-[12.5px]">
                              {hhmm(duty.start)} — {hhmm(duty.end)}
                            </bdi>
                          </Td>
                          <Td className="no-print text-end">
                            <Button
                              size="sm"
                              variant="quiet"
                              isDisabled={isSettled}
                              onPress={() => setEditing(duty.dutyId)}
                            >
                              <Pencil size={14} aria-hidden />
                              {t('change.edit')}
                            </Button>
                          </Td>
                        </Tr>
                      )),
                    )}
                </tbody>
              </Table>
            </Card>
          ))}
        </div>
      )}

      {schedule.data && view === 'teacher' && (
        <Card>
          <CardHead
            title={t('results.workload')}
            count={load.rows.length}
            actions={
              <SearchField
                label={t('teachers.search')}
                value={search}
                onChange={setSearch}
                placeholder={t('teachers.searchHint')}
                className="w-64"
              />
            }
          />

          {/* the fairness answer in one sentence, so nobody has to derive it
              from a column of 45 figures */}
          {load.spread && (
            <p className="border-t border-[var(--color-hairline)] px-5 py-3 text-[12.5px] text-[var(--color-quiet)]">
              {t('results.spread', load.spread)}
            </p>
          )}

          <CardRule />
          <Table>
            <thead>
              <tr>
                <Th width="140px">{t('teachers.matricule')}</Th>
                <Th>{t('teachers.name')}</Th>
                <Th width="200px">{t('results.surveillanceCount')}</Th>
                <Th width="110px">{t('results.roleRESERVE')}</Th>
                <Th width="130px">{t('results.rolePERMANENCE')}</Th>
                <Th width="140px">{t('results.priorTotal')}</Th>
                <Th width="100px">{t('results.total')}</Th>
              </tr>
            </thead>
            <tbody>
              {load.rows.map((row) => (
                <Tr key={row.matricule}>
                  <Td className="numeric text-[12.5px] text-[var(--color-quiet)]">
                    {row.matricule}
                  </Td>
                  <Td>
                    <div className="flex items-center gap-2.5">
                      <span className="font-medium">{row.name}</span>
                      {/* only the ends of the range are marked: a badge on
                          every row marks nothing */}
                      {row.gap !== null && (
                        <Badge tone={row.gap > 0 ? 'warn' : 'plain'}>
                          {row.gap > 0 ? `+${row.gap}` : row.gap}
                        </Badge>
                      )}
                    </div>
                  </Td>
                  <Td>
                    <LoadBar value={row.surveillance} of={load.heaviest} />
                  </Td>
                  {/*
                    Each duty on its own: réserve is standby, permanence is being
                    the subject's specialist on call, and the administrator reads
                    this to know what one teacher is actually doing.
                    <p>Their sum is not shown. The two share one queue and that is
                    what the fairness rule compares, but the queue is the solver's
                    business — an administrator who is not an IT person should not
                    have to learn the machinery to read a duty roster.
                  */}
                  <Td className="numeric">{row.reserve}</Td>
                  <Td className="numeric">{row.permanence}</Td>
                  <Td className="numeric text-[var(--color-quiet)]">{row.priorTotal}</Td>
                  <Td className="numeric font-semibold">{row.total}</Td>
                </Tr>
              ))}
            </tbody>
          </Table>

          {/* a search that matches nobody says so, rather than showing a table
              with a head and no rows */}
          {load.rows.length === 0 && (
            <Empty icon={<Users size={22} aria-hidden />}>
              {t('teachers.noMatch', { search: search.trim() })}
            </Empty>
          )}

          <div className="rounded-b-[var(--radius-card)] border-t border-[var(--color-hairline)] bg-[var(--color-sunken)] px-5 py-3">
            <p className="text-[11.5px] text-[var(--color-faint)]">{t('results.workloadHint')}</p>
          </div>
        </Card>
      )}

      {schedule.data && view === 'unfilled' && (
        <Card>
          <CardHead title={t('results.unfilled')} count={unfilled.length} />
          <CardRule />
          {unfilled.length === 0 ? (
            <Empty icon={<Check size={22} aria-hidden />}>{t('results.ok')}</Empty>
          ) : (
            <Table>
              <thead>
                <tr>
                  <Th width="200px">{t('sessions.startsOn')}</Th>
                  <Th width="150px">{t('results.room')}</Th>
                  <Th width="150px">{t('results.role')}</Th>
                  <Th>{t('schedule.subject')}</Th>
                  <Th width="150px">{t('schedule.stream')}</Th>
                  <Th width="120px" className="no-print" />
                </tr>
              </thead>
              <tbody>
                {unfilled.map((duty) => (
                  <Tr key={duty.dutyId}>
                    <Td>
                      <span className="numeric text-[12.5px]">{duty.date}</span>{' '}
                      <bdi dir="ltr" className="numeric text-[12.5px] text-[var(--color-quiet)]">
                        {hhmm(duty.start)} — {hhmm(duty.end)}
                      </bdi>
                    </Td>
                    <Td className="font-medium">{roomLabel(duty.roomId) ?? '—'}</Td>
                    <Td>
                      <Badge tone={ROLE_TONE[duty.role]}>{t(`results.role${duty.role}`)}</Badge>
                    </Td>
                    <Td className="text-[var(--color-quiet)]">{duty.subject ?? '—'}</Td>
                    <Td className="text-[var(--color-quiet)]">{duty.stream ?? '—'}</Td>
                    <Td className="no-print text-end">
                      <Button
                        size="sm"
                        variant="secondary"
                        isDisabled={isSettled}
                        onPress={() => setEditing(duty.dutyId)}
                      >
                        {t('change.apply')}
                      </Button>
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card>
      )}
      {job && (
        <ChangeDuty
          jobId={job.id}
          dutyId={editing}
          open={editing !== null}
          onClose={() => setEditing(null)}
        />
      )}
    </Page>
  )
}
