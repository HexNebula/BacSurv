import {
  Button as AriaButton,
  Label,
  ListBox,
  ListBoxItem,
  Popover,
  Select as AriaSelect,
  SelectValue,
} from 'react-aria-components'
import { Check, ChevronDown } from 'lucide-react'
import type { ReactNode } from 'react'

export type Choice = { id: string | number; label: string; hint?: string }

/**
 * A choice from a short list.
 *
 * <p>React Aria places and mirrors the popover itself from the locale handed to
 * `I18nProvider`, which is why this file never says left or right: in Arabic the
 * list opens against the other edge without being told.
 */
export function Select({
  label,
  hideLabel = false,
  choices,
  value,
  onChange,
  placeholder,
  leading,
  className = '',
}: {
  label: string
  /** For the session picker in the header, where the label is the value. */
  hideLabel?: boolean
  choices: Choice[]
  value: string | number | null
  onChange: (id: string | number) => void
  placeholder?: string
  leading?: ReactNode
  className?: string
}) {
  return (
    <AriaSelect
      aria-label={hideLabel ? label : undefined}
      selectedKey={value}
      onSelectionChange={(key) => onChange(key as string | number)}
      className={className}
      placeholder={placeholder}
    >
      {!hideLabel && (
        <Label className="mb-1.5 block text-[12px] font-medium text-[var(--color-quiet)]">
          {label}
        </Label>
      )}

      <AriaButton
        className="flex h-10 w-full items-center gap-2.5 rounded-[var(--radius-field)] bg-[var(--color-surface)] px-3.5
          text-[13.5px] ring-1 ring-[var(--color-hairline)] outline-none transition-shadow
          hover:ring-[var(--color-faint)]/45
          focus-visible:ring-2 focus-visible:ring-[var(--color-accent)]"
      >
        {leading && <span className="shrink-0 text-[var(--color-accent)]">{leading}</span>}
        <SelectValue className="min-w-0 flex-1 truncate text-start data-[placeholder]:text-[var(--color-faint)]" />
        <ChevronDown size={15} className="shrink-0 text-[var(--color-faint)]" aria-hidden />
      </AriaButton>

      <Popover
        offset={6}
        className="min-w-[var(--trigger-width)] overflow-hidden rounded-[var(--radius-card)] bg-[var(--color-surface)]
          p-1.5 shadow-[var(--shadow-raised)] ring-1 ring-[var(--color-hairline)]
          entering:animate-in entering:fade-in entering:zoom-in-95 exiting:animate-out exiting:fade-out"
      >
        <ListBox className="max-h-72 overflow-auto outline-none">
          {choices.map((choice) => (
            <ListBoxItem
              key={choice.id}
              id={choice.id}
              textValue={choice.label}
              className="group flex cursor-pointer items-center gap-2.5 rounded-lg px-3 py-2 text-[13.5px] outline-none
                selected:bg-[var(--color-accent-tint)] selected:text-[var(--color-accent-ink)]
                hover:bg-[var(--color-sunken)] focus:bg-[var(--color-sunken)]
                selected:hover:bg-[var(--color-accent-tint)]"
            >
              <span className="min-w-0 flex-1">
                <span className="block truncate">{choice.label}</span>
                {choice.hint && (
                  <span className="mt-0.5 block truncate text-[11.5px] text-[var(--color-faint)]">
                    {choice.hint}
                  </span>
                )}
              </span>
              <Check
                size={15}
                aria-hidden
                className="shrink-0 opacity-0 group-selected:opacity-100"
              />
            </ListBoxItem>
          ))}
        </ListBox>
      </Popover>
    </AriaSelect>
  )
}
