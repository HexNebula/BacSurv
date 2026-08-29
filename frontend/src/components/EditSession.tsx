import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { parseDate, type CalendarDate } from '@internationalized/date'
import { Lock, Pencil } from 'lucide-react'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { useLifecycleRefresh } from '../lib/session'
import { Button, DateField, Dialog, Notice, TextField } from '../ui'

type Session = {
  id: number
  reference: string
  startsOn: string | null
  endsOn: string | null
  state: 'DRAFT' | 'SETTLED'
}

/** The server takes a plain date; CalendarDate prints exactly that. */
function read(value: string | null) {
  return value === null ? null : parseDate(value)
}

/**
 * Correcting a session after the fact.
 *
 * <p>The two halves of a session behave nothing alike, and the form says so
 * rather than letting the server say it twice.
 *
 * <p>The name is a label. Nothing joins on it, no duty remembers it, so it is
 * corrected at any time — <em>including once the session is settled</em>, which
 * is precisely when it matters: a wrong name is on every convocation that went
 * out. Renaming unsettles nothing and makes no distribution stale.
 *
 * <p>The dates are not a label. They bound the planning and decide which school
 * year the session counts inside, so a settled session's are closed and shown
 * closed, with the way back named. Moving them is also refused when an épreuve
 * would fall outside the new range or when the move would carry the session
 * into another year; those two the server answers with a sentence that counts
 * what is in the way, so they are shown as written rather than pre-empted here.
 */
export function EditSession({ session, centerId }: { session: Session; centerId: number }) {
  const { t } = useTranslation()
  const refresh = useLifecycleRefresh()
  const [open, setOpen] = useState(false)
  const [reference, setReference] = useState('')
  const [startsOn, setStartsOn] = useState<CalendarDate | null>(null)
  const [endsOn, setEndsOn] = useState<CalendarDate | null>(null)

  const settled = session.state === 'SETTLED'

  useEffect(() => {
    if (!open) return
    setReference(session.reference)
    setStartsOn(read(session.startsOn))
    setEndsOn(read(session.endsOn))
  }, [open, session])

  const save = useApiMutation({
    // the dates go with every save, unchanged included: the server asks for
    // both, and sending the ones already on the session moves nothing
    run: () =>
      api.post(`/sessions/${session.id}`, {
        reference: reference.trim(),
        startsOn: startsOn?.toString() ?? null,
        endsOn: endsOn?.toString() ?? null,
      }),
    onDone: () => {
      // the reference is drawn twice — the centre's list and the header's
      // picker — and they read different endpoints
      refresh(centerId, session.id)
      setOpen(false)
      return t('sessions.corrected')
    },
  })

  const dated = startsOn !== null && endsOn !== null

  return (
    <>
      <Button
        size="sm"
        variant="quiet"
        isIcon
        aria-label={t('sessions.edit')}
        onPress={() => setOpen(true)}
      >
        <Pencil size={15} aria-hidden />
      </Button>

      <Dialog
        isOpen={open}
        onClose={() => setOpen(false)}
        title={t('sessions.edit')}
        subtitle={session.reference}
        footer={
          <>
            <Button variant="secondary" onPress={() => setOpen(false)}>
              {t('app.cancel')}
            </Button>
            <Button
              type="submit"
              form="edit-session"
              isPending={save.isPending}
              isDisabled={reference.trim() === '' || !dated}
            >
              {t('app.save')}
            </Button>
          </>
        }
      >
        <form
          id="edit-session"
          className="space-y-4 pb-4"
          onSubmit={(event) => {
            event.preventDefault()
            save.mutate(undefined)
          }}
        >
          <TextField
            label={t('sessions.reference.label')}
            value={reference}
            onChange={setReference}
            autoFocus
            hint={settled ? t('sessions.referenceSettled') : undefined}
          />

          {settled && (
            <Notice tone="plain" icon={<Lock size={16} aria-hidden />}>
              {t('sessions.datesSettled')}
            </Notice>
          )}

          <div className="grid grid-cols-2 gap-3">
            <DateField
              label={t('sessions.startsOn')}
              value={startsOn}
              onChange={setStartsOn}
              isDisabled={settled}
            />
            <DateField
              label={t('sessions.endsOn')}
              value={endsOn}
              onChange={setEndsOn}
              isDisabled={settled}
            />
          </div>

          {/* a session entered without dates cannot be renamed until it has
              them: the server asks for both on every correction */}
          {!dated && (
            <p className="text-[11.5px] text-[var(--color-faint)]">{t('sessions.datesNeeded')}</p>
          )}
        </form>
      </Dialog>
    </>
  )
}
