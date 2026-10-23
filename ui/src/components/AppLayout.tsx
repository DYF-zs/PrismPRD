import { useEffect, useState } from 'react'
import { Avatar } from 'antd'
import { AuditOutlined, FileTextOutlined, MessageOutlined, UserOutlined } from '@ant-design/icons'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { fetchMe } from '../api/prd'
import type { MeResponse } from '../api/types'

export default function AppLayout() {
  const [me, setMe] = useState<MeResponse | null>(null)
  const location = useLocation()
  const inConversation = location.pathname.startsWith('/prds/') && new URLSearchParams(location.search).get('view') === 'chat'
  useEffect(() => { fetchMe().then(setMe).catch(() => {}) }, [])
  return <div className="app-layout">
    <header className="app-navigation">
      <NavLink to="/" className="app-brand"><span className="brand-mark">P</span><strong>PrismPRD</strong></NavLink>
      <nav aria-label="主导航">
        <NavLink to="/" end className={({ isActive }) => isActive || inConversation ? 'active' : ''}><MessageOutlined /> AI 工作台</NavLink>
        <NavLink to="/prds" className={({ isActive }) => isActive && !inConversation ? 'active' : ''}><FileTextOutlined /> 文档库</NavLink>
        <NavLink to="/settings/review-rubrics"><AuditOutlined /> 评审标准</NavLink>
      </nav>
      <div className="app-user"><Avatar size="small" icon={<UserOutlined />} /><span>{me?.displayName ?? '本地用户'}</span></div>
    </header>
    <main className="app-content"><Outlet /></main>
  </div>
}
