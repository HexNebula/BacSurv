import type { ReactNode } from 'react'
import {
  Input,
  Label,
  SearchField as AriaSearchField,
  TextField as AriaTextField,
  Text,
} from 'react-aria-components'
import { Search, X } from 'lucide-react'
import { Button } from './Button'

const FIELD =
  `h-10 w-full rounded-[var(--radius-field)] bg-[var(--color-surface)] px-3.5 text-[13.5px]
   text-[var(--color-ink)] ring-1 ring-[var(--color-hairline)] outline-none transition-shadow
   placeholder:text-[var(--color-faint)]
   hover:ring-[var(--color-faint)]/45
   focus:ring-2 focus:ring-[var(--color-accent)]
   disabled:bg-[var(--color-sunken)] disabled:text-[var(--color-quiet)]`

const LABEL = 'mb-1.5 block text-[12px] font-medium text-[var(--color-quiet)]'

/**
 * One line of typing, with its label above it.
 *
 * <p>The label is always there, never a placeholder standing in for one: a form
 * being filled in loses its placeholders exactly when somebody wants to check
 * what they typed into which box.
 */
export function TextField({
  label,
  hint,
  className = '',
  inputClassName = '',
  ...props
}: {
  /** Omitted where the surrounding card already names the field; pass
      `aria-label` in that case so it is still announced. */
  label?: string
  hint?: ReactNode
  className?: string
  inputClassName?: string
} & React.ComponentProps<typeof AriaTextField> & { placeholder?: string; autoFocus?: boolean }) {
  const { placeholder, autoFocus, ...field } = props
  return (
    <AriaTextField {...field} className={className}>
      {label && <Label className={LABEL}>{label}</Label>}
      <Input placeholder={placeholder} autoFocus={autoFocus} className={`${FIELD} ${inputClassName}`} />
      {hint && (
        <Text slot="description" className="mt-1.5 block text-[11.5px] text-[var(--color-faint)]">
          {hint}
        </Text>
      )}
    </AriaTextField>
  )
}

/**
 * The search box in a card's header. The icon sits on the leading side and
 * mirrors with the page, because the padding is logical — `ps-`, not `pl-`.
 */
export function SearchField({
  value,
  onChange,
  placeholder,
  label,
  className = '',
}: {
  value: string
  onChange: (value: string) => void
  placeholder?: string
  /** Read by screen readers; the box itself shows only the icon. */
  label: string
  className?: string
}) {
  return (
    <AriaSearchField
      value={value}
      onChange={onChange}
      aria-label={label}
      className={`group relative ${className}`}
    >
      <Search
        size={15}
        aria-hidden
        className="pointer-events-none absolute inset-y-0 my-auto text-[var(--color-faint)] ltr:left-3.5 rtl:right-3.5"
      />
      <Input placeholder={placeholder} className={`${FIELD} ps-10 pe-9`} />
      {value !== '' && (
        <div className="absolute inset-y-0 my-auto flex items-center ltr:right-1.5 rtl:left-1.5">
          <Button variant="quiet" size="sm" isIcon bare aria-label={label} onPress={() => onChange('')}>
            <X size={14} aria-hidden />
          </Button>
        </div>
      )}
    </AriaSearchField>
  )
}
