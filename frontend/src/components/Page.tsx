import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { TriangleAlert } from 'lucide-react'

export function Page({
  title,
  subtitle,
  actions,
  children,
}: {
  title: string
  subtitle?: string
  actions?: ReactNode
  children: ReactNode
}) {
  return (
    <div className="mx-auto max-w-6xl px-8 py-7">
      <header className="mb-6 flex items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-neutral-500">{subtitle}</p>}
        </div>
        {actions && <div className="no-print flex items-center gap-2">{actions}</div>}
      </header>
      {children}
    </div>
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
    <div className="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3">
      <TriangleAlert size={18} className="mt-0.5 shrink-0 text-red-600" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="text-sm text-red-900">{error.message || t('app.error')}</p>
        {onRetry && (
          <button
            onClick={onRetry}
            className="mt-1.5 text-xs font-medium text-red-700 underline underline-offset-2"
          >
            {t('app.retry')}
          </button>
        )}
      </div>
    </div>
  )
}

export function Loading() {
  return (
    <div className="space-y-2" aria-busy="true">
      {[0, 1, 2].map((row) => (
        <div key={row} className="h-11 animate-pulse rounded-lg bg-neutral-100" />
      ))}
    </div>
  )
}

export function Empty({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-lg border border-dashed border-neutral-300 px-6 py-10 text-center text-sm text-neutral-500">
      {children}
    </div>
  )
}
