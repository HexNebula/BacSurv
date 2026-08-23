import { Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { CentersPage } from './pages/CentersPage'
import { CenterPage } from './pages/CenterPage'
import { TeachersPage } from './pages/TeachersPage'
import { Page, Empty } from './components/Page'

/** A section named in the rail but not built yet — honest rather than absent. */
function NotBuilt({ title }: { title: string }) {
  return (
    <Page title={title}>
      <Empty>—</Empty>
    </Page>
  )
}

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Navigate to="/centers" replace />} />
        <Route path="/centers" element={<CentersPage />} />
        <Route path="/centers/:id" element={<CenterPage />} />
        <Route path="/sessions" element={<NotBuilt title="Sessions" />} />
        <Route path="/teachers" element={<TeachersPage />} />
        <Route path="/schedule" element={<NotBuilt title="Planning" />} />
        <Route path="/results" element={<NotBuilt title="Résultats" />} />
        <Route path="/statistics" element={<NotBuilt title="Statistiques" />} />
      </Route>
    </Routes>
  )
}
