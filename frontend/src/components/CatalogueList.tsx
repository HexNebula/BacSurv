import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Check, Pencil, Plus, Trash2, X } from 'lucide-react'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { LEVELS, type Level } from '../lib/catalogue'
import { useNames } from '../lib/names'
import {
  Button,
  Card,
  CardHead,
  CardRule,
  Empty,
  Failed,
  SearchField,
  Select,
  Skeleton,
  TextField,
} from '../ui'

export type Entry = {
  id: number
  /** The Arabic name — the one teachers and épreuves store, and the one
      the solver matches on. */
  name: string
  /** A French label for French documents. Nothing joins on it. */
  nameFr: string | null
  /** BAC1 or BAC2 for a filière; a subject has none. */
  level: Level | null
  usedByTeachers: number
  usedByExams: number
}

/** A heading for the year a run of filières belongs to, or one of its names. */
type Item = { head: Level } | { row: Entry }

/**
 * One of a centre's own lists — its subjects, its filières.
 *
 * <p>The two behave identically, so they are one component: a short list of
 * names the administrator adds to, corrects and prunes. Each row says what
 * already depends on it, because that is what decides whether it can be removed
 * at all.
 *
 * <p>The list is read down a column, not across a row. A centre runs seventeen
 * filières and every one of them is a name, a level and a count — so the level
 * is a heading over a run of them rather than a chip repeated seventeen times,
 * and the count sits in a column of its own width instead of floating wherever
 * the name before it happened to end.
 *
 * <p>Nothing depending on a name yet is written as a dash, not as a warning. A
 * list entered in September is entirely unused by definition, and a page that
 * marks all seventeen rows in amber says only that it does not know what a
 * fault is. Where being unused genuinely matters — a subject examined with no
 * specialist to sit its permanence — it is said on the coverage screen, which
 * has the figures to say it with.
 */
export function CatalogueList({
  centerId,
  kind,
}: {
  centerId: number
  kind: 'subjects' | 'streams'
}) {
  const { t } = useTranslation()
  const [adding, setAdding] = useState('')
  const [addingFr, setAddingFr] = useState('')
  // 2BAC first: it is the session a centre runs most, and the one whose
  // filières get typed in most often
  const [level, setLevel] = useState<Level>('BAC2')
  const [search, setSearch] = useState('')

  const entries = useQuery({
    queryKey: [kind, centerId],
    queryFn: () => api.get<Entry[]>(`/centers/${centerId}/${kind}`),
  })

  const add = useApiMutation({
    run: () =>
      api.post<Entry[]>(`/centers/${centerId}/${kind}`, {
        name: adding.trim(),
        // the French label is optional and always sent: absent would mean
        // "leave the one that is there", and a new entry has none
        nameFr: addingFr.trim(),
        // a filière belongs to one year: without it the picker would offer it
        // when planning both
        level: kind === 'streams' ? level : null,
      }),
    invalidate: [kind, centerId],
    onDone: () => {
      setAdding('')
      setAddingFr('')
    },
  })

  const all = useMemo(() => entries.data ?? [], [entries.data])

  const shown = useMemo(() => {
    const needle = search.trim().toLowerCase()
    if (needle === '') return all
    return all.filter(
      (one) =>
        one.name.toLowerCase().includes(needle) ||
        (one.nameFr ?? '').toLowerCase().includes(needle),
    )
  }, [all, search])

  /**
   * Filières under the year that sits them, subjects in one run. The order is
   * the calendar's — 1re année then 2e — and a name whose level the server does
   * not recognise still appears, at the end, rather than vanishing from a list
   * it is plainly in.
   */
  const items = useMemo<Item[]>(() => {
    if (kind !== 'streams') return shown.map((row) => ({ row }))

    const groups = LEVELS.map((one) => ({
      head: one,
      rows: shown.filter((entry) => entry.level === one),
    }))
    const rest = shown.filter((entry) => entry.level === null || !LEVELS.includes(entry.level))

    return [
      ...groups.flatMap((group) =>
        group.rows.length === 0
          ? []
          : [{ head: group.head } as Item, ...group.rows.map((row) => ({ row }))],
      ),
      ...rest.map((row) => ({ row })),
    ]
  }, [kind, shown])

  return (
    <Card>
      {/* the page above already carries the noun; this line is the count and
          the way into a list too long to read down */}
      <CardHead
        title={t('catalogue.own')}
        count={all.length}
        actions={
          all.length > 0 && (
            <SearchField
              className="w-56"
              label={t('catalogue.search')}
              placeholder={t(`catalogue.${kind}.placeholder`)}
              value={search}
              onChange={setSearch}
            />
          )
        }
      />
      <CardRule />

      {entries.isPending && <Skeleton rows={3} />}
      {entries.isError && (
        <Failed error={entries.error as Error} onRetry={() => void entries.refetch()} />
      )}
      {entries.isSuccess &&
        (all.length === 0 ? (
          <Empty>{t(`catalogue.${kind}.empty`)}</Empty>
        ) : shown.length === 0 ? (
          <Empty>{t('catalogue.noMatch', { search: search.trim() })}</Empty>
        ) : (
          <ul>
            {items.map((item) =>
              'head' in item ? (
                <li
                  key={`head-${item.head}`}
                  className="border-b border-[var(--color-hairline)] bg-[var(--color-sunken)] px-5 py-2 last:border-b-0"
                >
                  <span className="eyebrow">{t(`streams.level${item.head}`)}</span>
                </li>
              ) : (
                <Row key={item.row.id} centerId={centerId} kind={kind} entry={item.row} />
              ),
            )}
          </ul>
        ))}

      <div className="rounded-b-[var(--radius-card)] border-t border-[var(--color-hairline)] bg-[var(--color-sunken)] px-5 py-4">
        <form
          className="flex items-center gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            if (adding.trim() !== '') add.mutate(undefined)
          }}
        >
          <TextField
            aria-label={t(`catalogue.${kind}.title`)}
            value={adding}
            onChange={setAdding}
            placeholder={t(`catalogue.${kind}.placeholder`)}
            className="flex-1"
          />
          {/* the French label, typed beside the name rather than found later
              through the pencil: it is the same act */}
          <TextField
            aria-label={t('catalogue.nameFr')}
            value={addingFr}
            onChange={setAddingFr}
            placeholder={t('catalogue.nameFr')}
            className="flex-1"
          />
          {kind === 'streams' && (
            <Select
              label={t('streams.level')}
              hideLabel
              className="w-44"
              value={level}
              onChange={(key) => setLevel(key as Level)}
              choices={LEVELS.map((one) => ({ id: one, label: t(`streams.level${one}`) }))}
            />
          )}
          <Button type="submit" isPending={add.isPending} isDisabled={adding.trim() === ''}>
            <Plus size={16} aria-hidden />
            {t('app.add')}
          </Button>
        </form>
        <p className="mt-2.5 text-[11.5px] text-[var(--color-faint)]">
          {t(`catalogue.${kind}.hint`)}
        </p>
      </div>
    </Card>
  )
}

function Row({
  centerId,
  kind,
  entry,
}: {
  centerId: number
  kind: 'subjects' | 'streams'
  entry: Entry
}) {
  const { t } = useTranslation()
  const { label, second } = useNames()
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(entry.name)
  const [nameFr, setNameFr] = useState(entry.nameFr ?? '')
  const [level, setLevel] = useState<Level | null>(entry.level)
  const [confirming, setConfirming] = useState(false)

  const rename = useApiMutation({
    run: () =>
      api.post<Entry[]>(`/centers/${centerId}/${kind}/${entry.id}`, {
        name: name.trim(),
        // always sent from this form, empty included: the server reads an
        // absent field as "leave the label alone", so omitting it would make
        // clearing one impossible
        nameFr: nameFr.trim(),
        level,
      }),
    invalidate: [kind, centerId],
    onDone: () => {
      setEditing(false)
      // teachers and épreuves were rewritten with it, so they must be refetched
      return t('catalogue.renamed')
    },
  })

  const remove = useApiMutation({
    run: () => api.del<Entry[]>(`/centers/${centerId}/${kind}/${entry.id}`),
    invalidate: [kind, centerId],
    onDone: () => {
      setConfirming(false)
    },
  })

  const used = entry.usedByTeachers > 0 || entry.usedByExams > 0
  // the same field counts different things: teachers for a subject, sessions
  // for a filière — so it must not be labelled the same way
  const firstLabel = kind === 'streams' ? 'catalogue.usedBySessions' : 'catalogue.usedByTeachers'

  if (editing) {
    return (
      <li className="flex items-center gap-2 border-b border-[var(--color-hairline)] bg-[var(--color-accent-tint)]/40 px-5 py-3 last:border-b-0">
        <TextField
          aria-label={t('app.rename')}
          value={name}
          onChange={setName}
          className="flex-1"
          autoFocus
        />
        <TextField
          aria-label={t('catalogue.nameFr')}
          value={nameFr}
          onChange={setNameFr}
          placeholder={t('catalogue.nameFr')}
          className="flex-1"
        />
        {/* the level only appears here now: down the list it is the heading
            above the run this name belongs to */}
        {kind === 'streams' && (
          <Select
            label={t('streams.level')}
            hideLabel
            className="w-44"
            value={level}
            onChange={(key) => setLevel(key as Level)}
            choices={LEVELS.map((one) => ({ id: one, label: t(`streams.level${one}`) }))}
          />
        )}
        <Button isPending={rename.isPending} onPress={() => rename.mutate(undefined)}>
          <Check size={16} aria-hidden />
          {t('app.save')}
        </Button>
        <Button
          variant="quiet"
          isIcon
          aria-label={t('app.cancel')}
          onPress={() => {
            setName(entry.name)
            setNameFr(entry.nameFr ?? '')
            setLevel(entry.level)
            setEditing(false)
          }}
        >
          <X size={16} aria-hidden />
        </Button>
      </li>
    )
  }

  return (
    <li className="flex items-center gap-4 border-b border-[var(--color-hairline)] px-5 py-3 transition-colors last:border-b-0 hover:bg-[var(--color-sunken)]">
      {/* an Arabic name in a French interface, or the reverse: the name is its
          own run of text and keeps its own direction */}
      <span className="min-w-0 flex-1">
        <bdi className="block truncate text-[14px] font-medium">{label(entry)}</bdi>
        {/* the same entry said the other way: renaming rewrites every teacher
            and épreuve that stored the Arabic name, so it is worth seeing which
            one you are about to edit */}
        {second(entry) && (
          <bdi className="mt-0.5 block truncate text-[11.5px] text-[var(--color-quiet)]">
            {second(entry)}
          </bdi>
        )}
      </span>

      {/* one column, one statement: what already leans on this name. A dash
          where nothing does — the fact belongs in the same place on every row,
          and it is not a fault */}
      <span className="w-48 shrink-0 text-end text-[12px] text-[var(--color-quiet)]">
        {used ? (
          [
            entry.usedByTeachers > 0 &&
              `${entry.usedByTeachers} ${t(firstLabel, { count: entry.usedByTeachers })}`,
            entry.usedByExams > 0 &&
              `${entry.usedByExams} ${t('catalogue.usedByExams', { count: entry.usedByExams })}`,
          ]
            .filter(Boolean)
            .join(' · ')
        ) : (
          <span className="text-[var(--color-faint)]" title={t('catalogue.unused')}>
            —
          </span>
        )}
      </span>

      {confirming ? (
        <span className="flex shrink-0 items-center justify-end gap-2">
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
        </span>
      ) : (
        // held to the width of the two buttons, so the pencils line up down the
        // card whatever the names beside them do
        <span className="flex w-[68px] shrink-0 items-center justify-end gap-1">
          <Button
            size="sm"
            variant="quiet"
            isIcon
            aria-label={t('app.rename')}
            onPress={() => setEditing(true)}
          >
            <Pencil size={15} aria-hidden />
          </Button>
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
        </span>
      )}
    </li>
  )
}
