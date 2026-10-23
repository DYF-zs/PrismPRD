import { Button, Collapse, Input, InputNumber, Popconfirm, Space, Typography } from 'antd'
import type { RubricDimension } from '../../api/rubric'
import { severityLabels } from './rubricRules'

export default function RubricDimensionEditor({ dimensions, onChange, disabled = false }: {
  dimensions: RubricDimension[]; onChange: (value: RubricDimension[]) => void; disabled?: boolean
}) {
  const update = (index: number, value: Partial<RubricDimension>) => onChange(dimensions.map((item, i) => i === index ? { ...item, ...value } : item))
  const move = (index: number, offset: number) => {
    const next = [...dimensions]; [next[index], next[index + offset]] = [next[index + offset], next[index]]; onChange(next)
  }
  return <Collapse defaultActiveKey={['0']} items={dimensions.map((d, index) => ({ key: String(index), label: `${index + 1}. ${d.name || '未命名维度'} · ${d.dimensionKey}`,
    children: <Space direction="vertical" style={{ width: '100%' }}>
      <Space wrap>
        <Button size="small" disabled={disabled || index === 0} onClick={() => move(index, -1)}>上移维度</Button>
        <Button size="small" disabled={disabled || index === dimensions.length - 1} onClick={() => move(index, 1)}>下移维度</Button>
        <Popconfirm title="移除此维度？" onConfirm={() => onChange(dimensions.filter((_, i) => i !== index))}><Button size="small" danger>移除维度</Button></Popconfirm>
      </Space>
      <Typography.Text strong>维度名称</Typography.Text>
      <Input aria-label={`维度${index + 1}名称`} maxLength={128} value={d.name} onChange={e => update(index, { name: e.target.value })} />
      <Typography.Text strong>稳定标识（大写英文、数字、下划线）</Typography.Text>
      <Input aria-label={`维度${index + 1}标识`} maxLength={64} value={d.dimensionKey} onChange={e => update(index, { dimensionKey: e.target.value })} />
      <Typography.Text strong>维度说明</Typography.Text>
      <Input.TextArea aria-label={`维度${index + 1}说明`} rows={2} maxLength={4000} value={d.description} onChange={e => update(index, { description: e.target.value })} />
      <Typography.Text strong>检查项</Typography.Text>
      {d.checklist.map((text, i) => <Space.Compact key={`checklist-${i}`} style={{ width: '100%' }}>
        <Input aria-label={`维度${index + 1}检查项${i + 1}`} maxLength={500} value={text} onChange={e => update(index, { checklist: d.checklist.map((item, j) => j === i ? e.target.value : item) })} />
        <Button aria-label={`删除检查项${i + 1}`} onClick={() => update(index, { checklist: d.checklist.filter((_, j) => j !== i) })}>删除</Button>
      </Space.Compact>)}
      <Button size="small" disabled={disabled || d.checklist.length >= 50} onClick={() => update(index, { checklist: [...d.checklist, ''] })}>添加检查项</Button>
      <Typography.Text strong>评分规则（0–10 分，完整覆盖，区间不可重叠）</Typography.Text>
      {d.scoreAnchor.map((band, i) => <div key={`score-${i}`} style={{ padding: 8, background: '#f6faf7', width: '100%', boxSizing: 'border-box' }}>
        <Space wrap>
          <InputNumber aria-label={`维度${index + 1}区间${i + 1}起始分`} min={0} max={10} step={0.1} value={band.min} onChange={value => update(index, { scoreAnchor: d.scoreAnchor.map((b, j) => j === i ? { ...b, min: value } : b) })} />
          <span>至</span><InputNumber aria-label={`维度${index + 1}区间${i + 1}结束分`} min={0} max={10} step={0.1} value={band.max} onChange={value => update(index, { scoreAnchor: d.scoreAnchor.map((b, j) => j === i ? { ...b, max: value } : b) })} />
          <Button size="small" onClick={() => update(index, { scoreAnchor: d.scoreAnchor.filter((_, j) => j !== i) })}>删除区间</Button>
        </Space>
        <Input.TextArea aria-label={`维度${index + 1}区间${i + 1}判定说明`} rows={2} maxLength={1000} value={band.description} style={{ marginTop: 8 }}
          onChange={e => update(index, { scoreAnchor: d.scoreAnchor.map((b, j) => j === i ? { ...b, description: e.target.value } : b) })} />
      </div>)}
      <Button size="small" disabled={disabled || d.scoreAnchor.length >= 20} onClick={() => update(index, { scoreAnchor: [...d.scoreAnchor, { min: null, max: null, description: '' }] })}>添加评分区间</Button>
      <Typography.Text strong>严重程度规则</Typography.Text>
      {Object.entries(severityLabels).map(([key, label]) => <div key={key} style={{ width: '100%' }}><Typography.Text>{label}（{key}）</Typography.Text>
        <Input.TextArea aria-label={`维度${index + 1}${label}规则`} rows={2} maxLength={1000} value={d.severityAnchor[key] ?? ''}
          onChange={e => update(index, { severityAnchor: { ...d.severityAnchor, [key]: e.target.value } })} />
      </div>)}
      <Typography.Text strong>适用条件</Typography.Text>
      <Input.TextArea aria-label={`维度${index + 1}适用条件`} rows={2} maxLength={4000} placeholder="例如：涉及资金交易时适用" value={d.applicabilityRule ?? ''} onChange={e => update(index, { applicabilityRule: e.target.value })} />
    </Space>,
  }))} />
}
