import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { BookOpen, SquarePen, TriangleAlert, Users } from 'lucide-react'
import { api } from '../lib/api'
import { useWorkspace } from '../context/Workspace'
import { Page } from '../components/Page'
import { CatalogueList } from '../components/CatalogueList'
import {
  Badge,
  Button,
  Card,
  CardHead,
  CardRule,
  Empty,
  Failed,
  Notice,
  SegmentedTabs,
  Skeleton,
  Table,
  Td,
  Th,
  Tr,
} from '../ui'

type Subject = { id: number; name: string; usedByTeachers: number; usedByExams: number }
type Teacher = { matricule: string; name: string; subject: string }

/**
 * What the centre examines, and who can stand for each subject.
 *
 * <p>The catalogue was a list inside the centre's page, which hid the figure
 * that actually decides a session: one specialist per subject is on permanence
 * for a séance, so a subject examined by the centre and taught by nobody is a
 * permanence with no candidate, and a subject taught by one person is a session
 * that fails the day that person is away.
 */
export function SubjectsPage() {
  const { t } = useTranslation()
  const { centerId, hasCenter, isLoading } = useWorkspace()
  const [view, setView] = useState('coverage')

  const subjects = useQuery({
    queryKey: ['subjects', centerId],
    queryFn: () => api.get<Subject[]>(`/centers/${centerId}/subjects`),
    enabled: centerId !== null,
  })

  const teachers = useQuery({
    queryKey: ['teachers', centerId],
    queryFn: () => api.get<Teacher[]>(`/centers/${centerId}/teachers`),
    enabled: centerId !== null,
  })

  /** The pool counted per subject, so the table reads one row per subject. */
  const pool = useMemo(() => {
    const counts = new Map<string, number>()
    for (const teacher of teachers.data ?? []) {
      counts.set(teacher.subject, (counts.get(teacher.subject) ?? 0) + 1)
    }
    return counts
  }, [teachers.data])

  const rows = useMemo(
    () =>
      (subjects.data ?? [])
        .map((subject) => ({ ...subject, teachers: pool.get(subject.name) ?? 0 }))
        .sort((a, b) => a.teachers - b.teachers || b.usedByExams - a.usedByExams),
    [subjects.data, pool],
  )

  /** Examined this session but taught by nobody: the permanence has no one. */
  const uncovered = rows.filter((row) => row.usedByExams > 0 && row.teachers === 0)

  if (!isLoading && !hasCenter) {
    return (
      <Page title={t('subjects.title')}>
        <Card>
          <Empty icon={<BookOpen size={22} aria-hidden />}>{t('teachers.noCenter')}</Empty>
        </Card>
      </Page>
    )
  }

  return (
    <Page
      title={t('subjects.title')}
      subtitle={t('subjects.subtitle')}
      tabs={
        <SegmentedTabs
          value={view}
          onChange={setView}
          tabs={[
            {
              id: 'coverage',
              label: t('subjects.coverage'),
              icon: <Users size={15} aria-hidden />,
              count: rows.length,
              flag: uncovered.length > 0,
            },
            {
              id: 'catalogue',
              label: t('catalogue.edit'),
              icon: <SquarePen size={15} aria-hidden />,
              count: subjects.data?.length,
            },
          ]}
        />
      }
      actions={
        view === 'coverage' &&
        subjects.isSuccess && (
          <Button variant="secondary" onPress={() => setView('catalogue')}>
            <SquarePen size={16} aria-hidden />
            {t('catalogue.edit')}
          </Button>
        )
      }
    >
      {view === 'coverage' ? (
        <>
          {uncovered.length > 0 && (
            <div className="mb-5">
              <Notice tone="warn" icon={<TriangleAlert size={16} aria-hidden />}>
                {t('subjects.uncovered', {
                  count: uncovered.length,
                  names: uncovered.map((row) => row.name).join(', '),
                })}
              </Notice>
            </div>
          )}

          <Card>
            <CardHead title={t('subjects.coverage')} count={rows.length} />
            <CardRule />

            {subjects.isPending && <Skeleton rows={6} />}
            {subjects.isError && (
              <Failed error={subjects.error as Error} onRetry={() => void subjects.refetch()} />
            )}

            {subjects.isSuccess &&
              (rows.length === 0 ? (
                <Empty
                  icon={<BookOpen size={22} aria-hidden />}
                  action={
                    <Button onPress={() => setView('catalogue')}>
                      <SquarePen size={16} aria-hidden />
                      {t('catalogue.edit')}
                    </Button>
                  }
                >
                  {t('catalogue.subjects.empty')}
                </Empty>
              ) : (
                <Table>
                  <thead>
                    <tr>
                      <Th>{t('teachers.subject')}</Th>
                      <Th width="180px">{t('subjects.teachers')}</Th>
                      <Th width="180px">{t('subjects.exams')}</Th>
                      <Th width="200px" />
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((row) => (
                      <Tr key={row.id}>
                        <Td className="font-medium">{row.name}</Td>
                        <Td className="numeric">{row.teachers}</Td>
                        <Td className="numeric text-[var(--color-quiet)]">{row.usedByExams}</Td>
                        <Td>
                          {/* the two states worth naming: nobody at all, and a
                              single person carrying the subject on their own */}
                          {row.usedByExams > 0 && row.teachers === 0 ? (
                            <Badge tone="alarm">{t('subjects.noSpecialist')}</Badge>
                          ) : row.teachers === 1 ? (
                            <Badge tone="warn">{t('teachers.thinSubject')}</Badge>
                          ) : null}
                        </Td>
                      </Tr>
                    ))}
                  </tbody>
                </Table>
              ))}
          </Card>
        </>
      ) : (
        centerId !== null && <CatalogueList centerId={centerId} kind="subjects" />
      )}
    </Page>
  )
}
