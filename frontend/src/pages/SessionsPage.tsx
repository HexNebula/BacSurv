import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useWorkspace } from '../context/Workspace'
import { Page, Empty } from '../components/Page'
import { Readiness } from '../components/Readiness'

/**
 * Where the chosen session stands.
 *
 * <p>The one screen that answers "what do I do next?", which is the question
 * an administrator actually arrives with. Everything else in the rail is a
 * place to do one particular thing; this says which of them to open.
 */
export function SessionsPage() {
  const { t } = useTranslation()
  const { session, center, centerId, sessionsHere, isLoading } = useWorkspace()

  if (isLoading) return <Page title={t('nav.sessions')}>{null}</Page>

  if (!center) {
    return (
      <Page title={t('nav.sessions')}>
        <div className="rounded-md border border-[var(--color-hairline)] bg-white">
          <Empty>{t('sessionsPage.noCenter')}</Empty>
        </div>
      </Page>
    )
  }

  if (sessionsHere.length === 0 || !session) {
    return (
      <Page title={t('nav.sessions')} subtitle={center.name}>
        <div className="rounded-md border border-[var(--color-hairline)] bg-white">
          <Empty
            action={
              <Link
                to={`/centers/${centerId}`}
                className="rounded-md bg-[var(--color-brand)] px-3 py-1.5 text-[12px] font-medium text-white"
              >
                {t('sessions.create')}
              </Link>
            }
          >
            {t('sessionsPage.noSession')}
          </Empty>
        </div>
      </Page>
    )
  }

  return (
    <Page title={session.reference} subtitle={center.name}>
      <Readiness />
    </Page>
  )
}
