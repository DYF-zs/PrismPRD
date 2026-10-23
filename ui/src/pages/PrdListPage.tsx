import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { MouseEvent } from 'react'
import { RequestEpoch } from '../api/requestEpoch'
import {
  Button,
  Card,
  List,
  Input,
  Modal,
  Radio,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  FileTextOutlined,
  PlusOutlined,
  SearchOutlined,
  DeleteOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { Link, useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import { createPrd, deletePrd, fetchMe, fetchPrdList, fetchTemplates } from '../api/prd'
import ImportPrdButton from '../components/ImportPrdButton'
import type {
  CreatePrdRequest,
  CreationMode,
  PageResult,
  PrdDocumentSummary,
  PrdStatus,
  TemplateResponse,
} from '../api/types'

const STATUS_META: Record<PrdStatus, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  REVIEWING: { label: '评审中', color: 'processing' },
  NEED_CHANGES: { label: '待修改', color: 'warning' },
  APPROVED: { label: '已通过', color: 'success' },
  ARCHIVED: { label: '已归档', color: 'default' },
}

const PAGE_SIZE = 10

export default function PrdListPage() {
  const navigate = useNavigate()
  const [data, setData] = useState<PageResult<PrdDocumentSummary> | null>(null)
  const [loading, setLoading] = useState(false)
  const [query, setQuery] = useState('')
  const [searchText, setSearchText] = useState('')
  const [status, setStatus] = useState<string | undefined>()
  const [page, setPage] = useState(1)
  const [currentUserId, setCurrentUserId] = useState<string | null>(null)
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const deletingRef = useRef(false)

  const [createOpen, setCreateOpen] = useState(false)
  const [templates, setTemplates] = useState<TemplateResponse[]>([])
  const [mode, setMode] = useState<CreationMode>('BLANK')
  const [title, setTitle] = useState('')
  const [templateKey, setTemplateKey] = useState<string | undefined>()
  const [requirement, setRequirement] = useState('')
  const [creating, setCreating] = useState(false)
  const requests = useRef(new RequestEpoch())
  const creatingRef = useRef(false)

  const openDocumentRow = (event: MouseEvent<HTMLElement>, id: string) => {
    if (event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return
    if (event.target instanceof Element && event.target.closest('a, button, [role="button"]')) return
    if (deletingRef.current) return
    const selection = window.getSelection()
    if (selection && !selection.isCollapsed && event.currentTarget.contains(selection.anchorNode)) return
    navigate(`/prds/${id}`)
  }

  const load = useCallback(async () => {
    const token = requests.current.next()
    setLoading(true)
    try {
      const result = await fetchPrdList({ query, status, page, size: PAGE_SIZE })
      if (requests.current.isCurrent(token)) {
        const lastPage = Math.max(1, Math.ceil(result.total / PAGE_SIZE))
        if (page > lastPage) { setPage(lastPage); return }
        setData(result)
      }
    } catch (e) {
      if (requests.current.isCurrent(token)) message.error((e as Error).message)
    } finally {
      if (requests.current.isCurrent(token)) setLoading(false)
    }
  }, [query, status, page])
  const loadRef = useRef(load)
  loadRef.current = load

  useEffect(() => {
    load()
    return () => requests.current.invalidate()
  }, [load])

  useEffect(() => {
    fetchTemplates().then(setTemplates).catch(() => {})
    let alive = true
    fetchMe().then(me => { if (alive) setCurrentUserId(me.userId) }).catch(() => {})
    return () => { alive = false }
  }, [])

  const confirmDeleteDocument = useCallback((record: PrdDocumentSummary) => {
    if (record.ownerId !== currentUserId || deletingRef.current) return
    Modal.confirm({
      title: `永久删除「${record.title}」？`,
      content: '文档及全部版本、评审、对话、修改记录和测试用例将从数据库删除，无法恢复。共享知识库保留。',
      okText: '永久删除', cancelText: '取消', okButtonProps: { danger: true },
      onOk: async () => {
        if (deletingRef.current) return
        deletingRef.current = true
        setDeletingId(record.id)
        try {
          await deletePrd(record.id)
          message.success('文档已删除')
          await loadRef.current()
        } catch (e) {
          message.error((e as Error).message)
          throw e
        } finally {
          deletingRef.current = false
          setDeletingId(null)
        }
      },
    })
  }, [currentUserId])

  const renderDelete = useCallback((record: PrdDocumentSummary) => record.ownerId === currentUserId ? (
    <Button size="small" type="text" danger icon={<DeleteOutlined />} aria-label={`删除文档 ${record.title}`}
      loading={deletingId === record.id} disabled={deletingId !== null}
      onClick={event => { event.stopPropagation(); confirmDeleteDocument(record) }}>删除</Button>
  ) : null, [currentUserId, deletingId, confirmDeleteDocument])

  const openCreate = () => {
    setMode('BLANK')
    setTitle('')
    setTemplateKey(undefined)
    setRequirement('')
    setCreateOpen(true)
  }

  const doCreate = async () => {
    if (creatingRef.current) return
    if (!title.trim()) {
      message.warning('请输入 PRD 标题')
      return
    }
    if (mode === 'TEMPLATE' && !templateKey) {
      message.warning('请选择模板')
      return
    }
    if (mode === 'AI_DRAFT' && !requirement.trim()) {
      message.warning('请输入需求描述')
      return
    }
    const body: CreatePrdRequest = { creationMode: mode, title: title.trim() }
    if (mode === 'TEMPLATE') body.templateKey = templateKey
    if (mode === 'AI_DRAFT') body.initialRequirement = requirement.trim()
    creatingRef.current = true
    setCreating(true)
    try {
      const resp = await createPrd(body)
      message.success('PRD 创建成功')
      setCreateOpen(false)
      navigate(`/prds/${resp.prdId}${mode === 'AI_DRAFT' ? '?view=chat' : ''}`,
        mode === 'AI_DRAFT' ? { state: { startSessionId: resp.defaultSessionId } } : undefined)
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      creatingRef.current = false
      setCreating(false)
    }
  }

  const columns: ColumnsType<PrdDocumentSummary> = useMemo(
    () => [
      {
        title: '标题',
        dataIndex: 'title',
        render: (_, record) => (
          <Space>
            <FileTextOutlined style={{ color: '#16a34a' }} />
            <Link to={`/prds/${record.id}`}>{record.title}</Link>
          </Space>
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (s: PrdStatus) => {
          const meta = STATUS_META[s] ?? { label: s, color: 'default' }
          return <Tag color={meta.color}>{meta.label}</Tag>
        },
      },
      {
        title: '当前版本',
        dataIndex: 'currentVersionNo',
        width: 100,
        render: (v: number | null) => (v ? `v${v}` : '—'),
      },
      {
        title: '评审得分',
        dataIndex: 'currentScore',
        width: 100,
        render: (s: number | null) => (s != null ? s.toFixed(1) : '—'),
      },
      {
        title: '负责人',
        dataIndex: 'ownerName',
        width: 110,
      },
      {
        title: '更新时间',
        dataIndex: 'updatedAt',
        width: 150,
        render: (t: string) => dayjs(t).format('YYYY-MM-DD HH:mm'),
      },
      {
        title: '操作',
        key: 'actions',
        width: 90,
        align: 'right',
        render: (_, record) => renderDelete(record),
      },
    ],
    [renderDelete],
  )

  return (
    <div className="library-page">
      <div className="page-container">
        <Typography.Title level={3}>文档库</Typography.Title>
        <Card>
          <div className="library-toolbar">
            <Input
              allowClear
              prefix={<SearchOutlined style={{ color: '#9ca3af' }} />}
              placeholder="搜索 PRD 标题"
              value={searchText}
              onChange={(e) => {
                setSearchText(e.target.value)
                if (!e.target.value) { setPage(1); setQuery('') }
              }}
              onPressEnter={() => {
                setPage(1)
                setQuery(searchText.trim())
              }}
              className="library-search"
              aria-label="搜索 PRD 标题"
            />
            <Select
              allowClear
              placeholder="全部状态"
              value={status}
              onChange={(v) => {
                setPage(1)
                setStatus(v)
              }}
              options={Object.entries(STATUS_META).map(([value, meta]) => ({
                value,
                label: meta.label,
              }))}
              className="library-status"
              aria-label="筛选文档状态"
            />
            <div className="library-toolbar-spacer" />
            <ImportPrdButton />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建 PRD
            </Button>
          </div>
          <Table<PrdDocumentSummary>
            className="library-table"
            rowKey="id"
            rowClassName="library-document-row"
            onRow={record => ({ onClick: event => openDocumentRow(event, record.id) })}
            loading={loading}
            columns={columns}
            dataSource={data?.items ?? []}
            scroll={{ x: 900 }}
            pagination={{
              current: page,
              pageSize: PAGE_SIZE,
              total: data?.total ?? 0,
              showSizeChanger: false,
              onChange: setPage,
            }}
          />
          <List<PrdDocumentSummary>
            className="library-cards"
            loading={loading}
            dataSource={data?.items ?? []}
            locale={{ emptyText: '暂无文档' }}
            pagination={{ current: page, pageSize: PAGE_SIZE, total: data?.total ?? 0, showSizeChanger: false, onChange: setPage }}
            renderItem={record => <List.Item>
              <article className="library-document" onClick={event => openDocumentRow(event, record.id)}>
                <Link className="library-document-title" to={`/prds/${record.id}`}>
                  <FileTextOutlined />{record.title}
                </Link>
                <div className="metadata-row">
                  <Tag color={STATUS_META[record.status]?.color}>{STATUS_META[record.status]?.label ?? record.status}</Tag>
                  <span>{record.currentVersionNo ? `v${record.currentVersionNo}` : '草稿'}</span>
                  {record.currentScore != null && <span>评审 {record.currentScore.toFixed(1)} 分</span>}
                </div>
                <div className="library-document-footer">
                  <div className="library-document-meta"><span>{record.ownerName}</span><time>{dayjs(record.updatedAt).format('YYYY-MM-DD HH:mm')}</time></div>
                  {renderDelete(record)}
                </div>
              </article>
            </List.Item>}
          />
        </Card>
      </div>

      <Modal
        title="新建 PRD"
        open={createOpen}
        onOk={doCreate}
        onCancel={() => setCreateOpen(false)}
        confirmLoading={creating}
        okText="创建"
        cancelText="取消"
        width={560}
      >
        <Space direction="vertical" size={16} style={{ width: '100%', marginTop: 12 }}>
          <div>
            <Typography.Text strong>创建方式</Typography.Text>
            <Radio.Group
              value={mode}
              onChange={(e) => setMode(e.target.value)}
              style={{ display: 'block', marginTop: 8 }}
            >
              <Space direction="vertical">
                <Radio value="BLANK">空白文档</Radio>
                <Radio value="TEMPLATE">从模板创建</Radio>
                <Radio value="AI_DRAFT">AI 起草（描述需求，AI 生成初稿）</Radio>
              </Space>
            </Radio.Group>
          </div>
          {mode === 'TEMPLATE' && (
            <div>
              <Typography.Text strong>选择模板</Typography.Text>
              <Select
                placeholder="选择内置模板"
                value={templateKey}
                onChange={setTemplateKey}
                style={{ width: '100%', marginTop: 8 }}
                options={templates.map((t) => ({
                  value: t.key,
                  label: `${t.name} — ${t.description}`,
                }))}
              />
            </div>
          )}
          <div>
            <Typography.Text strong>标题</Typography.Text>
            <Input
              placeholder="例如：电商订单退款流程 PRD"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={255}
              style={{ marginTop: 8 }}
            />
          </div>
          {mode === 'AI_DRAFT' && (
            <div>
              <Typography.Text strong>需求描述</Typography.Text>
              <Input.TextArea
                rows={4}
                placeholder="用一段话描述你的产品需求，AI 将生成 PRD 初稿"
                value={requirement}
                onChange={(e) => setRequirement(e.target.value)}
                maxLength={4000}
                style={{ marginTop: 8 }}
              />
            </div>
          )}
        </Space>
      </Modal>
    </div>
  )
}
