import type { ReactNode } from 'react'
import { Tab, TabList, Tabs as AriaTabs } from 'react-aria-components'

export type TabDef = {
  id: string
  label: string
  icon?: ReactNode
  /** How many rows sit behind this tab, shown as a figure beside the label. */
  count?: number
  /** A dot on the tab: this one holds something the administrator should look at. */
  flag?: boolean
}

/**
 * The states of one list, side by side.
 *
 * <p>Sub-states of a screen are tabs rather than a filter dropdown, because the
 * fact that there *are* three unqualified teachers should be visible without
 * opening anything. The active tab is a raised white pill on a sunken track —
 * the same paper-on-table idea as the cards, one level down.
 */
export function SegmentedTabs({
  tabs,
  value,
  onChange,
  className = '',
}: {
  tabs: TabDef[]
  value: string
  onChange: (id: string) => void
  className?: string
}) {
  return (
    <AriaTabs
      selectedKey={value}
      onSelectionChange={(key) => onChange(String(key))}
      className={className}
    >
      <TabList
        aria-label=""
        className="inline-flex flex-wrap items-center gap-1 rounded-[var(--radius-card)] bg-[var(--color-sunken)] p-1 ring-1 ring-[var(--color-hairline)]"
      >
        {tabs.map((tab) => (
          <Tab
            key={tab.id}
            id={tab.id}
            className="group relative flex cursor-pointer items-center gap-2 rounded-[10px] px-3.5 py-2 text-[13px]
              font-medium text-[var(--color-quiet)] outline-none transition-colors
              hover:text-[var(--color-ink)]
              selected:bg-[var(--color-surface)] selected:text-[var(--color-accent-ink)] selected:shadow-[var(--shadow-card)]
              focus-visible:ring-2 focus-visible:ring-[var(--color-accent)]/45"
          >
            {tab.icon && (
              <span className="text-[var(--color-faint)] group-selected:text-[var(--color-accent)]">
                {tab.icon}
              </span>
            )}
            {tab.label}
            {tab.count !== undefined && (
              <span className="numeric text-[11.5px] text-[var(--color-faint)] group-selected:text-[var(--color-accent)]">
                {tab.count}
              </span>
            )}
            {tab.flag && (
              <span
                className="size-1.5 rounded-full bg-[var(--color-warn)]"
                aria-hidden
              />
            )}
          </Tab>
        ))}
      </TabList>
    </AriaTabs>
  )
}
