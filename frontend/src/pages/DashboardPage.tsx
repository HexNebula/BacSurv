import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import {
  ArrowRight,
  Building2,
  CalendarPlus,
  Check,
  CircleDashed,
  DoorOpen,
  LayoutGrid,
  TriangleAlert,
  Users,
} from 'lucide-react'
import { api } from '../lib/api'
import { screenOf } from '../lib/screens'
import { useWorkspace } from '../context/Workspace'
import { Page } from '../components/Page'
import { Badge, Button, Card, CardHead, CardRule, Empty, Skeleton, Stat } from '../ui'

type State = 'READY' | 'CHECK' | 'TODO'

type Step = {
  key: string
  state: State
  detail: string
  args: string[]
  screen: 'center' | 'teachers' | 'schedule' | 'results'
}

type Readiness = {
  sessionId: number
  reference: string
  centerId: number
  centerName: string
  steps: Step[]
  next: string | null
}

type CenterDetail = {
  id: number
  name: string
  teacherCount: number
  rooms: unknown[]
  sessions: { id: number; slotCount: number; startsOn: string | null; endsOn: string | null }[]
}

const MARKS: Record<State, { tone: 'good' | 'warn' | 'plain'; Icon: typeof Check }> = {
  READY: { tone: 'good', Icon: Check },
  CHECK: { tone: 'warn', Icon: TriangleAlert },
  TODO: { tone: 'plain', Icon: CircleDashed },
}

/**
 * Where the chosen session stands, and what to do next.
 *
 * <p>This is the screen an administrator arrives on, so it answers the question
 * they arrive with — not "here are six sections", but "this is the one thing
 * left, and here is the way in". The six steps are underneath it as a list you
 * can read at a glance: what is done, what needs looking at, what nobody has
 * started.
 */
export function DashboardPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { session, center, centerId, sessionId, sessionsHere, isLoading } = useWorkspace()

  const readiness = useQuery({
    queryKey: ['readiness', sessionId],
    queryFn: () => api.get<Readiness>(`/sessions/${sessionId}/readiness`),
    enabled: sessionId !== null,
  })

  const detail = useQuery({
    queryKey: ['center', centerId],
    queryFn: () => api.get<CenterDetail>(`/centers/${centerId}`),
    enabled: centerId !== null,
  })

  if (isLoading) {
    return (
      <Page title={t('readiness.title')}>
        <Card>
          <Skeleton rows={6} />
        </Card>
      </Page>
    )
  }

  if (!center) {
    return (
      <Page title={t('readiness.title')}>
        <Card>
          <Empty icon={<Building2 size={22} aria-hidden />}>{t('sessionsPage.noCenter')}</Empty>
        </Card>
      </Page>
    )
  }

  if (sessionsHere.length === 0 || !session) {
    return (
      <Page title={center.name}>
        <Card>
          <Empty
            icon={<CalendarPlus size={22} aria-hidden />}
            title={t('sessionsPage.noSession')}
            action={
              <Button onPress={() => void navigate('/center')}>
                <CalendarPlus size={16} aria-hidden />
                {t('sessions.create')}
              </Button>
            }
          >
            {t('dashboard.sessionExplains')}
          </Empty>
        </Card>
      </Page>
    )
  }

  const steps = readiness.data?.steps ?? []
  const next = steps.find((step) => step.key === readiness.data?.next)
  const here = detail.data?.sessions.find((one) => one.id === session.id)

  return (
    <Page title={session.reference} subtitle={`${center.name} · ${t(`sessions.type.${session.type}`)}`}>
      {/* the size of the thing, before any of the detail */}
      {/* the three figures arrive left to right; the sheets carry .rise
          themselves, so only their order has to be said here */}
      <div className="mb-6 grid gap-4 [&>*:nth-child(2)]:[--i:3] [&>*:nth-child(3)]:[--i:4] sm:grid-cols-2 lg:grid-cols-3">
        <Stat
          label={t('dashboard.stat.teachers')}
          value={detail.data?.teacherCount ?? center.teacherCount}
          icon={<Users size={18} aria-hidden />}
          tone="accent"
        />
        <Stat
          label={t('dashboard.stat.rooms')}
          value={detail.data?.rooms.length ?? '—'}
          icon={<DoorOpen size={18} aria-hidden />}
        />
        <Stat
          label={t('dashboard.stat.exams')}
          value={here?.slotCount ?? '—'}
          icon={<LayoutGrid size={18} aria-hidden />}
        />
      </div>

      {/*
        One action, given the whole width. The readiness endpoint already picks
        which of the six steps is blocking; the old screen buried that pick in a
        row of a list, where it read as one item among six.
      */}
      {next && (
        <Card className="rise mb-6 overflow-hidden [--i:5]">
          <div className="flex flex-wrap items-center gap-5 border-s-[3px] border-[var(--color-accent)] bg-[var(--color-accent-tint)] px-6 py-5">
            <div className="min-w-0 flex-1">
              <div className="eyebrow text-[var(--color-accent)]">{t('dashboard.next')}</div>
              <h2 className="mt-1.5 text-[19px] font-semibold tracking-[-0.01em]">
                {t(`readiness.step.${next.key}`)}
              </h2>
              <p className="mt-1 text-[13px] leading-relaxed text-[var(--color-quiet)]">
                {t(`readiness.detail.${next.detail}`, {
                  one: next.args[0] ?? '',
                  two: next.args[1] ?? '',
                  three: next.args[2] ?? '',
                  defaultValue: next.detail,
                })}
              </p>
            </div>
            <Button onPress={() => void navigate(screenOf(next))}>
              {t('readiness.go')}
              <ArrowRight size={16} className="rtl:rotate-180" aria-hidden />
            </Button>
          </div>
        </Card>
      )}

      {!next && steps.length > 0 && (
        <Card className="rise mb-6 [--i:5]">
          <div className="flex flex-wrap items-center gap-5 px-6 py-5">
            <span className="flex size-11 shrink-0 items-center justify-center rounded-[5px] border border-[var(--color-good)]/25 bg-[var(--color-good-tint)] text-[var(--color-good)]">
              <Check size={20} aria-hidden />
            </span>
            <p className="min-w-0 flex-1 text-[14px] font-medium">{t('dashboard.allReady')}</p>
            <Button onPress={() => void navigate('/results')}>
              {t('dashboard.solve')}
              <ArrowRight size={16} className="rtl:rotate-180" aria-hidden />
            </Button>
          </div>
        </Card>
      )}

      <Card className="rise [--i:6]">
        <CardHead
          title={t('dashboard.steps')}
          count={steps.length || undefined}
          actions={
            steps.length > 0 && (
              <span className="numeric text-[12.5px] text-[var(--color-quiet)]">
                {t('dashboard.doneOf', {
                  done: steps.filter((step) => step.state === 'READY').length,
                  total: steps.length,
                })}
              </span>
            )
          }
        />
        <CardRule />

        {readiness.isPending && <Skeleton rows={6} />}

        <ol>
          {steps.map((step) => {
            const { tone, Icon } = MARKS[step.state]
            const marks = {
              good: 'border-[var(--color-good)]/25 bg-[var(--color-good-tint)] text-[var(--color-good)]',
              warn: 'border-[var(--color-warn)]/25 bg-[var(--color-warn-tint)] text-[var(--color-warn)]',
              plain: 'border-[var(--color-hairline)] bg-[var(--color-sunken)] text-[var(--color-faint)]',
            }[tone]

            return (
              <li key={step.key}>
                <Link
                  to={screenOf(step)}
                  className="flex items-center gap-4 border-b border-[var(--color-hairline)] px-5 py-4 transition-colors last:border-b-0 hover:bg-[var(--color-sunken)]"
                >
                  <span
                    className={`flex size-8 shrink-0 items-center justify-center rounded-[4px] border ${marks}`}
                    aria-hidden
                  >
                    <Icon size={15} />
                  </span>

                  <span className="min-w-0 flex-1">
                    <span
                      className={`block text-[14px] ${
                        step.state === 'READY'
                          ? 'text-[var(--color-quiet)]'
                          : 'font-semibold text-[var(--color-ink)]'
                      }`}
                    >
                      {t(`readiness.step.${step.key}`)}
                    </span>
                    <span className="mt-0.5 block text-[12.5px] leading-relaxed text-[var(--color-quiet)]">
                      {t(`readiness.detail.${step.detail}`, {
                        one: step.args[0] ?? '',
                        two: step.args[1] ?? '',
                        three: step.args[2] ?? '',
                        defaultValue: step.detail,
                      })}
                    </span>
                  </span>

                  <Badge tone={tone === 'plain' ? 'plain' : tone}>
                    {t(`dashboard.state.${step.state}`)}
                  </Badge>
                </Link>
              </li>
            )
          })}
        </ol>
      </Card>
    </Page>
  )
}
