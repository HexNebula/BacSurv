import { NavLink, Outlet } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import {
  BookOpen,
  Building2,
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
  { to: '/center', key: 'nav.center', Icon: Building2 },
  { to: '/rooms', key: 'nav.rooms', Icon: DoorOpen },
  { to: '/subjects', key: 'nav.subjects', Icon: BookOpen },
  { to: '/streams', key: 'nav.streams', Icon: GraduationCap },
  { to: '/teachers', key: 'nav.teachers', Icon: Users },
  { to: '/schedule', key: 'nav.schedule', Icon: LayoutGrid },
  { to: '/results', key: 'nav.results', Icon: ListChecks },
  { to: '/statistics', key: 'nav.statistics', Icon: ChartNoAxesColumn },
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
    <div className="rounded-[var(--radius-card)] bg-[var(--color-sunken)] px-3.5 py-3">
      <div className="flex items-baseline justify-between gap-2">
        <span className="text-[11.5px] font-medium text-[var(--color-quiet)]">
          {t('readiness.title')}
        </span>
        <span className="numeric text-[11.5px] font-semibold text-[var(--color-accent-ink)]">
          {done}/{total}
        </span>
      </div>
      <div className="mt-2 flex gap-1" aria-hidden>
        {Array.from({ length: total }, (_, step) => (
          <span
            key={step}
            className={`h-1.5 flex-1 rounded-full ${
              step < done ? 'bg-[var(--color-accent)]' : 'bg-[var(--color-hairline)]'
            }`}
          />
        ))}
      </div>
    </div>
  )
}

/**
 * The frame every screen sits in: a white rail on the left in French, on the
 * right in Arabic. The direction lives on `<html>`, so nothing here needs to
 * know which way round it is — the rail is a flex child, not a thing pinned to
 * a side.
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
      <aside className="no-print sticky top-0 flex h-screen w-[248px] shrink-0 flex-col bg-[var(--color-surface)] px-4 py-6">
        <div className="mb-7 flex items-center gap-3 px-2">
          <span className="flex size-9 items-center justify-center rounded-xl bg-[var(--color-accent)] text-white">
            <CalendarCheck size={18} aria-hidden />
          </span>
          <span className="min-w-0">
            <span className="block truncate text-[15px] font-semibold tracking-[-0.01em]">
              {t('app.name')}
            </span>
            <span className="block truncate text-[11.5px] text-[var(--color-faint)]">
              {center?.name ?? t('app.tagline')}
            </span>
          </span>
        </div>

        <nav className="flex flex-1 flex-col gap-1">
          {SECTIONS.map(({ to, key, Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `group flex items-center gap-3 rounded-[var(--radius-field)] px-3 py-2.5 text-[13.5px] transition-colors ${
                  isActive
                    ? 'bg-[var(--color-accent-tint)] font-semibold text-[var(--color-accent-ink)]'
                    : 'font-medium text-[var(--color-quiet)] hover:bg-[var(--color-sunken)] hover:text-[var(--color-ink)]'
                }`
              }
            >
              {({ isActive }) => (
                <>
                  <Icon
                    size={17}
                    strokeWidth={2}
                    aria-hidden
                    className={
                      isActive ? 'text-[var(--color-accent)]' : 'text-[var(--color-faint)]'
                    }
                  />
                  <span className="min-w-0 flex-1 truncate">{t(key)}</span>
                  {/* the section holding the next thing to do, marked where the
                      eye already is rather than only on the session screen */}
                  {!isActive && to === nextScreen && (
                    <span
                      className="size-1.5 shrink-0 rounded-full bg-[var(--color-warn)]"
                      aria-label={t('readiness.go')}
                    />
                  )}
                </>
              )}
            </NavLink>
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
