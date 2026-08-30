import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { RotateCw, TriangleAlert } from 'lucide-react'

export type Tone = 'good' | 'warn' | 'alarm' | 'accent' | 'plain'

const TONES: Record<Tone, string> = {
  good: 'border-[var(--color-good)]/25 bg-[var(--color-good-tint)] text-[var(--color-good)]',
  warn: 'border-[var(--color-warn)]/25 bg-[var(--color-warn-tint)] text-[var(--color-warn)]',
  alarm: 'border-[var(--color-alarm)]/25 bg-[var(--color-alarm-tint)] text-[var(--color-alarm)]',
  accent: 'border-[var(--color-accent)]/25 bg-[var(--color-accent-tint)] text-[var(--color-accent-ink)]',
  plain: 'border-[var(--color-rule)] bg-[var(--color-sunken)] text-[var(--color-quiet)]',
}

/**
 * A word carrying a state: a role, a count, a step that is done.
 *
 * <p>Written on the sheet as a tag would be — squared and ruled in its own ink —
 * rather than as a lozenge. The rule is what makes a pale tint legible on paper
 * that is itself warm.
 */
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
      className={`inline-flex items-center gap-1.5 rounded-[3px] border px-2 py-0.5 text-[11.5px] font-medium ${TONES[tone]}`}
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
    <div className="m-5 flex items-start gap-3 rounded-[var(--radius-card)] border-s-[3px] border-[var(--color-alarm)] bg-[var(--color-alarm-tint)] px-4 py-3.5">
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
 * The edges of a notice, which is what tells it from the paper.
 *
 * <p>A tint alone cannot do it: the ground here is bone, and a pale wash of
 * warm colour laid on warm paper is four percent of difference and no edge —
 * it reads as a stain rather than as something placed. So the tint is taken
 * back and the tone is carried by a rule down the leading side, the way a file
 * is marked, with a hairline closing the other three. The same construction as
 * a failed request, which is the one thing on the page that already looked
 * deliberate.
 */
const NOTICE_TONES: Record<Tone, string> = {
  good: 'border-[var(--color-good)]/20 border-s-[var(--color-good)] bg-[var(--color-good-tint)]/55',
  warn: 'border-[var(--color-warn)]/20 border-s-[var(--color-warn)] bg-[var(--color-warn-tint)]/55',
  alarm:
    'border-[var(--color-alarm)]/20 border-s-[var(--color-alarm)] bg-[var(--color-alarm-tint)]/55',
  accent:
    'border-[var(--color-accent)]/20 border-s-[var(--color-accent)] bg-[var(--color-accent-tint)]/60',
  plain: 'border-[var(--color-hairline)] border-s-[var(--color-rule)] bg-[var(--color-sunken)]/60',
}

/** The mark keeps the tone at full strength; the ground does not need it. */
const NOTICE_MARKS: Record<Tone, string> = {
  good: 'text-[var(--color-good)]',
  warn: 'text-[var(--color-warn)]',
  alarm: 'text-[var(--color-alarm)]',
  accent: 'text-[var(--color-accent)]',
  plain: 'text-[var(--color-faint)]',
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
      className={`flex items-center gap-3 rounded-[var(--radius-card)] border border-s-[3px] px-4 py-3 text-[13px] ${NOTICE_TONES[tone]}`}
    >
      {icon && <span className={`shrink-0 ${NOTICE_MARKS[tone]}`}>{icon}</span>}
      <span className="min-w-0 flex-1 leading-relaxed text-[var(--color-ink)]">{children}</span>
      {action}
    </div>
  )
}
