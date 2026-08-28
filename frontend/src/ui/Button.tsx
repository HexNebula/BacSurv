import { Button as AriaButton } from 'react-aria-components'
import type { ButtonProps } from 'react-aria-components'
import { LoaderCircle } from 'lucide-react'

/**
 * The four things a button can be here.
 *
 * <p>`primary` is the one action a screen is for, and it is solid accent — there
 * is never a second one competing with it. `secondary` is a white card-edged
 * button for the things beside it. `quiet` carries row-level actions. `danger`
 * is only ever the confirmation half of a delete, never its trigger.
 *
 * <p>A quiet button reduced to a single icon is held in a hairline square, so
 * that a pencil at the end of a row reads as something you press rather than
 * something printed there. The rule is faint enough not to draw a grid down the
 * card; `bare` drops it for the two places where the icon already sits inside
 * another edge — a dialog's corner, a field's own box.
 */
type Variant = 'primary' | 'secondary' | 'quiet' | 'danger'
type Size = 'sm' | 'md'

const VARIANTS: Record<Variant, string> = {
  primary:
    'bg-[var(--color-accent)] text-[var(--color-surface)] shadow-[inset_0_1px_0_rgb(255_255_255_/_0.14)] hover:bg-[var(--color-accent-strong)] pressed:bg-[var(--color-accent-strong)]',
  secondary:
    'bg-[var(--color-surface)] text-[var(--color-ink)] ring-1 ring-[var(--color-rule)] hover:bg-[var(--color-sunken)] hover:ring-[var(--color-quiet)]/50',
  quiet: 'text-[var(--color-quiet)] hover:bg-[var(--color-sunken)] hover:text-[var(--color-ink)]',
  danger:
    'bg-[var(--color-alarm)] text-[var(--color-surface)] hover:brightness-95 pressed:brightness-90',
}

const SIZES: Record<Size, string> = {
  sm: 'h-8 gap-1.5 px-3 text-[12.5px]',
  md: 'h-10 gap-2 px-4 text-[13.5px]',
}

/** What makes a lone icon look pressable, without bordering every row. */
const QUIET_ICON = 'ring-1 ring-[var(--color-hairline)] hover:ring-[var(--color-rule)]'

const ICON_SIZES: Record<Size, string> = {
  sm: 'size-8 px-0',
  md: 'size-10 px-0',
}

export function Button({
  variant = 'primary',
  size = 'md',
  isIcon = false,
  isPending = false,
  bare = false,
  className = '',
  children,
  ...props
}: ButtonProps & {
  variant?: Variant
  size?: Size
  /** Square, for a single icon: the label goes in `aria-label`. */
  isIcon?: boolean
  isPending?: boolean
  /** Drop the quiet icon square, where an edge already surrounds it. */
  bare?: boolean
}) {
  const quietIcon = variant === 'quiet' && isIcon && !bare ? QUIET_ICON : ''

  return (
    <AriaButton
      {...props}
      isDisabled={props.isDisabled || isPending}
      className={`inline-flex shrink-0 items-center justify-center rounded-[var(--radius-field)] font-medium transition-[background-color,box-shadow,color] outline-none
        focus-visible:ring-2 focus-visible:ring-[var(--color-accent)]/45 focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--color-ground)]
        disabled:pointer-events-none disabled:opacity-45
        ${VARIANTS[variant]} ${quietIcon} ${isIcon ? ICON_SIZES[size] : SIZES[size]} ${className}`}
    >
      {isPending ? (
        <>
          <LoaderCircle size={size === 'sm' ? 14 : 16} className="animate-spin" aria-hidden />
          {!isIcon && children}
        </>
      ) : (
        children
      )}
    </AriaButton>
  )
}
