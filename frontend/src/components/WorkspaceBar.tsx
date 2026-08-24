import { useTranslation } from 'react-i18next'
import { Building2, CalendarDays } from 'lucide-react'
import { ListBox, Select } from '@heroui/react'
import { useWorkspace } from '../context/Workspace'

/**
 * The centre and the session every screen underneath is about.
 *
 * <p>One bar rather than a picker on each page: the choice is the same choice
 * wherever you are, and seeing it stated is what stops an administrator
 * reading one centre's teachers next to another centre's timetable.
 */
export function WorkspaceBar() {
  const { t } = useTranslation()
  const { centers, sessionsHere, centerId, sessionId, chooseCenter, chooseSession } =
    useWorkspace()

  if (centers.length === 0) return null

  return (
    <div className="no-print flex flex-wrap items-center gap-x-2 gap-y-1 border-b border-[var(--color-hairline)] bg-white px-10 py-2.5">
      <Building2 size={14} className="shrink-0 text-[var(--color-quiet)]" aria-hidden />
      <Select
        selectedKey={centerId === null ? undefined : String(centerId)}
        onSelectionChange={(key) => chooseCenter(Number(key))}
        aria-label={t('workspace.center')}
      >
        <Select.Trigger>
          <Select.Value />
          <Select.Indicator />
        </Select.Trigger>
        <Select.Popover>
          <ListBox>
            {centers.map((center) => (
              <ListBox.Item key={center.id} id={String(center.id)} textValue={center.name}>
                {center.name}
              </ListBox.Item>
            ))}
          </ListBox>
        </Select.Popover>
      </Select>

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
