import { Collapse, Tag, Typography } from 'antd'
import type { RubricDetail } from '../../api/rubric'
import { severityLabels } from './rubricRules'

export default function RubricViewer({ rubric }: { rubric: RubricDetail }) {
  return <div className="rubric-viewer">
    <div className="rubric-heading">
      <Typography.Title level={4}>{rubric.name}</Typography.Title>
      <div className="metadata-row"><Tag>v{rubric.version}</Tag><Tag color={rubric.builtin ? 'blue' : undefined}>{rubric.builtin ? '内置 · 只读' : '已发布 · 只读'}</Tag><span>{rubric.dimensions.length} 个维度</span></div>
    </div>
    {rubric.description && <Typography.Paragraph type="secondary">{rubric.description}</Typography.Paragraph>}
    <Typography.Paragraph type="secondary">综合分按适用维度等权平均计算，不适用维度不计分；就绪度由系统规则判定。</Typography.Paragraph>
    <Collapse items={rubric.dimensions.map(d => ({ key: d.dimensionKey, label: d.name, children: <>
      <Typography.Paragraph type="secondary" className="rubric-dimension-key">维度标识：{d.dimensionKey}</Typography.Paragraph>
      <Typography.Paragraph>{d.description}</Typography.Paragraph>
      <Typography.Text strong>检查项</Typography.Text><ul>{d.checklist.map((text, i) => <li key={i}>{text}</li>)}</ul>
      <Typography.Text strong>评分规则</Typography.Text><ul>{d.scoreAnchor.map((band, i) => <li key={i}>{band.min}–{band.max} 分：{band.description}</li>)}</ul>
      <Typography.Text strong>严重程度规则</Typography.Text><ul>{Object.entries(severityLabels).map(([key, label]) => <li key={key}>{label}：{d.severityAnchor[key]}</li>)}</ul>
      <Typography.Paragraph><Typography.Text strong>适用条件：</Typography.Text>{d.applicabilityRule || '按维度要求判断适用性'}</Typography.Paragraph>
    </> }))} />
  </div>
}
