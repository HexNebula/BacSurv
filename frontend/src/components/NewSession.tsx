import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { CalendarPlus } from 'lucide-react'
import type { CalendarDate } from '@internationalized/date'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { useWorkspace } from '../context/Workspace'
import { Button, DateField, Dialog, Select, TextField } from '../ui'

const SESSION_TYPES = ['REGIONAL_1BAC', 'NATIONAL_2BAC', 'NATIONAL_2BAC_RATTRAPAGE'] as const

/** Only what creating one needs back: the centre's sessions, by id. */
type Created = { id: number; sessions: { id: number }[] }

/**
 * A session is created with its dates; the papers themselves come later.
 *
 * <p>It lives here rather than on the centre's page because it is reached from
 * two places: the centre, where the sessions are listed, and the header, where
 * they are switched between. Those are the two moments an administrator thinks
 * about sessions at all, and a July rattrapage should not require finding the
 * first one.
 */
export function NewSession({
  centerId,
  variant = 'button',
}: {
  centerId: number
  /** `icon` for the header, where the picker already says what this is about. */
  variant?: 'button' | 'icon'
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { chooseSession } = useWorkspace()
  const [open, setOpen] = useState(false)
  const [reference, setReference] = useState('')
  const [type, setType] = useState<string>('NATIONAL_2BAC')
  const [startsOn, setStartsOn] = useState<CalendarDate | null>(null)
  const [endsOn, setEndsOn] = useState<CalendarDate | null>(null)

  const create = useApiMutation({
    run: () =>
      api.post<Created>(`/centers/${centerId}/sessions`, {
        reference,
        type,
        // the server takes a plain date; CalendarDate prints exactly that
        startsOn: startsOn?.toString() ?? null,
        endsOn: endsOn?.toString() ?? null,
      }),
    invalidate: ['center', centerId],
    onDone: (result) => {
      // the header picker reads a different list from the centre's page; both
      // have to be dropped or the session just created is invisible up there
      void queryClient.invalidateQueries({ queryKey: ['operations'] })

      // a session is created last, so it holds the highest id the centre has
      const created = Math.max(...result.sessions.map((session) => session.id))
      if (Number.isFinite(created)) chooseSession(created)

      setOpen(false)
      setReference('')
      setStartsOn(null)
      setEndsOn(null)
      return t('sessions.created')
    },
  })

  return (
    <>
      {variant === 'icon' ? (
        // the title sits on the wrapper: an icon alone in the header has to say
        // what it is on hover, and the button itself takes no such attribute
        <span title={t('sessions.create')} className="flex">
          <Button
            variant="secondary"
            isIcon
            aria-label={t('sessions.create')}
            onPress={() => setOpen(true)}
          >
            <CalendarPlus size={16} aria-hidden />
          </Button>
        </span>
      ) : (
        <Button onPress={() => setOpen(true)}>
          <CalendarPlus size={16} aria-hidden />
          {t('sessions.create')}
        </Button>
      )}

      <Dialog
        isOpen={open}
        onClose={() => setOpen(false)}
        title={t('sessions.create')}
        footer={
          <>
            <Button variant="secondary" onPress={() => setOpen(false)}>
              {t('app.cancel')}
            </Button>
            <Button type="submit" form="new-session" isPending={create.isPending}>
              {t('app.save')}
            </Button>
          </>
        }
      >
        <form
          id="new-session"
          className="space-y-4 pb-4"
          onSubmit={(event) => {
            event.preventDefault()
            create.mutate(undefined)
          }}
        >
          <TextField
            label={t('sessions.reference.label')}
            value={reference}
            onChange={setReference}
            placeholder={t('sessions.reference.hint')}
            autoFocus
          />

          <Select
            label={t('sessions.type.label')}
            value={type}
            onChange={(key) => setType(String(key))}
            choices={SESSION_TYPES.map((value) => ({
              id: value,
              label: t(`sessions.type.${value}`),
            }))}
          />

          <div className="grid grid-cols-2 gap-3">
            <DateField label={t('sessions.startsOn')} value={startsOn} onChange={setStartsOn} />
            <DateField label={t('sessions.endsOn')} value={endsOn} onChange={setEndsOn} />
          </div>
        </form>
      </Dialog>
    </>
  )
}
