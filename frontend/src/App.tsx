import { Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { CenterPage } from './pages/CenterPage'
import { SetupPage } from './pages/SetupPage'
import { TeachersPage } from './pages/TeachersPage'
import { SchedulePage } from './pages/SchedulePage'
import { SessionsPage } from './pages/SessionsPage'
import { Page, Empty } from './components/Page'
import { useWorkspace } from './context/Workspace'

/** A section named in the rail but not built yet — honest rather than absent. */
function NotBuilt({ title }: { title: string }) {
  return (
    <Page title={title}>
      <Empty>—</Empty>
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
      <Route element={<Layout />}>
        <Route index element={<Navigate to="/sessions" replace />} />
        <Route path="/center" element={<CenterPage />} />
        {/* one centre, so its old addresses all mean the same screen */}
        <Route path="/centers" element={<Navigate to="/center" replace />} />
        <Route path="/centers/:id" element={<Navigate to="/center" replace />} />
        <Route path="/sessions" element={<SessionsPage />} />
        <Route path="/teachers" element={<TeachersPage />} />
        <Route path="/schedule" element={<SchedulePage />} />
        <Route path="/results" element={<NotBuilt title="Résultats" />} />
        <Route path="/statistics" element={<NotBuilt title="Statistiques" />} />
      </Route>
    </Routes>
  )
}
