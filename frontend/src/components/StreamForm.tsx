import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { TriangleAlert } from 'lucide-react'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { Button, Checkbox, ComboBox, Dialog, Notice, Skeleton } from '../ui'

export type RoomRef = { id: number; reference: string; label: string }
export type Stream = { id: number; name: string; rooms: RoomRef[] }
type CenterDetail = { id: number; rooms: RoomRef[] }
/** What the session answers with once a filière has been saved. */
type Timetable = { streams: Stream[] }

/**
 * A filière and the rooms it occupies for the whole session.
 *
 * <p>Set once here rather than on every épreuve: a centre gives Lettres salle 1
 * and Sciences physiques salles 6 à 10 for the three days, and restating it per
 * subject is what made entering a timetable an afternoon's work.
 */
export function StreamForm({
  sessionId,
  centerId,
  existing,
  open,
  onClose,
}: {
  sessionId: number
  centerId: number
  existing?: Stream
  open: boolean
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [name, setName] = useState('')
  const [chosen, setChosen] = useState<number[]>([])

  const center = useQuery({
    queryKey: ['center', centerId],
    queryFn: () => api.get<CenterDetail>(`/centers/${centerId}`),
    enabled: open,
  })

  /** The centre's filières: picked from, not retyped for every session. */
  const known = useQuery({
    queryKey: ['streams', centerId],
    queryFn: () => api.get<{ id: number; name: string }[]>(`/centers/${centerId}/streams`),
    enabled: open,
  })

  useEffect(() => {
    if (!open) return
    setName(existing?.name ?? '')
    setChosen(existing?.rooms.map((room) => room.id) ?? [])
  }, [open, existing])

  const save = useApiMutation({
    run: () => {
      const body = { name: name.trim(), roomIds: chosen }
      return existing
        ? api.post<Timetable>(`/sessions/${sessionId}/timetable/streams/${existing.id}`, body)
        : api.post<Timetable>(`/sessions/${sessionId}/timetable/streams`, body)
    },
    invalidate: ['timetable', sessionId],
    onDone: () => {
      onClose()
      return existing ? t('schedule.streamSaved') : t('schedule.streamAdded')
    },
  })

  const toggle = (roomId: number) =>
    setChosen((current) =>
      current.includes(roomId) ? current.filter((id) => id !== roomId) : [...current, roomId],
    )

  return (
    <Dialog
      isOpen={open}
      onClose={onClose}
      title={existing ? t('schedule.editStream') : t('schedule.addStream')}
      footer={
        <>
          <Button variant="secondary" onPress={onClose}>
            {t('app.cancel')}
          </Button>
          <Button type="submit" form="stream-form" isPending={save.isPending}>
            {t('app.save')}
          </Button>
        </>
      }
    >
      <form
        id="stream-form"
        className="space-y-5 pb-4"
        onSubmit={(event) => {
          event.preventDefault()
          save.mutate(undefined)
        }}
      >
        <ComboBox
          label={t('schedule.streamName')}
          value={name}
          onChange={setName}
          placeholder={t('schedule.streamHint')}
          autoFocus
          suggestions={(known.data ?? []).map((option) => ({
            id: option.name,
            label: option.name,
          }))}
        />

        <div>
          <div className="mb-2 flex items-baseline justify-between">
            <span className="text-[12px] font-medium text-[var(--color-quiet)]">
              {t('schedule.rooms')}
            </span>
            <span className="numeric text-[11.5px] text-[var(--color-faint)]">{chosen.length}</span>
          </div>

          {center.isPending && <Skeleton rows={2} />}
          {center.isSuccess && center.data.rooms.length === 0 && (
            <Notice tone="warn" icon={<TriangleAlert size={16} aria-hidden />}>
              {t('schedule.noRooms')}
            </Notice>
          )}
          {center.isSuccess && center.data.rooms.length > 0 && (
            <div className="grid max-h-56 grid-cols-2 gap-x-3 overflow-y-auto rounded-[var(--radius-field)] bg-[var(--color-sunken)] p-2">
              {center.data.rooms.map((room) => (
                <Checkbox
                  key={room.id}
                  isSelected={chosen.includes(room.id)}
                  onChange={() => toggle(room.id)}
                >
                  {room.label}
                </Checkbox>
              ))}
            </div>
          )}
        </div>
      </form>
    </Dialog>
  )
}
