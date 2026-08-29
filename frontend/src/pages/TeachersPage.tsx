import { Fragment, useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import {
  CalendarOff,
  FileUp,
  Layers,
  Mars,
  Pencil,
  Plus,
  Trash2,
  TriangleAlert,
  Upload,
  Users,
  Venus,
} from 'lucide-react'
import { api } from '../lib/api'
import { useNames } from '../lib/names'
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
  ComboBox,
  Dialog,
  Empty,
  Failed,
  Notice,
  SearchField,
  SegmentedTabs,
  Select,
  Skeleton,
  Table,
  Td,
  TextField,
  Th,
  Tr,
} from '../ui'

type Teacher = {
  matricule: string
  /** The Arabic name — always present, and the one every list leads with. */
  name: string
  /** The same person in French, for documents written in it. Often absent. */
  nameFr: string | null
  subject: string
  establishment: string | null
  /** السلك — printed on the official list, read by no rule. */
  corps: string | null
  gender: string | null
  /** How the record read before an import would change it. */
  was: string | null
  /** Days this teacher is known to be away, and so cannot be given a duty. */
  absences: number
}

type CatalogueSubject = {
  id: number
  name: string
  nameFr: string | null
  usedByTeachers: number
}

/**
 * السلك, as the ministry writes it.
 *
 * <p>Offered rather than imposed: the value is printed and never compared, and
 * a centre borrowing staff can meet a corps that is not one of these three. The
 * French gloss is a hint beside each, so the stored string stays the Arabic one
 * whichever language the screen is in — two spellings of one corps would be the
 * mess the subject list was.
 */
const CORPS = ['ثانوي تأهيلي', 'ثانوي إعدادي', 'ابتدائي'] as const

type RowError = { line: number; reason: string; detail: string | null; content: string }

type Preview = {
  centerId: number
  centerName: string
  created: Teacher[]
  updated: Teacher[]
  unchanged: Teacher[]
  errors: RowError[]
}

/**
 * What a row says about a teacher's gender.
 *
 * <p>Shown because the solver uses it — it prefers to put a man and a woman in
 * a room together where it can — and because a field nobody can see is a field
 * whose mistakes nobody finds. Quiet text with its sign, not a coloured badge:
 * every row carries one, and a mark on every row is not a mark.
 */
function GenderMark({ gender }: { gender: string | null }) {
  const { t } = useTranslation()

  if (gender !== 'MALE' && gender !== 'FEMALE') {
    return <span className="text-[var(--color-faint)]">—</span>
  }

  const Sign = gender === 'MALE' ? Mars : Venus
  return (
    <span className="inline-flex items-center gap-1.5 text-[12.5px] text-[var(--color-quiet)]">
      <Sign size={13} aria-hidden />
      {t(`teachers.gender${gender}`)}
    </span>
  )
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
 *
 * <p>The subject is offered from the centre's own list rather than typed from
 * memory. It is not tidiness: a teacher is barred from the paper of his own
 * subject by matching the two names exactly, so « Maths » entered where the
 * catalogue says « Mathématiques » makes a maths teacher eligible to invigilate
 * maths, and nothing anywhere says so. Free text still passes, because a centre
 * may have somebody whose subject it does not examine — but it is now a choice
 * made against a warning instead of a spelling nobody checked.
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
  const [nameFr, setNameFr] = useState('')
  const [subject, setSubject] = useState('')
  const [establishment, setEstablishment] = useState('')
  const [corps, setCorps] = useState('')
  const [gender, setGender] = useState('')

  const subjects = useQuery({
    queryKey: ['subjects', centerId],
    queryFn: () => api.get<CatalogueSubject[]>(`/centers/${centerId}/subjects`),
    enabled: open,
  })

  const listed = subjects.data ?? []
  const offList =
    subject.trim() !== '' &&
    listed.length > 0 &&
    !listed.some((one) => one.name === subject.trim())

  useEffect(() => {
    if (!open) return
    setMatricule(existing?.matricule ?? '')
    setName(existing?.name ?? '')
    setNameFr(existing?.nameFr ?? '')
    setSubject(existing?.subject ?? '')
    setEstablishment(existing?.establishment ?? '')
    setCorps(existing?.corps ?? '')
    setGender(existing?.gender ?? '')
  }, [open, existing])

  const save = useApiMutation({
    run: () => {
      // every field of the record is stated, because the server writes the
      // whole of it: a field left out here is a field cleared over there
      const body = {
        matricule: matricule.trim(),
        name: name.trim(),
        nameFr: trimmed(nameFr),
        subject: subject.trim(),
        establishment: trimmed(establishment),
        corps: trimmed(corps),
        gender: gender === '' ? null : gender,
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
        {/* the ministry's list is Arabic, so that is the name the record is
            built on; the French one is a label for French paperwork and stays
            optional */}
        <TextField label={t('teachers.name')} value={name} onChange={setName} autoFocus />
        <TextField
          label={t('teachers.nameFr')}
          value={nameFr}
          onChange={setNameFr}
          hint={t('teachers.nameFrHint')}
        />

        <div>
          <ComboBox
            label={t('teachers.subject')}
            value={subject}
            onChange={setSubject}
            placeholder={t('teachers.subjectPick')}
            suggestions={listed.map((one) => ({
              id: one.name,
              label: one.name,
              hint: one.usedByTeachers,
            }))}
          />
          {offList && (
            <div className="mt-2">
              <Notice tone="warn" icon={<TriangleAlert size={16} aria-hidden />}>
                {t('teachers.subjectOff')}
              </Notice>
            </div>
          )}
        </div>

        {/* which school he came from, and at what level: a centre short of
            surveillants borrows, and the official list has to say both */}
        <TextField
          label={t('teachers.establishment')}
          value={establishment}
          onChange={setEstablishment}
        />

        <div className="grid grid-cols-2 gap-3">
          <ComboBox
            label={t('teachers.corps')}
            value={corps}
            onChange={setCorps}
            placeholder={t('teachers.corpsPick')}
            suggestions={CORPS.map((one) => ({
              id: one,
              label: one,
              hint: t(`teachers.corpsGloss.${one}`, { defaultValue: '' }) || undefined,
            }))}
          />
          {/* the solver prefers to pair a man and a woman in a room where it
              can, so this is a fact it uses — and the one field here that
              makes a settled répartition worth running again */}
          <Select
            label={t('teachers.gender')}
            value={gender}
            onChange={(key) => setGender(String(key))}
            choices={[
              { id: '', label: t('teachers.genderUnset') },
              { id: 'MALE', label: t('teachers.genderMALE') },
              { id: 'FEMALE', label: t('teachers.genderFEMALE') },
            ]}
          />
        </div>
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
  // `label` is taken by this component's own prop, so the helper is kept whole
  const names = useNames()

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
              <bdi className="block truncate text-[13px]">{names.label(teacher)}</bdi>
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
  known,
  onEdit,
  onAbsences,
}: {
  centerId: number
  teacher: Teacher
  /** The catalogue's names; empty until it has been read. */
  known: Set<string>
  onEdit: () => void
  onAbsences: () => void
}) {
  const { t } = useTranslation()
  const { label, second } = useNames()
  const [confirming, setConfirming] = useState(false)
  const navigate = useNavigate()

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
      <Td className="font-medium whitespace-nowrap">
        {/* a block of its own: the name is its own bidi paragraph, so an Arabic
            one still reads right to left while both lines start at the same
            edge of the cell */}
        <span className="block">{label(teacher)}</span>
        {/* the name he is filed under elsewhere, so a French list can still be
            matched against the ministry's Arabic one */}
        {second(teacher) && (
          <span className="mt-0.5 block text-[11.5px] font-normal text-[var(--color-quiet)]">
            {second(teacher)}
          </span>
        )}
      </Td>
      <Td>
        {/* a name the catalogue does not hold: he is nobody's specialist, and
            he is not barred from his own paper either */}
        {known.size > 0 && !known.has(teacher.subject) ? (
          <span className="flex flex-wrap items-center gap-1.5">
            <span>{teacher.subject}</span>
            <Badge tone="warn" icon={<TriangleAlert size={11} aria-hidden />}>
              {t('teachers.subjectOffShort')}
            </Badge>
          </span>
        ) : (
          teacher.subject
        )}
      </Td>
      {/* which school, and at what level: the two halves of where he came
          from, kept in one cell because neither reads alone */}
      <Td className="text-[12.5px] text-[var(--color-quiet)]">
        <span className="block">{teacher.establishment ?? '—'}</span>
        {teacher.corps && (
          <span className="mt-0.5 block text-[11.5px] text-[var(--color-faint)]">
            {teacher.corps}
          </span>
        )}
      </Td>
      <Td>
        <GenderMark gender={teacher.gender} />
      </Td>
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
          <div className="flex flex-wrap items-center justify-end gap-2">
            {/*
              Supprimer is not the same act as leaving, and offering it where
              the other is meant is how a year of history goes. This one is for
              a row typed by mistake; somebody who has actually left comes out
              of next year's pool and keeps everything he did.
            */}
            <span className="text-[11.5px] text-[var(--color-quiet)]">
              {t('teachers.deleteOrLeft')}
            </span>
            <Button size="sm" variant="secondary" onPress={() => void navigate('/years')}>
              {t('years.left')}
            </Button>
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

/** What a run of the pool can be cut by. Every one of them is a fact printed
    on the row already, so a heading never says something the rows do not. */
type Grouping = 'none' | 'subject' | 'establishment' | 'corps' | 'gender'

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
  const [grouping, setGrouping] = useState<Grouping>('none')
  const [editing, setEditing] = useState<Teacher | undefined>()
  const [formOpen, setFormOpen] = useState(false)
  const [absencesFor, setAbsencesFor] = useState<Teacher | null>(null)

  const teachers = useQuery({
    queryKey: ['teachers', centerId],
    queryFn: () => api.get<Teacher[]>(`/centers/${centerId}/teachers`),
    enabled: centerId !== null,
  })

  const catalogue = useQuery({
    queryKey: ['subjects', centerId],
    queryFn: () => api.get<CatalogueSubject[]>(`/centers/${centerId}/subjects`),
    enabled: centerId !== null,
  })

  /**
   * The names the catalogue actually holds. A subject typed a second way is not
   * a second subject — it is a teacher the solver will never recognise as the
   * specialist of anything, so the rows carrying one are marked rather than
   * left to be found the day a permanence goes unfilled.
   */
  const known = useMemo(
    () => new Set((catalogue.data ?? []).map((one) => one.name)),
    [catalogue.data],
  )
  const strays = useMemo(
    () =>
      // an empty catalogue means the subjects were never entered, not that
      // every teacher is mistyped
      known.size === 0 ? [] : (teachers.data ?? []).filter((one) => !known.has(one.subject)),
    [teachers.data, known],
  )

  const shown = useMemo(() => {
    const needle = search.trim().toLowerCase()
    if (!needle || !teachers.data) return teachers.data ?? []
    return teachers.data.filter((teacher) =>
      // both names are searched: he may be looking for the person on the
      // ministry's Arabic list or on his own French one
      [
        teacher.matricule,
        teacher.name,
        teacher.nameFr ?? '',
        teacher.subject,
        teacher.establishment ?? '',
        teacher.corps ?? '',
      ]
        .join(' ')
        .toLowerCase()
        .includes(needle),
    )
  }, [teachers.data, search])

  /**
   * The pool cut into runs.
   *
   * <p>The server hands the register back in matricule order, which answers
   * « où est D10002 » and nothing else. Grouped by subject it answers the
   * question a session actually asks — who could stand for الفيزياء والكيمياء —
   * and the same machinery answers it for the establishment somebody was
   * borrowed from, his corps, or his gender.
   *
   * <p>Largest run first, because the point of grouping is to see the shape;
   * whoever has nothing stated goes last, since an empty heading is not a
   * group but a gap in the list.
   */
  const groups = useMemo(() => {
    if (grouping === 'none') return [{ key: 'all', rows: shown }]

    const runs = new Map<string, Teacher[]>()
    for (const teacher of shown) {
      const raw =
        grouping === 'subject'
          ? teacher.subject
          : grouping === 'establishment'
            ? teacher.establishment
            : grouping === 'corps'
              ? teacher.corps
              : teacher.gender
      const key = raw && raw.trim() !== '' ? raw : ''
      runs.set(key, [...(runs.get(key) ?? []), teacher])
    }

    return [...runs.entries()]
      .sort(([a, ofA], [b, ofB]) => {
        if (a === '') return 1
        if (b === '') return -1
        return ofB.length - ofA.length || a.localeCompare(b)
      })
      .map(([key, rows]) => ({ key, rows }))
  }, [shown, grouping])

  /** Men, women, and the ones nobody stated — the figure the mixed-pair
      preference actually has to work with. */
  const split = useMemo(() => {
    const pool = teachers.data ?? []
    return {
      male: pool.filter((one) => one.gender === 'MALE').length,
      female: pool.filter((one) => one.gender === 'FEMALE').length,
      unset: pool.filter((one) => one.gender !== 'MALE' && one.gender !== 'FEMALE').length,
    }
  }, [teachers.data])

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
              flag: strays.length > 0,
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
        <>
          {strays.length > 0 && (
            <div className="mb-5">
              <Notice tone="warn" icon={<TriangleAlert size={16} aria-hidden />}>
                {t('teachers.subjectsOff', {
                  count: strays.length,
                  names: [...new Set(strays.map((one) => one.subject))].join(', '),
                })}
              </Notice>
            </div>
          )}

          <Card>
            <CardHead
              title={t('teachers.pool')}
              count={teachers.data?.length}
              actions={
                (teachers.data?.length ?? 0) > 0 && (
                  <>
                    {/* the split said once, in figures: a row with the wrong
                        one makes the count wrong, which is how it gets found */}
                    <span className="text-[12px] text-[var(--color-quiet)]">
                      {[
                        t('teachers.splitMale', { count: split.male }),
                        t('teachers.splitFemale', { count: split.female }),
                        split.unset > 0 ? t('teachers.splitUnset', { count: split.unset }) : null,
                      ]
                        .filter(Boolean)
                        .join(' · ')}
                    </span>
                    {/* the label sits beside the field, not stacked above it:
                        stacked, it made the control taller than the search box
                        next to it and the two stopped lining up */}
                    <span className="text-[12px] text-[var(--color-quiet)]">
                      {t('teachers.groupBy')}
                    </span>
                    <Select
                      label={t('teachers.groupBy')}
                      hideLabel
                      className="w-44"
                      value={grouping}
                      onChange={(key) => setGrouping(key as Grouping)}
                      choices={[
                        { id: 'none', label: t('teachers.groupNone') },
                        { id: 'subject', label: t('teachers.subject') },
                        { id: 'establishment', label: t('teachers.establishment') },
                        { id: 'corps', label: t('teachers.corps') },
                        { id: 'gender', label: t('teachers.gender') },
                      ]}
                    />
                    <SearchField
                      className="w-64"
                      label={t('teachers.search')}
                      placeholder={t('teachers.searchHint')}
                      value={search}
                      onChange={setSearch}
                    />
                  </>
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
                      <Th width="118px">{t('teachers.matricule')}</Th>
                      <Th>{t('teachers.name')}</Th>
                      <Th width="180px">{t('teachers.subject')}</Th>
                      <Th width="165px">{t('teachers.establishment')}</Th>
                      <Th width="104px">{t('teachers.gender')}</Th>
                      <Th width="124px">{t('absences.title')}</Th>
                      <Th width="150px" className="no-print" />
                    </tr>
                  </thead>
                  {/* one tbody, headings and rows together, so the last row of
                      the table is the only one that loses its rule */}
                  <tbody>
                    {groups.map((group) => (
                      <Fragment key={group.key}>
                        {grouping !== 'none' && (
                          <tr className="border-b border-[var(--color-hairline)] bg-[var(--color-sunken)]">
                            <Td colSpan={7} className="py-2">
                              <span className="flex items-baseline gap-2.5">
                                <bdi className="text-[12.5px] font-semibold text-[var(--color-quiet)]">
                                  {group.key === ''
                                    ? t('teachers.groupUnset')
                                    : grouping === 'gender'
                                      ? t(`teachers.gender${group.key}`)
                                      : group.key}
                                </bdi>
                                <span className="numeric text-[11.5px] text-[var(--color-faint)]">
                                  {group.rows.length}
                                </span>
                              </span>
                            </Td>
                          </tr>
                        )}

                        {group.rows.map((teacher) => (
                          <TeacherRow
                            key={teacher.matricule}
                            centerId={centerId!}
                            teacher={teacher}
                            known={known}
                            onEdit={() => {
                              setEditing(teacher)
                              setFormOpen(true)
                            }}
                            onAbsences={() => setAbsencesFor(teacher)}
                          />
                        ))}
                      </Fragment>
                    ))}
                  </tbody>
                </Table>
              ))}
          </Card>
        </>
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
