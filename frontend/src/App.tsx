import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { CenterPage } from './pages/CenterPage'
import { DashboardPage } from './pages/DashboardPage'
import { ResultsPage } from './pages/ResultsPage'
import { RoomsPage } from './pages/RoomsPage'
import { SetupPage } from './pages/SetupPage'
import { StreamsPage } from './pages/StreamsPage'
import { StatisticsPage } from './pages/StatisticsPage'
import { SubjectsPage } from './pages/SubjectsPage'
import { TeachersPage } from './pages/TeachersPage'
import { SchedulePage } from './pages/SchedulePage'
import { useWorkspace } from './context/Workspace'

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
        <Route path="/rooms" element={<RoomsPage />} />
        <Route path="/subjects" element={<SubjectsPage />} />
        <Route path="/streams" element={<StreamsPage />} />
        <Route path="/teachers" element={<TeachersPage />} />
        <Route path="/schedule" element={<SchedulePage />} />
        <Route path="/results" element={<ResultsPage />} />
        <Route path="/statistics" element={<StatisticsPage />} />
      </Route>
    </Routes>
  )
}
