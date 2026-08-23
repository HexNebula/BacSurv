import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Check, Pencil, Plus, Trash2, X } from 'lucide-react'
import {
  Button,
  DateField,
  Input,
  Label,
  ListBox,
  Modal,
  NumberField,
  Select,
  TextField,
} from '@heroui/react'
import { CalendarDate, parseDate } from '@internationalized/date'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { Page, Failed, Loading, Empty } from '../components/Page'

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

/** A card is one thing the administrator sets up: the rooms, the sessions. */
function Section({
  title,
  hint,
  children,
}: {
  title: string
  hint?: string
  children: React.ReactNode
}) {
  return (
    <section className="rounded-xl border border-neutral-200 bg-white">
      <header className="border-b border-neutral-200 px-4 py-3">
        <h2 className="text-sm font-semibold">{title}</h2>
        {hint && <p className="mt-0.5 text-xs text-neutral-500">{hint}</p>}
      </header>
      {children}
    </section>
  )
}

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
      <div className="flex items-center gap-2">
        <h1 className="text-xl font-semibold tracking-tight">{center.name}</h1>
        <Button
          variant="ghost"
          size="sm"
          isIconOnly
          aria-label={t('center.rename')}
          onPress={() => {
            setDraft(center.name)
            setEditing(true)
          }}
        >
          <Pencil size={14} aria-hidden />
        </Button>
      </div>
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
      <TextField value={draft} onChange={setDraft} aria-label={t('centers.name')} autoFocus>
        <Input />
      </TextField>
      <Button type="submit" size="sm" isPending={rename.isPending}>
        <Check size={14} aria-hidden />
        {t('app.save')}
      </Button>
      <Button type="button" variant="ghost" size="sm" onPress={() => setEditing(false)}>
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
      <tr className="border-t border-neutral-100">
        <td className="px-4 py-2 align-middle">
          <TextField value={label} onChange={setLabel} aria-label={t('rooms.label')} autoFocus>
            <Input />
          </TextField>
        </td>
        <td className="px-4 py-2 align-middle">
          <NumberField
            value={surveillants ?? undefined}
            minValue={2}
            onChange={(value) => setSurveillants(Number.isNaN(value) ? null : value)}
            aria-label={t('rooms.surveillants.label')}
          >
            <NumberField.Group>
              <NumberField.DecrementButton />
              <NumberField.Input />
              <NumberField.IncrementButton />
            </NumberField.Group>
          </NumberField>
        </td>
        <td className="px-4 py-2 text-end align-middle">
          <div className="flex items-center justify-end gap-1">
            <Button size="sm" isPending={save.isPending} onPress={() => save.mutate(undefined)}>
              {t('app.save')}
            </Button>
            <Button
              size="sm"
              variant="ghost"
              isIconOnly
              aria-label={t('app.cancel')}
              onPress={() => {
                setLabel(room.label)
                setSurveillants(room.surveillants)
                setEditing(false)
              }}
            >
              <X size={14} aria-hidden />
            </Button>
          </div>
        </td>
      </tr>
    )
  }

  return (
    <tr className="border-t border-neutral-100">
      <td className="px-4 py-2.5 text-sm">{room.label}</td>
      <td className="px-4 py-2.5 text-sm">
        {room.surveillants === null ? (
          <span className="text-neutral-400">{t('rooms.surveillants.default')}</span>
        ) : (
          <span className="numeric">{room.surveillants}</span>
        )}
      </td>
      <td className="px-4 py-2.5 text-end">
        <div className="flex items-center justify-end gap-1">
          <Button
            size="sm"
            variant="ghost"
            isIconOnly
            aria-label={t('rooms.edit')}
            onPress={() => setEditing(true)}
          >
            <Pencil size={14} aria-hidden />
          </Button>
          <Button
            size="sm"
            variant="ghost"
            isIconOnly
            aria-label={t('app.delete')}
            isPending={remove.isPending}
            onPress={() => remove.mutate(undefined)}
          >
            <Trash2 size={14} className="text-red-600" aria-hidden />
          </Button>
        </div>
      </td>
    </tr>
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
    <form
      className="flex flex-wrap items-end gap-3 border-t border-neutral-200 bg-neutral-50 px-4 py-3"
      onSubmit={(event) => {
        event.preventDefault()
        add.mutate(undefined)
      }}
    >
      <NumberField
        value={count}
        minValue={1}
        maxValue={200}
        onChange={setCount}
        className="w-40"
      >
        <Label>{t('rooms.add.count')}</Label>
        <NumberField.Group>
          <NumberField.DecrementButton />
          <NumberField.Input />
          <NumberField.IncrementButton />
        </NumberField.Group>
      </NumberField>

      <TextField value={prefix} onChange={setPrefix} className="w-56">
        <Label>{t('rooms.add.prefix')}</Label>
        <Input placeholder={t('rooms.add.placeholder')} />
      </TextField>

      <Button type="submit" isPending={add.isPending}>
        <Plus size={15} aria-hidden />
        {t('rooms.add.button')}
      </Button>
    </form>
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
      <Button size="sm" onPress={() => setOpen(true)}>
        <Plus size={15} aria-hidden />
        {t('sessions.create')}
      </Button>

      <Modal isOpen={open} onOpenChange={setOpen}>
        <Modal.Backdrop>
          <Modal.Container>
            <Modal.Dialog>
              <Modal.Header>
                <Modal.Heading>{t('sessions.create')}</Modal.Heading>
              </Modal.Header>

              <Modal.Body>
                <form
                  id="new-session"
                  className="space-y-4"
                  onSubmit={(event) => {
                    event.preventDefault()
                    create.mutate(undefined)
                  }}
                >
                  <TextField value={reference} onChange={setReference} fullWidth>
                    <Label>{t('sessions.reference.label')}</Label>
                    <Input placeholder={t('sessions.reference.hint')} />
                  </TextField>

                  <Select
                    selectedKey={type}
                    onSelectionChange={(key) => setType(String(key))}
                    fullWidth
                  >
                    <Label>{t('sessions.type.label')}</Label>
                    <Select.Trigger>
                      <Select.Value />
                      <Select.Indicator />
                    </Select.Trigger>
                    <Select.Popover>
                      <ListBox>
                        {SESSION_TYPES.map((value) => (
                          <ListBox.Item key={value} id={value} textValue={t(`sessions.type.${value}`)}>
                            {t(`sessions.type.${value}`)}
                          </ListBox.Item>
                        ))}
                      </ListBox>
                    </Select.Popover>
                  </Select>

                  <div className="grid grid-cols-2 gap-3">
                    <DateField value={startsOn} onChange={setStartsOn}>
                      <Label>{t('sessions.startsOn')}</Label>
                      <DateField.Group>
                        <DateField.InputContainer>
                          <DateField.Input>
                            {(segment) => <DateField.Segment segment={segment} />}
                          </DateField.Input>
                        </DateField.InputContainer>
                      </DateField.Group>
                    </DateField>

                    <DateField value={endsOn} onChange={setEndsOn}>
                      <Label>{t('sessions.endsOn')}</Label>
                      <DateField.Group>
                        <DateField.InputContainer>
                          <DateField.Input>
                            {(segment) => <DateField.Segment segment={segment} />}
                          </DateField.Input>
                        </DateField.InputContainer>
                      </DateField.Group>
                    </DateField>
                  </div>
                </form>
              </Modal.Body>

              <Modal.Footer>
                <Button variant="ghost" onPress={() => setOpen(false)}>
                  {t('app.cancel')}
                </Button>
                <Button type="submit" form="new-session" isPending={create.isPending}>
                  {t('app.save')}
                </Button>
              </Modal.Footer>
            </Modal.Dialog>
          </Modal.Container>
        </Modal.Backdrop>
      </Modal>
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

export function CenterPage() {
  const { t, i18n } = useTranslation()
  const { id } = useParams()
  const centerId = Number(id)

  const center = useQuery({
    queryKey: ['center', centerId],
    queryFn: () => api.get<CenterDetail>(`/centers/${centerId}`),
    enabled: Number.isFinite(centerId),
  })

  const dates = new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium' })
  const span = (session: Session) => {
    const from = readDate(session.startsOn)
    const to = readDate(session.endsOn)
    if (!from || !to) return t('sessions.datesUnset')
    return `${dates.format(from.toDate('UTC'))} — ${dates.format(to.toDate('UTC'))}`
  }

  if (center.isPending) return <Page title={t('centers.title')}><Loading /></Page>
  if (center.isError) {
    return (
      <Page title={t('centers.title')}>
        <Failed error={center.error as Error} onRetry={() => void center.refetch()} />
      </Page>
    )
  }

  const detail = center.data

  return (
    <div className="mx-auto max-w-6xl px-8 py-7">
      <Link
        to="/centers"
        className="mb-4 inline-flex items-center gap-1.5 text-xs text-neutral-500 hover:text-neutral-900"
      >
        <ArrowLeft size={14} className="rtl:rotate-180" aria-hidden />
        {t('centers.title')}
      </Link>

      <header className="mb-6">
        <CenterName center={detail} />
        <p className="mt-1 text-sm text-neutral-500">
          <span className="numeric">{detail.teacherCount}</span> {t('centers.teachers')} ·{' '}
          <span className="numeric">{detail.rooms.length}</span> {t('centers.rooms')}
        </p>
      </header>

      <div className="space-y-6">
        <Section title={t('rooms.title')} hint={t('rooms.add.hint')}>
          {detail.rooms.length === 0 ? (
            <div className="px-4 py-6">
              <Empty>{t('rooms.empty')}</Empty>
            </div>
          ) : (
            <table className="w-full text-start">
              <thead>
                <tr className="text-xs font-medium text-neutral-500">
                  <th className="px-4 py-2 text-start font-medium">{t('rooms.label')}</th>
                  <th className="px-4 py-2 text-start font-medium">{t('rooms.surveillants.label')}</th>
                  <th className="px-4 py-2" />
                </tr>
              </thead>
              <tbody>
                {detail.rooms.map((room) => (
                  <RoomRow key={room.id} centerId={detail.id} room={room} />
                ))}
              </tbody>
            </table>
          )}
          <AddRooms centerId={detail.id} />
        </Section>

        <Section title={t('sessions.title')}>
          {detail.sessions.length === 0 ? (
            <div className="px-4 py-6">
              <Empty>{t('sessions.empty')}</Empty>
            </div>
          ) : (
            <ul className="divide-y divide-neutral-100">
              {detail.sessions.map((session) => (
                <li key={session.id} className="flex items-center justify-between gap-4 px-4 py-3">
                  <div className="min-w-0">
                    <div className="truncate text-sm font-medium">{session.reference}</div>
                    <div className="mt-0.5 text-xs text-neutral-500">
                      {t(`sessions.type.${session.type}`, { defaultValue: session.type })} ·{' '}
                      <span className="numeric">{span(session)}</span>
                    </div>
                  </div>
                  <span className="shrink-0 text-xs text-neutral-500">
                    <span className="numeric">{session.slotCount}</span> {t('sessions.slots')}
                  </span>
                </li>
              ))}
            </ul>
          )}
          <div className="border-t border-neutral-200 bg-neutral-50 px-4 py-3">
            <NewSession centerId={detail.id} />
          </div>
        </Section>
      </div>
    </div>
  )
}
