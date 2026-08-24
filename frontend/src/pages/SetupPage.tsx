import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { ArrowRight, Building2 } from 'lucide-react'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { LanguageSwitch } from '../components/LanguageSwitch'
import { Button, Card, TextField } from '../ui'

/**
 * The first thing an installation asks for: the name of the centre it is for.
 *
 * <p>Shown only once. There is no list of centres to choose from because an
 * administrator has one establishment — a choice offered here would be a choice
 * with a single answer, and an invitation to create a second one by mistake.
 *
 * <p>The language switch is on this screen too: it is the first thing somebody
 * who works in Arabic needs, and it would be unkind to make them find the rail
 * first.
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
    <div className="flex min-h-screen flex-col">
      <div className="flex justify-end p-5">
        <LanguageSwitch />
      </div>

      <div className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center px-6 pb-24">
        <div className="mb-7 text-center">
          <span
            className="mx-auto mb-5 flex size-14 items-center justify-center rounded-2xl bg-[var(--color-accent)] text-white shadow-[var(--shadow-card)]"
            aria-hidden
          >
            <Building2 size={24} />
          </span>
          <h1 className="text-[26px] font-semibold leading-tight tracking-[-0.02em]">
            {t('setup.title')}
          </h1>
          <p className="mx-auto mt-2.5 max-w-sm text-[13.5px] leading-relaxed text-[var(--color-quiet)]">
            {t('setup.explain')}
          </p>
        </div>

        <Card className="p-6">
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault()
              if (name.trim() !== '') create.mutate(undefined)
            }}
          >
            <TextField
              label={t('centers.name')}
              value={name}
              onChange={setName}
              placeholder={t('setup.placeholder')}
              autoFocus
            />
            <Button
              type="submit"
              className="w-full"
              isDisabled={name.trim() === ''}
              isPending={create.isPending}
            >
              {t('setup.start')}
              <ArrowRight size={16} className="rtl:rotate-180" aria-hidden />
            </Button>
          </form>
        </Card>
      </div>
    </div>
  )
}
