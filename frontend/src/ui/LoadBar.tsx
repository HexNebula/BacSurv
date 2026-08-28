/**
 * A figure and its size, side by side in one table cell.
 *
 * <p>A column of counts is read one row at a time; a column of bars is read at
 * a glance. The bar is not decoration — the question an administrator has about
 * a duty roster is whether the work fell evenly, and a flat column answers it
 * without anybody adding up 45 numbers. The number stays because the moment a
 * teacher disputes their share, "about this long" is not an answer.
 *
 * <p>Scaled against the heaviest load rather than against a round figure, so
 * the longest bar is always full: what matters is how the rest compare to it.
 */
export function LoadBar({ value, of }: { value: number; of: number }) {
  const share = of > 0 ? Math.max(value / of, value > 0 ? 0.06 : 0) : 0

  return (
    <div className="flex items-center gap-2.5">
      <span className="numeric w-6 shrink-0 text-end font-medium">{value}</span>
      {/* aria-hidden: the figure beside it already says this to a screen
          reader, and a second reading of the same number is noise */}
      <span
        aria-hidden
        className="h-2 min-w-0 flex-1 rounded-[2px] border border-[var(--color-hairline)] bg-[var(--color-sunken)]"
      >
        <span
          className="block h-full rounded-[1px] bg-[var(--color-accent)] transition-[width] duration-300"
          style={{ width: `${share * 100}%` }}
        />
      </span>
    </div>
  )
}
