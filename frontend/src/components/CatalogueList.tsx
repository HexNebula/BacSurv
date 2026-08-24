import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Check, Pencil, Plus, Trash2, X } from 'lucide-react'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { LEVELS, type Level } from '../lib/levels'
import {
  Badge,
  Button,
  Card,
  CardHead,
  CardRule,
  Empty,
  Failed,
  Select,
  Skeleton,
  TextField,
} from '../ui'

export type Entry = {
  id: number
  name: string
  /** BAC1 or BAC2 for a filière; a subject has none. */
  level: Level | null
  usedByTeachers: number
  usedByExams: number
}

/**
 * One of a centre's own lists — its subjects, its filières.
 *
 * <p>The two behave identically, so they are one component: a short list of
 * names the administrator adds to, corrects and prunes. Each row says what
 * already depends on it, because that is what decides whether it can be removed
 * at all.
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
  // 2BAC first: it is the session a centre runs most, and the one whose
  // filières get typed in most often
  const [level, setLevel] = useState<Level>('BAC2')

  const entries = useQuery({
    queryKey: [kind, centerId],
    queryFn: () => api.get<Entry[]>(`/centers/${centerId}/${kind}`),
  })

  const add = useApiMutation({
    run: () =>
      api.post<Entry[]>(`/centers/${centerId}/${kind}`, {
        name: adding.trim(),
        // a filière belongs to one year: without it the picker would offer it
        // when planning both
        level: kind === 'streams' ? level : null,
      }),
    invalidate: [kind, centerId],
    onDone: () => {
      setAdding('')
    },
  })

  return (
    <Card>
      <CardHead title={t(`catalogue.${kind}.title`)} count={entries.data?.length} />
      <CardRule />

      {entries.isPending && <Skeleton rows={3} />}
      {entries.isError && (
        <Failed error={entries.error as Error} onRetry={() => void entries.refetch()} />
      )}
      {entries.isSuccess &&
        (entries.data.length === 0 ? (
          <Empty>{t(`catalogue.${kind}.empty`)}</Empty>
        ) : (
          <ul>
            {entries.data.map((entry) => (
              <Row key={entry.id} centerId={centerId} kind={kind} entry={entry} />
            ))}
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
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(entry.name)
  const [level, setLevel] = useState<Level | null>(entry.level)
  const [confirming, setConfirming] = useState(false)

  const rename = useApiMutation({
    run: () =>
      api.post<Entry[]>(`/centers/${centerId}/${kind}/${entry.id}`, {
        name: name.trim(),
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
            setEditing(false)
          }}
        >
          <X size={16} aria-hidden />
        </Button>
      </li>
    )
  }

  return (
    <li className="flex items-center justify-between gap-4 border-b border-[var(--color-hairline)] px-5 py-3.5 transition-colors last:border-b-0 hover:bg-[var(--color-sunken)]">
      <span className="min-w-0">
        <span className="flex items-center gap-2">
          <span className="truncate text-[14px] font-medium">{entry.name}</span>
          {entry.level && <Badge tone="accent">{t(`streams.level${entry.level}`)}</Badge>}
        </span>
        <span className="mt-0.5 block text-[11.5px] text-[var(--color-quiet)]">
          {used
            ? [
                entry.usedByTeachers > 0 &&
                  `${entry.usedByTeachers} ${t(firstLabel, { count: entry.usedByTeachers })}`,
                entry.usedByExams > 0 &&
                  `${entry.usedByExams} ${t('catalogue.usedByExams', { count: entry.usedByExams })}`,
              ]
                .filter(Boolean)
                .join(' · ')
            : t('catalogue.unused')}
        </span>
      </span>

      {/* nothing depends on it yet — for a subject, that also means no
          specialist for its permanence */}
      {!used && <Badge tone="warn">{t('catalogue.unusedShort')}</Badge>}

      {confirming ? (
        <span className="flex shrink-0 items-center gap-2">
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
        <span className="flex shrink-0 items-center gap-1">
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
