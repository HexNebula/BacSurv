import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { CalendarRange } from 'lucide-react'
import { api } from '../lib/api'
import { useWorkspace } from '../context/Workspace'

type Year = { id: number; label: string; current: boolean }

/**
 * Which school year everything on the screen belongs to.
 *
 * <p>The header used to name the session and nothing else, which left the one
 * fact behind both halves of the model unsaid: the year decides who is in the
 * pool, and réserve and permanence are counted inside it and start again in
 * September. A session picker with no year above it invites the question "level
 * with whom?" and answers it nowhere.
 *
 * <p>It is a label rather than a switch. There is one year in progress and the
 * pool is always its own; reading an older one is a different act, on its own
 * screen, which is where this points.
 */
export function YearMark() {
  const { t } = useTranslation()
  const { centerId } = useWorkspace()

  const years = useQuery({
    queryKey: ['years', centerId],
    queryFn: () => api.get<Year[]>(`/centers/${centerId}/years`),
    enabled: centerId !== null,
  })

  const current = years.data?.find((year) => year.current)
  if (!current) return null

  return (
    <Link
      to="/years"
      title={t('years.title')}
      className="flex items-center gap-2 rounded-[var(--radius-field)] px-2.5 py-1.5 text-[12.5px] text-[var(--color-quiet)] transition-colors hover:bg-[var(--color-sunken)] hover:text-[var(--color-ink)]"
    >
      <CalendarRange size={14} className="text-[var(--color-faint)]" aria-hidden />
      <span className="numeric">{current.label}</span>
    </Link>
  )
}
