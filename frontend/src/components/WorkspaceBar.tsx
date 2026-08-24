import { useTranslation } from 'react-i18next'
import { Building2, CalendarDays } from 'lucide-react'
import { ListBox, Select } from '@heroui/react'
import { useWorkspace } from '../context/Workspace'

/**
 * The centre and the session every screen underneath is about.
 *
 * <p>The centre is stated, not chosen — an administrator runs one
 * establishment. The session is the thing that changes: 1BAC, 2BAC, the
 * rattrapage.
 */
export function WorkspaceBar() {
  const { t } = useTranslation()
  const { center, sessionsHere, sessionId, chooseSession } = useWorkspace()

  if (!center) return null

  return (
    <div className="no-print flex flex-wrap items-center gap-x-2 gap-y-1 border-b border-[var(--color-hairline)] bg-white px-10 py-2.5">
      <Building2 size={14} className="shrink-0 text-[var(--color-quiet)]" aria-hidden />
      {/* one account, one centre: stating it is useful, choosing it is not */}
      <span className="text-[13px] font-medium">{center.name}</span>

      <span className="px-1 text-[var(--color-hairline)]" aria-hidden>
        /
      </span>

      <CalendarDays size={14} className="shrink-0 text-[var(--color-quiet)]" aria-hidden />
      {sessionsHere.length === 0 ? (
        <span className="text-[13px] text-[var(--color-quiet)]">{t('workspace.noSession')}</span>
      ) : (
        <Select
          selectedKey={sessionId === null ? undefined : String(sessionId)}
          onSelectionChange={(key) => chooseSession(Number(key))}
          aria-label={t('workspace.session')}
          >
          <Select.Trigger>
            <Select.Value />
            <Select.Indicator />
          </Select.Trigger>
          <Select.Popover>
            <ListBox>
              {sessionsHere.map((session) => (
                <ListBox.Item
                  key={session.id}
                  id={String(session.id)}
                  textValue={session.reference}
                >
                  {session.reference}
                </ListBox.Item>
              ))}
            </ListBox>
          </Select.Popover>
        </Select>
      )}
    </div>
  )
}
