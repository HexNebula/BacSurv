import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { CalendarDays } from 'lucide-react'
import { useWorkspace } from '../context/Workspace'
import { Select } from '../ui'

/**
 * Which session every screen is talking about.
 *
 * <p>The centre is not offered beside it and never will be: an administrator
 * runs their own establishment and nobody else's. Sessions are the thing there
 * are several of — 1BAC in June, 2BAC in June, the rattrapage in July — so this
 * is the only switch in the header.
 */
export function SessionPicker() {
  const { t } = useTranslation()
  const { sessionsHere, sessionId, chooseSession } = useWorkspace()

  const choices = useMemo(
    () =>
      sessionsHere.map((session) => ({
        id: session.id,
        label: session.reference,
        hint: t(`sessions.type.${session.type}`, { defaultValue: '' }) || undefined,
      })),
    [sessionsHere, t],
  )

  if (choices.length === 0) return null

  return (
    <Select
      label={t('nav.sessions')}
      hideLabel
      className="w-56"
      leading={<CalendarDays size={15} aria-hidden />}
      choices={choices}
      value={sessionId}
      onChange={(id) => chooseSession(Number(id))}
    />
  )
}
