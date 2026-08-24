import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ArrowRight, Check, CircleDashed, TriangleAlert } from 'lucide-react'
import { api } from '../lib/api'
import { useWorkspace } from '../context/Workspace'

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

/**
 * Where a session stands, and what to do next.
 *
 * <p>The application always knew this — it has the rooms, the pool, the
 * timetable and the staffing check — but only ever said so as a refusal at the
 * moment somebody pressed solve. An administrator deciding which screen to
 * open deserves to be told beforehand.
 */
export function Readiness() {
  const { t } = useTranslation()
  const { sessionId } = useWorkspace()

  const readiness = useQuery({
    queryKey: ['readiness', sessionId],
    queryFn: () => api.get<Readiness>(`/sessions/${sessionId}/readiness`),
    enabled: sessionId !== null,
  })

  const data = readiness.data
  if (!data) return null

  const done = data.steps.filter((step) => step.state === 'READY').length

  return (
    <section className="mb-6">
      <div className="mb-2.5 flex items-end justify-between gap-4 px-0.5">
        <h2 className="text-[11px] font-semibold uppercase tracking-[0.07em] text-[var(--color-quiet)]">
          {t('readiness.title')}
        </h2>
        <span className="numeric text-[11px] text-[var(--color-quiet)]">
          {done}/{data.steps.length}
        </span>
      </div>

      <ol className="print-clean overflow-hidden rounded-md border border-[var(--color-hairline)] bg-white">
        {data.steps.map((step) => (
          <li
            key={step.key}
            className="flex items-center gap-3 border-b border-[var(--color-hairline)] px-4 py-2.5 last:border-b-0"
          >
            <Mark state={step.state} />

            <span className="min-w-0 flex-1">
              <span
                className={`block text-[13px] ${
                  step.state === 'READY'
                    ? 'text-[var(--color-quiet)]'
                    : 'font-medium text-[var(--color-ink)]'
                }`}
              >
                {t(`readiness.step.${step.key}`)}
              </span>
              <span className="mt-0.5 block text-[11px] text-[var(--color-quiet)]">
                {t(`readiness.detail.${step.detail}`, {
                  one: step.args[0] ?? '',
                  two: step.args[1] ?? '',
                  three: step.args[2] ?? '',
                  defaultValue: step.detail,
                })}
              </span>
            </span>

            {/* only the step actually blocking gets a way in: six links would
                be a menu, not a next action */}
            {data.next === step.key && (
              <Link
                to={
                  `/${step.screen}`
                }
                className="flex shrink-0 items-center gap-1.5 rounded-md bg-[var(--color-brand)] px-3 py-1.5 text-[12px] font-medium text-white transition-opacity hover:opacity-90"
              >
                {t('readiness.go')}
                <ArrowRight size={13} className="rtl:rotate-180" aria-hidden />
              </Link>
            )}
          </li>
        ))}
      </ol>
    </section>
  )
}

function Mark({ state }: { state: State }) {
  if (state === 'READY') {
    return (
      <span
        className="flex size-5 shrink-0 items-center justify-center rounded-full bg-[var(--color-brand)]/10"
        aria-hidden
      >
        <Check size={12} className="text-[var(--color-brand)]" />
      </span>
    )
  }
  if (state === 'CHECK') {
    return (
      <TriangleAlert size={16} className="shrink-0 text-amber-500" aria-hidden />
    )
  }
  return <CircleDashed size={16} className="shrink-0 text-[var(--color-hairline)]" aria-hidden />
}
