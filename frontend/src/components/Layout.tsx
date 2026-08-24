import { NavLink, Outlet } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Building2, CalendarDays, Users, LayoutGrid, ListChecks, ChartNoAxesColumn } from 'lucide-react'
import { LANGUAGES, applyLanguage, useLanguage, type Language } from '../i18n'
import { WorkspaceBar } from './WorkspaceBar'

const SECTIONS = [
  { to: '/center', key: 'nav.center', Icon: Building2 },
  { to: '/sessions', key: 'nav.sessions', Icon: CalendarDays },
  { to: '/teachers', key: 'nav.teachers', Icon: Users },
  { to: '/schedule', key: 'nav.schedule', Icon: LayoutGrid },
  { to: '/results', key: 'nav.results', Icon: ListChecks },
  { to: '/statistics', key: 'nav.statistics', Icon: ChartNoAxesColumn },
] as const

function LanguageSwitch() {
  // the same source the whole app reads, so the button and React Aria's
  // formatting can never disagree about which language is on
  const current = useLanguage()

  return (
    <div className="flex items-center gap-0.5 rounded-md bg-white/5 p-0.5">
      {(Object.keys(LANGUAGES) as Language[]).map((language) => (
        <button
          key={language}
          onClick={() => applyLanguage(language)}
          className={`flex-1 rounded px-2 py-1 text-xs font-medium transition-colors ${
            current === language
              ? 'bg-white/15 text-white'
              : 'text-white/45 hover:text-white/80'
          }`}
        >
          {LANGUAGES[language].label}
        </button>
      ))}
    </div>
  )
}

/**
 * A side rail rather than a top bar: the sections are fixed and few, and the
 * screens underneath are wide tables that want the vertical room. It mirrors
 * with the page in Arabic because the direction lives on <html>, so nothing
 * here needs to know which way round it is.
 */
export function Layout() {
  const { t } = useTranslation()

  return (
    <div className="flex min-h-screen">
      {/*
        The rail is the dark surface and the only one — everything to its side
        is paper. Making it ink rather than white means the active section can
        be marked with plain contrast instead of a heavy black block sitting on
        a white page and out-weighing the content.
      */}
      <aside className="no-print flex w-60 shrink-0 flex-col bg-[var(--color-rail)]">
        <div className="px-5 pb-6 pt-6">
          <div className="text-[15px] font-semibold tracking-tight text-white">{t('app.name')}</div>
          <div className="mt-1 text-xs leading-snug text-white/45">{t('app.tagline')}</div>
        </div>

        <nav className="flex flex-1 flex-col gap-px px-3">
          {SECTIONS.map(({ to, key, Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `group flex items-center gap-3 rounded-md px-3 py-2 text-[13px] transition-colors ${
                  isActive
                    ? 'bg-white/10 font-medium text-white'
                    : 'text-white/55 hover:bg-white/5 hover:text-white/90'
                }`
              }
            >
              {({ isActive }) => (
                <>
                  <Icon
                    size={16}
                    strokeWidth={2}
                    className={isActive ? 'text-[var(--color-brand)]' : ''}
                    aria-hidden
                  />
                  {t(key)}
                </>
              )}
            </NavLink>
          ))}
        </nav>

        <div className="px-3 pb-4 pt-6">
          <LanguageSwitch />
        </div>
      </aside>

      <main className="min-w-0 flex-1">
        <WorkspaceBar />
        <Outlet />
      </main>
    </div>
  )
}
