import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Collapse, Drawer, Empty, Input, List, Modal, Select, Space, Spin, Tag, Typography, message } from 'antd'
import { fetchVersions } from '../api/version'
import { fetchTestReport, generateTestCases } from '../api/testcase'
import type { TestReport } from '../api/testcase'
import type { VersionSummary } from '../api/types'

interface Props {
  prdId: string
  currentVersionId: string | null
  onLocate: (blockId: string) => void
  onAccept: (versionId: string, generationId: string, issueIndex: number) => Promise<void>
}
const LABEL: Record<string, string> = { FUNCTIONAL: '功能', BOUNDARY: '边界', EXCEPTION: '异常', SECURITY: '权限与安全' }

export default function TestCasePanel({ prdId, currentVersionId, onLocate, onAccept }: Props) {
  const [versions, setVersions] = useState<VersionSummary[]>([])
  const [versionId, setVersionId] = useState<string | null>(currentVersionId)
  const [report, setReport] = useState<TestReport | null>(null)
  const [loading, setLoading] = useState(false)
  const [generating, setGenerating] = useState(false)
  const [open, setOpen] = useState(false)
  const [instruction, setInstruction] = useState('')
  const epoch = useRef(0)
  const active = useRef(versionId)
  active.current = versionId

  useEffect(() => {
    let alive = true
    fetchVersions(prdId).then(list => { if (alive) setVersions(list) }).catch(e => message.error(e.message))
    return () => { alive = false }
  }, [prdId, currentVersionId])
  useEffect(() => { if (currentVersionId) setVersionId(currentVersionId) }, [currentVersionId])
  useEffect(() => {
    const token = ++epoch.current
    setReport(null)
    if (!versionId) return
    setLoading(true)
    fetchTestReport(versionId).then(r => { if (token === epoch.current) setReport(r) })
      .catch(e => { if (token === epoch.current) message.error(e.message) })
      .finally(() => { if (token === epoch.current) setLoading(false) })
    return () => { epoch.current++ }
  }, [versionId])

  const generate = async () => {
    if (!versionId || generating) return
    const token = ++epoch.current
    setGenerating(true)
    try {
      const result = await generateTestCases(versionId, instruction)
      if (token === epoch.current) { setReport(result); setOpen(true); message.success('测试分析已生成并保存') }
    } catch (e) { message.error((e as Error).message) }
    finally { setGenerating(false) }
  }
  const accept = (index: number) => {
    if (!versionId || !report?.generationId) return
    const selectedVersion = versionId, generation = report.generationId
    const issue = report.testabilityIssues[index]
    Modal.confirm({
      title: '确认将验收标准插入来源章节之后',
      width: 640,
      content: <><Alert type="info" message="该内容由 AI 建议，确认数值与业务规则后再写入。不会修改原有固定版本。" />
        <pre className="artifact-preview">{`### 验收标准\n\n${issue.suggestedAcceptanceCriteria}`}</pre></>,
      okText: '确认并写入草稿', cancelText: '取消',
      onOk: async () => {
        await onAccept(selectedVersion, generation, index)
        if (active.current === selectedVersion) setReport(await fetchTestReport(selectedVersion))
      },
    })
  }
  return <div className="test-panel">
    <div className="panel-section-header">测试与验收</div>
    <Space direction="vertical" className="artifact-controls">
      <Select aria-label="测试来源版本" style={{ width: '100%' }} value={versionId} disabled={generating}
        placeholder="先创建固定版本" onChange={setVersionId}
        options={versions.map(v => ({ value: v.id, label: `v${v.versionNo} ${v.changeSummary}` }))} />
      <Typography.Text type="secondary">基于固定版本生成，不读取尚未保存的草稿。</Typography.Text>
      <Input.TextArea aria-label="测试生成要求" rows={3} placeholder="可选：重点覆盖权限、异常或边界场景" value={instruction}
        maxLength={4000}
        onChange={e => setInstruction(e.target.value)} disabled={generating} />
      <Button type="primary" disabled={!versionId || loading} loading={generating}
        onClick={() => {
          if (report?.generationId) Modal.confirm({ title: '重新生成测试分析？', content: '将替换该版本的测试用例与分析报告，已写入草稿的内容会保留。', okText: '重新生成', cancelText: '取消', onOk: generate })
          else void generate()
        }}>{report?.generationId ? '重新生成测试分析' : '生成测试用例'}</Button>
      <Button disabled={!report || (!report.cases.length && !report.testabilityIssues.length)} onClick={() => setOpen(true)}>
        查看结果{report ? `（${report.cases.length} 条用例 / ${report.testabilityIssues.length} 个问题）` : ''}
      </Button>
      {loading && <Spin />}
    </Space>
    <Drawer title={`测试与验收 · v${versions.find(v => v.id === versionId)?.versionNo ?? '—'}`} width={780} open={open} onClose={() => setOpen(false)}>
      {!report ? <Empty /> : <>
        <Tag>{report.profile ?? '历史用例'}</Tag>
        <Typography.Paragraph>{report.summary}</Typography.Paragraph>
        <Typography.Title level={5}>可测试性与验收建议</Typography.Title>
        <List dataSource={report.testabilityIssues} locale={{ emptyText: '未发现可测试性问题' }} renderItem={(issue, i) => <List.Item>
          <div style={{ width: '100%' }}><Typography.Text strong>{issue.issue}</Typography.Text>
            <blockquote>{issue.sourceQuote}</blockquote><pre className="artifact-preview">{issue.suggestedAcceptanceCriteria}</pre>
            <Space><Button size="small" onClick={() => { setOpen(false); onLocate(issue.sourceBlockId) }}>定位来源</Button>
              <Button size="small" type="primary" disabled={!issue.suggestedAcceptanceCriteria || report.appliedIssueIndexes?.includes(i)} onClick={() => accept(i)}>
                {report.appliedIssueIndexes?.includes(i) ? '已写入草稿' : '预览并写入验收标准'}</Button></Space>
          </div></List.Item>} />
        <Typography.Title level={5}>测试用例（{report.cases.length}）</Typography.Title>
        <Collapse items={report.cases.map((c, i) => ({ key: c.id, label: <><Tag>{LABEL[c.caseType] ?? c.caseType}</Tag>{i + 1}. {c.scenario}</>, children: <>
          <p>前置条件：{c.preconditions || '—'}</p><p>输入：{c.inputData || '—'}</p>
          <ol>{c.steps.map((s, j) => <li key={j}>{s}</li>)}</ol><p><strong>预期结果：</strong>{c.expectedResult}</p>
          <Button size="small" onClick={() => { setOpen(false); onLocate(c.sourceBlockId) }}>定位 PRD 来源</Button>
        </> }))} />
      </>}
    </Drawer>
  </div>
}
