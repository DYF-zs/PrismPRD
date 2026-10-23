import { useEffect, useState } from 'react'
import { Avatar } from 'antd'
import { AuditOutlined, FileTextOutlined, MessageOutlined, SettingOutlined, UserOutlined } from '@ant-design/icons'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { fetchMe } from '../api/prd'
import type { MeResponse } from '../api/types'
import { fetchModelSettings } from '../api/models'
import type { ModelConfiguration } from '../api/models'

export default function AppLayout() {
  const [me, setMe] = useState<MeResponse | null>(null)
  const [model, setModel] = useState<ModelConfiguration | null>(null)
  const location = useLocation()
  const inConversation = location.pathname.startsWith('/prds/') && new URLSearchParams(location.search).get('view') === 'chat'
  useEffect(() => { fetchMe().then(setMe).catch(() => {}) }, [])
  useEffect(() => {
    let alive = true
    let generation = 0
    const refresh = () => {
      const request = ++generation
      fetchModelSettings().then(settings => {
        if (alive && request === generation) setModel(settings.configurations.find(c => c.id === settings.activeId) ?? null)
      }).catch(() => { if (alive && request === generation) setModel(null) })
    }
    refresh()
    window.addEventListener('prism:model-settings-changed', refresh)
    return () => { alive = false; window.removeEventListener('prism:model-settings-changed', refresh) }
  }, [])
  return <div className="app-layout">
    <header className="app-navigation">
      <NavLink to="/" className="app-brand"><span className="brand-mark">P</span><strong>PrismPRD</strong></NavLink>
      <nav aria-label="主导航">
        <NavLink to="/" end className={({ isActive }) => isActive || inConversation ? 'active' : ''}><MessageOutlined /> AI 工作台</NavLink>
        <NavLink to="/prds" className={({ isActive }) => isActive && !inConversation ? 'active' : ''}><FileTextOutlined /> 文档库</NavLink>
        <NavLink to="/settings/review-rubrics"><AuditOutlined /> 评审标准</NavLink>
        <NavLink to="/settings/models"><SettingOutlined /> 模型设置</NavLink>
      </nav>
      <div className="app-model" title={model ? `当前模型：${model.provider}/${model.model}` : '请在模型设置中添加模型'}>{model?.model ?? '待配置模型'}</div>
      <div className="app-user"><Avatar size="small" icon={<UserOutlined />} /><span>{me?.displayName ?? '本地用户'}</span></div>
    </header>
    <main className="app-content"><Outlet /></main>
  </div>
}
