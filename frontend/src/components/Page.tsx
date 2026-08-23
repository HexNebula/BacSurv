import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { TriangleAlert } from 'lucide-react'

/**
 * One screen. The title carries real weight and everything under it steps
 * down sharply — a heading, a section label and a table row must not all read
 * as the same thing, which is what happens when the only tool used is a grey.
 */
export function Page({
  title,
  subtitle,
  actions,
  children,
}: {
  title: string
  subtitle?: ReactNode
  actions?: ReactNode
  children: ReactNode
}) {
  return (
    <div className="mx-auto max-w-5xl px-10 py-9">
      <header className="mb-7 flex items-end justify-between gap-6">
        <div className="min-w-0">
          <h1 className="text-[22px] font-semibold leading-tight tracking-[-0.01em]">{title}</h1>
          {subtitle && (
            <p className="mt-1.5 text-[13px] text-[var(--color-quiet)]">{subtitle}</p>
          )}
        </div>
        {actions && <div className="no-print flex shrink-0 items-center gap-2">{actions}</div>}
      </header>
      {children}
    </div>
  )
}

/**
 * A panel is one thing being set up. The label above it is small and set in
 * caps rather than made big — it names the group without competing with the
 * page title, and the count sits beside it so the size of the thing is legible
 * before you read a single row.
 */
export function Panel({
  title,
  count,
  hint,
  actions,
  children,
  footer,
}: {
  title: string
  count?: number
  hint?: string
  actions?: ReactNode
  children: ReactNode
  footer?: ReactNode
}) {
  return (
    <section className="mb-6">
      <div className="mb-2.5 flex items-end justify-between gap-4 px-0.5">
        <div className="flex items-baseline gap-2">
          <h2 className="text-[11px] font-semibold uppercase tracking-[0.07em] text-[var(--color-quiet)]">
            {title}
          </h2>
          {count !== undefined && (
            <span className="numeric text-[11px] font-medium text-[var(--color-quiet)]/70">
              {count}
            </span>
          )}
        </div>
        {actions && <div className="no-print flex items-center gap-2">{actions}</div>}
      </div>

      <div className="print-clean overflow-hidden rounded-md border border-[var(--color-hairline)] bg-white">
        {children}
        {footer && (
          <div className="border-t border-[var(--color-hairline)] px-4 py-3">{footer}</div>
        )}
      </div>

      {hint && <p className="mt-2 px-0.5 text-xs text-[var(--color-quiet)]">{hint}</p>}
    </section>
  )
}

/**
 * A failed request says what went wrong in the administrator's own terms —
 * the server sends a sentence, not a code — so it is shown as written rather
 * than replaced with a generic apology.
 */
export function Failed({ error, onRetry }: { error: Error; onRetry?: () => void }) {
  const { t } = useTranslation()
  return (
    <div className="flex items-start gap-3 rounded-md border border-[var(--color-alarm)]/25 bg-[var(--color-alarm)]/[0.04] px-4 py-3.5">
      <TriangleAlert size={17} className="mt-px shrink-0 text-[var(--color-alarm)]" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="text-[13px] leading-relaxed text-[var(--color-ink)]">
          {error.message || t('app.error')}
        </p>
        {onRetry && (
          <button
            onClick={onRetry}
            className="mt-2 text-xs font-medium text-[var(--color-alarm)] hover:underline"
          >
            {t('app.retry')}
          </button>
        )}
      </div>
    </div>
  )
}

export function Loading({ rows = 3 }: { rows?: number }) {
  return (
    <div className="divide-y divide-[var(--color-hairline)]" aria-busy="true">
      {Array.from({ length: rows }, (_, row) => (
        <div key={row} className="flex items-center gap-4 px-4 py-3">
          <div
            className="h-3 animate-pulse rounded-sm bg-[var(--color-hairline)]"
            style={{ width: `${120 - row * 12}px` }}
          />
          <div className="h-3 w-16 animate-pulse rounded-sm bg-[var(--color-hairline)]/60" />
        </div>
      ))}
    </div>
  )
}

/**
 * Nothing here yet, said plainly and with the way out attached. A dashed
 * rectangle holding a full stop tells an administrator nothing about what to
 * do next.
 */
export function Empty({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-3 px-6 py-12 text-center">
      <p className="max-w-sm text-[13px] leading-relaxed text-[var(--color-quiet)]">{children}</p>
      {action}
    </div>
  )
}
