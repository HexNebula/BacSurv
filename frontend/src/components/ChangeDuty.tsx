import { useEffect, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Check, Pin, TriangleAlert } from 'lucide-react'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { Badge, Button, Checkbox, Dialog, Notice, Select, Skeleton } from '../ui'

export type Candidate = { id: number; matricule: string; name: string; subject: string }

type Breach = { code: string; args: string[] }

type Review = {
  dutyId: string
  dutyDescription: string
  currentHolder: string | null
  newHolder: string | null
  breaches: Breach[]
  newHolderDutiesBefore: number
  newHolderDutiesAfter: number
}

type DutyView = { dutyId: string; description: string; holder: string | null; pinned: boolean }

/**
 * Moving one duty to somebody else.
 *
 * <p>A real centre always has a teacher who has to be moved the day before, and
 * a distribution that cannot be corrected by hand is one an administrator has to
 * take or leave. The change is therefore reviewed before it is made: the server
 * says which rules it would break — this person is away that day, already has
 * another duty at that hour, is not the subject's specialist, would be watching
 * their own paper — and the administrator decides with that in front of them
 * rather than discovering it afterwards.
 *
 * <p>Nothing is refused outright. The rules belong to the centre, and the
 * administrator is the one who answers for them, so a breach is shown and can be
 * overridden deliberately.
 */
export function ChangeDuty({
  jobId,
  dutyId,
  open,
  onClose,
}: {
  jobId: number
  dutyId: string | null
  open: boolean
  onClose: () => void
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [chosen, setChosen] = useState<number | null>(null)
  const [review, setReview] = useState<Review | null>(null)

  const duty = useQuery({
    queryKey: ['duty', jobId, dutyId],
    queryFn: () => api.get<DutyView>(`/jobs/${jobId}/duties/${dutyId}`),
    enabled: open && dutyId !== null,
  })

  const candidates = useQuery({
    queryKey: ['candidates', jobId],
    queryFn: () => api.get<Candidate[]>(`/jobs/${jobId}/candidates`),
    enabled: open,
  })

  useEffect(() => {
    if (!open) return
    setChosen(null)
    setReview(null)
  }, [open, dutyId])

  /** What the change would break, asked for as soon as a name is picked. */
  const check = useApiMutation({
    run: (teacherId: number | null) =>
      api.post<Review>(
        `/jobs/${jobId}/assignments/${dutyId}/review${
          teacherId === null ? '' : `?teacherId=${teacherId}`
        }`,
      ),
    onDone: (result) => {
      setReview(result)
    },
  })

  const apply = useApiMutation({
    run: (force: boolean) =>
      api.post<Review>(
        `/jobs/${jobId}/assignments/${dutyId}?force=${force}` +
          (chosen === null ? '' : `&teacherId=${chosen}`),
      ),
    invalidate: ['schedule', jobId],
    onDone: () => {
      void queryClient.invalidateQueries({ queryKey: ['duty', jobId, dutyId] })
      onClose()
      return t('change.applied')
    },
  })

  const pin = useApiMutation({
    run: (pinned: boolean) =>
      api.post<void>(`/jobs/${jobId}/assignments/${dutyId}/pin?pinned=${pinned}`),
    invalidate: ['duty', jobId, dutyId],
    onDone: (_result, pinned) => (pinned ? t('change.pinnedOn') : t('change.pinnedOff')),
  })

  const choose = (teacherId: number | null) => {
    setChosen(teacherId)
    check.mutate(teacherId)
  }

  const breaches = review?.breaches ?? []
  const illegal = breaches.length > 0

  return (
    <Dialog
      isOpen={open}
      onClose={onClose}
      title={t('change.title')}
      subtitle={duty.data?.description}
      footer={
        <>
          <Button variant="secondary" onPress={onClose}>
            {t('app.cancel')}
          </Button>
          <Button
            variant={illegal ? 'danger' : 'primary'}
            isDisabled={review === null}
            isPending={apply.isPending}
            onPress={() => {
              // forcing is the same press, not a second dialog: the breaches are
              // on the screen and the administrator has just read them
              apply.mutate(illegal)
            }}
          >
            {illegal ? t('change.force') : t('change.apply')}
          </Button>
        </>
      }
    >
      <div className="space-y-5 pb-4">
        {duty.isPending && <Skeleton rows={2} />}

        {duty.data && (
          <div className="flex items-center justify-between gap-4 rounded-[var(--radius-field)] bg-[var(--color-sunken)] px-4 py-3">
            <div className="min-w-0">
              <div className="text-[11.5px] font-medium text-[var(--color-quiet)]">
                {t('change.current')}
              </div>
              <div className="mt-0.5 truncate text-[14px] font-medium">
                {duty.data.holder ?? (
                  <span className="text-[var(--color-faint)]">{t('change.nobodyYet')}</span>
                )}
              </div>
            </div>
            {duty.data.pinned && (
              <Badge tone="accent" icon={<Pin size={12} aria-hidden />}>
                {t('change.pinned')}
              </Badge>
            )}
          </div>
        )}

        {candidates.isPending ? (
          <Skeleton rows={2} />
        ) : (
          <Select
            label={t('change.replaceWith')}
            value={chosen}
            onChange={(key) => choose(Number(key))}
            placeholder={t('change.replaceWith')}
            choices={(candidates.data ?? []).map((one) => ({
              id: one.id,
              label: one.name,
              hint: one.subject,
            }))}
          />
        )}

        {check.isPending && <Skeleton rows={1} />}

        {/* the server's own sentences, one per rule the change would break */}
        {review && illegal && (
          <div className="space-y-2">
            {breaches.map((breach, index) => (
              <Notice
                key={`${breach.code}-${index}`}
                tone="alarm"
                icon={<TriangleAlert size={16} aria-hidden />}
              >
                {t(breach.code, {
                  one: breach.args[0] ?? '',
                  two: breach.args[1] ?? '',
                  defaultValue: breach.code,
                })}
              </Notice>
            ))}
          </div>
        )}

        {review && !illegal && (
          <Notice tone="good" icon={<Check size={16} aria-hidden />}>
            {t('change.legal')}
          </Notice>
        )}

        {/* what it costs the person taking it on: the count they would move to */}
        {review?.newHolder && (
          <p className="text-[12.5px] text-[var(--color-quiet)]">
            {t('change.workload', {
              name: review.newHolder,
              before: review.newHolderDutiesBefore,
              after: review.newHolderDutiesAfter,
            })}
          </p>
        )}

        {duty.data && (
          <div className="border-t border-[var(--color-hairline)] pt-4">
            <Checkbox
              isSelected={duty.data.pinned}
              onChange={() => pin.mutate(!duty.data.pinned)}
            >
              {t('change.pin')}
            </Checkbox>
            <p className="mt-1.5 ms-8 text-[11.5px] text-[var(--color-faint)]">
              {t('change.pinHint')}
            </p>
          </div>
        )}
      </div>
    </Dialog>
  )
}
