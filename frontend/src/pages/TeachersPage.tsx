import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { FileUp, Pencil, Plus, Trash2, Upload } from 'lucide-react'
import {
  Button,
  Input,
  Label,
  Modal,
  TextField,
} from '@heroui/react'
import { api } from '../lib/api'
import { useWorkspace } from '../context/Workspace'
import { useApiMutation } from '../lib/mutation'
import { Page, Panel, Failed, Loading, Empty } from '../components/Page'

type Teacher = {
  matricule: string
  name: string
  subject: string
  establishment: string | null
  gender: string | null
  /** How the record read before an import would change it. */
  was: string | null
}

type RowError = { line: number; reason: string; detail: string | null; content: string }

type Preview = {
  centerId: number
  centerName: string
  created: Teacher[]
  updated: Teacher[]
  unchanged: Teacher[]
  errors: RowError[]
}

/** An empty field is sent as null, so the server clears it rather than storing "". */
function trimmed(value: string): string | null {
  const cleaned = value.trim()
  return cleaned === '' ? null : cleaned
}

/**
 * Adding or correcting one teacher.
 *
 * <p>The matricule is shown but locked when editing: it is the identity every
 * past session was recorded against, so the server refuses to move it and the
 * form should not pretend otherwise.
 */
function TeacherForm({
  centerId,
  existing,
  open,
  onClose,
}: {
  centerId: number
  existing?: Teacher
  open: boolean
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [matricule, setMatricule] = useState('')
  const [name, setName] = useState('')
  const [subject, setSubject] = useState('')
  const [establishment, setEstablishment] = useState('')

  useEffect(() => {
    if (!open) return
    setMatricule(existing?.matricule ?? '')
    setName(existing?.name ?? '')
    setSubject(existing?.subject ?? '')
    setEstablishment(existing?.establishment ?? '')
  }, [open, existing])

  const save = useApiMutation({
    run: () => {
      const body = {
        matricule: matricule.trim(),
        name: name.trim(),
        subject: subject.trim(),
        establishment: trimmed(establishment),
        gender: existing?.gender ?? null,
      }
      return existing
        ? api.post<Teacher[]>(`/centers/${centerId}/teachers/${existing.matricule}`, body)
        : api.post<Teacher[]>(`/centers/${centerId}/teachers`, body)
    },
    invalidate: ['teachers', centerId],
    onDone: () => {
      onClose()
      return existing ? t('teachers.saved') : t('teachers.added')
    },
  })

  return (
    <Modal isOpen={open} onOpenChange={(next) => !next && onClose()}>
      <Modal.Backdrop>
        <Modal.Container>
          <Modal.Dialog>
            <Modal.Header>
              <Modal.Heading>
                {existing ? t('teachers.edit') : t('teachers.add')}
              </Modal.Heading>
            </Modal.Header>

            <Modal.Body>
              <form
                id="teacher-form"
                className="space-y-4"
                onSubmit={(event) => {
                  event.preventDefault()
                  save.mutate(undefined)
                }}
              >
                <TextField
                  value={matricule}
                  onChange={setMatricule}
                  isDisabled={existing !== undefined}
                  fullWidth
                >
                  <Label>{t('teachers.matricule')}</Label>
                  <Input placeholder="D100001" />
                </TextField>

                <TextField value={name} onChange={setName} fullWidth autoFocus>
                  <Label>{t('teachers.name')}</Label>
                  <Input />
                </TextField>

                <TextField value={subject} onChange={setSubject} fullWidth>
                  <Label>{t('teachers.subject')}</Label>
                  <Input />
                </TextField>

                <TextField value={establishment} onChange={setEstablishment} fullWidth>
                  <Label>{t('teachers.establishment')}</Label>
                  <Input />
                </TextField>
              </form>
            </Modal.Body>

            <Modal.Footer>
              <Button variant="ghost" onPress={onClose}>
                {t('app.cancel')}
              </Button>
              <Button type="submit" form="teacher-form" isPending={save.isPending}>
                {t('app.save')}
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </Modal>
  )
}

/** One group of the import report: what will be added, changed, or skipped. */
function Outcome({
  label,
  teachers,
  tone,
}: {
  label: string
  teachers: Teacher[]
  tone: 'add' | 'change' | 'same'
}) {
  if (teachers.length === 0) return null
  const dot =
    tone === 'add'
      ? 'bg-[var(--color-brand)]'
      : tone === 'change'
        ? 'bg-amber-500'
        : 'bg-[var(--color-hairline)]'

  return (
    <div>
      <div className="mb-1.5 flex items-center gap-2">
        <span className={`h-1.5 w-1.5 rounded-full ${dot}`} aria-hidden />
        <span className="text-[11px] font-semibold uppercase tracking-[0.07em] text-[var(--color-quiet)]">
          {label}
        </span>
        <span className="numeric text-[11px] text-[var(--color-quiet)]/70">
          {teachers.length}
        </span>
      </div>
      <ul className="max-h-48 overflow-y-auto rounded-md border border-[var(--color-hairline)]">
        {teachers.map((teacher) => (
          <li
            key={teacher.matricule}
            className="flex items-baseline gap-3 border-b border-[var(--color-hairline)] px-3 py-2 last:border-b-0"
          >
            <span className="numeric w-20 shrink-0 text-[11px] text-[var(--color-quiet)]">
              {teacher.matricule}
            </span>
            <span className="min-w-0 flex-1">
              <span className="block truncate text-[13px]">{teacher.name}</span>
              {/* what it is replacing, so a change is legible as a change */}
              {teacher.was && (
                <span className="mt-0.5 block truncate text-[11px] text-[var(--color-quiet)] line-through">
                  {teacher.was}
                </span>
              )}
            </span>
            <span className="shrink-0 text-[11px] text-[var(--color-quiet)]">
              {teacher.subject}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

/**
 * The two-step import: read the file and say what it would do, then do it.
 *
 * <p>Nothing is written by the first step, which is the point — a bad row is
 * something to look at before it becomes a bad record.
 */
function ImportTeachers({ centerId }: { centerId: number }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [csv, setCsv] = useState<string | null>(null)
  const [fileName, setFileName] = useState('')
  const [preview, setPreview] = useState<Preview | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)

  const close = () => {
    setOpen(false)
    setCsv(null)
    setFileName('')
    setPreview(null)
  }

  const read = useApiMutation({
    run: (text: string) => api.post<Preview>(`/centers/${centerId}/teachers/preview`, { csv: text }),
    onDone: (result) => {
      setPreview(result)
    },
  })

  const apply = useApiMutation({
    run: () => api.post<Preview>(`/centers/${centerId}/teachers/apply`, { csv }),
    invalidate: ['teachers', centerId],
    onDone: (result) => {
      close()
      return t('teachers.imported', {
        count: result.created.length + result.updated.length,
      })
    },
  })

  const choose = async (file: File | undefined) => {
    if (!file) return
    const text = await file.text()
    setCsv(text)
    setFileName(file.name)
    setPreview(null)
    read.mutate(text)
  }

  const nothingToDo =
    preview !== null && preview.created.length === 0 && preview.updated.length === 0

  return (
    <>
      <Button size="sm" variant="secondary" onPress={() => setOpen(true)}>
        <Upload size={15} aria-hidden />
        {t('teachers.import')}
      </Button>

      <Modal isOpen={open} onOpenChange={(next) => !next && close()}>
        <Modal.Backdrop>
          <Modal.Container>
            <Modal.Dialog>
              <Modal.Header>
                <Modal.Heading>{t('teachers.import')}</Modal.Heading>
              </Modal.Header>

              <Modal.Body>
                <div className="space-y-4">
                  <input
                    ref={fileInput}
                    type="file"
                    accept=".csv,text/csv,text/plain"
                    className="sr-only"
                    onChange={(event) => void choose(event.target.files?.[0])}
                  />

                  <button
                    type="button"
                    onClick={() => fileInput.current?.click()}
                    className="flex w-full flex-col items-center gap-2 rounded-md border border-dashed border-[var(--color-hairline)] px-6 py-8 transition-colors hover:border-[var(--color-brand)] hover:bg-[var(--color-ground)]"
                  >
                    <FileUp size={20} className="text-[var(--color-quiet)]" aria-hidden />
                    <span className="text-[13px] font-medium">
                      {fileName || t('teachers.chooseFile')}
                    </span>
                    <span className="text-[11px] text-[var(--color-quiet)]">
                      {t('teachers.columns')}
                    </span>
                  </button>

                  {read.isPending && <Loading rows={2} />}

                  {preview && (
                    <div className="space-y-4">
                      <Outcome label={t('teachers.willAdd')} teachers={preview.created} tone="add" />
                      <Outcome
                        label={t('teachers.willChange')}
                        teachers={preview.updated}
                        tone="change"
                      />
                      <Outcome
                        label={t('teachers.alreadyCorrect')}
                        teachers={preview.unchanged}
                        tone="same"
                      />

                      {preview.errors.length > 0 && (
                        <div>
                          <div className="mb-1.5 flex items-center gap-2">
                            <span
                              className="h-1.5 w-1.5 rounded-full bg-[var(--color-alarm)]"
                              aria-hidden
                            />
                            <span className="text-[11px] font-semibold uppercase tracking-[0.07em] text-[var(--color-alarm)]">
                              {t('teachers.unreadable')}
                            </span>
                            <span className="numeric text-[11px] text-[var(--color-quiet)]/70">
                              {preview.errors.length}
                            </span>
                          </div>
                          <ul className="max-h-40 overflow-y-auto rounded-md border border-[var(--color-alarm)]/25">
                            {preview.errors.map((row, index) => (
                              <li
                                key={`${row.line}-${index}`}
                                className="flex items-baseline gap-3 border-b border-[var(--color-alarm)]/15 px-3 py-2 text-[12px] last:border-b-0"
                              >
                                <span className="numeric shrink-0 text-[var(--color-quiet)]">
                                  {t('teachers.line', { line: row.line })}
                                </span>
                                <span className="min-w-0 flex-1">
                                  {t(`teachers.rowError.${row.reason}`, {
                                    detail: row.detail ?? '',
                                    defaultValue: row.reason,
                                  })}
                                </span>
                              </li>
                            ))}
                          </ul>
                          <p className="mt-1.5 text-[11px] text-[var(--color-quiet)]">
                            {t('teachers.badRowsSkipped')}
                          </p>
                        </div>
                      )}

                      {nothingToDo && preview.errors.length === 0 && (
                        <p className="text-[13px] text-[var(--color-quiet)]">
                          {t('teachers.nothingToDo')}
                        </p>
                      )}
                    </div>
                  )}
                </div>
              </Modal.Body>

              <Modal.Footer>
                <Button variant="ghost" onPress={close}>
                  {t('app.cancel')}
                </Button>
                <Button
                  isDisabled={preview === null || nothingToDo}
                  isPending={apply.isPending}
                  onPress={() => apply.mutate(undefined)}
                >
                  {t('teachers.confirmImport')}
                </Button>
              </Modal.Footer>
            </Modal.Dialog>
          </Modal.Container>
        </Modal.Backdrop>
      </Modal>
    </>
  )
}

function TeacherRow({
  centerId,
  teacher,
  onEdit,
}: {
  centerId: number
  teacher: Teacher
  onEdit: () => void
}) {
  const { t } = useTranslation()
  const [confirming, setConfirming] = useState(false)

  const remove = useApiMutation({
    run: () => api.del<Teacher[]>(`/centers/${centerId}/teachers/${teacher.matricule}`),
    invalidate: ['teachers', centerId],
    onDone: () => {
      setConfirming(false)
      return t('teachers.removed')
    },
  })

  return (
    <tr className="border-b border-[var(--color-hairline)] last:border-b-0 hover:bg-[var(--color-ground)]">
      <td className="px-4 py-2.5">
        <span className="numeric text-[11px] font-medium text-[var(--color-quiet)]">
          {teacher.matricule}
        </span>
      </td>
      <td className="px-4 py-2.5 text-[13px] font-medium">{teacher.name}</td>
      <td className="px-4 py-2.5 text-[13px]">{teacher.subject}</td>
      <td className="px-4 py-2.5 text-[12px] text-[var(--color-quiet)]">
        {teacher.establishment ?? '—'}
      </td>
      <td className="px-4 py-2.5 text-end">
        {confirming ? (
          <div className="flex items-center justify-end gap-2">
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
          </div>
        ) : (
          <div className="flex items-center justify-end gap-1">
            <Button size="sm" variant="ghost" isIconOnly aria-label={t('teachers.edit')} onPress={onEdit}>
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
          </div>
        )}
      </td>
    </tr>
  )
}

export function TeachersPage() {
  const { t } = useTranslation()
  const { centerId, center: current, hasCenter, isLoading } = useWorkspace()
  const [search, setSearch] = useState('')
  const [editing, setEditing] = useState<Teacher | undefined>()
  const [formOpen, setFormOpen] = useState(false)




  const teachers = useQuery({
    queryKey: ['teachers', centerId],
    queryFn: () => api.get<Teacher[]>(`/centers/${centerId}/teachers`),
    enabled: centerId !== null,
  })

  const shown = useMemo(() => {
    const needle = search.trim().toLowerCase()
    if (!needle || !teachers.data) return teachers.data ?? []
    return teachers.data.filter((teacher) =>
      [teacher.matricule, teacher.name, teacher.subject, teacher.establishment ?? '']
        .join(' ')
        .toLowerCase()
        .includes(needle),
    )
  }, [teachers.data, search])

  /** How many people teach each subject — the figure that decides whether a
      permanence can be staffed at all. */
  const subjects = useMemo(() => {
    const counts = new Map<string, number>()
    for (const teacher of teachers.data ?? []) {
      counts.set(teacher.subject, (counts.get(teacher.subject) ?? 0) + 1)
    }
    return [...counts.entries()].sort((a, b) => b[1] - a[1])
  }, [teachers.data])

  if (!isLoading && !hasCenter) {
    return (
      <Page title={t('teachers.title')}>
        <div className="rounded-md border border-[var(--color-hairline)] bg-white">
          <Empty>{t('teachers.noCenter')}</Empty>
        </div>
      </Page>
    )
  }


  return (
    <Page
      title={t('teachers.title')}
      subtitle={t('teachers.subtitle')}
      actions={
        centerId !== null && (
          <>
            <ImportTeachers centerId={centerId} />
            <Button
              size="sm"
              onPress={() => {
                setEditing(undefined)
                setFormOpen(true)
              }}
            >
              <Plus size={15} aria-hidden />
              {t('teachers.add')}
            </Button>
          </>
        )
      }
    >
      <div className="mb-5 flex flex-wrap items-end gap-3">
        {(teachers.data?.length ?? 0) > 0 && (
          <TextField value={search} onChange={setSearch} className="w-72">
            <Label>{t('teachers.search')}</Label>
            <Input placeholder={t('teachers.searchHint')} />
          </TextField>
        )}
      </div>

      <Panel
        title={t('teachers.pool')}
        count={teachers.data?.length}
        hint={current ? undefined : t('teachers.pickCenter')}
      >
        {teachers.isPending && centerId !== null && <Loading rows={5} />}
        {teachers.isError && (
          <div className="p-4">
            <Failed error={teachers.error as Error} onRetry={() => void teachers.refetch()} />
          </div>
        )}

        {teachers.isSuccess &&
          (teachers.data.length === 0 ? (
            <Empty action={centerId !== null && <ImportTeachers centerId={centerId} />}>
              {t('teachers.empty')}
            </Empty>
          ) : shown.length === 0 ? (
            <Empty>{t('teachers.noMatch', { search })}</Empty>
          ) : (
            <table className="w-full">
              <thead>
                <tr className="border-b border-[var(--color-hairline)]">
                  {['matricule', 'name', 'subject', 'establishment'].map((column) => (
                    <th
                      key={column}
                      className="px-4 py-2 text-start text-[11px] font-medium uppercase tracking-wide text-[var(--color-quiet)]"
                    >
                      {t(`teachers.${column}`)}
                    </th>
                  ))}
                  <th className="w-28 px-4 py-2" />
                </tr>
              </thead>
              <tbody>
                {shown.map((teacher) => (
                  <TeacherRow
                    key={teacher.matricule}
                    centerId={centerId!}
                    teacher={teacher}
                    onEdit={() => {
                      setEditing(teacher)
                      setFormOpen(true)
                    }}
                  />
                ))}
              </tbody>
            </table>
          ))}
      </Panel>

      {subjects.length > 0 && (
        <Panel title={t('teachers.subjects')} count={subjects.length}>
          <ul className="divide-y divide-[var(--color-hairline)]">
            {subjects.map(([subject, count]) => (
              <li key={subject} className="flex items-center justify-between px-4 py-2.5">
                <span className="text-[13px]">{subject}</span>
                <span className="numeric text-[13px] font-medium">{count}</span>
              </li>
            ))}
          </ul>
        </Panel>
      )}

      {centerId !== null && (
        <TeacherForm
          centerId={centerId}
          existing={editing}
          open={formOpen}
          onClose={() => setFormOpen(false)}
        />
      )}
    </Page>
  )
}
