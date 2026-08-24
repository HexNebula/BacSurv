import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  CalendarDays,
  Check,
  CircleSlash,
  ListChecks,
  Play,
  RotateCw,
  TriangleAlert,
  Users,
} from 'lucide-react'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { useWorkspace } from '../context/Workspace'
import { Page } from '../components/Page'
import {
  Badge,
  Button,
  Card,
  CardHead,
  CardRule,
  Empty,
  Failed,
  Notice,
  SegmentedTabs,
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
  const { sessionId, sessionsHere, isLoading } = useWorkspace()
  const [view, setView] = useState('day')

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
          <Button
            onPress={() => solve.mutate(undefined)}
            isPending={solve.isPending || running}
            isDisabled={running}
          >
            {job ? <RotateCw size={16} aria-hidden /> : <Play size={16} aria-hidden />}
            {running ? t('results.running') : job ? t('results.runAgain') : t('results.run')}
          </Button>
        )
      }
    >
      {job?.status === 'FAILED' && (
        <div className="mb-5">
          <Notice tone="alarm" icon={<TriangleAlert size={16} aria-hidden />}>
            {job.error || t('results.failed')}
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
                  </tr>
                </thead>
                <tbody>
                  {[...rooms.entries()]
                    .sort(([a], [b]) => byRoom(a, b))
                    .flatMap(([, duties]) =>
                      duties.map((duty) => (
                        <Tr key={duty.dutyId}>
                          <Td className="font-medium">
                            {duty.roomId ?? (
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
          <CardHead title={t('results.workload')} count={schedule.data.workload.length} />
          <CardRule />
          <Table>
            <thead>
              <tr>
                <Th width="140px">{t('teachers.matricule')}</Th>
                <Th>{t('teachers.name')}</Th>
                <Th width="150px">{t('results.surveillanceCount')}</Th>
                <Th width="180px">{t('results.privileges')}</Th>
                <Th width="150px">{t('results.priorTotal')}</Th>
                <Th width="110px">{t('results.total')}</Th>
              </tr>
            </thead>
            <tbody>
              {schedule.data.workload.map((row) => (
                <Tr key={row.matricule}>
                  <Td className="numeric text-[12.5px] text-[var(--color-quiet)]">
                    {row.matricule}
                  </Td>
                  <Td className="font-medium">{row.name}</Td>
                  <Td className="numeric">{row.surveillance}</Td>
                  {/*
                    réserve and permanence share one queue — nobody gets a second
                    privilege while a colleague has had none — so they are one
                    figure here rather than two columns to add up by eye
                  */}
                  <Td className="numeric">{row.reserve + row.permanence}</Td>
                  <Td className="numeric text-[var(--color-quiet)]">{row.priorTotal}</Td>
                  <Td className="numeric font-semibold">{row.total}</Td>
                </Tr>
              ))}
            </tbody>
          </Table>
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
                    <Td className="font-medium">{duty.roomId ?? '—'}</Td>
                    <Td>
                      <Badge tone={ROLE_TONE[duty.role]}>{t(`results.role${duty.role}`)}</Badge>
                    </Td>
                    <Td className="text-[var(--color-quiet)]">{duty.subject ?? '—'}</Td>
                    <Td className="text-[var(--color-quiet)]">{duty.stream ?? '—'}</Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card>
      )}
    </Page>
  )
}
