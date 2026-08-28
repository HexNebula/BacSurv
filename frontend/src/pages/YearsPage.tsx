import { useCallback, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import {
  CalendarRange,
  Check,
  LogIn,
  LogOut,
  Plus,
  Users,
} from 'lucide-react'
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
  Empty,
  Failed,
  Notice,
  SearchField,
  SegmentedTabs,
  Skeleton,
  TextField,
} from '../ui'

type Year = {
  id: number
  label: string
  teacherCount: number
  sessionCount: number
  current: boolean
}

type Member = {
  matricule: string
  name: string
  subject: string
  establishment: string | null
  gender: string | null
  /** He has already taken a duty this year, so leaving is a real departure. */
  served: boolean
}

type YearPool = {
  yearId: number
  label: string
  members: Member[]
  former: Member[]
}

/**
 * The school year, and the September morning it turns over.
 *
 * <p>The year is what decides who is in the pool and how far fairness looks
 * back: réserve and permanence are counted within a year and start again in
 * September, so a teacher who took three turns last year begins the new one
 * level with everybody else.
 *
 * <p>The rollover is deliberately not an import. Opening a year carries the
 * previous list into it, and what is left is the real work of that morning —
 * taking out the three who left and putting back the two who returned. Forty-one
 * people who stayed are not touched, and nobody has to retype them.
 */
export function YearsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { centerId, hasCenter, isLoading } = useWorkspace()
  const [view, setView] = useState('pool')
  const [opening, setOpening] = useState('')
  const [search, setSearch] = useState('')

  const years = useQuery({
    queryKey: ['years', centerId],
    queryFn: () => api.get<Year[]>(`/centers/${centerId}/years`),
    enabled: centerId !== null,
  })

  /** The year in progress: the only one whose pool is edited. */
  const current = years.data?.find((year) => year.current)

  const pool = useQuery({
    queryKey: ['yearPool', current?.id],
    queryFn: () => api.get<YearPool>(`/centers/${centerId}/years/${current?.id}/teachers`),
    enabled: centerId !== null && current !== undefined,
  })

  const open = useApiMutation({
    run: () => api.post<Year[]>(`/centers/${centerId}/years`, { label: opening.trim() }),
    invalidate: ['years', centerId],
    onDone: () => {
      setOpening('')
      return t('years.opened')
    },
  })

  /** Name, matricule or subject: the three things he would type looking. */
  const matching = useCallback(
    (people: Member[]) => {
      const needle = search.trim().toLowerCase()
      if (needle === '') return people
      return people.filter(
        (one) =>
          one.name.toLowerCase().includes(needle) ||
          one.matricule.toLowerCase().includes(needle) ||
          one.subject.toLowerCase().includes(needle),
      )
    },
    [search],
  )

  const members = useMemo(() => matching(pool.data?.members ?? []), [pool.data, matching])
  const former = useMemo(() => matching(pool.data?.former ?? []), [pool.data, matching])

  if (!isLoading && !hasCenter) {
    return (
      <Page title={t('years.title')}>
        <Card>
          <Empty icon={<CalendarRange size={22} aria-hidden />}>{t('teachers.noCenter')}</Empty>
        </Card>
      </Page>
    )
  }

  return (
    <Page
      title={t('years.title')}
      subtitle={t('years.subtitle')}
      tabs={
        <SegmentedTabs
          value={view}
          onChange={setView}
          tabs={[
            {
              id: 'pool',
              label: t('years.rollover'),
              icon: <Users size={15} aria-hidden />,
              count: pool.data?.members.length,
            },
            {
              id: 'years',
              label: t('years.all'),
              icon: <CalendarRange size={15} aria-hidden />,
              count: years.data?.length,
            },
          ]}
        />
      }
    >
      {years.isPending && (
        <Card>
          <Skeleton rows={4} />
        </Card>
      )}

      {years.isError && (
        <Card>
          <Failed error={years.error as Error} onRetry={() => void years.refetch()} />
        </Card>
      )}

      {years.isSuccess && years.data.length === 0 && (
        <Card>
          <Empty icon={<CalendarRange size={22} aria-hidden />}>{t('years.none')}</Empty>
        </Card>
      )}

      {view === 'pool' && current && (
        <>
          <div className="mb-5">
            <Notice tone="plain" icon={<CalendarRange size={16} aria-hidden />}>
              {t('years.poolExplains', { label: current.label })}
            </Notice>
          </div>

          <div className="mb-4 max-w-sm">
            <SearchField
              label={t('teachers.search')}
              value={search}
              onChange={setSearch}
              placeholder={t('teachers.searchHint')}
            />
          </div>

          {/* items-start: the empty half must keep its own height rather than be
                stretched to match a list of forty-one people */}
          <div className="grid items-start gap-6 lg:grid-cols-2">
            {/* who is here this year, and can be taken out */}
            <People
              title={t('years.members', { label: current.label })}
              hint={t('years.membersHint')}
              people={members}
              empty={t('years.noMembers')}
              centerId={centerId}
              yearId={current.id}
              direction="out"
            />

            {/* and who is not, and can be put back */}
            <People
              title={t('years.former')}
              hint={t('years.formerHint')}
              people={former}
              empty={t('years.noFormer')}
              centerId={centerId}
              yearId={current.id}
              direction="in"
            />
          </div>
        </>
      )}

      {view === 'years' && years.isSuccess && (
        <Card>
          <CardHead title={t('years.all')} count={years.data.length} />
          <CardRule />

          <ul>
            {years.data.map((year) => (
              <li
                key={year.id}
                className="flex flex-wrap items-center gap-4 border-b border-[var(--color-hairline)] px-5 py-4 last:border-b-0"
              >
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-2">
                    <span className="numeric text-[14px] font-medium">{year.label}</span>
                    {year.current && <Badge tone="accent">{t('years.current')}</Badge>}
                  </span>
                  <span className="mt-0.5 block text-[12px] text-[var(--color-quiet)]">
                    {t('years.holds', {
                      teachers: year.teacherCount,
                      sessions: year.sessionCount,
                    })}
                  </span>
                </span>

                <Button
                  size="sm"
                  variant="secondary"
                  onPress={() => void navigate(`/years/${year.id}`)}
                >
                  {t('years.read')}
                </Button>
              </li>
            ))}
          </ul>

          {/*
            September. The label is typed rather than computed: a centre may
            call it 2027-2028 or 2027/28, and the record should read the way its
            paperwork does.
          */}
          <div className="rounded-b-[var(--radius-card)] border-t border-[var(--color-hairline)] bg-[var(--color-sunken)] px-5 py-4">
            <form
              className="flex flex-wrap items-center gap-2"
              onSubmit={(event) => {
                event.preventDefault()
                if (opening.trim() !== '') open.mutate(undefined)
              }}
            >
              <TextField
                aria-label={t('years.open')}
                value={opening}
                onChange={setOpening}
                placeholder={t('years.openHint')}
                className="flex-1"
              />
              <Button
                type="submit"
                isPending={open.isPending}
                isDisabled={opening.trim() === ''}
              >
                <Plus size={16} aria-hidden />
                {t('years.open')}
              </Button>
            </form>
            <p className="mt-2.5 text-[11.5px] text-[var(--color-faint)]">{t('years.openExplains')}</p>
          </div>
        </Card>
      )}
    </Page>
  )
}

/**
 * One half of the rollover.
 *
 * <p>The two lists carry opposite acts, and the wording is the whole point:
 * taking somebody out of a year is *il a quitté l'établissement*, not
 * *supprimer*. Everything he did stays attached to his matricule, and he can be
 * put back the year he returns. Deleting a teacher is a different act on a
 * different screen, for a row typed by mistake.
 */
function People({
  title,
  hint,
  people,
  empty,
  centerId,
  yearId,
  direction,
}: {
  title: string
  hint: string
  people: Member[]
  empty: string
  centerId: number | null
  yearId: number
  direction: 'in' | 'out'
}) {
  return (
    <Card>
      <CardHead title={title} count={people.length} />
      <CardRule />

      {people.length === 0 ? (
        <Empty icon={<Users size={22} aria-hidden />}>{empty}</Empty>
      ) : (
        <ul className="max-h-[32rem] overflow-y-auto">
          {people.map((person) => (
            <Person
              key={person.matricule}
              person={person}
              centerId={centerId}
              yearId={yearId}
              direction={direction}
            />
          ))}
        </ul>
      )}

      <div className="rounded-b-[var(--radius-card)] border-t border-[var(--color-hairline)] bg-[var(--color-sunken)] px-5 py-3">
        <p className="text-[11.5px] text-[var(--color-faint)]">{hint}</p>
      </div>
    </Card>
  )
}

function Person({
  person,
  centerId,
  yearId,
  direction,
}: {
  person: Member
  centerId: number | null
  yearId: number
  direction: 'in' | 'out'
}) {
  const { t } = useTranslation()

  const move = useApiMutation({
    run: () =>
      direction === 'out'
        ? api.del(`/centers/${centerId}/years/${yearId}/teachers/${person.matricule}`)
        : api.post(`/centers/${centerId}/years/${yearId}/teachers/${person.matricule}`, {}),
    invalidate: ['yearPool', yearId],
    onDone: () =>
      direction === 'out'
        ? t('years.leftDone', { name: person.name })
        : t('years.backDone', { name: person.name }),
  })

  return (
    <li className="flex items-center gap-3 border-b border-[var(--color-hairline)] px-5 py-3 transition-colors last:border-b-0 hover:bg-[var(--color-sunken)]">
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-2">
          <span className="truncate text-[13.5px] font-medium">{person.name}</span>
          {/* he has already stood in a room this year: leaving is a departure
              mid-year, not a correction of the list */}
          {person.served && (
            <span
              className="flex size-4 shrink-0 items-center justify-center rounded-[2px] bg-[var(--color-good-tint)] text-[var(--color-good)]"
              title={t('years.served')}
            >
              <Check size={11} aria-hidden />
            </span>
          )}
        </span>
        <span className="mt-0.5 flex flex-wrap items-center gap-1.5 text-[11.5px] text-[var(--color-quiet)]">
          <span className="numeric">{person.matricule}</span>
          <span className="text-[var(--color-hairline)]">·</span>
          <span className="truncate">{person.subject}</span>
        </span>
      </span>

      <Button
        size="sm"
        variant="quiet"
        isPending={move.isPending}
        onPress={() => move.mutate(undefined)}
      >
        {direction === 'out' ? (
          <>
            <LogOut size={14} className="rtl:rotate-180" aria-hidden />
            {t('years.left')}
          </>
        ) : (
          <>
            <LogIn size={14} className="rtl:rotate-180" aria-hidden />
            {t('years.back')}
          </>
        )}
      </Button>
    </li>
  )
}
