import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { ChevronRight, Plus } from 'lucide-react'
import { Button, Input, Label, Modal, TextField } from '@heroui/react'
import { api } from '../lib/api'
import { useApiMutation } from '../lib/mutation'
import { Page, Failed, Loading, Empty } from '../components/Page'

type Center = {
  id: number
  name: string
  teacherCount: number
}

/**
 * A new centre is a name and nothing else — rooms, teachers and sessions are
 * set up on its own screen, so the administrator lands there rather than back
 * on a list with one more line in it.
 */
function NewCenter() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')

  const create = useApiMutation({
    run: () => api.post<Center>('/centers', { name }),
    invalidate: ['centers'],
    onDone: (created) => {
      setOpen(false)
      setName('')
      void navigate(`/centers/${created.id}`)
    },
  })

  return (
    <>
      <Button size="sm" onPress={() => setOpen(true)}>
        <Plus size={15} aria-hidden />
        {t('centers.create')}
      </Button>

      <Modal isOpen={open} onOpenChange={setOpen}>
        <Modal.Backdrop>
          <Modal.Container>
            <Modal.Dialog>
              <Modal.Header>
                <Modal.Heading>{t('centers.create')}</Modal.Heading>
              </Modal.Header>
              <Modal.Body>
                <form
                  id="new-center"
                  onSubmit={(event) => {
                    event.preventDefault()
                    create.mutate(undefined)
                  }}
                >
                  <TextField value={name} onChange={setName} fullWidth autoFocus>
                    <Label>{t('centers.name')}</Label>
                    <Input />
                  </TextField>
                </form>
              </Modal.Body>
              <Modal.Footer>
                <Button variant="ghost" onPress={() => setOpen(false)}>
                  {t('app.cancel')}
                </Button>
                <Button type="submit" form="new-center" isPending={create.isPending}>
                  {t('app.save')}
                </Button>
              </Modal.Footer>
            </Modal.Dialog>
          </Modal.Container>
        </Modal.Backdrop>
      </Modal>
    </>
  )
}

export function CentersPage() {
  const { t } = useTranslation()
  const centers = useQuery({
    queryKey: ['centers'],
    queryFn: () => api.get<Center[]>('/centers'),
  })

  return (
    <Page title={t('centers.title')} actions={<NewCenter />}>
      {centers.isPending && <Loading />}
      {centers.isError && (
        <Failed error={centers.error as Error} onRetry={() => void centers.refetch()} />
      )}

      {centers.isSuccess &&
        (centers.data.length === 0 ? (
          <Empty>{t('centers.empty')}</Empty>
        ) : (
          <ul className="divide-y divide-neutral-200 overflow-hidden rounded-xl border border-neutral-200 bg-white">
            {centers.data.map((center) => (
              <li key={center.id}>
                <Link
                  to={`/centers/${center.id}`}
                  className="flex items-center justify-between gap-4 px-4 py-3 transition-colors hover:bg-neutral-50"
                >
                  <span className="min-w-0">
                    <span className="block truncate text-sm font-medium">{center.name}</span>
                    <span className="mt-0.5 block text-xs text-neutral-500">
                      <span className="numeric">{center.teacherCount}</span> {t('centers.teachers')}
                    </span>
                  </span>
                  {/* rtl:rotate-180 so the chevron points the way the page reads */}
                  <ChevronRight
                    size={16}
                    className="shrink-0 text-neutral-400 rtl:rotate-180"
                    aria-hidden
                  />
                </Link>
              </li>
            ))}
          </ul>
        ))}
    </Page>
  )
}
