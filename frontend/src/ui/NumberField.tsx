import {
  Button as AriaButton,
  Group,
  Input,
  Label,
  NumberField as AriaNumberField,
  Text,
} from 'react-aria-components'
import { Minus, Plus } from 'lucide-react'
import type { ReactNode } from 'react'

/**
 * A count, with the two buttons that change it by one.
 *
 * <p>The figures are tabular and centred: this is used for how many rooms to
 * add and how many surveillants a room takes, and both are read as numbers
 * rather than as text. React Aria handles the arrow keys, the wheel and the
 * locale's own digits.
 */
export function NumberField({
  label,
  hint,
  value,
  onChange,
  minValue,
  maxValue,
  placeholder,
  className = '',
  'aria-label': ariaLabel,
}: {
  label?: string
  hint?: ReactNode
  value: number | undefined
  onChange: (value: number) => void
  minValue?: number
  maxValue?: number
  /** Shown while empty — for a field whose blank state means "keep the default". */
  placeholder?: string
  className?: string
  'aria-label'?: string
}) {
  return (
    <AriaNumberField
      value={value}
      onChange={onChange}
      minValue={minValue}
      maxValue={maxValue}
      aria-label={label ? undefined : ariaLabel}
      className={className}
    >
      {label && (
        <Label className="mb-1.5 block text-[12px] font-medium text-[var(--color-quiet)]">
          {label}
        </Label>
      )}

      <Group
        className="flex h-10 items-center rounded-[var(--radius-field)] bg-[var(--color-surface)] ring-1 ring-[var(--color-hairline)]
          transition-shadow hover:ring-[var(--color-faint)]/45 focus-within:ring-2 focus-within:ring-[var(--color-accent)]"
      >
        <Step slot="decrement">
          <Minus size={14} aria-hidden />
        </Step>
        <Input
          placeholder={placeholder}
          className="numeric min-w-0 flex-1 bg-transparent px-1 text-center text-[13.5px] outline-none
            placeholder:text-[var(--color-faint)]"
        />
        <Step slot="increment">
          <Plus size={14} aria-hidden />
        </Step>
      </Group>

      {hint && (
        <Text slot="description" className="mt-1.5 block text-[11.5px] text-[var(--color-faint)]">
          {hint}
        </Text>
      )}
    </AriaNumberField>
  )
}

function Step({ slot, children }: { slot: 'increment' | 'decrement'; children: ReactNode }) {
  return (
    <AriaButton
      slot={slot}
      className="flex size-9 shrink-0 items-center justify-center rounded-[8px] text-[var(--color-quiet)] outline-none
        transition-colors hover:bg-[var(--color-sunken)] hover:text-[var(--color-ink)]
        disabled:opacity-35 disabled:hover:bg-transparent"
    >
      {children}
    </AriaButton>
  )
}
