import { useEffect, useMemo, useState, type CSSProperties } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import {
  CalendarDays,
  Copy,
  LayoutGrid,
  Lock,
  Pencil,
  Plus,
  SlidersHorizontal,
  Trash2,
  TriangleAlert,
} from 'lucide-react'
import { Time, parseTime } from '@internationalized/date'
import { api } from '../lib/api'
import { useWorkspace } from '../context/Workspace'
import { useSessionState } from '../lib/session'
import { SettleSession } from '../components/SettleSession'
import { useApiMutation } from '../lib/mutation'
import { Page } from '../components/Page'
import { StreamForm, type Stream } from '../components/StreamForm'
import { Rules } from '../components/Rules'
import {
  Button,
  Card,
  CardHead,
  CardRule,
  ComboBox,
  Dialog,
  Empty,
  Failed,
  Notice,
  SegmentedTabs,
  Select,
  Skeleton,
  TimeField,
} from '../ui'

type Exam = {
  id: number
  streamId: number | null
  subject: string
  date: string
  startTime: string
  endTime: string
  roomCount: number
}
type Timetable = {
  operationId: number
  reference: string
  centerId: number
  centerName: string
  days: string[]
  streams: Stream[]
  exams: Exam[]
}
/** An entry of the centre's subject list, with what already depends on it. */
type SubjectOption = { id: number; name: string; usedByTeachers: number; usedByExams: number }

/**
 * Which half of the day an épreuve falls in.
 *
 * <p>A centre runs a morning séance and an afternoon one, and a filière can sit
 * a paper in each on the same day. Noon is the divider — the hours themselves
 * stay free, because a three-hour paper starting at 15:00 and a two-hour one
 * starting at 14:30 are both the afternoon séance.
 */
type Half = 'morning' | 'afternoon'

const HALVES: Half[] = ['morning', 'afternoon']

function halfOf(startTime: string): Half {
  return Number(startTime.slice(0, 2)) < 12 ? 'morning' : 'afternoon'
}

/** What an empty cell offers, by the séance it sits in. */
const DEFAULT_HOURS: Record<Half, [Time, Time]> = {
  morning: [new Time(8, 0), new Time(11, 0)],
  afternoon: [new Time(15, 0), new Time(17, 0)],
}

/** "08:00:00" from the server, "08:00" to it. */
function readTime(value: string): Time {
  return parseTime(value.length > 5 ? value.slice(0, 5) : value)
}

function writeTime(value: Time): string {
  return `${String(value.hour).padStart(2, '0')}:${String(value.minute).padStart(2, '0')}`
}

function shortTime(value: string): string {
  return value.slice(0, 5)
}

/** One cell of the grid: a filière's subject and hours on one day. */
function ExamForm({
  sessionId,
  stream,
  day,
  half,
  subjects,
  existing,
  open,
  onClose,
}: {
  sessionId: number
  stream: Stream | null
  day: string | null
  half: Half
  subjects: SubjectOption[]
  existing?: Exam
  open: boolean
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [subject, setSubject] = useState('')
  const [start, setStart] = useState<Time | null>(null)
  const [end, setEnd] = useState<Time | null>(null)

  useEffect(() => {
    if (!open) return
    setSubject(existing?.subject ?? '')
    // an empty cell opens on the hours of the séance it belongs to
    const [from, to] = DEFAULT_HOURS[half]
    setStart(existing ? readTime(existing.startTime) : from)
    setEnd(existing ? readTime(existing.endTime) : to)
  }, [open, existing, half])

  const save = useApiMutation({
    run: () =>
      api.post<Timetable>(`/sessions/${sessionId}/timetable/exams`, {
        streamId: stream?.id,
        subject: subject.trim(),
        date: day,
        startTime: start ? writeTime(start) : null,
        endTime: end ? writeTime(end) : null,
      }),
    invalidate: ['timetable', sessionId],
    onDone: () => {
      onClose()
    },
  })

  const remove = useApiMutation({
    run: () => api.del<Timetable>(`/sessions/${sessionId}/timetable/exams/${existing?.id}`),
    invalidate: ['timetable', sessionId],
    onDone: () => {
      onClose()
      return t('schedule.examRemoved')
    },
  })

  const nobodyTeaches =
    subject.trim() !== '' &&
    (subjects.find((one) => one.name === subject.trim())?.usedByTeachers ?? 0) === 0

  return (
    <Dialog
      isOpen={open}
      onClose={onClose}
      title={stream?.name ?? t('schedule.exam')}
      subtitle={t(`schedule.${half}`)}
      footer={
        <>
          {existing && (
            <Button
              variant="quiet"
              isPending={remove.isPending}
              onPress={() => remove.mutate(undefined)}
              className="me-auto hover:text-[var(--color-alarm)]"
            >
              <Trash2 size={15} aria-hidden />
              {t('app.delete')}
            </Button>
          )}
          <Button variant="secondary" onPress={onClose}>
            {t('app.cancel')}
          </Button>
          <Button type="submit" form="exam-form" isPending={save.isPending}>
            {t('app.save')}
          </Button>
        </>
      }
    >
      <form
        id="exam-form"
        className="space-y-4 pb-4"
        onSubmit={(event) => {
          event.preventDefault()
          save.mutate(undefined)
        }}
      >
        {/*
          Suggested from the centre's own list, because the solver matches a
          teacher's subject to an épreuve's by exact string. "Maths" typed where
          the list says "Mathématiques" does not fail loudly — it quietly stops a
          maths teacher from being barred from the maths paper. Free text is
          still allowed: a centre may examine a subject nobody there teaches.
        */}
        <ComboBox
          label={t('schedule.subject')}
          value={subject}
          onChange={setSubject}
          placeholder={t('schedule.subjectHint')}
          autoFocus
          suggestions={subjects.map((option) => ({
            id: option.name,
            label: option.name,
            hint: option.usedByTeachers,
          }))}
        />

        {nobodyTeaches && (
          <Notice tone="warn" icon={<TriangleAlert size={16} aria-hidden />}>
            {t('schedule.unknownSubject')}
          </Notice>
        )}

        <div className="grid grid-cols-2 gap-3">
          <TimeField label={t('schedule.from')} value={start} onChange={setStart} />
          <TimeField label={t('schedule.to')} value={end} onChange={setEnd} />
        </div>
      </form>
    </Dialog>
  )
}

/** Copying one filière's whole timetable onto another. */
function CopyStream({
  sessionId,
  target,
  streams,
  exams,
  open,
  onClose,
}: {
  sessionId: number
  target: Stream | null
  streams: Stream[]
  exams: Exam[]
  open: boolean
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [from, setFrom] = useState<number | null>(null)

  useEffect(() => {
    if (open) setFrom(null)
  }, [open])

  const copy = useApiMutation({
    run: () =>
      api.post<Timetable>(`/sessions/${sessionId}/timetable/copy`, {
        fromStreamId: from,
        toStreamId: target?.id,
      }),
    invalidate: ['timetable', sessionId],
    onDone: () => {
      onClose()
      return t('schedule.copied', { name: target?.name ?? '' })
    },
  })

  const sources = streams.filter((stream) => stream.id !== target?.id)

  return (
    <Dialog
      isOpen={open}
      onClose={onClose}
      title={t('schedule.copyInto', { name: target?.name ?? '' })}
      subtitle={t('schedule.copyExplain', { name: target?.name ?? '' })}
      footer={
        <>
          <Button variant="secondary" onPress={onClose}>
            {t('app.cancel')}
          </Button>
          <Button
            isDisabled={from === null}
            isPending={copy.isPending}
            onPress={() => copy.mutate(undefined)}
          >
            <Copy size={16} aria-hidden />
            {t('schedule.copy')}
          </Button>
        </>
      }
    >
      <div className="pb-4">
        <Select
          label={t('schedule.copyFrom')}
          value={from}
          onChange={(key) => setFrom(Number(key))}
          choices={sources.map((stream) => ({
            id: stream.id,
            label: stream.name,
            hint: t('schedule.examCount', {
              count: exams.filter((exam) => exam.streamId === stream.id).length,
            }),
          }))}
        />
      </div>
    </Dialog>
  )
}

/**
 * The timetable: which filière sits what, when.
 *
 * <p>One column per séance rather than per day. A filière can sit two papers on
 * the same day — one in the morning, one in the afternoon — and an earlier
 * version of this screen showed only the first, so an épreuve could sit
 * perfectly well in the database and be invisible here.
 */
export function SchedulePage() {
  const { t, i18n } = useTranslation()
  const { sessionId, centerId, sessionsHere, isLoading } = useWorkspace()
  const { impact, isSettled } = useSessionState(sessionId)
  const [streamForm, setStreamForm] = useState<{ open: boolean; stream?: Stream }>({ open: false })
  const [examForm, setExamForm] = useState<{
    open: boolean
    stream: Stream | null
    day: string | null
    half: Half
    exam?: Exam
  }>({ open: false, stream: null, day: null, half: 'morning' })
  const [copyFor, setCopyFor] = useState<Stream | null>(null)

  // which tab is showing lives in the address, so Les salles can send somebody
  // straight to the rule that decides its figure
  const [searchParams, setSearchParams] = useSearchParams()
  const view = searchParams.get('tab') === 'rules' ? 'rules' : 'exams'

  const grid = useQuery({
    queryKey: ['timetable', sessionId],
    queryFn: () => api.get<Timetable>(`/sessions/${sessionId}/timetable`),
    enabled: sessionId !== null,
  })

  /**
   * The centre's own list of subjects — not the teacher pool. A centre may
   * examine a paper nobody there teaches, so the two are different lists, and
   * only this one is the catalogue.
   */
  const catalogue = useQuery({
    queryKey: ['subjects', grid.data?.centerId],
    queryFn: () => api.get<SubjectOption[]>(`/centers/${grid.data?.centerId}/subjects`),
    enabled: grid.data?.centerId !== undefined,
  })

  const subjects = catalogue.data ?? []

  /** With nobody to teach it, a permanence for that subject has no specialist. */
  const untaught = useMemo(
    () => new Set(subjects.filter((one) => one.usedByTeachers === 0).map((one) => one.name)),
    [subjects],
  )
  const listed = useMemo(() => new Set(subjects.map((one) => one.name)), [subjects])

  const dayNames = useMemo(
    () =>
      new Intl.DateTimeFormat(i18n.language, { weekday: 'short', day: 'numeric', month: 'short' }),
    [i18n.language],
  )

  /**
   * Every épreuve a filière sits in one séance — not the first one found. A
   * filière with a paper in the morning and another in the afternoon has two,
   * and showing only one is how an épreuve goes missing from the screen while
   * sitting perfectly well in the database.
   */
  const cell = (streamId: number, day: string, half: Half) =>
    (grid.data?.exams ?? [])
      .filter((exam) => exam.streamId === streamId && exam.date === day)
      .filter((exam) => halfOf(exam.startTime) === half)
      .sort((a, b) => a.startTime.localeCompare(b.startTime))

  if (!isLoading && sessionsHere.length === 0) {
    return (
      <Page title={t('schedule.title')}>
        <Card>
          <Empty icon={<CalendarDays size={22} aria-hidden />}>{t('schedule.noSession')}</Empty>
        </Card>
      </Page>
    )
  }

  const data = grid.data
  const roomless = (data?.streams ?? []).filter((stream) => stream.rooms.length === 0)

  return (
    <Page
      title={t('schedule.title')}
      subtitle={data ? data.centerName : t('schedule.subtitle')}
      actions={
        view === 'exams' &&
        sessionId !== null &&
        !isSettled &&
        data && (
          <Button onPress={() => setStreamForm({ open: true })}>
            <Plus size={16} aria-hidden />
            {t('schedule.addStream')}
          </Button>
        )
      }
      tabs={
        sessionId !== null && (
          <SegmentedTabs
            value={view}
            onChange={(id) => setSearchParams(id === 'rules' ? { tab: 'rules' } : {})}
            tabs={[
              {
                id: 'exams',
                label: t('schedule.grid'),
                icon: <LayoutGrid size={15} aria-hidden />,
                count: data?.exams.length,
              },
              {
                id: 'rules',
                label: t('rules.title'),
                icon: <SlidersHorizontal size={15} aria-hidden />,
              },
            ]}
          />
        )
      }
    >
      {/* the rules of this session live behind the second tab rather than in a
          section of their own: they are set once while the session is being
          built, on the screen where it is built */}
      {view === 'rules' && sessionId !== null && <Rules sessionId={sessionId} />}

      {view === 'exams' && (
        <>
      {/*
        Said once, at the top, so that nothing below has to explain why it will
        not open. This planning is the one the convocations were printed from —
        the server refuses to move it, and being told that by an error after
        typing is worse than being told before.
      */}
      {isSettled && sessionId !== null && (
        <div className="rise mb-5">
          <Notice
            tone="good"
            icon={<Lock size={16} aria-hidden />}
            /* reopening is the answer to this refusal, so it is offered here
               rather than described and left on another screen */
            action={
              <SettleSession
                sessionId={sessionId}
                centerId={centerId}
                impact={impact}
                variant="compact"
              />
            }
          >
            {t('lifecycle.planningLocked')}
          </Notice>
        </div>
      )}

      {/* a filière with no rooms can hold no épreuve at all, which is worth
          saying once above the grid rather than only inside its own row */}
      {!isSettled && roomless.length > 0 && (
        <div className="rise mb-5">
          <Notice
            tone="warn"
            icon={<TriangleAlert size={16} aria-hidden />}
            action={
              <Button size="sm" variant="secondary" onPress={() => setStreamForm({ open: true, stream: roomless[0] })}>
                {t('schedule.editStream')}
              </Button>
            }
          >
            {t('schedule.roomlessStreams', {
              count: roomless.length,
              names: roomless.map((stream) => stream.name).join(', '),
            })}
          </Notice>
        </div>
      )}

      {/* the sheet arrives first, then the papers land on it — see .rise */}
      <Card className="rise [--i:1]">
        <CardHead title={t('schedule.grid')} count={data?.exams.length} />
        <CardRule />

        {grid.isPending && sessionId !== null && <Skeleton rows={4} />}
        {grid.isError && (
          <Failed error={grid.error as Error} onRetry={() => void grid.refetch()} />
        )}

        {data &&
          (data.streams.length === 0 ? (
            <Empty
              icon={<LayoutGrid size={22} aria-hidden />}
              action={
                <Button onPress={() => setStreamForm({ open: true })}>
                  <Plus size={16} aria-hidden />
                  {t('schedule.addStream')}
                </Button>
              }
            >
              {t('schedule.noStream')}
            </Empty>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] table-fixed border-collapse">
                <thead>
                  {/* two rows: the day, then the séance inside it */}
                  <tr>
                    <th
                      rowSpan={2}
                      className="w-[240px] px-5 pb-2.5 pt-1 text-start align-bottom text-[11.5px] font-medium text-[var(--color-faint)]"
                    >
                      {t('schedule.stream')}
                    </th>
                    {/* narrowed: a long French date sits over two séance
                        columns without widening the whole grid */}
                    {data.days.map((day) => (
                      <th
                        key={day}
                        colSpan={2}
                        className="narrow border-s border-[var(--color-hairline)] px-3 pb-1 pt-1 text-center text-[13px] font-semibold"
                      >
                        {dayNames.format(new Date(`${day}T00:00:00`))}
                      </th>
                    ))}
                  </tr>
                  <tr>
                    {data.days.flatMap((day) =>
                      HALVES.map((half) => (
                        <th
                          key={`${day}-${half}`}
                          className={`narrow px-2 pb-2.5 text-start text-[10.5px] font-medium uppercase tracking-[0.07em] text-[var(--color-faint)] ${
                            half === 'morning' ? 'border-s border-[var(--color-hairline)]' : ''
                          }`}
                        >
                          {t(`schedule.${half}`)}
                        </th>
                      )),
                    )}
                  </tr>
                </thead>
                <tbody>
                  {data.streams.map((stream, streamIndex) => (
                    <tr
                      key={stream.id}
                      className="group border-t border-[var(--color-hairline)] align-top"
                    >
                      <td className="px-5 py-3">
                        <div className="flex items-start justify-between gap-2">
                          <div className="min-w-0">
                            <div className="text-[14px] font-medium leading-tight">
                              {stream.name}
                            </div>
                            {/* the count, not the list: "Salle 2, Salle 3, Salle
                                4, Salle 5" costs more width than the filière's
                                own name. The full list is on hover. */}
                            <div
                              className="mt-1 text-[11.5px] text-[var(--color-quiet)]"
                              title={stream.rooms.map((room) => room.label).join(', ')}
                            >
                              {stream.rooms.length === 0 ? (
                                <span className="font-medium text-[var(--color-warn)]">
                                  {t('schedule.noRoomsYet')}
                                </span>
                              ) : (
                                <>
                                  <span className="numeric">{stream.rooms.length}</span>{' '}
                                  {t('schedule.roomCount', { count: stream.rooms.length })}
                                </>
                              )}
                            </div>
                          </div>
                          <div className="no-print flex shrink-0 items-center gap-0.5">
                            <Button
                              size="sm"
                              variant="quiet"
                              isIcon
                              aria-label={t('schedule.copyFrom')}
                              isDisabled={isSettled}
                              onPress={() => setCopyFor(stream)}
                            >
                              <Copy size={14} aria-hidden />
                            </Button>
                            <Button
                              size="sm"
                              variant="quiet"
                              isIcon
                              aria-label={t('schedule.editStream')}
                              isDisabled={isSettled}
                              onPress={() => setStreamForm({ open: true, stream })}
                            >
                              <Pencil size={14} aria-hidden />
                            </Button>
                          </div>
                        </div>
                      </td>

                      {data.days.flatMap((day, dayIndex) =>
                        HALVES.map((half) => {
                          const exams = cell(stream.id, day, half)
                          return (
                            <td
                              key={`${day}-${half}`}
                              className={`space-y-1.5 p-2 ${
                                half === 'morning' ? 'border-s border-[var(--color-hairline)]' : ''
                              }`}
                            >
                              {exams.map((exam) => {
                                const unstaffable =
                                  catalogue.isSuccess &&
                                  (!listed.has(exam.subject) || untaught.has(exam.subject))
                                return (
                                  <button
                                    key={exam.id}
                                    type="button"
                                    disabled={isSettled}
                                    onClick={() =>
                                      setExamForm({ open: true, stream, day, half, exam })
                                    }
                                    /*
                                     * The papers land in a wave down and across
                                     * the week — the order the grid is read in,
                                     * not the order React happens to map in.
                                     * Capped at the eighth step so a centre with
                                     * twelve filières over a fortnight is not
                                     * still assembling itself a second later.
                                     */
                                    style={
                                      {
                                        '--i': Math.min(streamIndex + dayIndex, 8) + 2,
                                      } as CSSProperties
                                    }
                                    className={`rise w-full rounded-[4px] border-s-[3px] px-2.5 py-2 text-start transition-[background-color,transform] duration-[var(--duration-quick)] hover:-translate-y-px active:translate-y-0 ${
                                      unstaffable
                                        ? 'border-s-[var(--color-warn)] bg-[var(--color-warn-tint)] hover:brightness-[0.98]'
                                        : 'border-s-[var(--color-accent)] bg-[var(--color-accent-tint)]/70 hover:bg-[var(--color-accent-tint)]'
                                    }`}
                                  >
                                    <span className="flex items-start gap-1.5 text-[13px] font-semibold leading-tight">
                                      {/* nobody teaches it: the permanence has no
                                          specialist and the own-subject rule
                                          cannot bite */}
                                      {unstaffable && (
                                        <TriangleAlert
                                          size={13}
                                          className="mt-px shrink-0 text-[var(--color-warn)]"
                                          aria-label={t('schedule.unknownSubject')}
                                        />
                                      )}
                                      <span className="min-w-0">{exam.subject}</span>
                                    </span>
                                    {/* a range is read left to right in both
                                        languages: left to the page's own
                                        direction, "08:00 — 10:00" comes out
                                        reversed on an Arabic page */}
                                    <bdi
                                      dir="ltr"
                                      className="numeric mt-1 block text-[11.5px] text-[var(--color-quiet)]"
                                    >
                                      {shortTime(exam.startTime)} — {shortTime(exam.endTime)}
                                    </bdi>
                                  </button>
                                )
                              })}

                              {/* an empty séance stays reachable, and a filled
                                  one can still take a second paper */}
                              <button
                                type="button"
                                hidden={isSettled}
                                onClick={() =>
                                  setExamForm({ open: true, stream, day, half, exam: undefined })
                                }
                                className={`no-print w-full items-center gap-1.5 rounded-[4px] px-2 text-[11.5px] text-[var(--color-faint)] transition-colors hover:text-[var(--color-accent)] ${
                                  exams.length === 0
                                    ? 'flex justify-center py-3 ring-1 ring-dashed ring-[var(--color-hairline)] hover:bg-[var(--color-sunken)] hover:ring-[var(--color-accent)]/40'
                                    : 'hidden py-1 group-focus-within:flex group-hover:flex'
                                }`}
                              >
                                <Plus size={13} aria-hidden />
                                {exams.length === 0 ? t('schedule.addExam') : t('schedule.addMore')}
                              </button>
                            </td>
                          )
                        }),
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ))}

        {data && data.streams.length > 0 && (
          <div className="rounded-b-[var(--radius-card)] border-t border-[var(--color-hairline)] bg-[var(--color-sunken)] px-5 py-3">
            <p className="text-[11.5px] text-[var(--color-faint)]">{t('schedule.hint')}</p>
          </div>
        )}
      </Card>
        </>
      )}

      {sessionId !== null && data && (
        <>
          <StreamForm
            sessionId={sessionId}
            centerId={data.centerId}
            existing={streamForm.stream}
            open={streamForm.open}
            onClose={() => setStreamForm({ open: false })}
          />
          <ExamForm
            sessionId={sessionId}
            stream={examForm.stream}
            day={examForm.day}
            half={examForm.half}
            subjects={subjects}
            existing={examForm.exam}
            open={examForm.open}
            onClose={() => setExamForm({ open: false, stream: null, day: null, half: 'morning' })}
          />
          <CopyStream
            sessionId={sessionId}
            target={copyFor}
            exams={data.exams}
            streams={data.streams}
            open={copyFor !== null}
            onClose={() => setCopyFor(null)}
          />
        </>
      )}
    </Page>
  )
}
