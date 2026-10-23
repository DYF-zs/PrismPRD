import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Card, Empty, Form, Input, Modal, Select, Spin, Tag, Typography, message } from 'antd'
import { CheckCircleFilled, PlusOutlined, RobotOutlined } from '@ant-design/icons'
import {
  activateModel, deleteModel, fetchModelSettings, notifyModelSettingsChanged,
  saveModelConfiguration, testModel,
} from '../api/models'
import type { ModelConfiguration, ModelInput, ModelProvider, ModelSettings, ModelTestResult } from '../api/models'

const PROVIDERS: Record<ModelProvider, { label: string; baseUrl: string; model: string }> = {
  deepseek: { label: 'DeepSeek', baseUrl: 'https://api.deepseek.com', model: 'deepseek-flash' },
  zhipuai: { label: '智谱 AI', baseUrl: 'https://open.bigmodel.cn/api/paas', model: 'glm-4.6' },
  openai: { label: 'OpenAI / 兼容接口', baseUrl: 'https://api.openai.com/v1', model: '' },
}
const blankSettings: ModelSettings = { configurations: [], activeId: null }

export default function ModelSettingsPage() {
  const [settings, setSettings] = useState<ModelSettings>(blankSettings)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState<ModelConfiguration | null | undefined>(undefined)
  const [busy, setBusy] = useState('')
  const [testResult, setTestResult] = useState<ModelTestResult | null>(null)
  const [form] = Form.useForm<ModelInput>()
  const pending = useRef(false)
  const mounted = useRef(true)
  const active = settings.configurations.find(c => c.id === settings.activeId)
  const load = useCallback(async () => {
    try {
      const next = await fetchModelSettings()
      if (mounted.current) { setSettings(next); setError('') }
    } catch (e) { if (mounted.current) setError((e as Error).message) }
    finally { if (mounted.current) setLoading(false) }
  }, [])
  useEffect(() => { mounted.current = true; void load(); return () => { mounted.current = false } }, [load])

  const run = async (action: string, task: () => Promise<void>) => {
    if (pending.current) return
    pending.current = true; setBusy(action)
    try { await task() }
    catch (e) { message.error((e as Error).message) }
    finally { pending.current = false; if (mounted.current) setBusy('') }
  }
  const openEditor = (configuration: ModelConfiguration | null) => {
    setTestResult(null); form.resetFields()
    form.setFieldsValue(configuration
      ? { name: configuration.name, provider: configuration.provider, model: configuration.model, baseUrl: configuration.baseUrl, apiKey: '', revision: configuration.revision }
      : { name: '', provider: 'deepseek', model: PROVIDERS.deepseek.model, baseUrl: PROVIDERS.deepseek.baseUrl, apiKey: '' })
    setEditing(configuration)
  }
  const closeEditor = () => { if (!pending.current) { form.resetFields(); setTestResult(null); setEditing(undefined) } }
  const inputFor = (c: ModelConfiguration): ModelInput => ({
    name: c.name, provider: c.provider, model: c.model, baseUrl: c.baseUrl, revision: c.revision,
  })
  const testSaved = (configuration: ModelConfiguration) => void run(configuration.id, async () => {
    const result = await testModel(configuration.id, inputFor(configuration))
    if (result.toolCalling) message.success(result.message)
    else Modal.warning({ title: '模型能力检查', content: result.message })
  })
  const save = async () => {
    let input: ModelInput
    try { input = await form.validateFields() } catch { return }
    void run('save', async () => {
      await saveModelConfiguration(editing?.id, input)
      form.resetFields(); setTestResult(null); setEditing(undefined)
      notifyModelSettingsChanged(); await load(); message.success('模型配置已保存')
    })
  }
  const testDraft = async () => {
    let input: ModelInput
    try { input = await form.validateFields() } catch { return }
    void run('test', async () => { setTestResult(null); setTestResult(await testModel(editing?.id, input)) })
  }

  return <div className="models-page"><div className="page-container">
    <div className="models-heading"><div><Typography.Title level={3}>模型设置</Typography.Title>
      <Typography.Paragraph type="secondary">统一管理对话、评审、正文修改和测试用例生成使用的 AI 模型。</Typography.Paragraph></div>
      <Button type="primary" icon={<PlusOutlined />} disabled={!!busy || loading || !!error} onClick={() => openEditor(null)}>添加模型</Button>
    </div>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => { setLoading(true); void load() }}>重试</Button>} />}
    {loading ? <div className="models-loading"><Spin /></div> : <>
      <div className="models-current">
        <RobotOutlined /><div><span className="models-eyebrow">当前启用</span>
          <strong>{active ? active.name : '请选择模型配置'}</strong>
          <span>{active ? `${PROVIDERS[active.provider].label} · ${active.model}` : '添加 API Key 和模型信息后，即可使用 AI 功能。'}</span>
        </div>
        <Typography.Text type="secondary">所有 AI 功能共用此配置，切换后对新任务生效。</Typography.Text>
      </div>
      {!settings.configurations.length ? <Card><Empty description="添加第一份模型配置"><Button type="primary" disabled={!!error} onClick={() => openEditor(null)}>添加模型</Button></Empty></Card>
        : <div className="models-grid">{settings.configurations.map(configuration => <Card key={configuration.id}
          className={configuration.active ? 'model-card model-card--active' : 'model-card'}>
          <div className="model-card-heading"><Typography.Title level={5}>{configuration.name}</Typography.Title>
            {configuration.active && <Tag icon={<CheckCircleFilled />} color="green">使用中</Tag>}</div>
          <div className="model-card-provider">{PROVIDERS[configuration.provider].label}</div>
          <div className="model-card-name">{configuration.model}</div>
          <div className="model-card-url">{configuration.baseUrl}</div>
          <div className="model-card-key">API Key 已保存</div>
          <div className="model-card-actions">
            <Button type={configuration.active ? 'default' : 'primary'} disabled={!!busy || configuration.active} onClick={() => void run(configuration.id, async () => {
              await activateModel(configuration.id); notifyModelSettingsChanged(); await load(); message.success('已启用模型')
            })}>{configuration.active ? '当前模型' : '启用'}</Button>
            <Button loading={busy === configuration.id} disabled={!!busy} onClick={() => testSaved(configuration)}>测试连接</Button>
            <Button disabled={!!busy} onClick={() => openEditor(configuration)}>编辑</Button>
            <Button danger type="text" disabled={!!busy} onClick={() => Modal.confirm({
              title: '删除模型配置？', content: `删除“${configuration.name}”及其 API Key，历史文档和评审报告保留。${configuration.active ? '启用其他配置后可继续发起 AI 任务。' : ''}`,
              okText: '删除', cancelText: '取消', okButtonProps: { danger: true },
              onOk: () => run(configuration.id, async () => { await deleteModel(configuration); await load(); notifyModelSettingsChanged() }),
            })}>删除</Button>
          </div>
        </Card>)}</div>}
      <div className="models-notes">API Key 在后端加密保存。连接测试会发送两条短请求，验证结构化输出与工具调用，并可能产生 API 费用。知识库向量模型由 Ollama 独立提供。</div>
    </>}
    <Modal title={editing ? '编辑模型' : '添加模型'} open={editing !== undefined} onCancel={closeEditor}
      closable={!busy} maskClosable={!busy} width={600} footer={[
        <Button key="cancel" disabled={!!busy} onClick={closeEditor}>取消</Button>,
        <Button key="test" loading={busy === 'test'} disabled={!!busy} onClick={() => void testDraft()}>测试连接</Button>,
        <Button key="save" type="primary" loading={busy === 'save'} disabled={!!busy} onClick={() => void save()}>保存配置</Button>,
      ]}>
      <Form form={form} layout="vertical" disabled={!!busy} onValuesChange={() => setTestResult(null)}>
        <Form.Item name="revision" hidden><Input /></Form.Item>
        <Form.Item name="name" label="配置名称" rules={[{ required: true, whitespace: true, message: '请输入配置名称' }, { max: 80 }]}><Input placeholder="例如：需求评审" maxLength={80} /></Form.Item>
        <Form.Item name="provider" label="服务平台" rules={[{ required: true }]}><Select options={Object.entries(PROVIDERS).map(([value,p]) => ({ value, label: p.label }))}
          onChange={(provider: ModelProvider) => { form.setFieldsValue({ baseUrl: PROVIDERS[provider].baseUrl, model: PROVIDERS[provider].model, apiKey: '' }); setTestResult(null) }} /></Form.Item>
        <Form.Item name="baseUrl" label="API 地址" extra="填写 API 根路径。OpenAI 兼容接口通常以 /v1 结尾；本机容器服务可使用 host.docker.internal。"
          rules={[{ required: true, whitespace: true, message: '请输入 API 地址' }, { max: 1024 }]}><Input placeholder="https://api.example.com/v1" maxLength={1024} /></Form.Item>
        <Form.Item name="model" label="模型名称" extra="填写平台提供的模型标识，具体可用名称以平台账户为准。"
          rules={[{ required: true, whitespace: true, message: '请输入模型名称' }, { max: 160 }]}><Input placeholder="模型标识" maxLength={160} /></Form.Item>
        <Form.Item name="apiKey" label="API Key" extra={editing ? '留空保留已保存的 Key；修改平台或 API 地址后须重新填写。' : '首次保存的模型会自动启用。'}
          rules={editing ? [{ max: 4096 }] : [{ required: true, whitespace: true, message: '请输入 API Key' }, { max: 4096 }]}>
          <Input.Password autoComplete="new-password" placeholder={editing ? '已保存，留空保留' : '填写平台 API Key'} maxLength={4096} />
        </Form.Item>
      </Form>
      {testResult && <Alert showIcon type={testResult.toolCalling ? 'success' : 'warning'} message={testResult.message} />}
    </Modal>
  </div></div>
}

