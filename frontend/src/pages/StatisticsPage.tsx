import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import {
  ArrowRight,
  CalendarRange,
  ChartNoAxesColumn,
  CircleSlash,
  Scale,
  TriangleAlert,
  Users,
} from 'lucide-react'
import { api } from '../lib/api'
import { useWorkspace } from '../context/Workspace'
import { Page } from '../components/Page'
import { Bars, Columns } from '../components/Charts'
import { YearLoad } from '../components/YearLoad'
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
  Stat,
  Table,
  Td,
  Th,
  Tr,
} from '../ui'

type Job = {
  id: number
  operationId: number
  status: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'
  finishedAt: string | null
  stale: boolean
}

type Assignment = {
  date: string
  start: string
  role: 'SURVEILLANCE' | 'RESERVE' | 'PERMANENCE'
  teacherMatricule: string | null
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
  unfilled: number
  assignments: Assignment[]
  workload: Workload[]
}

/** An entry of the centre's subject list, with what already depends on it. */
type SubjectOption = { id: number; name: string; usedByTeachers: number; usedByExams: number }

/** The three duties, each balanced on its own — never folded into one total. */
const ROLES = ['surveillance', 'reserve', 'permanence'] as const

/** The middle value, which a long tail does not drag the way an average does. */
function median(values: number[]): number {
  if (values.length === 0) return 0
  const sorted = [...values].sort((a, b) => a - b)
  const middle = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 0 ? (sorted[middle - 1] + sorted[middle]) / 2 : sorted[middle]
}

/** How many teachers sit at each count, from nobody to the heaviest. */
function bucket(counts: number[]): { bin: number; count: number }[] {
  const highest = Math.max(...counts, 0)
  return Array.from({ length: highest + 1 }, (_, bin) => ({
    bin,
    count: counts.filter((one) => one === bin).length,
  }))
}

function tidy(value: number): string {
  return String(Math.round(value * 10) / 10)
}

/**
 * The shape of a distribution, rather than the distribution itself.
 *
 * <p>La répartition answers "who surveys what". This screen answers the
 * question an administrator gets asked afterwards, usually by a teacher
 * standing in the doorway: *why me, and is this fair*. So everything here is a
 * fact about duties — how the load falls across the pool, how each role falls
 * separately, where the week is heavy, what is not covered. Nothing here is a
 * solver score, and nothing is a chart for the sake of having one.
 *
 * <p>It all comes from the same solved job La répartition already fetched, so
 * opening this screen costs no request.
 */
/**
 * Les statistiques, which answers two different questions with one word.
 *
 * <p>The session is the scope the old screen had, and it is the wrong one for
 * the question the administrator is actually asked: 110 duties at the régionale
 * against 30 at the rattrapage means nobody is owed an equal share of either.
 * Fairness is a year, which is also the scope the privilege queue counts in. So
 * the year comes first, and the session stays for reading one distribution.
 */
export function StatisticsPage() {
  const { t } = useTranslation()
  const [view, setView] = useState<'year' | 'session'>('year')

  return (
    <Page
      title={t('statistics.title')}
      subtitle={t('statistics.subtitle')}
      tabs={
        <SegmentedTabs
          value={view}
          onChange={(id) => setView(id as 'year' | 'session')}
          tabs={[
            { id: 'year', label: t('yearLoad.tabYear'), icon: <CalendarRange size={15} aria-hidden /> },
            {
              id: 'session',
              label: t('yearLoad.tabSession'),
              icon: <ChartNoAxesColumn size={15} aria-hidden />,
            },
          ]}
        />
      }
    >
      {view === 'year' ? <YearLoad /> : <SessionFigures />}
    </Page>
  )
}

/** The shape of one solved distribution, which is what this screen always was. */
function SessionFigures() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const { sessionId, centerId, sessionsHere, isLoading } = useWorkspace()

  const jobs = useQuery({ queryKey: ['jobs'], queryFn: () => api.get<Job[]>('/jobs') })

  const job = useMemo(
    () => (jobs.data ?? []).find((one) => one.operationId === sessionId),
    [jobs.data, sessionId],
  )

  const schedule = useQuery({
    queryKey: ['schedule', job?.id],
    queryFn: () => api.get<Schedule>(`/jobs/${job?.id}/schedule`),
    enabled: job !== undefined && job.status === 'DONE',
  })

  const catalogue = useQuery({
    queryKey: ['subjects', centerId],
    queryFn: () => api.get<SubjectOption[]>(`/centers/${centerId}/subjects`),
    enabled: centerId !== null,
  })

  const dayNames = useMemo(
    () => new Intl.DateTimeFormat(i18n.language, { weekday: 'short', day: 'numeric', month: 'short' }),
    [i18n.language],
  )

  /**
   * Everything the screen shows, computed once from the one payload.
   *
   * <p>The comparison that matters is on surveillance alone: that is the load a
   * teacher feels standing in a room. Réserve and permanence are hours spent
   * waiting, they are counted, and they are balanced on their own — which is
   * why each role gets its own figure rather than being folded into a total.
   */
  const figures = useMemo(() => {
    const pool = schedule.data?.workload ?? []
    const duties = schedule.data?.assignments ?? []
    if (pool.length === 0) return null

    const surveillance = pool.map((row) => row.surveillance)
    const lightest = Math.min(...surveillance)
    const heaviest = Math.max(...surveillance)

    // heaviest first, and only the two ends are ever shown
    const ranked = [...pool].sort(
      (a, b) => b.surveillance - a.surveillance || a.name.localeCompare(b.name),
    )
    const ends = Math.min(4, Math.floor(pool.length / 2))

    const days = new Map<string, { morning: number; afternoon: number }>()
    for (const duty of duties) {
      const day = days.get(duty.date) ?? { morning: 0, afternoon: 0 }
      if (Number(duty.start.slice(0, 2)) < 12) day.morning += 1
      else day.afternoon += 1
      days.set(duty.date, day)
    }

    const carried = pool.some((row) => row.priorTotal > 0)

    return {
      pool,
      dutyCount: duties.length,
      working: pool.filter((row) => row.total > 0).length,
      spread: heaviest - lightest,
      lightest,
      heaviest,
      median: median(surveillance),
      bins: bucket(surveillance),
      roles: ROLES.map((role) => ({
        role,
        bins: bucket(pool.map((row) => row[role])),
        median: median(pool.map((row) => row[role])),
        most: Math.max(...pool.map((row) => row[role])),
      })),
      most: ranked.slice(0, ends),
      least: ranked.slice(-ends).reverse(),
      days: [...days.entries()].sort(([a], [b]) => a.localeCompare(b)),
      // only worth a section when there is a past session to carry
      carried: carried
        ? {
            bins: bucket(pool.map((row) => row.total + row.priorTotal)),
            median: median(pool.map((row) => row.total + row.priorTotal)),
          }
        : null,
    }
  }, [schedule.data])

  const untaught = (catalogue.data ?? []).filter((one) => one.usedByTeachers === 0)

  if (!isLoading && sessionsHere.length === 0) {
    return (
      <Card>
        <Empty icon={<ChartNoAxesColumn size={22} aria-hidden />}>{t('schedule.noSession')}</Empty>
      </Card>
    )
  }

  /*
   * Every figure on this screen hangs off a solved distribution. Without one
   * there is nothing to average, and showing a screen of zeros would be
   * inventing an answer — so it says so, and offers the way in.
   */
  if (!jobs.isPending && (job === undefined || job.status !== 'DONE')) {
    return (
      <Card>
        <Empty
          icon={<ChartNoAxesColumn size={22} aria-hidden />}
          title={t('statistics.noneTitle')}
          action={
            <Button onPress={() => void navigate('/results')}>
              {t('statistics.goSolve')}
              <ArrowRight size={16} className="rtl:rotate-180" aria-hidden />
            </Button>
          }
        >
          {t('statistics.none')}
        </Empty>
      </Card>
    )
  }

  return (
    <>
      {/* the distribution these figures describe is no longer the current one */}
      {job?.stale && (
        <div className="rise mb-5 [--i:1]">
          <Notice tone="warn" icon={<TriangleAlert size={16} aria-hidden />}>
            {t('statistics.stale')}
          </Notice>
        </div>
      )}

      {(schedule.isPending || jobs.isPending) && (
        <Card>
          <Skeleton rows={6} />
        </Card>
      )}

      {schedule.isError && (
        <Card>
          <Failed error={schedule.error as Error} onRetry={() => void schedule.refetch()} />
        </Card>
      )}

      {figures && (
        <>
          <div className="mb-6 grid gap-4 [&>*:nth-child(2)]:[--i:3] [&>*:nth-child(3)]:[--i:4] sm:grid-cols-2 lg:grid-cols-3">
            <Stat
              label={t('statistics.stat.duties')}
              value={figures.dutyCount}
              icon={<ChartNoAxesColumn size={18} aria-hidden />}
              tone="accent"
            />
            <Stat
              label={t('statistics.stat.working')}
              value={`${figures.working}/${figures.pool.length}`}
              hint={t('statistics.stat.workingHint')}
              icon={<Users size={18} aria-hidden />}
            />
            <Stat
              label={t('statistics.stat.spread')}
              value={figures.spread}
              hint={t('statistics.stat.spreadHint', {
                least: figures.lightest,
                most: figures.heaviest,
              })}
              icon={<Scale size={18} aria-hidden />}
              tone={figures.spread > 2 ? 'warn' : 'plain'}
            />
          </div>

          {/*
            The one figure worth defending in a doorway: how many teachers sit at
            each number of surveillances. A single tall column is a fair week.
          */}
          <Card className="mb-6">
            <CardHead
              title={t('statistics.load.title')}
              actions={
                <span className="text-[12.5px] text-[var(--color-quiet)]">
                  {t('statistics.load.median', { value: tidy(figures.median) })}
                </span>
              }
            />
            <CardRule />
            <Columns
              bins={figures.bins}
              countLabel={(count) => t('statistics.teacherCount', { count })}
              binLabel={(bin) => t('statistics.surveillanceCount', { count: bin })}
            />
            <div className="rounded-b-[var(--radius-card)] border-t border-[var(--color-hairline)] bg-[var(--color-sunken)] px-5 py-3">
              <p className="text-[11.5px] text-[var(--color-faint)]">{t('statistics.load.hint')}</p>
            </div>
          </Card>

          {/*
            Each role balanced on its own, because they are not the same work:
            an hour in a room and an hour waiting in the staff room are paid the
            same and felt differently.
          */}
          <div className="mb-6 grid gap-4 [&>*:nth-child(2)]:[--i:3] [&>*:nth-child(3)]:[--i:4] lg:grid-cols-3">
            {figures.roles.map(({ role, bins, median: middle, most }) => (
              <Card key={role}>
                <div className="px-5 pb-1 pt-4">
                  <h3 className="eyebrow">{t(`results.role${role.toUpperCase()}`)}</h3>
                  <p className="numeric mt-1.5 text-[15px] font-medium">
                    {tidy(middle)}
                    <span className="ms-1.5 font-sans text-[11.5px] font-normal text-[var(--color-quiet)]">
                      {t('statistics.roles.median')}
                    </span>
                  </p>
                </div>
                <Columns
                  bins={bins}
                  countLabel={(count) => t('statistics.teacherCount', { count })}
                  binLabel={(bin) => t('statistics.dutyCount', { count: bin })}
                />
                <div className="border-t border-[var(--color-hairline)] px-5 py-2.5">
                  <p className="text-[11.5px] text-[var(--color-faint)]">
                    {t('statistics.roles.most', { count: most })}
                  </p>
                </div>
              </Card>
            ))}
          </div>

          {/* the two ends of the pool, named — the abstract made answerable */}
          <Card className="mb-6">
            <CardHead title={t('statistics.extremes.title')} />
            <CardRule />
            <Table>
              <thead>
                <tr>
                  <Th>{t('results.teacher')}</Th>
                  <Th width="120px">{t('results.roleSURVEILLANCE')}</Th>
                  <Th width="110px">{t('results.total')}</Th>
                  <Th width="90px" />
                </tr>
              </thead>
              <tbody>
                {figures.most.map((row) => (
                  <Tr key={row.matricule}>
                    <Td>
                      <span className="block font-medium">{row.name}</span>
                      <span className="numeric mt-0.5 block text-[11.5px] text-[var(--color-quiet)]">
                        {row.matricule} · {row.subject}
                      </span>
                    </Td>
                    <Td className="numeric font-medium">{row.surveillance}</Td>
                    <Td className="numeric">{row.total}</Td>
                    <Td>
                      <Badge tone="warn">{t('statistics.extremes.most')}</Badge>
                    </Td>
                  </Tr>
                ))}
                {figures.least.map((row) => (
                  <Tr key={row.matricule}>
                    <Td>
                      <span className="block font-medium">{row.name}</span>
                      <span className="numeric mt-0.5 block text-[11.5px] text-[var(--color-quiet)]">
                        {row.matricule} · {row.subject}
                      </span>
                    </Td>
                    <Td className="numeric font-medium">{row.surveillance}</Td>
                    <Td className="numeric">{row.total}</Td>
                    <Td>
                      <Badge>{t('statistics.extremes.least')}</Badge>
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
          </Card>

          {/* where the week is heavy, and which séance carries it */}
          <Card className="mb-6">
            <CardHead title={t('statistics.days.title')} count={figures.days.length} />
            <CardRule />
            <Bars
              legend={{ first: t('schedule.morning'), second: t('schedule.afternoon') }}
              rows={figures.days.map(([day, halves]) => ({
                key: day,
                label: dayNames.format(new Date(`${day}T00:00:00`)),
                first: halves.morning,
                second: halves.afternoon,
                hint: `${t('schedule.morning')} ${halves.morning} · ${t('schedule.afternoon')} ${halves.afternoon}`,
              }))}
            />
          </Card>

          {/*
            Only when there is a past session to carry: in July, what June
            already asked of somebody is part of the answer to "is this fair".
          */}
          {figures.carried && (
            <Card className="mb-6">
              <CardHead
                title={t('statistics.carried.title')}
                actions={
                  <span className="text-[12.5px] text-[var(--color-quiet)]">
                    {t('statistics.load.median', { value: tidy(figures.carried.median) })}
                  </span>
                }
              />
              <CardRule />
              <Columns
                bins={figures.carried.bins}
                countLabel={(count) => t('statistics.teacherCount', { count })}
                binLabel={(bin) => t('statistics.dutyCount', { count: bin })}
              />
              <div className="rounded-b-[var(--radius-card)] border-t border-[var(--color-hairline)] bg-[var(--color-sunken)] px-5 py-3">
                <p className="text-[11.5px] text-[var(--color-faint)]">
                  {t('statistics.carried.hint')}
                </p>
              </div>
            </Card>
          )}

          {/* what is not covered, stated as facts with a way in */}
          <Card>
            <CardHead title={t('statistics.gaps.title')} />
            <CardRule />
            {(schedule.data?.unfilled ?? 0) === 0 && untaught.length === 0 ? (
              <Empty icon={<CircleSlash size={22} aria-hidden />}>{t('statistics.gaps.none')}</Empty>
            ) : (
              <ul>
                {(schedule.data?.unfilled ?? 0) > 0 && (
                  <li className="flex flex-wrap items-center gap-3 border-b border-[var(--color-hairline)] px-5 py-4 last:border-b-0">
                    <TriangleAlert
                      size={16}
                      className="shrink-0 text-[var(--color-alarm)]"
                      aria-hidden
                    />
                    <span className="min-w-0 flex-1 text-[13.5px]">
                      {t('statistics.gaps.unfilled', { count: schedule.data?.unfilled ?? 0 })}
                    </span>
                    <Button
                      size="sm"
                      variant="secondary"
                      onPress={() => void navigate('/results')}
                    >
                      {t('statistics.gaps.seeResults')}
                    </Button>
                  </li>
                )}
                {untaught.length > 0 && (
                  <li className="flex flex-wrap items-center gap-3 px-5 py-4">
                    <TriangleAlert
                      size={16}
                      className="shrink-0 text-[var(--color-warn)]"
                      aria-hidden
                    />
                    <span className="min-w-0 flex-1 text-[13.5px]">
                      {t('statistics.gaps.untaught', {
                        count: untaught.length,
                        names: untaught.map((one) => one.name).join(', '),
                      })}
                    </span>
                    <Button
                      size="sm"
                      variant="secondary"
                      onPress={() => void navigate('/subjects')}
                    >
                      {t('statistics.gaps.seeSubjects')}
                    </Button>
                  </li>
                )}
              </ul>
            )}
          </Card>
        </>
      )}
    </>
  )
}
