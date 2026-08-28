import type { ReactNode } from 'react'

/**
 * A sheet laid on the desk.
 *
 * <p>Paper is read by its edge, not by a glow: the card carries a real rule
 * around it and the barest lift, so it sits on the ruled bone ground the way a
 * document sits on a table. `print-clean` flattens it back to a printable box.
 *
 * <p>Every sheet arrives with the page rather than being painted onto it: the
 * card carries `.rise` itself, so a screen settles in one movement without each
 * page having to remember to ask for it. `--i` orders the arrival where a screen
 * has more than one sheet.
 */
export function Card({
  className = '',
  children,
}: {
  className?: string
  children: ReactNode
}) {
  return (
    <section
      className={`rise print-clean rounded-[var(--radius-card)] border border-[var(--color-hairline)] bg-[var(--color-surface)] shadow-[var(--shadow-card)] ${className}`}
    >
      {children}
    </section>
  )
}

/**
 * The line across the top of a card: what it is, how many, and what you can do
 * to it. The count is set as a figure in the register hand, ruled off from the
 * title — the size of the thing is legible before a single row is read.
 */
export function CardHead({
  title,
  count,
  actions,
  className = '',
}: {
  title: ReactNode
  count?: number
  actions?: ReactNode
  className?: string
}) {
  return (
    <header
      className={`flex flex-wrap items-center justify-between gap-3 px-5 py-4 ${className}`}
    >
      <div className="flex min-w-0 items-center gap-2.5">
        <h2 className="truncate text-[17px] font-semibold tracking-[-0.01em]">{title}</h2>
        {count !== undefined && (
          <span className="numeric rounded-[3px] border border-[var(--color-accent)]/25 bg-[var(--color-accent-tint)] px-1.5 py-0.5 text-[11.5px] font-medium text-[var(--color-accent-ink)]">
            {count}
          </span>
        )}
      </div>
      {actions && <div className="no-print flex items-center gap-2">{actions}</div>}
    </header>
  )
}

/** A rule inside a card, drawn edge to edge rather than inset. */
export function CardRule() {
  return <div className="h-px bg-[var(--color-rule)]" />
}

/**
 * A figure worth a glance: teachers in the pool, épreuves in the timetable.
 * The number is large and tabular, the label small — the opposite of the old
 * screens, where a count and its caption were the same size.
 */
export function Stat({
  label,
  value,
  hint,
  icon,
  tone = 'plain',
}: {
  label: string
  value: ReactNode
  hint?: ReactNode
  icon?: ReactNode
  tone?: 'plain' | 'accent' | 'warn'
}) {
  const marks = {
    plain: 'bg-[var(--color-sunken)] text-[var(--color-quiet)]',
    accent: 'bg-[var(--color-accent-tint)] text-[var(--color-accent-ink)]',
    warn: 'bg-[var(--color-warn-tint)] text-[var(--color-warn)]',
  }[tone]

  return (
    <Card className="flex items-center gap-3.5 px-4 py-3.5">
      {icon && (
        <span className={`flex size-9 shrink-0 items-center justify-center rounded-[4px] ${marks}`}>
          {icon}
        </span>
      )}
      <div className="min-w-0">
        <div className="numeric text-[22px] font-semibold leading-none tracking-[-0.02em]">
          {value}
        </div>
        <div className="mt-1 truncate text-[12.5px] text-[var(--color-quiet)]">{label}</div>
        {hint && <div className="mt-0.5 truncate text-[11.5px] text-[var(--color-faint)]">{hint}</div>}
      </div>
    </Card>
  )
}
