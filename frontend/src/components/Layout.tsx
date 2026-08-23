import { NavLink, Outlet } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Building2, CalendarDays, Users, LayoutGrid, ListChecks, ChartNoAxesColumn } from 'lucide-react'
import { LANGUAGES, applyLanguage, useLanguage, type Language } from '../i18n'

const SECTIONS = [
  { to: '/centers', key: 'nav.centers', Icon: Building2 },
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
    <div className="flex items-center gap-1 rounded-lg bg-neutral-100 p-0.5">
      {(Object.keys(LANGUAGES) as Language[]).map((language) => (
        <button
          key={language}
          onClick={() => applyLanguage(language)}
          className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
            current === language
              ? 'bg-white text-neutral-900 shadow-sm'
              : 'text-neutral-500 hover:text-neutral-800'
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
      <aside className="no-print flex w-56 shrink-0 flex-col border-e border-neutral-200 bg-white">
        <div className="px-5 py-5">
          <div className="text-lg font-semibold tracking-tight">{t('app.name')}</div>
          <div className="mt-0.5 text-xs leading-snug text-neutral-500">{t('app.tagline')}</div>
        </div>

        <nav className="flex flex-1 flex-col gap-0.5 px-3">
          {SECTIONS.map(({ to, key, Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm transition-colors ${
                  isActive
                    ? 'bg-neutral-900 font-medium text-white'
                    : 'text-neutral-600 hover:bg-neutral-100 hover:text-neutral-900'
                }`
              }
            >
              <Icon size={16} strokeWidth={2} aria-hidden />
              {t(key)}
            </NavLink>
          ))}
        </nav>

        <div className="px-3 py-4">
          <LanguageSwitch />
        </div>
      </aside>

      <main className="min-w-0 flex-1">
        <Outlet />
      </main>
    </div>
  )
}
