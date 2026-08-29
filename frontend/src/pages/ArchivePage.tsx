import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, CalendarRange, Lock, Printer } from 'lucide-react'
import { api } from '../lib/api'
import { useNames } from '../lib/names'
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
  Skeleton,
  Table,
  Td,
  Th,
  Tr,
} from '../ui'

type ArchivedSession = {
  id: number
  reference: string
  type: string
  startsOn: string | null
  endsOn: string | null
  state: 'DRAFT' | 'SETTLED'
  scheduleJobId: number | null
  dutyCount: number
}

type Tally = {
  matricule: string
  name: string
  nameFr: string | null
  subject: string
  surveillance: number
  reserve: number
  permanence: number
  total: number
}

type Archive = {
  yearId: number
  label: string
  centerId: number
  centerName: string
  sessions: ArchivedSession[]
  tally: Tally[]
}

/**
 * The record of a year that is over.
 *
 * <p>This screen exists for one question, asked out loud in a corridor in July:
 * « pourquoi j'ai eu trois surveillances de plus que lui ». So it is a table
 * that answers it — every teacher of the year, heaviest first, with the three
 * roles kept apart because they are not the same work.
 *
 * <p>Two things follow from the question. Teachers who did nothing are in the
 * list with zeros: leaving them out is exactly how the answer comes out wrong.
 * And somebody who has since left the establishment is still here, because he
 * served the year being read.
 */
export function ArchivePage() {
  const { t, i18n } = useTranslation()
  const { label, second } = useNames()
  const navigate = useNavigate()
  const { yearId } = useParams()
  const { centerId } = useWorkspace()

  const archive = useQuery({
    queryKey: ['archive', centerId, yearId],
    queryFn: () => api.get<Archive>(`/centers/${centerId}/years/${yearId}/archive`),
    enabled: centerId !== null && yearId !== undefined,
  })

  const dates = new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium' })
  const span = (session: ArchivedSession) => {
    if (!session.startsOn || !session.endsOn) return t('sessions.datesUnset')
    return `${dates.format(new Date(`${session.startsOn}T00:00:00`))} — ${dates.format(
      new Date(`${session.endsOn}T00:00:00`),
    )}`
  }

  const data = archive.data
  const settled = data?.sessions.filter((session) => session.state === 'SETTLED') ?? []

  return (
    <Page
      title={data ? t('archive.title', { label: data.label }) : t('archive.plain')}
      subtitle={data?.centerName}
      actions={
        <>
          <Button variant="secondary" onPress={() => void navigate('/years')}>
            <ArrowLeft size={16} className="rtl:rotate-180" aria-hidden />
            {t('archive.back')}
          </Button>
          {/* the record is a thing you print and take into the corridor */}
          <Button variant="secondary" onPress={() => window.print()}>
            <Printer size={16} aria-hidden />
            {t('archive.print')}
          </Button>
        </>
      }
    >
      {archive.isPending && (
        <Card>
          <Skeleton rows={6} />
        </Card>
      )}

      {archive.isError && (
        <Card>
          <Failed error={archive.error as Error} onRetry={() => void archive.refetch()} />
        </Card>
      )}

      {data && (
        <div className="space-y-6">
          <Card>
            <CardHead
              title={t('archive.sessions')}
              count={data.sessions.length}
              actions={
                <span className="numeric text-[12.5px] text-[var(--color-quiet)]">
                  {t('archive.countedFrom', { count: settled.length })}
                </span>
              }
            />
            <CardRule />

            {data.sessions.length === 0 ? (
              <Empty icon={<CalendarRange size={22} aria-hidden />}>{t('archive.noSession')}</Empty>
            ) : (
              <ul>
                {data.sessions.map((session) => (
                  <li
                    key={session.id}
                    className="flex flex-wrap items-center gap-4 border-b border-[var(--color-hairline)] px-5 py-4 last:border-b-0"
                  >
                    <span className="min-w-0 flex-1">
                      <span className="flex flex-wrap items-center gap-2">
                        <span className="truncate text-[14px] font-medium">
                          {session.reference}
                        </span>
                        {session.state === 'SETTLED' ? (
                          <Badge tone="accent">
                            <Lock size={11} aria-hidden />
                            {t('lifecycle.state.SETTLED')}
                          </Badge>
                        ) : (
                          <Badge tone="plain">{t('lifecycle.state.DRAFT')}</Badge>
                        )}
                      </span>
                      <span className="mt-1 flex flex-wrap items-center gap-2 text-[12px] text-[var(--color-quiet)]">
                        <span>
                          {t(`sessions.type.${session.type}`, { defaultValue: session.type })}
                        </span>
                        <span className="text-[var(--color-hairline)]">·</span>
                        <bdi dir="ltr" className="numeric">
                          {span(session)}
                        </bdi>
                      </span>
                    </span>

                    <span className="numeric shrink-0 text-[12.5px] text-[var(--color-quiet)]">
                      {t('archive.duties', { count: session.dutyCount })}
                    </span>

                    {/* the room-by-room detail is not copied into the archive:
                        it is the schedule of the solve that went out */}
                    {session.scheduleJobId !== null && (
                      <Button
                        size="sm"
                        variant="secondary"
                        className="no-print"
                        onPress={() => void navigate('/results')}
                      >
                        {t('archive.openSchedule')}
                      </Button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </Card>

          <Card>
            <CardHead title={t('archive.tally')} count={data.tally.length} />
            <CardRule />

            {data.tally.length === 0 ? (
              <Empty>{t('archive.noTally')}</Empty>
            ) : (
              <Table>
                <thead>
                  <tr>
                    <Th>{t('teachers.name')}</Th>
                    <Th width="150px">{t('teachers.subject')}</Th>
                    <Th width="120px">{t('results.roleSURVEILLANCE')}</Th>
                    <Th width="110px">{t('results.roleRESERVE')}</Th>
                    <Th width="120px">{t('results.rolePERMANENCE')}</Th>
                    <Th width="90px">{t('archive.total')}</Th>
                  </tr>
                </thead>
                <tbody>
                  {data.tally.map((row) => (
                    <Tr key={row.matricule}>
                      <Td>
                        <bdi className="block font-medium">{label(row)}</bdi>
                        {second(row) && (
                          <bdi className="mt-0.5 block text-[11.5px] text-[var(--color-quiet)]">
                            {second(row)}
                          </bdi>
                        )}
                        <span className="numeric mt-0.5 block text-[11.5px] text-[var(--color-quiet)]">
                          {row.matricule}
                        </span>
                      </Td>
                      <Td className="text-[var(--color-quiet)]">{row.subject}</Td>
                      <Td className="numeric">{row.surveillance}</Td>
                      <Td className="numeric">{row.reserve}</Td>
                      <Td className="numeric">{row.permanence}</Td>
                      <Td className="numeric font-semibold">{row.total}</Td>
                    </Tr>
                  ))}
                </tbody>
              </Table>
            )}

            <div className="rounded-b-[var(--radius-card)] border-t border-[var(--color-hairline)] bg-[var(--color-sunken)] px-5 py-3">
              <p className="text-[11.5px] text-[var(--color-faint)]">{t('archive.tallyHint')}</p>
            </div>
          </Card>
        </div>
      )}
    </Page>
  )
}
