import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { CalendarPlus, Check, DoorOpen, Pencil, Plus, Trash2, X } from 'lucide-react'
import { CalendarDate, parseDate } from '@internationalized/date'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { useWorkspace } from '../context/Workspace'
import { Page } from '../components/Page'
import { CatalogueList } from '../components/CatalogueList'
import {
  Badge,
  Button,
  Card,
  CardHead,
  CardRule,
  DateField,
  Dialog,
  Empty,
  Failed,
  NumberField,
  Select,
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

type Session = {
  id: number
  reference: string
  type: string
  startsOn: string | null
  endsOn: string | null
  slotCount: number
}

type CenterDetail = {
  id: number
  name: string
  teacherCount: number
  rooms: Room[]
  sessions: Session[]
}

const SESSION_TYPES = ['REGIONAL_1BAC', 'NATIONAL_2BAC', 'NATIONAL_2BAC_RATTRAPAGE'] as const

/**
 * The centre's name, changed in place. Renaming is rare enough that a field
 * sitting open all the time would only invite a stray keystroke.
 */
function CenterName({ center }: { center: CenterDetail }) {
  const { t } = useTranslation()
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(center.name)

  const rename = useApiMutation({
    run: (name: string) => api.post<CenterDetail>(`/centers/${center.id}/name`, { name }),
    invalidate: ['center', center.id],
    onDone: () => {
      setEditing(false)
      return t('center.renamed')
    },
  })

  if (!editing) {
    return (
      <span className="flex items-center gap-2">
        {center.name}
        <Button
          variant="quiet"
          size="sm"
          isIcon
          aria-label={t('center.rename')}
          onPress={() => {
            setDraft(center.name)
            setEditing(true)
          }}
        >
          <Pencil size={15} aria-hidden />
        </Button>
      </span>
    )
  }

  return (
    <form
      className="flex items-center gap-2"
      onSubmit={(event) => {
        event.preventDefault()
        rename.mutate(draft)
      }}
    >
      <TextField
        aria-label={t('centers.name')}
        value={draft}
        onChange={setDraft}
        className="w-80"
        autoFocus
      />
      <Button type="submit" isPending={rename.isPending}>
        <Check size={16} aria-hidden />
        {t('app.save')}
      </Button>
      <Button type="button" variant="quiet" onPress={() => setEditing(false)}>
        {t('app.cancel')}
      </Button>
    </form>
  )
}

/**
 * One row of the room table. The surveillants figure is left empty when the
 * room follows the centre's own number, so an administrator who never touches
 * it is not asked to restate it on every room.
 */
function RoomRow({ centerId, room }: { centerId: number; room: Room }) {
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

/** A session is created with its dates; the papers themselves come later. */
function NewSession({ centerId }: { centerId: number }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [reference, setReference] = useState('')
  const [type, setType] = useState<string>('NATIONAL_2BAC')
  const [startsOn, setStartsOn] = useState<CalendarDate | null>(null)
  const [endsOn, setEndsOn] = useState<CalendarDate | null>(null)

  const create = useApiMutation({
    run: () =>
      api.post<CenterDetail>(`/centers/${centerId}/sessions`, {
        reference,
        type,
        // the server takes a plain date; CalendarDate prints exactly that
        startsOn: startsOn?.toString() ?? null,
        endsOn: endsOn?.toString() ?? null,
      }),
    invalidate: ['center', centerId],
    onDone: () => {
      setOpen(false)
      setReference('')
      setStartsOn(null)
      setEndsOn(null)
      return t('sessions.created')
    },
  })

  return (
    <>
      <Button onPress={() => setOpen(true)}>
        <CalendarPlus size={16} aria-hidden />
        {t('sessions.create')}
      </Button>

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

/** A date the server may not hold yet: sessions imported before dates existed. */
function readDate(value: string | null): CalendarDate | null {
  if (!value) return null
  try {
    return parseDate(value)
  } catch {
    return null
  }
}

/**
 * The establishment itself: its rooms, its own lists, its sessions.
 *
 * <p>Everything here outlives a single session. The rooms are the centre's for
 * the year, the subjects and filières are the vocabulary its paperwork uses,
 * and a session is a period the timetable then hangs off.
 */
export function CenterPage() {
  const { t, i18n } = useTranslation()
  const { centerId, sessionId, chooseSession } = useWorkspace()

  const center = useQuery({
    queryKey: ['center', centerId],
    queryFn: () => api.get<CenterDetail>(`/centers/${centerId}`),
    enabled: centerId !== null,
  })

  const dates = new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium' })
  const span = (session: Session) => {
    const from = readDate(session.startsOn)
    const to = readDate(session.endsOn)
    if (!from || !to) return t('sessions.datesUnset')
    return `${dates.format(from.toDate('UTC'))} — ${dates.format(to.toDate('UTC'))}`
  }

  if (center.isPending) {
    return (
      <Page title={t('nav.center')}>
        <Card>
          <Skeleton rows={5} />
        </Card>
      </Page>
    )
  }

  if (center.isError) {
    return (
      <Page title={t('nav.center')}>
        <Card>
          <Failed error={center.error as Error} onRetry={() => void center.refetch()} />
        </Card>
      </Page>
    )
  }

  const detail = center.data

  return (
    <Page
      title={<CenterName center={detail} />}
      subtitle={
        /* the noun agrees with the number: "1 session", not "1 sessions" */
        (
          [
            ['teacherCount', detail.teacherCount],
            ['roomCount', detail.rooms.length],
            ['sessionCount', detail.sessions.length],
          ] as const
        )
          .map(([key, value]) => `${value} ${t(`centers.${key}`, { count: value })}`)
          .join(' · ')
      }
    >
      <div className="space-y-6">
        <Card>
          <CardHead title={t('rooms.title')} count={detail.rooms.length} />
          <CardRule />
          {detail.rooms.length === 0 ? (
            <Empty icon={<DoorOpen size={22} aria-hidden />}>{t('rooms.empty')}</Empty>
          ) : (
            <Table>
              <thead>
                <tr>
                  <Th>{t('rooms.label')}</Th>
                  <Th width="200px">{t('rooms.surveillants.label')}</Th>
                  <Th width="240px" className="no-print" />
                </tr>
              </thead>
              <tbody>
                {detail.rooms.map((room) => (
                  <RoomRow key={room.id} centerId={detail.id} room={room} />
                ))}
              </tbody>
            </Table>
          )}
          <AddRooms centerId={detail.id} />
        </Card>

        {/* items-start: a short list must not stretch into a slab of white
            just because the list beside it is long */}
        <div className="grid items-start gap-6 lg:grid-cols-2">
          <CatalogueList centerId={detail.id} kind="subjects" />
          <CatalogueList centerId={detail.id} kind="streams" />
        </div>

        <Card>
          <CardHead
            title={t('sessions.title')}
            count={detail.sessions.length}
            actions={<NewSession centerId={detail.id} />}
          />
          <CardRule />
          {detail.sessions.length === 0 ? (
            <Empty icon={<CalendarPlus size={22} aria-hidden />}>{t('sessions.empty')}</Empty>
          ) : (
            <ul>
              {detail.sessions.map((session) => {
                const current = session.id === sessionId
                return (
                  <li key={session.id}>
                    {/* choosing one here is the same act as choosing it in the
                        header: there is one session in play at a time */}
                    <button
                      type="button"
                      onClick={() => chooseSession(session.id)}
                      className={`flex w-full items-center justify-between gap-4 border-b border-[var(--color-hairline)] px-5 py-4 text-start transition-colors last:border-b-0 ${
                        current
                          ? 'bg-[var(--color-accent-tint)]/50'
                          : 'hover:bg-[var(--color-sunken)]'
                      }`}
                    >
                      <span className="min-w-0">
                        <span className="flex items-center gap-2">
                          <span className="truncate text-[14px] font-medium">
                            {session.reference}
                          </span>
                          {current && <Badge tone="accent">{t('sessions.current')}</Badge>}
                        </span>
                        <span className="mt-1 flex flex-wrap items-center gap-2 text-[12px] text-[var(--color-quiet)]">
                          <span>
                            {t(`sessions.type.${session.type}`, { defaultValue: session.type })}
                          </span>
                          <span className="text-[var(--color-hairline)]">·</span>
                          {/* the two dates keep their order on an Arabic page */}
                          <bdi dir="ltr" className="numeric">
                            {span(session)}
                          </bdi>
                        </span>
                      </span>
                      <span className="numeric shrink-0 text-[12.5px] text-[var(--color-quiet)]">
                        {session.slotCount} {t('sessions.slots')}
                      </span>
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
        </Card>
      </div>
    </Page>
  )
}
