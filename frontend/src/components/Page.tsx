import type { ReactNode } from 'react'
import { SessionPicker } from './SessionPicker'
import { LanguageSwitch } from './LanguageSwitch'

/**
 * One screen, headed the way a document is headed.
 *
 * <p>A thin strip carries the two facts every screen is relative to — which
 * session, which language — and is ruled off from the screen's own name. Then
 * the name itself, in the largest type in the application, with a rule drawn
 * under it as the page settles. Then what you can do, then the thing itself.
 *
 * <p>The order never changes, because an administrator who has found the title
 * once should not have to look for it again on the next screen. Everything
 * steps down sharply from it — a heading, a section label and a table row must
 * not read as the same thing, which is what happens when the only tool used is
 * a grey.
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
    <div className="mx-auto max-w-6xl px-8 pb-16 pt-6">
      {/* the file's own header line: what this is relative to, then a rule */}
      <div className="no-print mb-5 flex flex-wrap items-center justify-end gap-2.5 border-b border-[var(--color-hairline)] pb-3">
        <SessionPicker />
        <LanguageSwitch />
      </div>

      <header className="rise mb-6 [--i:0]">
        <h1 className="text-[30px] font-semibold leading-[1.15] tracking-[-0.025em]">{title}</h1>
        {/* the one piece of theatre: the rule under the title draws itself */}
        <div className="rule-in mt-3 h-[2px] w-full bg-[var(--color-ink)] [--i:1]" />
        {subtitle && (
          <p className="mt-2.5 text-[13.5px] leading-relaxed text-[var(--color-quiet)]">
            {subtitle}
          </p>
        )}
      </header>

      {(tabs || actions) && (
        <div className="no-print mb-6 flex flex-wrap items-end justify-between gap-3">
          <div className="min-w-0">{tabs}</div>
          <div className="flex flex-wrap items-center gap-2">{actions}</div>
        </div>
      )}

      {children}
    </div>
  )
}
