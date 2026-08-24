import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Check, Pencil, Plus, Trash2, X } from 'lucide-react'
import { Button, Input, TextField } from '@heroui/react'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { Panel, Failed, Loading, Empty } from './Page'

export type Entry = {
  id: number
  name: string
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

  const entries = useQuery({
    queryKey: [kind, centerId],
    queryFn: () => api.get<Entry[]>(`/centers/${centerId}/${kind}`),
  })

  const add = useApiMutation({
    run: () => api.post<Entry[]>(`/centers/${centerId}/${kind}`, { name: adding.trim() }),
    invalidate: [kind, centerId],
    onDone: () => {
      setAdding('')
    },
  })

  return (
    <Panel
      title={t(`catalogue.${kind}.title`)}
      count={entries.data?.length}
      hint={t(`catalogue.${kind}.hint`)}
      footer={
        <form
          className="flex items-end gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            if (adding.trim() !== '') add.mutate(undefined)
          }}
        >
          <TextField value={adding} onChange={setAdding} className="flex-1">
            <Input placeholder={t(`catalogue.${kind}.placeholder`)} />
          </TextField>
          <Button type="submit" size="sm" isPending={add.isPending}>
            <Plus size={15} aria-hidden />
            {t('app.add')}
          </Button>
        </form>
      }
    >
      {entries.isPending && <Loading rows={3} />}
      {entries.isError && (
        <div className="p-4">
          <Failed error={entries.error as Error} onRetry={() => void entries.refetch()} />
        </div>
      )}
      {entries.isSuccess &&
        (entries.data.length === 0 ? (
          <Empty>{t(`catalogue.${kind}.empty`)}</Empty>
        ) : (
          <ul className="divide-y divide-[var(--color-hairline)]">
            {entries.data.map((entry) => (
              <Row key={entry.id} centerId={centerId} kind={kind} entry={entry} />
            ))}
          </ul>
        ))}
    </Panel>
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
  const [confirming, setConfirming] = useState(false)

  const rename = useApiMutation({
    run: () =>
      api.post<Entry[]>(`/centers/${centerId}/${kind}/${entry.id}`, { name: name.trim() }),
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
      <li className="flex items-center gap-2 bg-[var(--color-ground)] px-4 py-2">
        <TextField value={name} onChange={setName} className="flex-1" autoFocus>
          <Input />
        </TextField>
        <Button size="sm" isPending={rename.isPending} onPress={() => rename.mutate(undefined)}>
          <Check size={14} aria-hidden />
          {t('app.save')}
        </Button>
        <Button
          size="sm"
          variant="ghost"
          isIconOnly
          aria-label={t('app.cancel')}
          onPress={() => {
            setName(entry.name)
            setEditing(false)
          }}
        >
          <X size={14} aria-hidden />
        </Button>
      </li>
    )
  }

  return (
    <li className="flex items-center justify-between gap-4 px-4 py-2.5 hover:bg-[var(--color-ground)]">
      <span className="min-w-0">
        <span className="block truncate text-[13px] font-medium">{entry.name}</span>
        <span className="mt-0.5 block text-[11px] text-[var(--color-quiet)]">
          {used
            ? [
                entry.usedByTeachers > 0 &&
                  `${entry.usedByTeachers} ${t(firstLabel, {
                    count: entry.usedByTeachers,
                  })}`,
                entry.usedByExams > 0 &&
                  `${entry.usedByExams} ${t('catalogue.usedByExams', {
                    count: entry.usedByExams,
                  })}`,
              ]
                .filter(Boolean)
                .join(' · ')
            : t('catalogue.unused')}
        </span>
      </span>

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
          <Button size="sm" variant="ghost" onPress={() => setConfirming(false)}>
            {t('app.cancel')}
          </Button>
        </span>
      ) : (
        <span className="flex shrink-0 items-center gap-1">
          <Button
            size="sm"
            variant="ghost"
            isIconOnly
            aria-label={t('app.rename')}
            onPress={() => setEditing(true)}
          >
            <Pencil
              size={14}
              className="text-[var(--color-quiet)] transition-colors hover:text-[var(--color-ink)]"
              aria-hidden
            />
          </Button>
          <Button
            size="sm"
            variant="ghost"
            isIconOnly
            aria-label={t('app.delete')}
            onPress={() => setConfirming(true)}
          >
            <Trash2
              size={14}
              className="text-[var(--color-quiet)] transition-colors hover:text-[var(--color-alarm)]"
              aria-hidden
            />
          </Button>
        </span>
      )}
    </li>
  )
}
