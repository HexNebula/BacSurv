import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { CalendarPlus, Check, Download, Pencil } from 'lucide-react'
import { CalendarDate, parseDate } from '@internationalized/date'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { useWorkspace } from '../context/Workspace'
import { Page } from '../components/Page'
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
  Select,
  Skeleton,
  TextField,
} from '../ui'

type Session = {
  id: number
  reference: string
  type: string
  startsOn: string | null
  endsOn: string | null
  slotCount: number
}

/** How the establishment is identified on paper; every field may be absent. */
type Identity = {
  academy: string | null
  directorate: string | null
  commune: string | null
  ministerialReference: string | null
}

type CenterDetail = {
  id: number
  name: string
  identity: Identity
  teacherCount: number
  rooms: { id: number }[]
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
 * How the centre is identified on paper.
 *
 * <p>The académie, the direction provinciale, the commune and the ministerial
 * reference. None of it reaches the solver — this is the head of a convocation
 * and of every list pinned to a door, which is why it is here at all.
 *
 * <p>It reads as a list until somebody presses Modifier: four fields sitting
 * open on a screen an administrator visits to add rooms is four chances to
 * change something by accident.
 */
function CenterIdentity({ center }: { center: CenterDetail }) {
  const { t } = useTranslation()
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState<Identity>(center.identity)

  const save = useApiMutation({
    run: () =>
      api.post<CenterDetail>(`/centers/${center.id}`, { name: center.name, ...draft }),
    invalidate: ['center', center.id],
    onDone: () => {
      setEditing(false)
      return t('center.saved')
    },
  })

  const FIELDS = [
    ['academy', t('center.academy'), t('center.academyHint')],
    ['directorate', t('center.directorate'), t('center.directorateHint')],
    ['commune', t('center.commune'), undefined],
    ['ministerialReference', t('center.ministerialReference'), undefined],
  ] as const

  return (
    <Card>
      <CardHead
        title={t('center.title')}
        actions={
          !editing && (
            <Button
              variant="secondary"
              size="sm"
              onPress={() => {
                setDraft(center.identity)
                setEditing(true)
              }}
            >
              <Pencil size={15} aria-hidden />
              {t('center.edit')}
            </Button>
          )
        }
      />
      <CardRule />

      {editing ? (
        <form
          className="p-5"
          onSubmit={(event) => {
            event.preventDefault()
            save.mutate(undefined)
          }}
        >
          <div className="grid gap-4 sm:grid-cols-2">
            {FIELDS.map(([key, label, hint]) => (
              <TextField
                key={key}
                label={label}
                placeholder={hint}
                value={draft[key] ?? ''}
                onChange={(value) => setDraft((current) => ({ ...current, [key]: value }))}
              />
            ))}
          </div>
          <div className="mt-5 flex items-center gap-2">
            <Button type="submit" isPending={save.isPending}>
              <Check size={16} aria-hidden />
              {t('app.save')}
            </Button>
            <Button type="button" variant="quiet" onPress={() => setEditing(false)}>
              {t('app.cancel')}
            </Button>
          </div>
        </form>
      ) : (
        <dl className="grid gap-x-8 gap-y-4 p-5 sm:grid-cols-2">
          {FIELDS.map(([key, label]) => (
            <div key={key}>
              <dt className="text-[11.5px] font-medium text-[var(--color-faint)]">{label}</dt>
              <dd
                className={`mt-1 text-[14px] ${
                  center.identity[key]
                    ? 'text-[var(--color-ink)]'
                    : 'text-[var(--color-faint)]'
                }`}
              >
                {center.identity[key] ?? t('center.empty')}
              </dd>
            </div>
          ))}
        </dl>
      )}

      <div className="rounded-b-[var(--radius-card)] border-t border-[var(--color-hairline)] bg-[var(--color-sunken)] px-5 py-3">
        <p className="text-[11.5px] text-[var(--color-faint)]">{t('center.hint')}</p>
      </div>
    </Card>
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
      actions={
        /*
          A plain link, not a fetch: what is wanted is a file on a disk, and
          the browser is what puts it there. Nothing is sent anywhere — the
          server writes the centre out and the download stays on this machine.
        */
        <a
          href={`/api/centers/${detail.id}/export`}
          title={t('center.exportHint')}
          className="inline-flex h-10 items-center gap-2 rounded-[var(--radius-field)] bg-[var(--color-surface)] px-4
            text-[13.5px] font-medium ring-1 ring-[var(--color-hairline)] transition-shadow
            hover:ring-[var(--color-faint)]/45"
        >
          <Download size={16} aria-hidden />
          {t('center.export')}
        </a>
      }
    >
      <div className="space-y-6">
        <CenterIdentity center={detail} />

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
