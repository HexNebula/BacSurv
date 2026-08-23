import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Copy, Pencil, Plus, Trash2, TriangleAlert } from 'lucide-react'
import {
  Button,
  Checkbox,
  ComboBox,
  Input,
  Label,
  ListBox,
  Modal,
  Select,
  TextField,
  TimeField,
} from '@heroui/react'
import { Time, parseTime } from '@internationalized/date'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { Page, Panel, Failed, Loading, Empty } from '../components/Page'

type Operation = { id: number; reference: string; centerName: string; type: string }
type RoomRef = { id: number; reference: string; label: string }
type Stream = { id: number; name: string; rooms: RoomRef[] }
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
type CenterDetail = { id: number; rooms: RoomRef[] }
type Teacher = { matricule: string; subject: string }

/** A subject taught at the centre, and how many people teach it. */
type SubjectOption = { name: string; teachers: number }

const CHOSEN_SESSION = 'bacsurv-session'

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

/**
 * A filière and the rooms it occupies for the whole session.
 *
 * <p>Set once here rather than on every épreuve: a centre gives Lettres salle 1
 * and Sciences physiques salles 6 à 10 for the three days, and restating it per
 * subject is what made entering a timetable an afternoon's work.
 */
function StreamForm({
  sessionId,
  centerId,
  existing,
  open,
  onClose,
}: {
  sessionId: number
  centerId: number
  existing?: Stream
  open: boolean
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [name, setName] = useState('')
  const [chosen, setChosen] = useState<number[]>([])

  const center = useQuery({
    queryKey: ['center', centerId],
    queryFn: () => api.get<CenterDetail>(`/centers/${centerId}`),
    enabled: open,
  })

  useEffect(() => {
    if (!open) return
    setName(existing?.name ?? '')
    setChosen(existing?.rooms.map((room) => room.id) ?? [])
  }, [open, existing])

  const save = useApiMutation({
    run: () => {
      const body = { name: name.trim(), roomIds: chosen }
      return existing
        ? api.post<Timetable>(`/sessions/${sessionId}/timetable/streams/${existing.id}`, body)
        : api.post<Timetable>(`/sessions/${sessionId}/timetable/streams`, body)
    },
    invalidate: ['timetable', sessionId],
    onDone: () => {
      onClose()
      return existing ? t('schedule.streamSaved') : t('schedule.streamAdded')
    },
  })

  const toggle = (roomId: number) =>
    setChosen((current) =>
      current.includes(roomId)
        ? current.filter((id) => id !== roomId)
        : [...current, roomId],
    )

  return (
    <Modal isOpen={open} onOpenChange={(next) => !next && onClose()}>
      <Modal.Backdrop>
        <Modal.Container>
          <Modal.Dialog>
            <Modal.Header>
              <Modal.Heading>
                {existing ? t('schedule.editStream') : t('schedule.addStream')}
              </Modal.Heading>
            </Modal.Header>

            <Modal.Body>
              <form
                id="stream-form"
                className="space-y-4"
                onSubmit={(event) => {
                  event.preventDefault()
                  save.mutate(undefined)
                }}
              >
                <TextField value={name} onChange={setName} fullWidth autoFocus>
                  <Label>{t('schedule.streamName')}</Label>
                  <Input placeholder={t('schedule.streamHint')} />
                </TextField>

                <div>
                  <div className="mb-2 flex items-baseline justify-between">
                    <span className="text-[13px] font-medium">{t('schedule.rooms')}</span>
                    <span className="numeric text-xs text-[var(--color-quiet)]">
                      {chosen.length}
                    </span>
                  </div>
                  {center.isPending && <Loading rows={2} />}
                  {center.isSuccess && center.data.rooms.length === 0 && (
                    <p className="text-[13px] text-[var(--color-quiet)]">
                      {t('schedule.noRooms')}
                    </p>
                  )}
                  {center.isSuccess && center.data.rooms.length > 0 && (
                    <div className="grid max-h-56 grid-cols-2 gap-x-4 gap-y-1 overflow-y-auto rounded-md border border-[var(--color-hairline)] p-3">
                      {center.data.rooms.map((room) => (
                        <Checkbox
                          key={room.id}
                          isSelected={chosen.includes(room.id)}
                          onChange={() => toggle(room.id)}
                        >
                          <span className="text-[13px]">{room.label}</span>
                        </Checkbox>
                      ))}
                    </div>
                  )}
                </div>
              </form>
            </Modal.Body>

            <Modal.Footer>
              <Button variant="ghost" onPress={onClose}>
                {t('app.cancel')}
              </Button>
              <Button type="submit" form="stream-form" isPending={save.isPending}>
                {t('app.save')}
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </Modal>
  )
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

  const suggestions = subjects.filter((option) =>
    option.name.toLowerCase().includes(subject.trim().toLowerCase()),
  )

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

  return (
    <Modal isOpen={open} onOpenChange={(next) => !next && onClose()}>
      <Modal.Backdrop>
        <Modal.Container>
          <Modal.Dialog>
            <Modal.Header>
              <Modal.Heading>{stream?.name ?? t('schedule.exam')}</Modal.Heading>
              <p className="mt-0.5 text-[13px] text-[var(--color-quiet)]">
                {t(`schedule.${half}`)}
              </p>
            </Modal.Header>

            <Modal.Body>
              <form
                id="exam-form"
                className="space-y-4"
                onSubmit={(event) => {
                  event.preventDefault()
                  save.mutate(undefined)
                }}
              >
                {/*
                  Suggested from the teacher pool, because the solver matches a
                  teacher's subject to an épreuve's by exact string. "Maths"
                  typed where the list says "Mathématiques" does not fail
                  loudly — it quietly stops a maths teacher from being barred
                  from the maths paper. Free text is still allowed: a centre may
                  examine a subject nobody there teaches.
                */}
                <ComboBox
                  allowsCustomValue
                  menuTrigger="focus"
                  inputValue={subject}
                  onInputChange={setSubject}
                  onSelectionChange={(key) => key !== null && setSubject(String(key))}
                  fullWidth
                >
                  <Label>{t('schedule.subject')}</Label>
                  <ComboBox.InputGroup>
                    <Input placeholder={t('schedule.subjectHint')} autoFocus />
                    <ComboBox.Trigger />
                  </ComboBox.InputGroup>
                  <ComboBox.Popover>
                    <ListBox>
                      {suggestions.map((option) => (
                        <ListBox.Item
                          key={option.name}
                          id={option.name}
                          textValue={option.name}
                        >
                          <span className="flex w-full items-baseline justify-between gap-4">
                            <span>{option.name}</span>
                            <span className="numeric text-[11px] text-[var(--color-quiet)]">
                              {option.teachers}
                            </span>
                          </span>
                        </ListBox.Item>
                      ))}
                    </ListBox>
                  </ComboBox.Popover>
                </ComboBox>

                {subject.trim() !== '' && !subjects.some((s) => s.name === subject.trim()) && (
                  <p className="flex items-start gap-2 text-[12px] text-[var(--color-quiet)]">
                    <TriangleAlert
                      size={14}
                      className="mt-0.5 shrink-0 text-amber-500"
                      aria-hidden
                    />
                    {t('schedule.unknownSubject')}
                  </p>
                )}

                <div className="grid grid-cols-2 gap-3">
                  <TimeField value={start} onChange={setStart} hourCycle={24}>
                    <Label>{t('schedule.from')}</Label>
                    <TimeField.Group>
                      <TimeField.InputContainer>
                        <TimeField.Input>
                          {(segment) => <TimeField.Segment segment={segment} />}
                        </TimeField.Input>
                      </TimeField.InputContainer>
                    </TimeField.Group>
                  </TimeField>

                  <TimeField value={end} onChange={setEnd} hourCycle={24}>
                    <Label>{t('schedule.to')}</Label>
                    <TimeField.Group>
                      <TimeField.InputContainer>
                        <TimeField.Input>
                          {(segment) => <TimeField.Segment segment={segment} />}
                        </TimeField.Input>
                      </TimeField.InputContainer>
                    </TimeField.Group>
                  </TimeField>
                </div>
              </form>
            </Modal.Body>

            <Modal.Footer>
              {existing && (
                <Button
                  variant="ghost"
                  isPending={remove.isPending}
                  onPress={() => remove.mutate(undefined)}
                >
                  <Trash2 size={14} className="text-[var(--color-alarm)]" aria-hidden />
                  {t('app.delete')}
                </Button>
              )}
              <Button variant="ghost" onPress={onClose}>
                {t('app.cancel')}
              </Button>
              <Button type="submit" form="exam-form" isPending={save.isPending}>
                {t('app.save')}
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </Modal>
  )
}

/** Copying one filière's whole timetable onto another. */
function CopyStream({
  sessionId,
  target,
  streams,
  open,
  onClose,
}: {
  sessionId: number
  target: Stream | null
  streams: Stream[]
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
    <Modal isOpen={open} onOpenChange={(next) => !next && onClose()}>
      <Modal.Backdrop>
        <Modal.Container>
          <Modal.Dialog>
            <Modal.Header>
              <Modal.Heading>{t('schedule.copyInto', { name: target?.name ?? '' })}</Modal.Heading>
            </Modal.Header>
            <Modal.Body>
              <p className="mb-4 text-[13px] text-[var(--color-quiet)]">
                {t('schedule.copyExplain')}
              </p>
              <Select
                selectedKey={from === null ? undefined : String(from)}
                onSelectionChange={(key) => setFrom(Number(key))}
                fullWidth
              >
                <Label>{t('schedule.copyFrom')}</Label>
                <Select.Trigger>
                  <Select.Value />
                  <Select.Indicator />
                </Select.Trigger>
                <Select.Popover>
                  <ListBox>
                    {sources.map((stream) => (
                      <ListBox.Item key={stream.id} id={String(stream.id)} textValue={stream.name}>
                        {stream.name}
                      </ListBox.Item>
                    ))}
                  </ListBox>
                </Select.Popover>
              </Select>
            </Modal.Body>
            <Modal.Footer>
              <Button variant="ghost" onPress={onClose}>
                {t('app.cancel')}
              </Button>
              <Button
                isDisabled={from === null}
                isPending={copy.isPending}
                onPress={() => copy.mutate(undefined)}
              >
                {t('schedule.copy')}
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </Modal>
  )
}

export function SchedulePage() {
  const { t, i18n } = useTranslation()
  const [sessionId, setSessionId] = useState<number | null>(() => {
    const saved = Number(localStorage.getItem(CHOSEN_SESSION))
    return Number.isFinite(saved) && saved > 0 ? saved : null
  })
  const [streamForm, setStreamForm] = useState<{ open: boolean; stream?: Stream }>({ open: false })
  const [examForm, setExamForm] = useState<{
    open: boolean
    stream: Stream | null
    day: string | null
    half: Half
    exam?: Exam
  }>({ open: false, stream: null, day: null, half: 'morning' })
  const [copyFor, setCopyFor] = useState<Stream | null>(null)

  const sessions = useQuery({
    queryKey: ['operations'],
    queryFn: () => api.get<Operation[]>('/operations'),
  })

  useEffect(() => {
    if (sessionId === null && sessions.data?.length) setSessionId(sessions.data[0].id)
  }, [sessions.data, sessionId])

  useEffect(() => {
    if (sessionId !== null) localStorage.setItem(CHOSEN_SESSION, String(sessionId))
  }, [sessionId])

  const grid = useQuery({
    queryKey: ['timetable', sessionId],
    queryFn: () => api.get<Timetable>(`/sessions/${sessionId}/timetable`),
    enabled: sessionId !== null,
  })

  const pool = useQuery({
    queryKey: ['teachers', grid.data?.centerId],
    queryFn: () => api.get<Teacher[]>(`/centers/${grid.data?.centerId}/teachers`),
    enabled: grid.data?.centerId !== undefined,
  })

  /** The subjects actually taught at the centre — the list the solver matches
      an épreuve against, so it is also the list worth suggesting. */
  const subjects = useMemo<SubjectOption[]>(() => {
    const counts = new Map<string, number>()
    for (const teacher of pool.data ?? []) {
      counts.set(teacher.subject, (counts.get(teacher.subject) ?? 0) + 1)
    }
    return [...counts.entries()]
      .map(([name, teachers]) => ({ name, teachers }))
      .sort((a, b) => a.name.localeCompare(b.name))
  }, [pool.data])

  const taught = useMemo(() => new Set(subjects.map((s) => s.name)), [subjects])

  const dayNames = useMemo(
    () => new Intl.DateTimeFormat(i18n.language, { weekday: 'short', day: 'numeric', month: 'short' }),
    [i18n.language],
  )

  /**
   * Every épreuve a filière sits in one séance — not the first one found.
   * A filière with a paper in the morning and another in the afternoon has
   * two, and showing only one is how an épreuve goes missing from the screen
   * while sitting perfectly well in the database.
   */
  const cell = (streamId: number, day: string, half: Half) =>
    (grid.data?.exams ?? [])
      .filter((exam) => exam.streamId === streamId && exam.date === day)
      .filter((exam) => halfOf(exam.startTime) === half)
      .sort((a, b) => a.startTime.localeCompare(b.startTime))

  if (sessions.isError) {
    return (
      <Page title={t('schedule.title')}>
        <Failed error={sessions.error as Error} onRetry={() => void sessions.refetch()} />
      </Page>
    )
  }

  if (sessions.isSuccess && sessions.data.length === 0) {
    return (
      <Page title={t('schedule.title')}>
        <div className="rounded-md border border-[var(--color-hairline)] bg-white">
          <Empty>{t('schedule.noSession')}</Empty>
        </div>
      </Page>
    )
  }

  const data = grid.data

  return (
    <Page
      title={t('schedule.title')}
      subtitle={data ? `${data.centerName}` : t('schedule.subtitle')}
      actions={
        sessionId !== null &&
        data && (
          <Button size="sm" onPress={() => setStreamForm({ open: true })}>
            <Plus size={15} aria-hidden />
            {t('schedule.addStream')}
          </Button>
        )
      }
    >
      <div className="mb-5">
        <Select
          selectedKey={sessionId === null ? undefined : String(sessionId)}
          onSelectionChange={(key) => setSessionId(Number(key))}
          className="w-80"
        >
          <Label>{t('schedule.session')}</Label>
          <Select.Trigger>
            <Select.Value />
            <Select.Indicator />
          </Select.Trigger>
          <Select.Popover>
            <ListBox>
              {(sessions.data ?? []).map((session) => (
                <ListBox.Item
                  key={session.id}
                  id={String(session.id)}
                  textValue={session.reference}
                >
                  {session.reference}
                </ListBox.Item>
              ))}
            </ListBox>
          </Select.Popover>
        </Select>
      </div>

      <Panel
        title={t('schedule.grid')}
        count={data?.exams.length}
        hint={data && data.streams.length > 0 ? t('schedule.hint') : undefined}
      >
        {grid.isPending && sessionId !== null && <Loading rows={4} />}
        {grid.isError && (
          <div className="p-4">
            <Failed error={grid.error as Error} onRetry={() => void grid.refetch()} />
          </div>
        )}

        {data &&
          (data.streams.length === 0 ? (
            <Empty
              action={
                <Button size="sm" onPress={() => setStreamForm({ open: true })}>
                  <Plus size={15} aria-hidden />
                  {t('schedule.addStream')}
                </Button>
              }
            >
              {t('schedule.noStream')}
            </Empty>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] table-fixed border-collapse">
                <thead>
                  {/* two rows: the day, then the séance inside it */}
                  <tr>
                    <th
                      rowSpan={2}
                      className="w-[230px] border-b border-[var(--color-hairline)] px-4 py-2 text-start align-bottom text-[11px] font-medium uppercase tracking-wide text-[var(--color-quiet)]"
                    >
                      {t('schedule.stream')}
                    </th>
                    {data.days.map((day) => (
                      <th
                        key={day}
                        colSpan={2}
                        className="border-s border-[var(--color-hairline)] px-3 pb-1 pt-2 text-center text-[12px] font-semibold"
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
                          className={`border-b border-[var(--color-hairline)] px-2 pb-1.5 text-start text-[10px] font-medium uppercase tracking-[0.06em] text-[var(--color-quiet)] ${
                            half === 'morning' ? 'border-s border-s-[var(--color-hairline)]' : ''
                          }`}
                        >
                          {t(`schedule.${half}`)}
                        </th>
                      )),
                    )}
                  </tr>
                </thead>
                <tbody>
                  {data.streams.map((stream) => (
                    <tr
                      key={stream.id}
                      className="group border-b border-[var(--color-hairline)] last:border-b-0"
                    >
                      <td className="px-4 py-2.5 align-top">
                        <div className="flex items-start justify-between gap-2">
                          <div className="min-w-0">
                            <div className="text-[13px] font-medium leading-tight">{stream.name}</div>
                            {/* the count, not the list: "Salle 2, Salle 3, Salle
                                4, Salle 5" costs more width than the filière's
                                own name. The full list is on hover. */}
                            <div
                              className="mt-0.5 text-[11px] text-[var(--color-quiet)]"
                              title={stream.rooms.map((room) => room.label).join(', ')}
                            >
                              {stream.rooms.length === 0 ? (
                                <span className="text-[var(--color-alarm)]">
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
                          <div className="flex shrink-0 items-center gap-0.5">
                            <Button
                              size="sm"
                              variant="ghost"
                              isIconOnly
                              aria-label={t('schedule.copyFrom')}
                              onPress={() => setCopyFor(stream)}
                            >
                              <Copy
                                size={13}
                                className="text-[var(--color-quiet)] transition-colors hover:text-[var(--color-ink)]"
                                aria-hidden
                              />
                            </Button>
                            <Button
                              size="sm"
                              variant="ghost"
                              isIconOnly
                              aria-label={t('schedule.editStream')}
                              onPress={() => setStreamForm({ open: true, stream })}
                            >
                              <Pencil
                                size={13}
                                className="text-[var(--color-quiet)] transition-colors hover:text-[var(--color-ink)]"
                                aria-hidden
                              />
                            </Button>
                          </div>
                        </div>
                      </td>

                      {data.days.flatMap((day) =>
                        HALVES.map((half) => {
                          const exams = cell(stream.id, day, half)
                          return (
                            <td
                              key={`${day}-${half}`}
                              className={`space-y-1 px-1.5 py-1.5 align-top ${
                                half === 'morning'
                                  ? 'border-s border-[var(--color-hairline)]'
                                  : ''
                              }`}
                            >
                              {exams.map((exam) => (
                                <button
                                  key={exam.id}
                                  type="button"
                                  onClick={() =>
                                    setExamForm({ open: true, stream, day, half, exam })
                                  }
                                  className="w-full rounded-md border-s-2 border-s-[var(--color-brand)] bg-[var(--color-ground)] px-2 py-1.5 text-start transition-colors hover:bg-[var(--color-hairline)]/60"
                                >
                                  <span className="flex items-start gap-1 text-[13px] font-medium leading-tight">
                                    {/* nobody teaches it: the permanence has no
                                        specialist and the own-subject rule
                                        cannot bite */}
                                    {pool.isSuccess && !taught.has(exam.subject) && (
                                      <TriangleAlert
                                        size={12}
                                        className="mt-0.5 shrink-0 text-amber-500"
                                        aria-label={t('schedule.unknownSubject')}
                                      />
                                    )}
                                    <span>{exam.subject}</span>
                                  </span>
                                  <span className="numeric mt-0.5 block text-[11px] text-[var(--color-quiet)]">
                                    {shortTime(exam.startTime)} — {shortTime(exam.endTime)}
                                  </span>
                                </button>
                              ))}

                              {/* an empty séance stays reachable, and a filled
                                  one can still take a second paper */}
                              <button
                                type="button"
                                onClick={() =>
                                  setExamForm({ open: true, stream, day, half, exam: undefined })
                                }
                                className={`w-full items-center gap-1.5 rounded-md px-2 text-[11px] text-[var(--color-quiet)] transition-colors hover:text-[var(--color-brand)] ${
                                  exams.length === 0
                                    ? 'flex justify-center border border-dashed border-[var(--color-hairline)] py-2.5 hover:border-[var(--color-brand)]'
                                    : 'hidden py-1 group-hover:flex group-focus-within:flex'
                                }`}
                              >
                                <Plus size={12} aria-hidden />
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
      </Panel>

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
            streams={data.streams}
            open={copyFor !== null}
            onClose={() => setCopyFor(null)}
          />
        </>
      )}
    </Page>
  )
}
