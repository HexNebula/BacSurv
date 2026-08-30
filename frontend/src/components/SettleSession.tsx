import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Lock, LockOpen, RotateCw, Stamp } from 'lucide-react'
import { api, codeOf } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { useLifecycleRefresh, type Impact } from '../lib/session'
import { Button, Dialog } from '../ui'

/**
 * The refusals a fresh distribution undoes, and the word for the button that
 * does it. A refusal absent from here is one no amount of solving would move.
 */
const FIX: Record<string, string> = {
  'session.settle.noDistribution': 'none',
  'session.settle.stale': 'stale',
  'session.settle.teachersBusy': 'busy',
}

/**
 * Arrêter la répartition, and the way back.
 *
 * <p>This is the act the whole model turns on. Until a session is arrêtée its
 * duties count for nothing: the next session will offer réserve and permanence
 * to the very people who already took theirs. It is also the moment the
 * planning stops moving, because from here on it is the planning the
 * convocations were printed from.
 *
 * <p>So it is a button that says what it does and then says what it did, not a
 * toggle. Reopening is allowed — a wrong date should not be permanent — but it
 * says first what it costs, in the figures the server counted rather than the
 * ones a screen guessed.
 */
export function SettleSession({
  sessionId,
  centerId,
  impact,
  /** `full` on Résultats, where it is the screen's own act. */
  variant = 'full',
  onSolve,
  isSolving = false,
}: {
  sessionId: number
  centerId: number | null
  impact: Impact | undefined
  variant?: 'full' | 'compact'
  /** Offered when a refusal says the distribution is the thing to fix. */
  onSolve?: () => void
  isSolving?: boolean
}) {
  const { t } = useTranslation()
  const refresh = useLifecycleRefresh()
  const [asking, setAsking] = useState(false)

  const settle = useApiMutation({
    run: () => api.post<Impact>(`/sessions/${sessionId}/settle`, {}),
    onDone: () => {
      refresh(centerId, sessionId)
      return t('lifecycle.settled')
    },
  })

  const reopen = useApiMutation({
    run: () => api.post<Impact>(`/sessions/${sessionId}/reopen`, {}),
    onDone: () => {
      refresh(centerId, sessionId)
      setAsking(false)
      return t('lifecycle.reopened')
    },
  })

  if (impact?.state === 'SETTLED') {
    return (
      <>
        <Button
          variant="secondary"
          size={variant === 'compact' ? 'sm' : 'md'}
          onPress={() => setAsking(true)}
        >
          <LockOpen size={variant === 'compact' ? 15 : 16} aria-hidden />
          {t('lifecycle.reopen')}
        </Button>

        <Dialog
          isOpen={asking}
          onClose={() => setAsking(false)}
          title={t('lifecycle.reopen')}
          subtitle={impact.reference}
          footer={
            <>
              <Button variant="secondary" onPress={() => setAsking(false)}>
                {t('app.cancel')}
              </Button>
              <Button
                variant="danger"
                isPending={reopen.isPending}
                onPress={() => reopen.mutate(undefined)}
              >
                <LockOpen size={16} aria-hidden />
                {t('lifecycle.reopen')}
              </Button>
            </>
          }
        >
          {/* the cost, in the server's own figures: this is the sentence that
              decides whether he goes ahead */}
          <div className="space-y-3 pb-4 text-[13.5px] leading-relaxed">
            <p>
              {t('lifecycle.reopenCost', {
                duties: impact.dutyCount,
                teachers: impact.teacherCount,
              })}
            </p>
            <p className="text-[var(--color-quiet)]">{t('lifecycle.reopenThen')}</p>
          </div>
        </Dialog>
      </>
    )
  }

  /*
   * Settling is refused for several reasons, and each has its own way out:
   * nothing solved yet, a distribution that leaves rooms unstaffed, a
   * distribution answering a timetable that has since moved, and people already
   * on duty in a session running the same hours. The sentence arrives from the
   * server as a toast like every other refusal; what is added here is the move
   * it implies, so he is not left reading a rule with nothing to press.
   *
   * <p>The last one is undone by solving again, because a settled neighbour now
   * reaches the solver as an unavailability and the second attempt avoids those
   * people by itself. Rooms held by a neighbour are not in this list: no
   * distribution frees a door, and the way out is to give this session other
   * rooms or to reopen the other one.
   */
  const refusal = codeOf(settle.error)
  const resolvable = refusal !== undefined && refusal in FIX

  return (
    <span className="flex flex-wrap items-center gap-2">
      {resolvable && onSolve && (
        <Button
          size={variant === 'compact' ? 'sm' : 'md'}
          variant="secondary"
          isPending={isSolving}
          onPress={onSolve}
        >
          <RotateCw size={variant === 'compact' ? 15 : 16} aria-hidden />
          {t(`lifecycle.fix.${FIX[refusal ?? ''] ?? 'none'}`)}
        </Button>
      )}

      <Button
        size={variant === 'compact' ? 'sm' : 'md'}
        isPending={settle.isPending}
        onPress={() => settle.mutate(undefined)}
      >
        <Stamp size={variant === 'compact' ? 15 : 16} aria-hidden />
        {t('lifecycle.settle')}
      </Button>
    </span>
  )
}

/** The mark a settled session carries wherever it is named. */
export function SettledMark() {
  const { t } = useTranslation()

  return (
    <span className="inline-flex items-center gap-1.5 rounded-[3px] border border-[var(--color-accent)]/25 bg-[var(--color-accent-tint)] px-2 py-0.5 text-[11.5px] font-medium text-[var(--color-accent-ink)]">
      <Lock size={12} aria-hidden />
      {t('lifecycle.state.SETTLED')}
    </span>
  )
}
