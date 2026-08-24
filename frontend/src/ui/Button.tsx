import { Button as AriaButton } from 'react-aria-components'
import type { ButtonProps } from 'react-aria-components'
import { LoaderCircle } from 'lucide-react'

/**
 * The four things a button can be here.
 *
 * <p>`primary` is the one action a screen is for, and it is solid accent — there
 * is never a second one competing with it. `secondary` is a white card-edged
 * button for the things beside it. `quiet` carries row-level actions, where a
 * border on every row would draw a grid nobody asked for. `danger` is only ever
 * the confirmation half of a delete, never its trigger.
 */
type Variant = 'primary' | 'secondary' | 'quiet' | 'danger'
type Size = 'sm' | 'md'

const VARIANTS: Record<Variant, string> = {
  primary:
    'bg-[var(--color-accent)] text-white shadow-[0_1px_2px_rgb(8_145_178_/_0.35)] hover:bg-[var(--color-accent-strong)] pressed:bg-[var(--color-accent-strong)]',
  secondary:
    'bg-[var(--color-surface)] text-[var(--color-ink)] ring-1 ring-[var(--color-hairline)] hover:bg-[var(--color-sunken)] hover:ring-[var(--color-faint)]/40',
  quiet:
    'text-[var(--color-quiet)] hover:bg-[var(--color-sunken)] hover:text-[var(--color-ink)]',
  danger:
    'bg-[var(--color-alarm)] text-white hover:brightness-95 pressed:brightness-90',
}

const SIZES: Record<Size, string> = {
  sm: 'h-8 gap-1.5 px-3 text-[12.5px]',
  md: 'h-10 gap-2 px-4 text-[13.5px]',
}

const ICON_SIZES: Record<Size, string> = {
  sm: 'size-8 px-0',
  md: 'size-10 px-0',
}

export function Button({
  variant = 'primary',
  size = 'md',
  isIcon = false,
  isPending = false,
  className = '',
  children,
  ...props
}: ButtonProps & {
  variant?: Variant
  size?: Size
  /** Square, for a single icon: the label goes in `aria-label`. */
  isIcon?: boolean
  isPending?: boolean
}) {
  return (
    <AriaButton
      {...props}
      isDisabled={props.isDisabled || isPending}
      className={`inline-flex shrink-0 items-center justify-center rounded-[var(--radius-field)] font-medium transition-[background-color,box-shadow,color] outline-none
        focus-visible:ring-2 focus-visible:ring-[var(--color-accent)]/45 focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--color-ground)]
        disabled:pointer-events-none disabled:opacity-45
        ${VARIANTS[variant]} ${isIcon ? ICON_SIZES[size] : SIZES[size]} ${className}`}
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
