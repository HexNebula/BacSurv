/**
 * Every write goes through here so a refusal is never silent.
 *
 * <p>The server answers a bad request with a sentence it has already written
 * in the caller's language — "the official minimum is two surveillants per
 * room" — so the interface shows that sentence rather than inventing its own
 * wording, which would drift from the rule the moment the rule changes.
 */

import { useMutation, useQueryClient, type QueryKey } from '@tanstack/react-query'
import { toast } from '../ui'
import { useTranslation } from 'react-i18next'
import { ApiError } from './api'

export function useApiMutation<TResult, TInput>(options: {
  run: (input: TInput) => Promise<TResult>
  /** Cache to drop once it worked, so the screen shows what the server now holds. */
  invalidate?: QueryKey
  /** Shown on success. Silence is right for edits the table itself makes obvious. */
  onDone?: (result: TResult, input: TInput) => string | void
}) {
  const queryClient = useQueryClient()
  const { t } = useTranslation()

  return useMutation({
    mutationFn: options.run,
    onSuccess: (result, input) => {
      if (options.invalidate) {
        void queryClient.invalidateQueries({ queryKey: options.invalidate })
      }
      const said = options.onDone?.(result, input)
      if (said) toast.ok(said)
    },
    onError: (error) => {
      toast.bad(
        error instanceof ApiError && error.message ? error.message : t('app.error'),
      )
    },
  })
}
