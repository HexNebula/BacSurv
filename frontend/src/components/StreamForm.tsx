import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, Plus, TriangleAlert } from 'lucide-react'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { useWorkspace } from '../context/Workspace'
import { levelOf } from '../lib/levels'
import { Button, Checkbox, Dialog, Notice, Select, Skeleton, TextField } from '../ui'

export type RoomRef = { id: number; reference: string; label: string }
export type Stream = { id: number; name: string; rooms: RoomRef[] }
type CenterDetail = { id: number; rooms: RoomRef[] }
type CatalogueStream = { id: number; name: string; level: string }
/** What the session answers with once a filière has been saved. */
type Timetable = { streams: Stream[] }

/**
 * A filière and the rooms it occupies for the whole session.
 *
 * <p>Set once here rather than on every épreuve: a centre gives Lettres salle 1
 * and Sciences physiques salles 6 à 10 for the three days, and restating it per
 * subject is what made entering a timetable an afternoon's work.
 *
 * <p>The name is chosen from the centre's list, filtered to the level this
 * session examines — a 2BAC session offers the 2BAC filières and nothing else.
 * It used to be free text with the list as a suggestion, which meant a stray
 * keystroke created a second filière outside the catalogue: the solver matches
 * a filière by its exact name, so "Lettres" and "lettres" are two of them.
 * Creating one is still possible, but as an act of its own.
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
  const { session } = useWorkspace()
  const [name, setName] = useState('')
  const [creating, setCreating] = useState(false)
  const [chosen, setChosen] = useState<number[]>([])

  // the level this session examines: the filières of the other year are not
  // wrong choices to be corrected later, they are not choices at all
  const level = levelOf(session?.type)

  const center = useQuery({
    queryKey: ['center', centerId],
    queryFn: () => api.get<CenterDetail>(`/centers/${centerId}`),
    enabled: open,
  })

  /**
   * What the other filières of this session already hold. A room seats one
   * filière for the whole session, so those are not choices to be corrected
   * after a refusal — they are shown as taken, with the name of whoever has
   * them.
   */
  const timetable = useQuery({
    queryKey: ['timetable', sessionId],
    queryFn: () => api.get<Timetable>(`/sessions/${sessionId}/timetable`),
    enabled: open,
  })

  const heldBy = new Map<number, string>()
  for (const stream of timetable.data?.streams ?? []) {
    if (existing && stream.id === existing.id) continue
    for (const room of stream.rooms) heldBy.set(room.id, stream.name)
  }

  /** The centre's filières: picked from, not retyped for every session. */
  const known = useQuery({
    queryKey: ['streams', centerId],
    queryFn: () => api.get<CatalogueStream[]>(`/centers/${centerId}/streams`),
    enabled: open,
  })

  const here = (known.data ?? []).filter((one) => level === null || one.level === level)

  useEffect(() => {
    if (!open) return
    setName(existing?.name ?? '')
    // editing an existing filière is about its rooms; a new one starts on the
    // list, and only goes to the field if nothing there fits
    setCreating(false)
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

  const levelName = level ? t(`streams.level${level}`) : ''
  const nothingListed = known.isSuccess && here.length === 0

  return (
    <Dialog
      isOpen={open}
      onClose={onClose}
      title={existing ? t('schedule.editStream') : t('schedule.addStream')}
      subtitle={levelName || undefined}
      footer={
        <>
          <Button variant="secondary" onPress={onClose}>
            {t('app.cancel')}
          </Button>
          <Button
            type="submit"
            form="stream-form"
            isDisabled={name.trim() === ''}
            isPending={save.isPending}
          >
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
        {/* an existing filière keeps its name: renaming it belongs to the
            centre's list, where the épreuves that carry the name are rewritten
            with it */}
        {existing ? (
          <div>
            <div className="text-[12px] font-medium text-[var(--color-quiet)]">
              {t('schedule.streamName')}
            </div>
            <div className="mt-1 text-[15px] font-semibold">{existing.name}</div>
          </div>
        ) : creating || nothingListed ? (
          <div>
            <TextField
              label={t('schedule.streamName')}
              value={name}
              onChange={setName}
              placeholder={t('schedule.streamHint')}
              autoFocus
              hint={levelName ? t('streams.createdAt', { level: levelName }) : undefined}
            />
            {!nothingListed && (
              <Button
                variant="quiet"
                size="sm"
                className="mt-2"
                onPress={() => {
                  setCreating(false)
                  setName('')
                }}
              >
                <ArrowLeft size={14} className="rtl:rotate-180" aria-hidden />
                {t('streams.back')}
              </Button>
            )}
          </div>
        ) : (
          <div>
            <Select
              label={t('streams.pick')}
              value={name === '' ? null : name}
              onChange={(key) => setName(String(key))}
              choices={here.map((one) => ({ id: one.name, label: one.name }))}
              placeholder={t('streams.pick')}
            />
            <div className="mt-2 flex items-center justify-between gap-3">
              <span className="text-[11.5px] text-[var(--color-faint)]">
                {t('streams.pickHint')}
              </span>
              <Button variant="quiet" size="sm" onPress={() => setCreating(true)}>
                <Plus size={14} aria-hidden />
                {t('streams.create')}
              </Button>
            </div>
          </div>
        )}

        {nothingListed && !existing && (
          <Notice tone="warn" icon={<TriangleAlert size={16} aria-hidden />}>
            {t('streams.noneAtLevel')}
          </Notice>
        )}

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
              {center.data.rooms.map((room) => {
                const taken = heldBy.get(room.id)
                return (
                  <Checkbox
                    key={room.id}
                    isSelected={chosen.includes(room.id)}
                    isDisabled={taken !== undefined}
                    onChange={() => toggle(room.id)}
                  >
                    {taken === undefined ? room.label : `${room.label} · ${taken}`}
                  </Checkbox>
                )
              })}
            </div>
          )}
        </div>
      </form>
    </Dialog>
  )
}
