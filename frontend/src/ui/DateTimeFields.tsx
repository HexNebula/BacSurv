import {
  DateField as AriaDateField,
  TimeField as AriaTimeField,
  DateInput,
  DateSegment,
  Label,
} from 'react-aria-components'
import type { CalendarDate, Time } from '@internationalized/date'

const FRAME =
  `flex h-10 w-full items-center rounded-[var(--radius-field)] bg-[var(--color-surface)] px-3
   text-[13.5px] ring-1 ring-[var(--color-hairline)] outline-none transition-shadow
   hover:ring-[var(--color-faint)]/45 focus-within:ring-2 focus-within:ring-[var(--color-accent)]`

const SEGMENT =
  `numeric rounded px-0.5 outline-none caret-transparent
   focus:bg-[var(--color-accent)] focus:text-white
   type-literal:px-0 type-literal:text-[var(--color-faint)]
   placeholder-shown:text-[var(--color-faint)]`

const LABEL = 'mb-1.5 block text-[12px] font-medium text-[var(--color-quiet)]'

/**
 * A day, in the order the reader's own language writes days.
 *
 * <p>The segments come from the locale React Aria was handed, which is why the
 * language switch has to reach it: a page turned Arabic while a date field
 * still asked for jj/mm/aaaa was a real bug here.
 */
export function DateField({
  label,
  value,
  onChange,
  className = '',
}: {
  label: string
  value: CalendarDate | null
  onChange: (value: CalendarDate | null) => void
  className?: string
}) {
  return (
    <AriaDateField value={value} onChange={onChange} className={className}>
      <Label className={LABEL}>{label}</Label>
      <DateInput className={FRAME}>
        {(segment) => <DateSegment segment={segment} className={SEGMENT} />}
      </DateInput>
    </AriaDateField>
  )
}

/** An hour of the day, always on the 24-hour clock: an exam starts at 15:00. */
export function TimeField({
  label,
  value,
  onChange,
  className = '',
}: {
  label: string
  value: Time | null
  onChange: (value: Time | null) => void
  className?: string
}) {
  return (
    <AriaTimeField value={value} onChange={onChange} hourCycle={24} className={className}>
      <Label className={LABEL}>{label}</Label>
      <DateInput className={FRAME}>
        {(segment) => <DateSegment segment={segment} className={SEGMENT} />}
      </DateInput>
    </AriaTimeField>
  )
}
