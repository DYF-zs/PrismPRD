import { createBrowserRouter, Navigate, RouterProvider, useParams } from 'react-router-dom'
import PrdListPage from './pages/PrdListPage'
import PrdWorkspacePage from './pages/PrdWorkspacePage'
import RubricManagerPage from './pages/RubricManagerPage'
import AppLayout from './components/AppLayout'
import WorkbenchPage from './pages/WorkbenchPage'
import ModelSettingsPage from './pages/ModelSettingsPage'

function WorkspaceRoute() {
  const { prdId } = useParams()
  return <PrdWorkspacePage key={prdId} />
}

const router = createBrowserRouter([
  { element: <AppLayout />, children: [
    { path: '/', element: <WorkbenchPage /> },
    { path: '/prds', element: <PrdListPage /> },
    { path: '/settings/review-rubrics', element: <RubricManagerPage /> },
    { path: '/settings/models', element: <ModelSettingsPage /> },
    { path: '/prds/:prdId', element: <WorkspaceRoute /> },
  ] },
  { path: '*', element: <Navigate to="/" replace /> },
])

export default function App() {
  return <RouterProvider router={router} />
}
