import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { GraduationCap, Layers, Pencil, Plus, TriangleAlert } from 'lucide-react'
import { api } from '../lib/api'
import { useWorkspace } from '../context/Workspace'
import { Page } from '../components/Page'
import { CatalogueList } from '../components/CatalogueList'
import { StreamForm, type Stream } from '../components/StreamForm'
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
} from '../ui'

type Timetable = { centerId: number; streams: Stream[]; exams: { streamId: number | null }[] }

/**
 * The filières of the session, and the rooms each one holds.
 *
 * <p>Until now a filière existed in two places and was visible in neither: the
 * centre's catalogue held its name, and the rooms it occupies were buried in a
 * dialog inside the planning grid. An administrator could not answer "which
 * filières am I running, and where do they sit?" without opening the grid and
 * clicking through it filière by filière.
 *
 * <p>Two views, because there are genuinely two things: the ones running in
 * this session, and the centre's own list that they are chosen from.
 */
export function StreamsPage() {
  const { t } = useTranslation()
  const { centerId, sessionId, sessionsHere, isLoading } = useWorkspace()
  const [view, setView] = useState('session')
  const [form, setForm] = useState<{ open: boolean; stream?: Stream }>({ open: false })

  const timetable = useQuery({
    queryKey: ['timetable', sessionId],
    queryFn: () => api.get<Timetable>(`/sessions/${sessionId}/timetable`),
    enabled: sessionId !== null,
  })

  const catalogue = useQuery({
    queryKey: ['streams', centerId],
    queryFn: () => api.get<{ id: number }[]>(`/centers/${centerId}/streams`),
    enabled: centerId !== null,
  })

  const streams = timetable.data?.streams ?? []
  const roomless = streams.filter((stream) => stream.rooms.length === 0)

  /**
   * Rooms handed to more than one filière. Nothing refused this until now, so
   * a session entered earlier can still hold the fault — and a double-booked
   * room is two épreuves behind one door, each asking for its own surveillants.
   */
  const clashes = (() => {
    const holders = new Map<string, string[]>()
    for (const stream of streams) {
      for (const room of stream.rooms) {
        holders.set(room.label, [...(holders.get(room.label) ?? []), stream.name])
      }
    }
    return [...holders.entries()].filter(([, names]) => names.length > 1)
  })()

  /** How many épreuves each filière sits, so an idle one is visible here too. */
  const examCount = (streamId: number) =>
    (timetable.data?.exams ?? []).filter((exam) => exam.streamId === streamId).length

  if (!isLoading && sessionsHere.length === 0) {
    return (
      <Page title={t('streams.title')}>
        <Card>
          <Empty icon={<GraduationCap size={22} aria-hidden />}>{t('schedule.noSession')}</Empty>
        </Card>
      </Page>
    )
  }

  return (
    <Page
      title={t('streams.title')}
      subtitle={t('streams.subtitle')}
      tabs={
        <SegmentedTabs
          value={view}
          onChange={setView}
          tabs={[
            {
              id: 'session',
              label: t('streams.thisSession'),
              icon: <GraduationCap size={15} aria-hidden />,
              count: streams.length,
              flag: roomless.length > 0,
            },
            {
              id: 'catalogue',
              label: t('catalogue.streams.title'),
              icon: <Layers size={15} aria-hidden />,
              count: catalogue.data?.length,
            },
          ]}
        />
      }
      actions={
        view === 'session' &&
        sessionId !== null && (
          <Button onPress={() => setForm({ open: true })}>
            <Plus size={16} aria-hidden />
            {t('schedule.addStream')}
          </Button>
        )
      }
    >
      {view === 'session' ? (
        <>
          {clashes.length > 0 && (
            <div className="mb-5">
              <Notice tone="alarm" icon={<TriangleAlert size={16} aria-hidden />}>
                {t('streams.roomClash', {
                  count: clashes.length,
                  rooms: clashes.map(([room]) => room).join(', '),
                })}
              </Notice>
            </div>
          )}

          {roomless.length > 0 && (
            <div className="mb-5">
              <Notice tone="warn" icon={<TriangleAlert size={16} aria-hidden />}>
                {t('schedule.roomlessStreams', {
                  count: roomless.length,
                  names: roomless.map((stream) => stream.name).join(', '),
                })}
              </Notice>
            </div>
          )}

          <Card>
            <CardHead title={t('streams.thisSession')} count={streams.length} />
            <CardRule />

            {timetable.isPending && sessionId !== null && <Skeleton rows={3} />}
            {timetable.isError && (
              <Failed
                error={timetable.error as Error}
                onRetry={() => void timetable.refetch()}
              />
            )}

            {timetable.isSuccess &&
              (streams.length === 0 ? (
                <Empty
                  icon={<GraduationCap size={22} aria-hidden />}
                  action={
                    <Button onPress={() => setForm({ open: true })}>
                      <Plus size={16} aria-hidden />
                      {t('schedule.addStream')}
                    </Button>
                  }
                >
                  {t('schedule.noStream')}
                </Empty>
              ) : (
                <ul>
                  {streams.map((stream) => (
                    <li
                      key={stream.id}
                      className="flex flex-wrap items-center gap-4 border-b border-[var(--color-hairline)] px-5 py-4 last:border-b-0"
                    >
                      <div className="min-w-0 flex-1">
                        <div className="text-[15px] font-semibold">{stream.name}</div>
                        <div className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-[12px] text-[var(--color-quiet)]">
                          {/* the rooms themselves, not just how many: this is
                              the screen where you check that Lettres really did
                              get salle 1 and nothing else */}
                          {stream.rooms.length === 0 ? (
                            <span className="font-medium text-[var(--color-warn)]">
                              {t('schedule.noRoomsYet')}
                            </span>
                          ) : (
                            stream.rooms.map((room) => (
                              <span
                                key={room.id}
                                className="rounded-md bg-[var(--color-sunken)] px-2 py-0.5"
                              >
                                {room.label}
                              </span>
                            ))
                          )}
                        </div>
                      </div>

                      <div className="flex shrink-0 items-center gap-3">
                        <span className="numeric text-[12.5px] text-[var(--color-quiet)]">
                          {t('streams.examCount', { count: examCount(stream.id) })}
                        </span>
                        {examCount(stream.id) === 0 && (
                          <Badge tone="warn">{t('streams.noExam')}</Badge>
                        )}
                        <Button
                          size="sm"
                          variant="secondary"
                          onPress={() => setForm({ open: true, stream })}
                        >
                          <Pencil size={15} aria-hidden />
                          {t('streams.rooms')}
                        </Button>
                      </div>
                    </li>
                  ))}
                </ul>
              ))}
          </Card>
        </>
      ) : (
        centerId !== null && <CatalogueList centerId={centerId} kind="streams" />
      )}

      {sessionId !== null && timetable.data && (
        <StreamForm
          sessionId={sessionId}
          centerId={timetable.data.centerId}
          existing={form.stream}
          open={form.open}
          onClose={() => setForm({ open: false })}
        />
      )}
    </Page>
  )
}
