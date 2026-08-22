import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ChevronRight } from 'lucide-react'
import { api } from '../lib/api'
import { Page, Failed, Loading, Empty } from '../components/Page'

type Center = {
  id: number
  name: string
  teacherCount: number
}

export function CentersPage() {
  const { t } = useTranslation()
  const centers = useQuery({
    queryKey: ['centers'],
    queryFn: () => api.get<Center[]>('/centers'),
  })

  return (
    <Page title={t('centers.title')}>
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
