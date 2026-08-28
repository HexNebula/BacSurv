import type { ReactNode } from 'react'
import {
  Button as AriaButton,
  ComboBox as AriaComboBox,
  Group,
  Input,
  Label,
  ListBox,
  ListBoxItem,
  Popover,
} from 'react-aria-components'
import { ChevronDown } from 'lucide-react'

export type Suggestion = { id: string; label: string; hint?: ReactNode }

/**
 * Type it, or take one already known.
 *
 * <p>Both places this is used need the same thing: the centre's own list is
 * offered, but free text is still allowed, because a centre can examine a
 * subject nobody there teaches and can run a filière it has never run before.
 * Choosing from the list matters — the solver matches a teacher's subject to an
 * épreuve's by exact string, so "Maths" typed where the list says
 * "Mathématiques" quietly stops a maths teacher being barred from the maths
 * paper.
 */
export function ComboBox({
  label,
  value,
  onChange,
  suggestions,
  placeholder,
  autoFocus = false,
  className = '',
}: {
  label: string
  value: string
  onChange: (value: string) => void
  suggestions: Suggestion[]
  placeholder?: string
  autoFocus?: boolean
  className?: string
}) {
  const needle = value.trim().toLowerCase()
  const shown = suggestions.filter((one) => one.label.toLowerCase().includes(needle))

  return (
    <AriaComboBox
      allowsCustomValue
      /* on typing, not on focus: opened by the focus alone it covers the
         fields underneath it the moment the dialog appears */
      menuTrigger="input"
      inputValue={value}
      onInputChange={onChange}
      onSelectionChange={(key) => key !== null && onChange(String(key))}
      className={className}
    >
      <Label className="mb-1.5 block text-[12px] font-medium text-[var(--color-quiet)]">
        {label}
      </Label>

      <Group
        className="flex h-10 items-center rounded-[var(--radius-field)] bg-[var(--color-surface)] ring-1 ring-[var(--color-rule)]
          transition-shadow hover:ring-[var(--color-faint)]/45 focus-within:ring-2 focus-within:ring-[var(--color-accent)]"
      >
        <Input
          autoFocus={autoFocus}
          placeholder={placeholder}
          className="min-w-0 flex-1 bg-transparent px-3.5 text-[13.5px] outline-none placeholder:text-[var(--color-faint)]"
        />
        <AriaButton className="flex size-9 shrink-0 items-center justify-center text-[var(--color-faint)] outline-none transition-colors hover:text-[var(--color-ink)]">
          <ChevronDown size={15} aria-hidden />
        </AriaButton>
      </Group>

      <Popover
        offset={6}
        className="min-w-[var(--trigger-width)] overflow-hidden rounded-[var(--radius-card)] bg-[var(--color-surface)]
          p-1.5 shadow-[var(--shadow-raised)] ring-1 ring-[var(--color-rule)]
          entering:animate-in entering:fade-in entering:zoom-in-95 exiting:animate-out exiting:fade-out"
      >
        <ListBox className="max-h-64 overflow-auto outline-none">
          {shown.map((one) => (
            <ListBoxItem
              key={one.id}
              id={one.id}
              textValue={one.label}
              className="flex cursor-pointer items-baseline justify-between gap-4 rounded-[4px] px-3 py-2 text-[13.5px] outline-none
                selected:bg-[var(--color-accent-tint)] selected:text-[var(--color-accent-ink)]
                hover:bg-[var(--color-sunken)] focus:bg-[var(--color-sunken)]"
            >
              <span className="min-w-0 truncate">{one.label}</span>
              {one.hint !== undefined && (
                <span className="numeric shrink-0 text-[11.5px] text-[var(--color-faint)]">
                  {one.hint}
                </span>
              )}
            </ListBoxItem>
          ))}
        </ListBox>
      </Popover>
    </AriaComboBox>
  )
}
