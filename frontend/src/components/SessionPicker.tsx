import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { CalendarDays } from 'lucide-react'
import { useWorkspace } from '../context/Workspace'
import { NewSession } from './NewSession'
import { Select } from '../ui'

/**
 * Which session every screen is talking about.
 *
 * <p>The centre is not offered beside it and never will be: an administrator
 * runs their own establishment and nobody else's. Sessions are the thing there
 * are several of — 1BAC in June, 2BAC in June, the rattrapage in July — so this
 * is the only switch in the header.
 *
 * <p>Making one sits beside switching between them, because this is where an
 * administrator looks for sessions. It used to be only on the centre's page,
 * two thirds down, which meant the header could show a list you had no way of
 * adding to from anywhere you happened to be standing.
 */
export function SessionPicker() {
  const { t } = useTranslation()
  const { sessionsHere, sessionId, chooseSession, centerId } = useWorkspace()

  const choices = useMemo(
    () =>
      sessionsHere.map((session) => ({
        id: session.id,
        label: session.reference,
        hint: t(`sessions.type.${session.type}`, { defaultValue: '' }) || undefined,
      })),
    [sessionsHere, t],
  )

  if (centerId === null) return null

  return (
    <div className="flex items-center gap-2">
      {/* a centre with no session yet still gets the way to make one */}
      {choices.length > 0 && (
        <Select
          label={t('nav.sessions')}
          hideLabel
          className="w-56"
          leading={<CalendarDays size={15} aria-hidden />}
          choices={choices}
          value={sessionId}
          onChange={(id) => chooseSession(Number(id))}
        />
      )}
      <NewSession centerId={centerId} variant="icon" />
    </div>
  )
}
