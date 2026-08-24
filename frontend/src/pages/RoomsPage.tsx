import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { DoorOpen, Pencil, Plus, Trash2, X } from 'lucide-react'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { useWorkspace } from '../context/Workspace'
import { Page } from '../components/Page'
import type { Stream } from '../components/StreamForm'
import {
  Badge,
  Button,
  Card,
  CardHead,
  CardRule,
  Empty,
  Failed,
  NumberField,
  Skeleton,
  Table,
  Td,
  TextField,
  Th,
  Tr,
} from '../ui'

type Room = {
  id: number
  reference: string
  label: string
  /** null means the centre's own figure applies, whatever it is set to. */
  surveillants: number | null
}

type CenterDetail = { id: number; name: string; rooms: Room[] }
type Timetable = { streams: Stream[] }

/**
 * One row of the room table. The surveillants figure is left empty when the
 * room follows the centre's own number, so an administrator who never touches
 * it is not asked to restate it on every room.
 */
function RoomRow({
  centerId,
  room,
  heldBy,
}: {
  centerId: number
  room: Room
  /** The filière holding it this session, if any. */
  heldBy: string | null
}) {
  const { t } = useTranslation()
  const [editing, setEditing] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [label, setLabel] = useState(room.label)
  const [surveillants, setSurveillants] = useState<number | null>(room.surveillants)

  const save = useApiMutation({
    run: () =>
      api.post<CenterDetail>(`/centers/${centerId}/rooms/${room.id}`, { label, surveillants }),
    invalidate: ['center', centerId],
    onDone: () => {
      setEditing(false)
    },
  })

  const remove = useApiMutation({
    run: () => api.del<CenterDetail>(`/centers/${centerId}/rooms/${room.id}`),
    invalidate: ['center', centerId],
    onDone: () => t('rooms.deleted'),
  })

  if (editing) {
    return (
      <tr className="border-b border-[var(--color-hairline)] bg-[var(--color-accent-tint)]/40 last:border-b-0">
        <Td>
          <TextField
            aria-label={t('rooms.label')}
            value={label}
            onChange={setLabel}
            className="max-w-xs"
            autoFocus
          />
        </Td>
        <Td>
          <span className="text-[12.5px] text-[var(--color-quiet)]">{heldBy ?? '—'}</span>
        </Td>
        <Td>
          <NumberField
            aria-label={t('rooms.surveillants.label')}
            value={surveillants ?? undefined}
            minValue={2}
            onChange={(value) => setSurveillants(Number.isNaN(value) ? null : value)}
            className="w-36"
          />
        </Td>
        <Td className="text-end">
          <div className="flex items-center justify-end gap-2">
            <Button size="sm" isPending={save.isPending} onPress={() => save.mutate(undefined)}>
              {t('app.save')}
            </Button>
            <Button
              size="sm"
              variant="quiet"
              isIcon
              aria-label={t('app.cancel')}
              onPress={() => {
                setLabel(room.label)
                setSurveillants(room.surveillants)
                setEditing(false)
              }}
            >
              <X size={15} aria-hidden />
            </Button>
          </div>
        </Td>
      </tr>
    )
  }

  return (
    <Tr>
      <Td>
        <div className="flex items-center gap-3">
          {/* the reference is what the order and the printed sheets go by, so
              it is on the row rather than hidden behind the label */}
          <span className="numeric w-10 shrink-0 text-[12px] font-medium text-[var(--color-faint)]">
            {room.reference}
          </span>
          <span className="font-medium">{room.label}</span>
        </div>
      </Td>
      <Td>
        {/* a room nobody has given to a filière takes no épreuve at all */}
        {heldBy === null ? (
          <span className="text-[12.5px] text-[var(--color-faint)]">{'—'}</span>
        ) : (
          <Badge tone="accent">{heldBy}</Badge>
        )}
      </Td>
      <Td>
        {room.surveillants === null ? (
          <span className="text-[12.5px] text-[var(--color-quiet)]">
            {t('rooms.surveillants.default')}
          </span>
        ) : (
          <span className="numeric font-medium">{room.surveillants}</span>
        )}
      </Td>
      <Td className="no-print text-end">
        {/*
          Deleting asks first, in the row itself. A single stray click on a bin
          should not cost a room, and a question that appears where the hand
          already is beats a dialog in the middle of the screen.
        */}
        {confirming ? (
          <div className="flex items-center justify-end gap-2">
            <span className="text-[12px] text-[var(--color-quiet)]">
              {t('rooms.delete.confirm', { room: room.label })}
            </span>
            <Button
              size="sm"
              variant="danger"
              isPending={remove.isPending}
              onPress={() => remove.mutate(undefined)}
            >
              {t('app.delete')}
            </Button>
            <Button size="sm" variant="quiet" onPress={() => setConfirming(false)}>
              {t('app.cancel')}
            </Button>
          </div>
        ) : (
          <div className="flex items-center justify-end gap-1">
            <Button
              size="sm"
              variant="quiet"
              isIcon
              aria-label={t('rooms.edit')}
              onPress={() => setEditing(true)}
            >
              <Pencil size={15} aria-hidden />
            </Button>
            {/* quiet until reached for: a red bin repeated down thirteen rows
                reads as thirteen warnings rather than one available action */}
            <Button
              size="sm"
              variant="quiet"
              isIcon
              aria-label={t('app.delete')}
              onPress={() => setConfirming(true)}
              className="hover:text-[var(--color-alarm)]"
            >
              <Trash2 size={15} aria-hidden />
            </Button>
          </div>
        )}
      </Td>
    </Tr>
  )
}

/**
 * Rooms arrive by the dozen and are named alike, so they are added in bulk and
 * renamed afterwards — a centre of thirteen rooms is one number, not thirteen
 * forms.
 */
function AddRooms({ centerId }: { centerId: number }) {
  const { t } = useTranslation()
  const [count, setCount] = useState(1)
  const [prefix, setPrefix] = useState('')

  const add = useApiMutation({
    run: () => api.post<CenterDetail>(`/centers/${centerId}/rooms`, { count, prefix }),
    invalidate: ['center', centerId],
    onDone: () => {
      const added = count
      setCount(1)
      return t('rooms.added', { count: added })
    },
  })

  return (
    <div className="rounded-b-[var(--radius-card)] border-t border-[var(--color-hairline)] bg-[var(--color-sunken)] px-5 py-4">
      <form
        className="flex flex-wrap items-end gap-3"
        onSubmit={(event) => {
          event.preventDefault()
          add.mutate(undefined)
        }}
      >
        <NumberField
          label={t('rooms.add.count')}
          value={count}
          minValue={1}
          maxValue={200}
          onChange={setCount}
          className="w-40"
        />
        <TextField
          label={t('rooms.add.prefix')}
          value={prefix}
          onChange={setPrefix}
          placeholder={t('rooms.add.placeholder')}
          className="w-56"
        />
        <Button type="submit" isPending={add.isPending}>
          <Plus size={16} aria-hidden />
          {t('rooms.add.button')}
        </Button>
      </form>
      <p className="mt-2.5 text-[11.5px] text-[var(--color-faint)]">{t('rooms.add.hint')}</p>
    </div>
  )
}

/**
 * The rooms of the centre, and what is happening in them.
 *
 * <p>They used to be a table halfway down the centre's page, which meant the
 * only way to look at the rooms was to go somewhere else first. The column
 * that could not exist there is the one that matters during a session: which
 * filière holds this room, and therefore which rooms nobody is using — a room
 * standing empty while another filière is short of seats is the sort of thing
 * that should be visible without opening the planning grid.
 */
export function RoomsPage() {
  const { t } = useTranslation()
  const { centerId, sessionId, hasCenter, isLoading } = useWorkspace()

  const center = useQuery({
    queryKey: ['center', centerId],
    queryFn: () => api.get<CenterDetail>(`/centers/${centerId}`),
    enabled: centerId !== null,
  })

  const timetable = useQuery({
    queryKey: ['timetable', sessionId],
    queryFn: () => api.get<Timetable>(`/sessions/${sessionId}/timetable`),
    enabled: sessionId !== null,
  })

  /** Which filière holds each room for the whole session, by room id. */
  const heldBy = new Map<number, string>()
  for (const stream of timetable.data?.streams ?? []) {
    for (const room of stream.rooms) heldBy.set(room.id, stream.name)
  }

  const rooms = center.data?.rooms ?? []
  const idle = rooms.filter((room) => !heldBy.has(room.id)).length

  if (!isLoading && !hasCenter) {
    return (
      <Page title={t('rooms.title')}>
        <Card>
          <Empty icon={<DoorOpen size={22} aria-hidden />}>{t('teachers.noCenter')}</Empty>
        </Card>
      </Page>
    )
  }

  return (
    <Page
      title={t('rooms.title')}
      subtitle={t('rooms.subtitle')}
    >
      <Card>
        <CardHead
          title={t('rooms.title')}
          count={rooms.length}
          actions={
            /* said once, above the table, rather than as a badge repeated down
               however many rows are free */
            timetable.isSuccess && idle > 0 ? (
              <Badge tone="warn">{t('rooms.idle', { count: idle })}</Badge>
            ) : undefined
          }
        />
        <CardRule />

        {center.isPending && <Skeleton rows={6} />}
        {center.isError && (
          <Failed error={center.error as Error} onRetry={() => void center.refetch()} />
        )}

        {center.isSuccess &&
          (rooms.length === 0 ? (
            <Empty icon={<DoorOpen size={22} aria-hidden />}>{t('rooms.empty')}</Empty>
          ) : (
            <Table>
              <thead>
                <tr>
                  <Th>{t('rooms.label')}</Th>
                  <Th width="220px">{t('rooms.heldBy')}</Th>
                  <Th width="180px">{t('rooms.surveillants.label')}</Th>
                  <Th width="240px" className="no-print" />
                </tr>
              </thead>
              <tbody>
                {rooms.map((room) => (
                  <RoomRow
                    key={room.id}
                    centerId={center.data.id}
                    room={room}
                    heldBy={heldBy.get(room.id) ?? null}
                  />
                ))}
              </tbody>
            </Table>
          ))}

        {center.isSuccess && <AddRooms centerId={center.data.id} />}
      </Card>
    </Page>
  )
}
