import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { CalendarOff, FileUp, Layers, Pencil, Plus, Trash2, Upload, Users } from 'lucide-react'
import { api } from '../lib/api'
import { useWorkspace } from '../context/Workspace'
import { useApiMutation } from '../lib/mutation'
import { Page } from '../components/Page'
import { Absences } from '../components/Absences'
import {
  Badge,
  Button,
  Card,
  CardHead,
  CardRule,
  Dialog,
  Empty,
  Failed,
  SearchField,
  SegmentedTabs,
  Skeleton,
  Table,
  Td,
  TextField,
  Th,
  Tr,
} from '../ui'

type Teacher = {
  matricule: string
  name: string
  subject: string
  establishment: string | null
  gender: string | null
  /** How the record read before an import would change it. */
  was: string | null
  /** Days this teacher is known to be away, and so cannot be given a duty. */
  absences: number
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
    <Dialog
      isOpen={open}
      onClose={onClose}
      title={existing ? t('teachers.edit') : t('teachers.add')}
      subtitle={existing ? existing.matricule : undefined}
      footer={
        <>
          <Button variant="secondary" onPress={onClose}>
            {t('app.cancel')}
          </Button>
          <Button type="submit" form="teacher-form" isPending={save.isPending}>
            {t('app.save')}
          </Button>
        </>
      }
    >
      <form
        id="teacher-form"
        className="space-y-4 pb-4"
        onSubmit={(event) => {
          event.preventDefault()
          save.mutate(undefined)
        }}
      >
        <TextField
          label={t('teachers.matricule')}
          value={matricule}
          onChange={setMatricule}
          isDisabled={existing !== undefined}
          placeholder="D100001"
          inputClassName="numeric"
          hint={existing ? t('teachers.matriculeFixed') : undefined}
        />
        <TextField label={t('teachers.name')} value={name} onChange={setName} autoFocus />
        <TextField label={t('teachers.subject')} value={subject} onChange={setSubject} />
        <TextField
          label={t('teachers.establishment')}
          value={establishment}
          onChange={setEstablishment}
        />
      </form>
    </Dialog>
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
  tone: 'good' | 'warn' | 'plain'
}) {
  if (teachers.length === 0) return null

  return (
    <div>
      <div className="mb-2 flex items-center gap-2">
        <Badge tone={tone}>{label}</Badge>
        <span className="numeric text-[11.5px] text-[var(--color-faint)]">{teachers.length}</span>
      </div>
      <ul className="max-h-48 overflow-y-auto rounded-[var(--radius-field)] bg-[var(--color-sunken)]">
        {teachers.map((teacher) => (
          <li
            key={teacher.matricule}
            className="flex items-baseline gap-3 border-b border-[var(--color-hairline)] px-3.5 py-2.5 last:border-b-0"
          >
            <span className="numeric w-20 shrink-0 text-[11.5px] text-[var(--color-quiet)]">
              {teacher.matricule}
            </span>
            <span className="min-w-0 flex-1">
              <span className="block truncate text-[13px]">{teacher.name}</span>
              {/* what it is replacing, so a change is legible as a change */}
              {teacher.was && (
                <span className="mt-0.5 block truncate text-[11.5px] text-[var(--color-faint)] line-through">
                  {teacher.was}
                </span>
              )}
            </span>
            <span className="shrink-0 text-[11.5px] text-[var(--color-quiet)]">
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
function ImportTeachers({
  centerId,
  variant = 'secondary',
}: {
  centerId: number
  variant?: 'primary' | 'secondary'
}) {
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
    run: (text: string) =>
      api.post<Preview>(`/centers/${centerId}/teachers/preview`, { csv: text }),
    onDone: (result) => {
      setPreview(result)
    },
  })

  const apply = useApiMutation({
    run: () => api.post<Preview>(`/centers/${centerId}/teachers/apply`, { csv }),
    invalidate: ['teachers', centerId],
    onDone: (result) => {
      close()
      return t('teachers.imported', { count: result.created.length + result.updated.length })
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
      <Button variant={variant} onPress={() => setOpen(true)}>
        <Upload size={16} aria-hidden />
        {t('teachers.import')}
      </Button>

      <Dialog
        isOpen={open}
        onClose={close}
        width="lg"
        title={t('teachers.import')}
        subtitle={t('teachers.columns')}
        footer={
          <>
            <Button variant="secondary" onPress={close}>
              {t('app.cancel')}
            </Button>
            <Button
              isDisabled={preview === null || nothingToDo}
              isPending={apply.isPending}
              onPress={() => apply.mutate(undefined)}
            >
              {t('teachers.confirmImport')}
            </Button>
          </>
        }
      >
        <div className="space-y-5 pb-4">
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
            className="flex w-full flex-col items-center gap-2 rounded-[var(--radius-card)] bg-[var(--color-sunken)] px-6 py-9
              ring-1 ring-dashed ring-[var(--color-hairline)] transition-colors
              hover:bg-[var(--color-accent-tint)] hover:ring-[var(--color-accent)]/40"
          >
            <FileUp size={22} className="text-[var(--color-accent)]" aria-hidden />
            <span className="text-[13.5px] font-medium">
              {fileName || t('teachers.chooseFile')}
            </span>
          </button>

          {read.isPending && <Skeleton rows={3} />}

          {preview && (
            <div className="space-y-5">
              <Outcome label={t('teachers.willAdd')} teachers={preview.created} tone="good" />
              <Outcome label={t('teachers.willChange')} teachers={preview.updated} tone="warn" />
              <Outcome
                label={t('teachers.alreadyCorrect')}
                teachers={preview.unchanged}
                tone="plain"
              />

              {preview.errors.length > 0 && (
                <div>
                  <div className="mb-2 flex items-center gap-2">
                    <Badge tone="alarm">{t('teachers.unreadable')}</Badge>
                    <span className="numeric text-[11.5px] text-[var(--color-faint)]">
                      {preview.errors.length}
                    </span>
                  </div>
                  <ul className="max-h-40 overflow-y-auto rounded-[var(--radius-field)] bg-[var(--color-alarm-tint)]">
                    {preview.errors.map((row, index) => (
                      <li
                        key={`${row.line}-${index}`}
                        className="flex items-baseline gap-3 px-3.5 py-2.5 text-[12.5px]"
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
                  <p className="mt-2 text-[11.5px] text-[var(--color-faint)]">
                    {t('teachers.badRowsSkipped')}
                  </p>
                </div>
              )}

              {nothingToDo && preview.errors.length === 0 && (
                <p className="text-[13px] text-[var(--color-quiet)]">{t('teachers.nothingToDo')}</p>
              )}
            </div>
          )}
        </div>
      </Dialog>
    </>
  )
}

function TeacherRow({
  centerId,
  teacher,
  onEdit,
  onAbsences,
}: {
  centerId: number
  teacher: Teacher
  onEdit: () => void
  onAbsences: () => void
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
    <Tr>
      <Td className="numeric text-[12.5px] font-medium text-[var(--color-quiet)]">
        {teacher.matricule}
      </Td>
      <Td className="font-medium">{teacher.name}</Td>
      <Td>{teacher.subject}</Td>
      <Td className="text-[12.5px] text-[var(--color-quiet)]">{teacher.establishment ?? '—'}</Td>
      <Td>
        {/* somebody away cannot be given a duty that day, and the solver already
            knows it — the pool has to say so too */}
        {teacher.absences > 0 ? (
          <Badge tone="warn" icon={<CalendarOff size={12} aria-hidden />}>
            {t('absences.count', { count: teacher.absences })}
          </Badge>
        ) : (
          <span className="text-[12.5px] text-[var(--color-faint)]">—</span>
        )}
      </Td>
      <Td className="no-print text-end">
        {/* the confirmation stays in the row: what you are about to lose is
            still on the screen while you decide */}
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
              aria-label={t('absences.title')}
              onPress={onAbsences}
            >
              <CalendarOff size={15} aria-hidden />
            </Button>
            <Button
              size="sm"
              variant="quiet"
              isIcon
              aria-label={t('teachers.edit')}
              onPress={onEdit}
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
          </div>
        )}
      </Td>
    </Tr>
  )
}

/**
 * The pool the surveillance is shared out of.
 *
 * <p>Two views of the same people: the list, and the count per subject. The
 * second is not decoration — one specialist per subject is on permanence for a
 * séance, so a subject with a single teacher in it is a session waiting to go
 * wrong, and that is worth seeing without counting rows by hand.
 */
export function TeachersPage() {
  const { t } = useTranslation()
  const { centerId, hasCenter, isLoading } = useWorkspace()
  const [view, setView] = useState('pool')
  const [search, setSearch] = useState('')
  const [editing, setEditing] = useState<Teacher | undefined>()
  const [formOpen, setFormOpen] = useState(false)
  const [absencesFor, setAbsencesFor] = useState<Teacher | null>(null)

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

  const thin = subjects.filter(([, count]) => count < 2).length

  if (!isLoading && !hasCenter) {
    return (
      <Page title={t('teachers.title')}>
        <Card>
          <Empty icon={<Users size={22} aria-hidden />}>{t('teachers.noCenter')}</Empty>
        </Card>
      </Page>
    )
  }

  return (
    <Page
      title={t('teachers.title')}
      subtitle={t('teachers.subtitle')}
      tabs={
        <SegmentedTabs
          value={view}
          onChange={setView}
          tabs={[
            {
              id: 'pool',
              label: t('teachers.pool'),
              icon: <Users size={15} aria-hidden />,
              count: teachers.data?.length,
            },
            {
              id: 'subjects',
              label: t('teachers.subjects'),
              icon: <Layers size={15} aria-hidden />,
              count: subjects.length,
              flag: thin > 0,
            },
          ]}
        />
      }
      actions={
        centerId !== null && (
          <>
            <ImportTeachers centerId={centerId} />
            <Button
              onPress={() => {
                setEditing(undefined)
                setFormOpen(true)
              }}
            >
              <Plus size={16} aria-hidden />
              {t('teachers.add')}
            </Button>
          </>
        )
      }
    >
      {view === 'pool' ? (
        <Card>
          <CardHead
            title={t('teachers.pool')}
            actions={
              (teachers.data?.length ?? 0) > 0 && (
                <SearchField
                  className="w-64"
                  label={t('teachers.search')}
                  placeholder={t('teachers.searchHint')}
                  value={search}
                  onChange={setSearch}
                />
              )
            }
          />
          <CardRule />

          {teachers.isPending && centerId !== null && <Skeleton rows={6} />}
          {teachers.isError && (
            <Failed error={teachers.error as Error} onRetry={() => void teachers.refetch()} />
          )}

          {teachers.isSuccess &&
            (teachers.data.length === 0 ? (
              <Empty
                icon={<Users size={22} aria-hidden />}
                title={t('teachers.title')}
                action={centerId !== null && <ImportTeachers centerId={centerId} variant="primary" />}
              >
                {t('teachers.empty')}
              </Empty>
            ) : shown.length === 0 ? (
              <Empty>{t('teachers.noMatch', { search })}</Empty>
            ) : (
              <Table>
                <thead>
                  <tr>
                    <Th width="140px">{t('teachers.matricule')}</Th>
                    <Th>{t('teachers.name')}</Th>
                    <Th width="200px">{t('teachers.subject')}</Th>
                    <Th width="180px">{t('teachers.establishment')}</Th>
                    <Th width="150px">{t('absences.title')}</Th>
                    <Th width="150px" className="no-print" />
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
                      onAbsences={() => setAbsencesFor(teacher)}
                    />
                  ))}
                </tbody>
              </Table>
            ))}
        </Card>
      ) : (
        <Card>
          <CardHead title={t('teachers.subjects')} count={subjects.length} />
          <CardRule />
          {subjects.length === 0 ? (
            <Empty icon={<Layers size={22} aria-hidden />}>{t('teachers.empty')}</Empty>
          ) : (
            <ul>
              {subjects.map(([subject, count]) => (
                <li
                  key={subject}
                  className="flex items-center gap-4 border-b border-[var(--color-hairline)] px-5 py-3.5 last:border-b-0"
                >
                  <span className="min-w-0 flex-1 text-[14px] font-medium">{subject}</span>
                  {/* one specialist is on permanence per subject per séance:
                      a subject held up by a single teacher is worth flagging */}
                  {count < 2 && <Badge tone="warn">{t('teachers.thinSubject')}</Badge>}
                  <span className="numeric w-10 text-end text-[15px] font-semibold">{count}</span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}

      {centerId !== null && (
        <Absences
          centerId={centerId}
          matricule={absencesFor?.matricule ?? null}
          name={absencesFor?.name ?? ''}
          open={absencesFor !== null}
          onClose={() => setAbsencesFor(null)}
        />
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
