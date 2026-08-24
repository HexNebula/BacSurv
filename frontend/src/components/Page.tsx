import type { ReactNode } from 'react'
import { SessionPicker } from './SessionPicker'
import { LanguageSwitch } from './LanguageSwitch'

/**
 * One screen.
 *
 * <p>Three bands, always in the same order: what this screen is, then what you
 * can do to it, then the thing itself. The title carries real weight and
 * everything under it steps down sharply — a heading, a section label and a
 * table row must not all read as the same thing, which is what happens when the
 * only tool used is a grey.
 *
 * <p>The session picker and the language switch live here rather than in the
 * rail: they are the two facts every screen is relative to, so they sit on the
 * line that says which screen you are on.
 */
export function Page({
  title,
  subtitle,
  tabs,
  actions,
  children,
}: {
  title: ReactNode
  subtitle?: ReactNode
  /** The states of this screen's list, if it has more than one. */
  tabs?: ReactNode
  actions?: ReactNode
  children: ReactNode
}) {
  return (
    <div className="mx-auto max-w-6xl px-8 pb-16 pt-8">
      <header className="mb-7 flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <h1 className="text-[27px] font-semibold leading-tight tracking-[-0.02em]">{title}</h1>
          {subtitle && (
            <p className="mt-1.5 text-[13.5px] text-[var(--color-quiet)]">{subtitle}</p>
          )}
        </div>

        <div className="no-print flex shrink-0 flex-wrap items-center gap-2.5">
          <SessionPicker />
          <LanguageSwitch />
        </div>
      </header>

      {(tabs || actions) && (
        <div className="no-print mb-6 flex flex-wrap items-center justify-between gap-3">
          <div className="min-w-0">{tabs}</div>
          <div className="flex flex-wrap items-center gap-2">{actions}</div>
        </div>
      )}

      {children}
    </div>
  )
}
