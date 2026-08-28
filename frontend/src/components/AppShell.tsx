import { NavLink, Outlet } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import {
  BookOpen,
  Building2,
  CalendarRange,
  CalendarCheck,
  ChartNoAxesColumn,
  DoorOpen,
  GraduationCap,
  Home,
  LayoutGrid,
  ListChecks,
  Users,
} from 'lucide-react'
import { api } from '../lib/api'
import { screenOf } from '../lib/screens'
import { useWorkspace } from '../context/Workspace'

/*
 * Each thing an administrator sets up is a place to go, not a section buried
 * inside another screen: the rooms, the subjects and the filières used to exist
 * only inside the centre's page and inside a dialog in the planning grid, which
 * meant there was no way to simply look at them.
 */
const SECTIONS = [
  { to: '/sessions', key: 'nav.home', Icon: Home },
] as const

/*
 * The rail is the index of a dossier, so it is divided the way the work is: what
 * the établissement is and holds, then what this session does with it. Nine flat
 * links in a column made the eye count them; two short lists under their own
 * headings are read at a glance.
 */
const GROUPS = [
  {
    key: 'nav.groupCenter',
    items: [
      { to: '/center', key: 'nav.center', Icon: Building2 },
      { to: '/rooms', key: 'nav.rooms', Icon: DoorOpen },
      { to: '/subjects', key: 'nav.subjects', Icon: BookOpen },
      { to: '/streams', key: 'nav.streams', Icon: GraduationCap },
      { to: '/teachers', key: 'nav.teachers', Icon: Users },
      { to: '/years', key: 'nav.years', Icon: CalendarRange },
    ],
  },
  {
    key: 'nav.groupSession',
    items: [
      { to: '/schedule', key: 'nav.schedule', Icon: LayoutGrid },
      { to: '/results', key: 'nav.results', Icon: ListChecks },
      { to: '/statistics', key: 'nav.statistics', Icon: ChartNoAxesColumn },
    ],
  },
] as const

type Step = { key: string; state: 'READY' | 'CHECK' | 'TODO'; screen: string }

type Readiness = { steps: Step[]; next: string | null }

/**
 * How far the session has got, in the rail.
 *
 * <p>The old rail was six links and nothing else — a fifth of the screen
 * telling you what you could already see in the URL. The application knows how
 * many of the six steps are done and which screen the next one is on, so the
 * rail says it, and the section that needs attention is marked where the eye
 * already is.
 */
function useReadiness() {
  const { sessionId } = useWorkspace()
  return useQuery({
    queryKey: ['readiness', sessionId],
    queryFn: () => api.get<Readiness>(`/sessions/${sessionId}/readiness`),
    enabled: sessionId !== null,
  })
}

function Progress({ done, total }: { done: number; total: number }) {
  const { t } = useTranslation()
  return (
    <div className="rounded-[var(--radius-card)] border border-[var(--color-hairline)] bg-[var(--color-sunken)] px-3.5 py-3">
      <div className="flex items-baseline justify-between gap-2">
        <span className="eyebrow">{t('readiness.title')}</span>
        <span className="numeric text-[11.5px] font-semibold text-[var(--color-accent-ink)]">
          {done}/{total}
        </span>
      </div>
      <div className="mt-2 flex gap-1" aria-hidden>
        {Array.from({ length: total }, (_, step) => (
          <span
            key={step}
            className={`h-2 flex-1 rounded-[1px] ${
              step < done
                ? 'bg-[var(--color-accent)]'
                : 'border border-[var(--color-hairline)] bg-[var(--color-surface)]'
            }`}
          />
        ))}
      </div>
    </div>
  )
}

/**
 * One entry in the index: an icon, a name, and — when it is the one you are on —
 * a mark down its spine in the administration's green, the way a tab is turned
 * out of a file to show where you stopped reading.
 */
function Entry({
  to,
  label,
  Icon,
  flagged,
  flagLabel,
}: {
  to: string
  label: string
  Icon: typeof Home
  flagged: boolean
  flagLabel: string
}) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        `group flex items-center gap-3 border-s-2 py-2 pe-2 ps-3 text-[13.5px] transition-colors ${
          isActive
            ? 'border-[var(--color-accent)] bg-[var(--color-accent-tint)] font-semibold text-[var(--color-accent-ink)]'
            : 'border-transparent font-medium text-[var(--color-quiet)] hover:border-[var(--color-rule)] hover:bg-[var(--color-sunken)] hover:text-[var(--color-ink)]'
        }`
      }
    >
      {({ isActive }) => (
        <>
          <Icon
            size={16}
            strokeWidth={2}
            aria-hidden
            className={isActive ? 'text-[var(--color-accent)]' : 'text-[var(--color-faint)]'}
          />
          <span className="min-w-0 flex-1 truncate">{label}</span>
          {/* the section holding the next thing to do, marked where the eye
              already is rather than only on the session screen */}
          {!isActive && flagged && (
            <span
              className="size-1.5 shrink-0 rounded-[1px] bg-[var(--color-warn)]"
              aria-label={flagLabel}
            />
          )}
        </>
      )}
    </NavLink>
  )
}

/**
 * The frame every screen sits in: the index on the left in French, on the right
 * in Arabic. The direction lives on `<html>`, so nothing here needs to know
 * which way round it is — the rail is a flex child, not a thing pinned to a
 * side.
 *
 * <p>It is paper too, but a plainer sheet than the ones in the middle: no
 * ruling, one rule down its edge, so the eye reads the desk as a bound folder
 * with the work opened beside it.
 */
export function AppShell() {
  const { t } = useTranslation()
  const { center } = useWorkspace()
  const readiness = useReadiness()

  const steps = readiness.data?.steps ?? []
  const done = steps.filter((step) => step.state === 'READY').length
  // the screen the next unfinished step lives on, so the rail can point at it
  const next = steps.find((step) => step.key === readiness.data?.next)
  const nextScreen = next ? screenOf(next) : undefined

  return (
    <div className="flex min-h-screen">
      <aside className="no-print sticky top-0 flex h-screen w-[244px] shrink-0 flex-col gap-6 overflow-y-auto border-e border-[var(--color-rule)] bg-[var(--color-surface)] px-3 py-5">
        <div className="flex items-center gap-3 px-2">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-[4px] bg-[var(--color-accent)] text-[var(--color-surface)]">
            <CalendarCheck size={17} aria-hidden />
          </span>
          <span className="min-w-0">
            <span className="block truncate text-[14.5px] font-semibold tracking-[-0.01em]">
              {t('app.name')}
            </span>
            <span className="block truncate text-[11.5px] text-[var(--color-faint)]">
              {center?.name ?? t('app.tagline')}
            </span>
          </span>
        </div>

        <nav className="flex flex-1 flex-col gap-5">
          <div className="flex flex-col">
            {SECTIONS.map(({ to, key, Icon }) => (
              <Entry
                key={to}
                to={to}
                label={t(key)}
                Icon={Icon}
                flagged={to === nextScreen}
                flagLabel={t('readiness.go')}
              />
            ))}
          </div>

          {GROUPS.map((group) => (
            <div key={group.key} className="flex flex-col">
              <h2 className="eyebrow mb-1.5 px-3">{t(group.key)}</h2>
              {group.items.map(({ to, key, Icon }) => (
                <Entry
                  key={to}
                  to={to}
                  label={t(key)}
                  Icon={Icon}
                  flagged={to === nextScreen}
                  flagLabel={t('readiness.go')}
                />
              ))}
            </div>
          ))}
        </nav>

        {steps.length > 0 && <Progress done={done} total={steps.length} />}
      </aside>

      <main className="min-w-0 flex-1">
        <Outlet />
      </main>
    </div>
  )
}
