import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { Building2 } from 'lucide-react'
import { Button, Input, Label, TextField } from '@heroui/react'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'

/**
 * The first thing an installation asks for: the name of the centre it is for.
 *
 * <p>Shown only once. There is no list of centres to choose from because an
 * administrator has one establishment — a choice offered here would be a
 * choice with a single answer, and an invitation to create a second one by
 * mistake.
 */
export function SetupPage() {
  const { t } = useTranslation()
  const [name, setName] = useState('')
  const queryClient = useQueryClient()

  const create = useApiMutation({
    run: () => api.post<{ id: number }>('/centers', { name: name.trim() }),
    invalidate: ['centers'],
    onDone: () => {
      void queryClient.invalidateQueries()
      return t('setup.created')
    },
  })

  return (
    <div className="mx-auto flex max-w-md flex-col items-center px-10 py-24 text-center">
      <span
        className="mb-5 flex size-11 items-center justify-center rounded-md bg-[var(--color-brand)]/10"
        aria-hidden
      >
        <Building2 size={20} className="text-[var(--color-brand)]" />
      </span>

      <h1 className="text-[22px] font-semibold leading-tight tracking-[-0.01em]">
        {t('setup.title')}
      </h1>
      <p className="mt-2 text-[13px] leading-relaxed text-[var(--color-quiet)]">
        {t('setup.explain')}
      </p>

      <form
        className="mt-7 w-full space-y-3 text-start"
        onSubmit={(event) => {
          event.preventDefault()
          if (name.trim() !== '') create.mutate(undefined)
        }}
      >
        <TextField value={name} onChange={setName} fullWidth autoFocus>
          <Label>{t('centers.name')}</Label>
          <Input placeholder={t('setup.placeholder')} />
        </TextField>
        <Button type="submit" fullWidth isPending={create.isPending}>
          {t('setup.start')}
        </Button>
      </form>
    </div>
  )
}
