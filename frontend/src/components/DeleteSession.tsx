import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Trash2 } from 'lucide-react'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { useWorkspace } from '../context/Workspace'
import { impactKey, useLifecycleRefresh, type Impact } from '../lib/session'
import { Button } from '../ui'

/**
 * Removing a session, with what that costs said out loud.
 *
 * <p>A draft is typing and nothing else — filières entered, épreuves entered,
 * trials run — so it goes freely, but the confirmation names the typing rather
 * than asking "êtes-vous sûr ?". The figures come from the server: the screen
 * showing a session does not know how much is under it.
 *
 * <p>A settled session has no button at all. It is not that the act is
 * dangerous and needs guarding — it is that the répartition went out on paper,
 * and the queue is built by counting it. The row says so, and reopening is the
 * way through.
 */
export function DeleteSession({
  sessionId,
  reference,
}: {
  sessionId: number
  reference: string
}) {
  const { t } = useTranslation()
  const { centerId, sessionId: chosen, chooseSession } = useWorkspace()
  const refresh = useLifecycleRefresh()
  const [confirming, setConfirming] = useState(false)

  // only asked for once he reaches for the button: the centre's page would
  // otherwise fire one of these per session row on every visit
  const impact = useQuery({
    queryKey: impactKey(sessionId),
    queryFn: () => api.get<Impact>(`/sessions/${sessionId}/impact`),
    enabled: confirming,
  })

  const remove = useApiMutation({
    run: () => api.del(`/sessions/${sessionId}`),
    onDone: () => {
      // the header is pointed at a session that no longer exists; the workspace
      // picks the next one once /operations comes back without it
      if (chosen === sessionId) chooseSession(null)
      refresh(centerId, sessionId)
      setConfirming(false)
      return t('lifecycle.deleted', { reference })
    },
  })

  if (!confirming) {
    return (
      <Button
        size="sm"
        variant="quiet"
        isIcon
        aria-label={t('lifecycle.delete')}
        onPress={() => setConfirming(true)}
        className="hover:text-[var(--color-alarm)]"
      >
        <Trash2 size={15} aria-hidden />
      </Button>
    )
  }

  return (
    <span className="flex flex-wrap items-center justify-end gap-2">
      {/* what is about to go, counted by the server rather than guessed */}
      <span className="text-[11.5px] text-[var(--color-quiet)]">
        {impact.isPending
          ? t('lifecycle.counting')
          : /* each half is counted on its own so the noun agrees: one
               filière, not "1 filières" */
            t('lifecycle.deleteCost', {
              streams: t('lifecycle.streamCount', { count: impact.data?.streamCount ?? 0 }),
              exams: t('lifecycle.examCount', { count: impact.data?.examCount ?? 0 }),
            })}
      </span>
      <Button
        size="sm"
        variant="danger"
        isPending={remove.isPending}
        isDisabled={impact.isPending}
        onPress={() => remove.mutate(undefined)}
      >
        {t('lifecycle.deleteConfirm')}
      </Button>
      <Button size="sm" variant="quiet" onPress={() => setConfirming(false)}>
        {t('app.cancel')}
      </Button>
    </span>
  )
}
