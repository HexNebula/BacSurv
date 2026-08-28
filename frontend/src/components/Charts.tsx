import type { ReactNode } from 'react'

/*
 * The two shapes of figure this application needs, drawn in plain elements
 * rather than by a charting library.
 *
 * <p>They are deliberately plain. Every bar carries its own number, so the
 * colour is never the only thing that says how big something is — an
 * administrator reading a printed copy, or a colourblind one reading the
 * screen, gets the same answer. There is one hue, the administration's green,
 * with a second lighter step of it where a bar is split in two; ordered halves
 * of a day are a scale, not two unrelated categories, and drawing them in two
 * different colours would say otherwise.
 */

/** The lighter step of the one hue, for the second half of a split bar. */
const SECOND = '#5f9c7d'

/**
 * How many of something, per bucket: teachers per number of surveillances.
 *
 * <p>Vertical columns, because the buckets are a number line — 0, 1, 2, 3 — and
 * reading them left to right is reading the shape of the distribution. A tall
 * column in the middle and nothing at the ends is a fair week; two towers far
 * apart is not, and that is legible before a single figure is read.
 */
export function Columns({
  bins,
  countLabel,
  binLabel,
}: {
  bins: { bin: number; count: number }[]
  /** What one column counts, for the tooltip: "3 enseignants". */
  countLabel: (count: number) => string
  /** What one bucket is, for the tooltip: "5 surveillances". */
  binLabel: (bin: number) => string
}) {
  const tallest = Math.max(...bins.map((one) => one.count), 1)

  return (
    <div className="flex items-end gap-1.5 overflow-x-auto px-5 pb-4 pt-5" role="list">
      {bins.map(({ bin, count }) => (
        <div
          key={bin}
          role="listitem"
          className="group relative flex min-w-8 flex-1 flex-col items-center gap-1.5"
        >
          {/* the figure sits above its column: never colour alone */}
          <span className="numeric text-[11.5px] font-medium text-[var(--color-quiet)]">
            {count > 0 ? count : ''}
          </span>
          <div
            className="chart-fill w-full rounded-t-[3px] bg-[var(--color-accent)] transition-[height]"
            style={{ height: `${Math.max((count / tallest) * 132, count > 0 ? 3 : 0)}px` }}
          />
          <div className="h-px w-full bg-[var(--color-rule)]" />
          <span className="numeric text-[11.5px] text-[var(--color-faint)]">{bin}</span>

          {/* the hover layer: what this column actually says, in words */}
          <span
            role="tooltip"
            className="pointer-events-none absolute -top-8 z-10 hidden whitespace-nowrap rounded-[4px] border border-[var(--color-rule)] bg-[var(--color-surface)] px-2 py-1 text-[11.5px] shadow-[var(--shadow-raised)] group-hover:block"
          >
            {countLabel(count)} · {binLabel(bin)}
          </span>
        </div>
      ))}
    </div>
  )
}

/**
 * One row per thing, with its bar running along it — and, where a thing has two
 * ordered halves, the bar split in two with a hairline of paper between them.
 *
 * <p>Horizontal, because the labels are dates and names: a vertical chart would
 * turn them on their side, and nobody reads a Tuesday sideways.
 */
export function Bars({
  rows,
  legend,
}: {
  rows: { key: string; label: ReactNode; first: number; second?: number; hint?: string }[]
  /** The names of the two halves, when a row has two. */
  legend?: { first: string; second: string }
}) {
  const widest = Math.max(...rows.map((row) => row.first + (row.second ?? 0)), 1)

  return (
    <div className="px-5 pb-5 pt-4">
      {legend && (
        <div className="mb-3 flex flex-wrap items-center gap-4 text-[11.5px] text-[var(--color-quiet)]">
          <span className="flex items-center gap-1.5">
            <span className="chart-fill size-2.5 rounded-[2px] bg-[var(--color-accent)]" aria-hidden />
            {legend.first}
          </span>
          <span className="flex items-center gap-1.5">
            <span
              className="chart-fill size-2.5 rounded-[2px]"
              style={{ background: SECOND }}
              aria-hidden
            />
            {legend.second}
          </span>
        </div>
      )}

      <div className="flex flex-col gap-2.5" role="list">
        {rows.map((row) => {
          const total = row.first + (row.second ?? 0)
          return (
            <div key={row.key} role="listitem" className="group flex items-center gap-3">
              <span className="w-[140px] shrink-0 truncate text-[12.5px] text-[var(--color-quiet)]">
                {row.label}
              </span>

              <span className="relative flex h-4 min-w-0 flex-1 items-stretch gap-[2px]">
                <span
                  className="chart-fill rounded-[2px] bg-[var(--color-accent)]"
                  style={{ width: `${(row.first / widest) * 100}%` }}
                />
                {row.second !== undefined && row.second > 0 && (
                  <span
                    className="chart-fill rounded-[2px]"
                    style={{ width: `${(row.second / widest) * 100}%`, background: SECOND }}
                  />
                )}
                {row.hint && (
                  <span
                    role="tooltip"
                    className="pointer-events-none absolute -top-7 start-0 z-10 hidden whitespace-nowrap rounded-[4px] border border-[var(--color-rule)] bg-[var(--color-surface)] px-2 py-1 text-[11.5px] shadow-[var(--shadow-raised)] group-hover:block"
                  >
                    {row.hint}
                  </span>
                )}
              </span>

              <span className="numeric w-8 shrink-0 text-end text-[12px] font-medium">{total}</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}
