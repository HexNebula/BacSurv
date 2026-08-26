import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import {
  Button,
  Card,
  CardHead,
  CardRule,
  Checkbox,
  Failed,
  NumberField,
  Select,
  Skeleton,
  toast,
} from '../ui'

/**
 * As the server holds them. The fields this screen never shows are still read
 * and written back untouched — saving one rule must not quietly reset another.
 */
type Settings = {
  defaultSurveillantsPerRoom: number
  minimumSurveillantsPerRoom: number
  reserveMode: 'PERCENTAGE' | 'FIXED_COUNT'
  reservePercentage: number
  reserveFixedCount: number
  maxConsecutiveDays: number
  consecutiveDaysStrength: 'SOFT' | 'HARD'
  minGapMinutes: number
  ownSubjectStrength: 'SOFT' | 'HARD'
  forbidOwnSubjectReserve: boolean
  solveSeconds: number
}

/**
 * The rules the centre sets for one session.
 *
 * <p>Everything here was already in the solver and could only be reached by
 * editing a file, so a centre that puts three people in its large rooms had no
 * way to say so. The wording deliberately avoids the vocabulary the machinery
 * uses: an administrator decides that a teacher must never watch their own
 * paper, not that a constraint is HARD rather than SOFT.
 *
 * <p>How long the search may run is not on the screen. It is a property of the
 * computer, not of the centre, and nobody outside this file should have to hold
 * an opinion about it — it is read and written back as it was.
 */
export function Rules({ sessionId }: { sessionId: number }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [draft, setDraft] = useState<Settings | null>(null)

  const settings = useQuery({
    queryKey: ['settings', sessionId],
    queryFn: () => api.get<Settings>(`/operations/${sessionId}/settings`),
  })

  useEffect(() => {
    if (settings.data) setDraft(settings.data)
  }, [settings.data])

  /**
   * Relaunching from here rather than making him go and find the button: the
   * rules he just changed decide the distribution, and the shortest path
   * between the two is the toast that says the rules were saved.
   */
  const solve = useApiMutation({
    run: () => api.post<unknown>(`/operations/${sessionId}/solve`),
    invalidate: ['jobs'],
    onDone: () => {
      void navigate('/results')
    },
  })

  const save = useApiMutation({
    run: () => api.post<Settings>(`/operations/${sessionId}/settings`, draft),
    invalidate: ['settings', sessionId],
    onDone: () => {
      // offered on the toast, not asked in a dialog: three rules changed in a
      // row would be three interruptions, and a question always dismissed is
      // a question nobody reads
      toast.ok(t('rules.saved'), {
        label: t('rules.relaunch'),
        run: () => solve.mutate(undefined),
      })
    },
  })

  if (settings.isError) {
    return (
      <Card>
        <Failed error={settings.error as Error} onRetry={() => void settings.refetch()} />
      </Card>
    )
  }

  if (!draft) {
    return (
      <Card>
        <Skeleton rows={6} />
      </Card>
    )
  }

  const set = (patch: Partial<Settings>) => setDraft({ ...draft, ...patch })

  /* the stored figure is a rate; an administrator reads a percentage */
  const percent = Math.round(draft.reservePercentage * 100)

  return (
    <div className="space-y-5">
      {/* stretched, not sized to their contents: boxes of four different
          heights read as four unrelated things rather than four rules */}
      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <CardHead title={t('rules.staffing')} />
          <CardRule />
          <div className="space-y-4 p-5">
            <NumberField
              label={t('rules.surveillants')}
              value={draft.defaultSurveillantsPerRoom}
              minValue={draft.minimumSurveillantsPerRoom}
              maxValue={20}
              onChange={(value) => set({ defaultSurveillantsPerRoom: value })}
              className="w-44"
              hint={t('rules.surveillantsHint', { count: draft.minimumSurveillantsPerRoom })}
            />
            {/* the exception to this figure is a property of the room, so it is
                set where the rooms are, not a second time here */}
            <p className="text-[11.5px] text-[var(--color-faint)]">
              {t('rules.roomsElsewhere')}{' '}
              <Link
                to="/rooms"
                className="font-medium text-[var(--color-accent)] hover:underline"
              >
                {t('rooms.title')}
              </Link>
            </p>
          </div>
        </Card>

        <Card>
          <CardHead title={t('rules.reserve')} />
          <CardRule />
          <div className="space-y-4 p-5">
            <Select
              label={t('rules.reserveMode')}
              value={draft.reserveMode}
              onChange={(key) => set({ reserveMode: key as Settings['reserveMode'] })}
              choices={[
                { id: 'PERCENTAGE', label: t('rules.reserveMode.percentage') },
                { id: 'FIXED_COUNT', label: t('rules.reserveMode.fixed') },
              ]}
            />
            {draft.reserveMode === 'PERCENTAGE' ? (
              <NumberField
                label={t('rules.reservePercent')}
                value={percent}
                minValue={0}
                maxValue={100}
                onChange={(value) => set({ reservePercentage: value / 100 })}
                suffix="%"
                className="w-44"
                hint={t('rules.reservePercentHint')}
              />
            ) : (
              <NumberField
                label={t('rules.reserveCount')}
                value={draft.reserveFixedCount}
                minValue={0}
                maxValue={99}
                onChange={(value) => set({ reserveFixedCount: value })}
                className="w-44"
                hint={t('rules.reserveCountHint')}
              />
            )}
          </div>
        </Card>

        <Card>
          <CardHead title={t('rules.rest')} />
          <CardRule />
          <div className="space-y-4 p-5">
            <div className="flex flex-wrap items-end gap-4">
              <NumberField
                label={t('rules.consecutive')}
                value={draft.maxConsecutiveDays}
                minValue={1}
                maxValue={15}
                onChange={(value) => set({ maxConsecutiveDays: value })}
                className="w-44"
              />
              <Select
                label={t('rules.strength')}
                value={draft.consecutiveDaysStrength}
                onChange={(key) =>
                  set({ consecutiveDaysStrength: key as Settings['consecutiveDaysStrength'] })
                }
                choices={[
                  { id: 'HARD', label: t('rules.strength.hard') },
                  { id: 'SOFT', label: t('rules.strength.soft') },
                ]}
                className="w-56"
              />
            </div>
            <NumberField
              label={t('rules.gap')}
              value={draft.minGapMinutes}
              minValue={0}
              maxValue={600}
              onChange={(value) => set({ minGapMinutes: value })}
              suffix={t('rules.minutes')}
              className="w-44"
              hint={t('rules.gapHint')}
            />
          </div>
        </Card>

        <Card>
          <CardHead title={t('rules.ownSubject')} />
          <CardRule />
          <div className="space-y-4 p-5">
            <Select
              label={t('rules.ownSubjectRule')}
              value={draft.ownSubjectStrength}
              onChange={(key) =>
                set({ ownSubjectStrength: key as Settings['ownSubjectStrength'] })
              }
              choices={[
                { id: 'HARD', label: t('rules.strength.hard') },
                { id: 'SOFT', label: t('rules.strength.soft') },
              ]}
            />
            <Checkbox
              isSelected={draft.forbidOwnSubjectReserve}
              onChange={() => set({ forbidOwnSubjectReserve: !draft.forbidOwnSubjectReserve })}
            >
              {t('rules.ownSubjectReserve')}
            </Checkbox>
            <p className="ms-8 text-[11.5px] text-[var(--color-faint)]">
              {t('rules.ownSubjectHint')}
            </p>
          </div>
        </Card>
      </div>

      <div className="flex items-center justify-end gap-4">
        <span className="text-[12px] text-[var(--color-quiet)]">{t('rules.afterSaving')}</span>
        <Button isPending={save.isPending} onPress={() => save.mutate(undefined)}>
          {t('app.save')}
        </Button>
      </div>
    </div>
  )
}
