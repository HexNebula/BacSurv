import { Navigate, Route, Routes } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Hammer } from 'lucide-react'
import { AppShell } from './components/AppShell'
import { CenterPage } from './pages/CenterPage'
import { DashboardPage } from './pages/DashboardPage'
import { SetupPage } from './pages/SetupPage'
import { TeachersPage } from './pages/TeachersPage'
import { SchedulePage } from './pages/SchedulePage'
import { Page } from './components/Page'
import { Card, Empty } from './ui'
import { useWorkspace } from './context/Workspace'

/** A section named in the rail but not built yet — honest rather than absent. */
function NotBuilt({ title }: { title: string }) {
  const { t } = useTranslation()
  return (
    <Page title={title}>
      <Card>
        <Empty icon={<Hammer size={22} aria-hidden />} title={title}>
          {t('app.notBuilt')}
        </Empty>
      </Card>
    </Page>
  )
}

export default function App() {
  const { hasCenter, isLoading } = useWorkspace()

  // nothing in the rail means anything before the centre exists
  if (isLoading) return null
  if (!hasCenter) return <SetupPage />

  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<Navigate to="/sessions" replace />} />
        <Route path="/center" element={<CenterPage />} />
        {/* one centre, so its old addresses all mean the same screen */}
        <Route path="/centers" element={<Navigate to="/center" replace />} />
        <Route path="/centers/:id" element={<Navigate to="/center" replace />} />
        <Route path="/sessions" element={<DashboardPage />} />
        <Route path="/teachers" element={<TeachersPage />} />
        <Route path="/schedule" element={<SchedulePage />} />
        <Route path="/results" element={<NotBuilt title="Résultats" />} />
        <Route path="/statistics" element={<NotBuilt title="Statistiques" />} />
      </Route>
    </Routes>
  )
}
