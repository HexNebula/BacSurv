import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { RotateCw, TriangleAlert } from 'lucide-react'

export type Tone = 'good' | 'warn' | 'alarm' | 'accent' | 'plain'

const TONES: Record<Tone, string> = {
  good: 'bg-[var(--color-good-tint)] text-[var(--color-good)]',
  warn: 'bg-[var(--color-warn-tint)] text-[var(--color-warn)]',
  alarm: 'bg-[var(--color-alarm-tint)] text-[var(--color-alarm)]',
  accent: 'bg-[var(--color-accent-tint)] text-[var(--color-accent-ink)]',
  plain: 'bg-[var(--color-sunken)] text-[var(--color-quiet)]',
}

/** A word carrying a state: a role, a count, a step that is done. */
export function Badge({
  tone = 'plain',
  icon,
  children,
}: {
  tone?: Tone
  icon?: ReactNode
  children: ReactNode
}) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11.5px] font-medium ${TONES[tone]}`}
    >
      {icon}
      {children}
    </span>
  )
}

/**
 * A request that failed says what went wrong in the administrator's own terms —
 * the server sends a whole sentence, already translated — so it is shown as
 * written rather than replaced with a generic apology.
 */
export function Failed({ error, onRetry }: { error: Error; onRetry?: () => void }) {
  const { t } = useTranslation()
  return (
    <div className="m-5 flex items-start gap-3 rounded-[var(--radius-card)] bg-[var(--color-alarm-tint)] px-4 py-3.5">
      <TriangleAlert size={17} className="mt-px shrink-0 text-[var(--color-alarm)]" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="text-[13px] leading-relaxed">{error.message || t('app.error')}</p>
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="mt-2 inline-flex items-center gap-1.5 text-[12px] font-medium text-[var(--color-alarm)] hover:underline"
          >
            <RotateCw size={13} aria-hidden />
            {t('app.retry')}
          </button>
        )}
      </div>
    </div>
  )
}

/**
 * Something true about the session that the administrator should see but that
 * does not stop them: a subject nobody teaches, a filière with no rooms.
 */
export function Notice({
  tone = 'warn',
  icon,
  children,
  action,
}: {
  tone?: Tone
  icon?: ReactNode
  children: ReactNode
  action?: ReactNode
}) {
  return (
    <div
      className={`flex items-center gap-3 rounded-[var(--radius-card)] px-4 py-3 text-[13px] ${TONES[tone]}`}
    >
      {icon}
      <span className="min-w-0 flex-1 leading-relaxed text-[var(--color-ink)]">{children}</span>
      {action}
    </div>
  )
}
