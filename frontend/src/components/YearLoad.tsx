import { useMemo, useState } from 'react'
import { useQueries, useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { CalendarRange, Scale, TriangleAlert, Users } from 'lucide-react'
import { api } from '../lib/api'
import { useWorkspace } from '../context/Workspace'
import { useNames } from '../lib/names'
import {
  Card,
  CardHead,
  CardRule,
  Dialog,
  Empty,
  Failed,
  Notice,
  Skeleton,
  Table,
  Td,
  Th,
  Tr,
} from '../ui'

type Year = { id: number; label: string; current: boolean }

type ArchivedSession = {
  id: number
  reference: string
  type: string
  startsOn: string | null
  state: 'DRAFT' | 'SETTLED'
  scheduleJobId: number | null
  dutyCount: number
}

type Tally = {
  matricule: string
  name: string
  nameFr: string | null
  subject: string
  surveillance: number
  reserve: number
  permanence: number
  total: number
}

type Archive = {
  yearId: number
  label: string
  centerId: number
  sessions: ArchivedSession[]
  tally: Tally[]
}

type Assignment = {
  slotId: string
  date: string
  start: string
  end: string
  role: 'SURVEILLANCE' | 'RESERVE' | 'PERMANENCE'
  examId: string | null
  subject: string | null
  roomId: string | null
  teacherMatricule: string | null
}

type Schedule = { assignments: Assignment[] }

type Room = { reference: string; label: string }
type CenterDetail = { rooms: Room[] }

/** One line of a teacher's year: a duty, and whoever stood beside them. */
type Duty = { session: string; row: Assignment; with: string | null }

/**
 * A range, written as one figure when both ends agree.
 *
 * <p>No zero seed: `Math.min(...values, 0)` would floor every range at nought
 * and report « 0 – 9 » for a pool where the lightest teacher carries eight.
 */
function span(values: number[]): { low: number; high: number } {
  if (values.length === 0) return { low: 0, high: 0 }
  return { low: Math.min(...values), high: Math.max(...values) }
}

/**
 * « 8 – 9 », or « 9 » when nobody is above or below it.
 *
 * <p>Held to left-to-right: an Arabic page would otherwise lay the two ends out
 * in its own direction and print « 9 – 8 ». A reader of Arabic would still take
 * it the right way round, but every other range in the interface — 08:00 –
 * 11:00 — reads the other, and two conventions for one thing is one too many.
 */
function Range({ low, high }: { low: number; high: number }) {
  return <bdi dir="ltr">{low === high ? String(low) : `${low} – ${high}`}</bdi>
}

/**
 * What each teacher carried over the whole year.
 *
 * <p>Fairness does not mean anything inside one session: the régionale is 110
 * duties and the rattrapage is 30, and nobody is owed an equal share of either.
 * It means something over a year, which is also the scope the privilege queue
 * works in — réserve and permanence are counted from September and start again
 * in September. So this is the screen the question « pourquoi moi plus que lui »
 * is actually answered on.
 *
 * <p>Everything here comes from the year's own archive, which already exists
 * for a finished year and is just as true of the one in progress. The four
 * lines at the top are there so that he can stop reading: 8 – 9 is fair and he
 * can leave; 4 – 12 is when he goes looking.
 */
export function YearLoad() {
  const { t } = useTranslation()
  const { centerId } = useWorkspace()
  const { label: named } = useNames()
  const [opened, setOpened] = useState<Tally | null>(null)

  const years = useQuery({
    queryKey: ['years', centerId],
    queryFn: () => api.get<Year[]>(`/centers/${centerId}/years`),
    enabled: centerId !== null,
  })

  // the header names the year rather than choosing it: there is one in
  // progress, and reading a finished one is a different act on its own screen
  const current = years.data?.find((year) => year.current)

  const archive = useQuery({
    queryKey: ['archive', centerId, current?.id],
    queryFn: () => api.get<Archive>(`/centers/${centerId}/years/${current?.id}/archive`),
    enabled: centerId !== null && current !== undefined,
  })

  const center = useQuery({
    queryKey: ['center', centerId],
    queryFn: () => api.get<CenterDetail>(`/centers/${centerId}`),
    enabled: centerId !== null,
  })

  /** The settled sessions, which are the only ones the tally counted. */
  const counted = useMemo(
    () =>
      (archive.data?.sessions ?? []).filter(
        (one) => one.state === 'SETTLED' && one.scheduleJobId !== null,
      ),
    [archive.data],
  )

  const drafts = (archive.data?.sessions ?? []).filter((one) => one.state === 'DRAFT')

  /*
   * A teacher's own rows are not in the tally — it holds counts, not duties —
   * so they come from the schedules the sessions were settled on, one request
   * each and cached from then on. They are only wanted once a name is clicked.
   */
  const schedules = useQueries({
    queries: counted.map((session) => ({
      queryKey: ['schedule', session.scheduleJobId],
      queryFn: () => api.get<Schedule>(`/jobs/${session.scheduleJobId}/schedule`),
      enabled: opened !== null,
    })),
  })

  const roomLabel = useMemo(() => {
    const labels = new Map((center.data?.rooms ?? []).map((room) => [room.reference, room.label]))
    return (reference: string | null) =>
      reference === null ? null : (labels.get(reference) ?? reference)
  }, [center.data])

  /** Both names of everyone in the year, so a binôme is not read off the row. */
  const byMatricule = useMemo(
    () => new Map((archive.data?.tally ?? []).map((one) => [one.matricule, one])),
    [archive.data],
  )

  const figures = useMemo(() => {
    const tally = archive.data?.tally ?? []
    if (tally.length === 0) return null

    const totals = tally.map((one) => one.total)

    return {
      tally,
      people: tally.length,
      duties: totals.reduce((sum, one) => sum + one, 0),
      idle: tally.filter((one) => one.total === 0).length,
      total: span(totals),
      // added, here and only here. The summary exists so that he can stop
      // reading, and the queue balances the two as one quantity — which makes
      // the combined range the tighter and truer one. Which of the two is
      // short is a question about a person, and it is answered a card lower.
      light: span(tally.map((one) => one.reserve + one.permanence)),
    }
  }, [archive.data])

  /** One teacher's year, session by session, with the room and the binôme. */
  const duties = useMemo((): Duty[] => {
    if (opened === null) return []
    const out: Duty[] = []

    counted.forEach((session, index) => {
      const rows = schedules[index]?.data?.assignments ?? []
      for (const row of rows) {
        if (row.teacherMatricule !== opened.matricule) continue

        // the other surveillant of the same room at the same hour: one fact
        // about that morning, and the thing he is asked about by name
        const beside =
          row.role === 'SURVEILLANCE'
            ? rows.find(
                (other) =>
                  other.role === 'SURVEILLANCE' &&
                  other.slotId === row.slotId &&
                  other.examId === row.examId &&
                  other.roomId === row.roomId &&
                  other.teacherMatricule !== null &&
                  other.teacherMatricule !== opened.matricule,
              )
            : undefined

        const partner = beside?.teacherMatricule
          ? byMatricule.get(beside.teacherMatricule)
          : undefined

        out.push({
          session: session.reference,
          row,
          // never the schedule's own teacherName: that one is Arabic only
          with: partner ? named(partner) : null,
        })
      }
    })

    return out
  }, [opened, counted, schedules, byMatricule, named])

  /** The same duties, under the session that asked for them. */
  const grouped = useMemo(() => {
    const out = new Map<string, Duty[]>()
    for (const duty of duties) out.set(duty.session, [...(out.get(duty.session) ?? []), duty])
    return [...out.entries()]
  }, [duties])

  if (archive.isError) {
    return (
      <Card>
        <Failed error={archive.error as Error} onRetry={() => void archive.refetch()} />
      </Card>
    )
  }

  if (years.isSuccess && current === undefined) {
    return (
      <Card>
        <Empty icon={<CalendarRange size={22} aria-hidden />}>{t('yearLoad.noYear')}</Empty>
      </Card>
    )
  }

  if (archive.isPending || years.isPending) {
    return (
      <Card>
        <Skeleton rows={6} />
      </Card>
    )
  }

  if (!figures || counted.length === 0) {
    return (
      <Card>
        <Empty icon={<Scale size={22} aria-hidden />} title={t('yearLoad.noneTitle')}>
          {t('yearLoad.none')}
        </Empty>
      </Card>
    )
  }

  return (
    <>
      {/*
        Four lines, and he should be able to stop after them. The last is the
        one that goes bad first: surveillance is plentiful and levels itself,
        réserve and permanence are scarce, and it is the scarce one people
        count against each other.
      */}
      <Card className="mb-6 [--i:2]">
        <CardHead title={t('yearLoad.summary')} />
        <CardRule />
        <dl className="divide-y divide-[var(--color-hairline)]">
          <Line
            icon={<Users size={16} aria-hidden />}
            term={t('yearLoad.pool')}
            value={t('yearLoad.poolValue', { people: figures.people, duties: figures.duties })}
          />
          <Line
            icon={<Scale size={16} aria-hidden />}
            term={t('yearLoad.perTeacher')}
            value={<Range {...figures.total} />}
          />
          <Line
            icon={<Users size={16} aria-hidden />}
            term={t('yearLoad.idle')}
            value={
              figures.idle === 0
                ? t('yearLoad.idleNone')
                : t('yearLoad.idleSome', { count: figures.idle })
            }
            flag={figures.idle > 0}
          />
          <Line
            icon={<Scale size={16} aria-hidden />}
            term={t('yearLoad.lightDuties')}
            value={<Range {...figures.light} />}
          />
        </dl>
      </Card>

      {/*
        The tally counts settled sessions only — the same rule as the privilege
        queue. He will distribute a session, come here, and find the figures
        unmoved; said out loud, that is where he learns why arrêter matters.
      */}
      <div className="mb-6 space-y-2">
        <Notice tone="plain" icon={<CalendarRange size={16} aria-hidden />}>
          {t('yearLoad.counted', { count: counted.length })}
        </Notice>
        {drafts.length > 0 && (
          <Notice tone="warn" icon={<TriangleAlert size={16} aria-hidden />}>
            {t('yearLoad.drafts', {
              names: drafts.map((one) => `« ${one.reference} »`).join(', '),
              count: drafts.length,
            })}
          </Notice>
        )}
      </div>

      <Card className="[--i:3]">
        <CardHead title={t('yearLoad.everyone')} count={figures.people} />
        <CardRule />
        <Table>
          <thead>
            <Tr>
              <Th className="w-[118px]">{t('teachers.matricule')}</Th>
              <Th>{t('results.teacher')}</Th>
              <Th className="w-[180px]">{t('teachers.subject')}</Th>
              <Th className="w-[124px] text-end">{t('results.roleSURVEILLANCE')}</Th>
              <Th className="w-[112px] text-end">{t('results.roleRESERVE')}</Th>
              <Th className="w-[124px] text-end">{t('results.rolePERMANENCE')}</Th>
              <Th className="w-[92px] text-end">{t('results.total')}</Th>
            </Tr>
          </thead>
          <tbody>
            {figures.tally.map((one) => (
              <Tr key={one.matricule}>
                <Td className="numeric text-[var(--color-quiet)]">{one.matricule}</Td>
                <Td className="font-medium whitespace-nowrap">
                  <button
                    type="button"
                    onClick={() => setOpened(one)}
                    className="cursor-pointer text-start hover:text-[var(--color-accent)] hover:underline"
                  >
                    {named(one)}
                  </button>
                </Td>
                <Td className="text-[var(--color-quiet)]">{one.subject}</Td>
                <Td className="numeric text-end">{one.surveillance}</Td>
                <Td className="numeric text-end">{one.reserve}</Td>
                <Td className="numeric text-end">{one.permanence}</Td>
                <Td className="numeric text-end font-medium">{one.total}</Td>
              </Tr>
            ))}
          </tbody>
        </Table>
      </Card>

      {/*
        One teacher, the whole year. Not the fairness question — that one is
        answered above — but the one asked at the door: what am I doing, and
        can you defend it. So his own figure carries the centre's beside it.
      */}
      <Dialog
        isOpen={opened !== null}
        onClose={() => setOpened(null)}
        title={opened ? named(opened) : ''}
        subtitle={
          opened
            ? t('yearLoad.card.subtitle', {
                matricule: opened.matricule,
                subject: opened.subject,
              })
            : ''
        }
      >
        {opened && (
          <div className="pb-4">
            <p className="mb-4 text-[13px]">
              {t('yearLoad.card.load', {
                count: opened.total,
                low: figures.total.low,
                high: figures.total.high,
              })}
              <span className="text-[var(--color-quiet)]">
                {' · '}
                {t('yearLoad.card.split', {
                  surveillance: opened.surveillance,
                  reserve: opened.reserve,
                  permanence: opened.permanence,
                })}
              </span>
            </p>

            {schedules.some((one) => one.isPending) && <Skeleton rows={4} />}

            {/*
              Grouped under the session that asked for them, named once. Eight
              rows repeating « National Session Normale 2026 (Candidats
              scolarisés) » is the session shouting over the duty.
            */}
            {grouped.map(([session, rows]) => (
              <section key={session} className="mb-4 last:mb-0">
                <h3 className="border-b border-[var(--color-rule)] pb-1.5 text-[12px] font-semibold text-[var(--color-quiet)]">
                  {session}
                </h3>
                <ul className="divide-y divide-[var(--color-hairline)]">
                  {rows.map((duty, index) => (
                    <li key={index} className="flex items-baseline gap-3 py-2.5 text-[12.5px]">
                      <span className="numeric shrink-0 text-[var(--color-quiet)]">
                        {duty.row.date.slice(5)} · {duty.row.start.slice(0, 5)}–
                        {duty.row.end.slice(0, 5)}
                      </span>
                      <span className="w-[86px] shrink-0 font-medium">
                        {roomLabel(duty.row.roomId) ?? t(`results.role${duty.row.role}`)}
                      </span>
                      <span className="min-w-0 flex-1 truncate text-[var(--color-quiet)]">
                        {duty.row.subject ?? ''}
                      </span>
                      {duty.with && (
                        <span className="shrink-0 text-[var(--color-quiet)]">
                          {t('yearLoad.card.with', { name: duty.with })}
                        </span>
                      )}
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        )}
      </Dialog>
    </>
  )
}

/** A summary line: a mark, what is being counted, and the figure. */
function Line({
  icon,
  term,
  value,
  flag = false,
}: {
  icon: React.ReactNode
  term: string
  value: React.ReactNode
  flag?: boolean
}) {
  return (
    <div className="flex flex-wrap items-baseline gap-x-3 px-5 py-3.5">
      <span className={`self-center ${flag ? 'text-[var(--color-warn)]' : 'text-[var(--color-faint)]'}`}>
        {icon}
      </span>
      <dt className="min-w-0 flex-1 text-[13px] text-[var(--color-quiet)]">{term}</dt>
      <dd className="numeric text-[14.5px] font-medium">{value}</dd>
    </div>
  )
}

