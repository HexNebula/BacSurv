import { useEffect, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { CalendarOff, Plus, Trash2 } from 'lucide-react'
import { CalendarDate, Time, parseDate, parseTime } from '@internationalized/date'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { Button, Checkbox, DateField, Dialog, Empty, Skeleton, TimeField } from '../ui'

/** As the server holds it: null hours mean the whole day. */
type Absence = {
  id: number | null
  date: string
  startTime: string | null
  endTime: string | null
}

/** As the form holds it while being edited. */
type Draft = {
  key: string
  date: CalendarDate | null
  wholeDay: boolean
  start: Time | null
  end: Time | null
}

const MORNING: [Time, Time] = [new Time(8, 0), new Time(12, 0)]

function toDraft(absence: Absence, index: number): Draft {
  return {
    key: `${absence.id ?? 'new'}-${index}`,
    date: absence.date ? parseDate(absence.date) : null,
    wholeDay: absence.startTime === null,
    start: absence.startTime ? parseTime(absence.startTime.slice(0, 5)) : MORNING[0],
    end: absence.endTime ? parseTime(absence.endTime.slice(0, 5)) : MORNING[1],
  }
}

function written(value: Time): string {
  return `${String(value.hour).padStart(2, '0')}:${String(value.minute).padStart(2, '0')}`
}

/**
 * The days a teacher cannot be given a duty.
 *
 * <p>The solver has always honoured these — a teacher who is away is not
 * eligible for that hour — but nothing could record one, so the fact lived in
 * the administrator's head and the distribution was quietly wrong. These are
 * absences known in advance: somebody who fails to turn up on the morning is a
 * distribution to repair on the spot, not a fact to write down here.
 *
 * <p>The whole list is saved at once. An administrator thinks "these are the
 * days he is away", not "add this one, remove that one", and a screen that has
 * to remember which row it deleted is a screen that eventually deletes the
 * wrong one.
 */
export function Absences({
  centerId,
  matricule,
  name,
  open,
  onClose,
}: {
  centerId: number
  matricule: string | null
  name: string
  open: boolean
  onClose: () => void
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [drafts, setDrafts] = useState<Draft[]>([])

  const absences = useQuery({
    queryKey: ['absences', centerId, matricule],
    queryFn: () => api.get<Absence[]>(`/centers/${centerId}/teachers/${matricule}/absences`),
    enabled: open && matricule !== null,
  })

  useEffect(() => {
    if (!open || !absences.data) return
    setDrafts(absences.data.map(toDraft))
  }, [open, absences.data])

  const save = useApiMutation({
    run: () =>
      api.post<Absence[]>(
        `/centers/${centerId}/teachers/${matricule}/absences`,
        drafts
          .map((draft) => ({
            id: null,
            date: draft.date!.toString(),
            startTime: draft.wholeDay ? null : written(draft.start ?? MORNING[0]),
            endTime: draft.wholeDay ? null : written(draft.end ?? MORNING[1]),
          })),
      ),
    invalidate: ['teachers', centerId],
    onDone: () => {
      void queryClient.invalidateQueries({ queryKey: ['absences', centerId, matricule] })
      onClose()
      return t('absences.saved')
    },
  })

  // a row whose day was never finished is not saved silently: dropping what
  // somebody typed and answering "saved" is how a teacher stays available on a
  // day everyone believes was recorded
  const incomplete = drafts.some((draft) => draft.date === null)

  const change = (key: string, patch: Partial<Draft>) =>
    setDrafts((current) =>
      current.map((draft) => (draft.key === key ? { ...draft, ...patch } : draft)),
    )

  return (
    <Dialog
      isOpen={open}
      onClose={onClose}
      width="lg"
      title={t('absences.of', { name })}
      subtitle={matricule ?? undefined}
      footer={
        <>
          <Button variant="secondary" onPress={onClose}>
            {t('app.cancel')}
          </Button>
          <Button
            isDisabled={incomplete}
            isPending={save.isPending}
            onPress={() => save.mutate(undefined)}
          >
            {t('app.save')}
          </Button>
        </>
      }
    >
      <div className="space-y-4 pb-4">
        {absences.isPending && <Skeleton rows={2} />}

        {absences.isSuccess && drafts.length === 0 && (
          <Empty icon={<CalendarOff size={22} aria-hidden />}>{t('absences.none')}</Empty>
        )}

        {drafts.map((draft) => (
          <div
            key={draft.key}
            className="rounded-[var(--radius-card)] bg-[var(--color-sunken)] p-4"
          >
            <div className="flex flex-wrap items-end gap-3">
              <DateField
                label={t('absences.date')}
                value={draft.date}
                onChange={(value) => change(draft.key, { date: value })}
                className="w-52"
              />

              {/* a whole day is the common case, so it is the default and the
                  hours only appear when somebody says otherwise */}
              {!draft.wholeDay && (
                <>
                  <TimeField
                    label={t('absences.from')}
                    value={draft.start}
                    onChange={(value) => change(draft.key, { start: value })}
                    className="w-32"
                  />
                  <TimeField
                    label={t('absences.to')}
                    value={draft.end}
                    onChange={(value) => change(draft.key, { end: value })}
                    className="w-32"
                  />
                </>
              )}

              <Button
                variant="quiet"
                isIcon
                aria-label={t('absences.remove')}
                className="ms-auto hover:text-[var(--color-alarm)]"
                onPress={() =>
                  setDrafts((current) => current.filter((one) => one.key !== draft.key))
                }
              >
                <Trash2 size={16} aria-hidden />
              </Button>
            </div>

            <div className="mt-3">
              <Checkbox
                isSelected={draft.wholeDay}
                onChange={() => change(draft.key, { wholeDay: !draft.wholeDay })}
              >
                {t('absences.wholeDay')}
              </Checkbox>
            </div>
          </div>
        ))}

        <Button
          variant="secondary"
          onPress={() =>
            setDrafts((current) => [
              ...current,
              {
                key: `new-${Date.now()}`,
                date: null,
                wholeDay: true,
                start: MORNING[0],
                end: MORNING[1],
              },
            ])
          }
        >
          <Plus size={16} aria-hidden />
          {t('absences.add')}
        </Button>

        {incomplete && (
          <p className="text-[12px] text-[var(--color-warn)]">{t('absences.dateMissing')}</p>
        )}

        <p className="text-[11.5px] leading-relaxed text-[var(--color-faint)]">
          {t('absences.hint')}
        </p>
      </div>
    </Dialog>
  )
}
