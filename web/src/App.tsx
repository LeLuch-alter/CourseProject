import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './lib/auth'
import { DataProvider } from './lib/data'
import { Shell } from './components/Shell'
import { LoginPage } from './pages/Login'
import { ResetPasswordPage } from './pages/ResetPassword'
import { DashboardPage } from './pages/Dashboard'
import { DebtsPage } from './pages/Debts'
import { PaymentsPage } from './pages/Payments'
import { ReportsPage } from './pages/Reports'
import { CalendarPage } from './pages/Calendar'
import { FriendsPage } from './pages/Friends'
import { LoansPage } from './pages/Loans'
import { LoanPage } from './pages/Loan'
import { ProfilePage } from './pages/Profile'
import { SettingsPage } from './pages/Settings'
import { AddFriendPage } from './pages/AddFriend'
import { Mark } from './pages/Login'

function Splash() {
  return (
    <div className="h-full flex items-center justify-center">
      <div className="animate-float">
        <Mark size={40} />
      </div>
    </div>
  )
}

export default function App() {
  const { session, loading } = useAuth()
  if (loading) return <Splash />

  if (!session) {
    return (
      <Routes>
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="*" element={<LoginPage />} />
      </Routes>
    )
  }

  return (
    <DataProvider>
      <Routes>
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route element={<Shell />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/debts" element={<DebtsPage />} />
          <Route path="/payments" element={<PaymentsPage />} />
          <Route path="/reports" element={<ReportsPage />} />
          <Route path="/calendar" element={<CalendarPage />} />
          <Route path="/friends" element={<FriendsPage />} />
          <Route path="/friends/:id" element={<FriendsPage />} />
          <Route path="/friends/room/:roomId" element={<FriendsPage />} />
          <Route path="/add/:username" element={<AddFriendPage />} />
          <Route path="/loans" element={<LoansPage />} />
          <Route path="/loans/:id" element={<LoanPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </DataProvider>
  )
}
