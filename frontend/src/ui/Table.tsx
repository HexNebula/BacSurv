import type { ReactNode } from 'react'

/**
 * A table inside a card.
 *
 * <p>Rows are tall and separated by a hairline rather than boxed, and the header
 * is a small muted line rather than a shaded band: forty-five rows should read
 * as a list of people, not as a spreadsheet. The hover tint is the only thing
 * that moves.
 */
export function Table({ children }: { children: ReactNode }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse">{children}</table>
    </div>
  )
}

export function Th({
  children,
  className = '',
  width,
}: {
  children?: ReactNode
  className?: string
  width?: string
}) {
  return (
    <th
      style={width ? { width } : undefined}
      className={`border-b border-[var(--color-hairline)] px-5 pb-2.5 pt-1 text-start text-[11.5px]
        font-medium tracking-[0.02em] text-[var(--color-faint)] ${className}`}
    >
      {children}
    </th>
  )
}

export function Tr({ children }: { children: ReactNode }) {
  return (
    <tr className="border-b border-[var(--color-hairline)] transition-colors last:border-b-0 hover:bg-[var(--color-sunken)]">
      {children}
    </tr>
  )
}

export function Td({
  children,
  className = '',
}: {
  children: ReactNode
  className?: string
}) {
  return <td className={`px-5 py-3.5 align-middle text-[13.5px] ${className}`}>{children}</td>
}

/**
 * Nothing here yet, said plainly with the way out attached — a dashed rectangle
 * holding a full stop tells an administrator nothing about what to do next.
 */
export function Empty({
  icon,
  title,
  children,
  action,
}: {
  icon?: ReactNode
  title?: string
  children: ReactNode
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center gap-3 px-6 py-14 text-center">
      {icon && (
        <span className="mb-1 flex size-12 items-center justify-center rounded-2xl bg-[var(--color-sunken)] text-[var(--color-faint)]">
          {icon}
        </span>
      )}
      {title && <p className="text-[15px] font-semibold">{title}</p>}
      <p className="max-w-sm text-[13px] leading-relaxed text-[var(--color-quiet)]">{children}</p>
      {action && <div className="mt-1">{action}</div>}
    </div>
  )
}

/** The shape of a table before its rows arrive: rows, not a spinner. */
export function Skeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div aria-busy="true">
      {Array.from({ length: rows }, (_, row) => (
        <div
          key={row}
          className="flex items-center gap-4 border-b border-[var(--color-hairline)] px-5 py-4 last:border-b-0"
        >
          <div className="h-3 w-20 animate-pulse rounded-full bg-[var(--color-hairline)]" />
          <div
            className="h-3 animate-pulse rounded-full bg-[var(--color-hairline)]"
            style={{ width: `${170 - row * 14}px` }}
          />
          <div className="h-3 w-24 animate-pulse rounded-full bg-[var(--color-hairline)]/60" />
        </div>
      ))}
    </div>
  )
}
