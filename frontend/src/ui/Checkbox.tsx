import type { ReactNode } from 'react'
import { Checkbox as AriaCheckbox } from 'react-aria-components'
import { Check } from 'lucide-react'

/**
 * One room ticked for a filière.
 *
 * <p>Used in a grid of a dozen or more, so the whole row is the target rather
 * than the box alone: picking salles 6 to 10 should not be five small aims.
 */
export function Checkbox({
  isSelected,
  onChange,
  isDisabled = false,
  children,
}: {
  isSelected: boolean
  onChange: () => void
  /** Ticking it is not a choice here — a room another filière already holds. */
  isDisabled?: boolean
  children: ReactNode
}) {
  return (
    <AriaCheckbox
      isSelected={isSelected}
      onChange={onChange}
      isDisabled={isDisabled}
      className="group flex cursor-pointer items-center gap-2.5 rounded-[4px] px-2 py-1.5 text-[13px] outline-none
        transition-colors hover:bg-[var(--color-sunken)]
        focus-visible:ring-2 focus-visible:ring-[var(--color-accent)]/45
        disabled:cursor-default disabled:opacity-55 disabled:hover:bg-transparent"
    >
      <span
        className="flex size-[18px] shrink-0 items-center justify-center rounded-[6px] ring-1 ring-[var(--color-hairline)]
          transition-colors group-selected:bg-[var(--color-accent)] group-selected:ring-[var(--color-accent)]"
        aria-hidden
      >
        <Check size={12} className="text-[var(--color-surface)] opacity-0 group-selected:opacity-100" />
      </span>
      <span className="min-w-0 truncate">{children}</span>
    </AriaCheckbox>
  )
}
