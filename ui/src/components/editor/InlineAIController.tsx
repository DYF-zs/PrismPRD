import { useEffect, useRef } from 'react'
import { Button, Input, Space, Tag, Tooltip } from 'antd'
import type { InputRef } from 'antd'
import {
  CheckOutlined,
  CloseOutlined,
  ExperimentOutlined,
  ReloadOutlined,
  SendOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import type { UseInlineAI } from './useInlineAI'

const QUICK_INSTRUCTIONS = ['改写得更专业', '精简这段', '扩写补充细节', '转为要点列表']

/** Inline AI 气泡与「Ask AI」选区按钮，绝对定位于编辑器滚动容器内 */
export default function InlineAIController({ ai }: { ai: UseInlineAI }) {
  const inputRef = useRef<InputRef>(null)

  useEffect(() => {
    if (ai.mode === 'compose') {
      // 气泡出现后聚焦指令输入框
      setTimeout(() => inputRef.current?.focus(), 30)
    }
  }, [ai.mode])

  const composing = ai.mode === 'compose'
  const generating = ai.mode === 'generating' || ai.mode === 'applying'
  const previewing = ai.mode === 'preview'

  return (
    <>
      {ai.mode === 'idle' && ai.askButtonRect && (
        <button
          type="button"
          className="inlineai-ask-btn"
          style={{ top: ai.askButtonRect.top, left: ai.askButtonRect.left }}
          onMouseDown={(e) => e.preventDefault()}
          onClick={ai.open}
        >
          <ThunderboltOutlined /> Ask AI
        </button>
      )}

      {ai.mode !== 'idle' && ai.anchorRect && (
        <div
          className="inlineai-popover"
          style={{ top: ai.anchorRect.top, left: ai.anchorRect.left }}
          data-inlineai-mode={ai.mode}
        >
          {composing && (
            <>
              <div className="inlineai-popover-title">
                <ExperimentOutlined style={{ color: '#16a34a' }} />
                <span>Inline AI</span>
                <Button type="text" size="small" icon={<CloseOutlined />} onClick={ai.close} />
              </div>
              <Input
                ref={inputRef}
                className="inlineai-instruction-input"
                placeholder="输入修改指令，如：改写得更专业 / 精简 / 补充异常流程…"
                value={ai.instruction}
                onChange={(e) => ai.setInstruction(e.target.value)}
                onPressEnter={() => void ai.submit()}
                onKeyDown={(e) => {
                  if (e.key === 'Escape') {
                    e.stopPropagation()
                    ai.close()
                  }
                }}
                maxLength={200}
              />
              <div className="inlineai-quick-list">
                {QUICK_INSTRUCTIONS.map((q) => (
                  <Tag key={q} className="inlineai-quick-tag" onClick={() => ai.setInstruction(q)}>
                    {q}
                  </Tag>
                ))}
              </div>
              <div className="inlineai-popover-footer">
                <span className="inlineai-hint">Enter 生成 · Esc 取消</span>
                <Button
                  type="primary"
                  size="small"
                  icon={<SendOutlined />}
                  disabled={!ai.instruction.trim()}
                  onClick={() => void ai.submit()}
                >
                  生成
                </Button>
              </div>
            </>
          )}

          {generating && (
            <div className="inlineai-generating">
              <span className="inlineai-spinner" />
              {ai.mode === 'applying' ? '正在保存 AI 修改…' : 'AI 正在生成修改建议…'}
            </div>
          )}

          {previewing && (
            <>
              <div className="inlineai-popover-title">
                <ExperimentOutlined style={{ color: '#16a34a' }} />
                <span>AI 修改建议</span>
                <Tag color="green" style={{ marginLeft: 8 }}>预览</Tag>
                <Button type="text" size="small" icon={<CloseOutlined />} onClick={ai.close} />
              </div>
              {ai.reason && <div className="inlineai-reason">{ai.reason}</div>}
              <div className="inlineai-legend">
                <span className="inlineai-legend-del">删除内容</span>
                <span className="inlineai-legend-add">新增内容</span>
              </div>
              <Space size={8}>
                <Tooltip title="应用修改到正文（自动保存）">
                  <Button type="primary" size="small" icon={<CheckOutlined />} onClick={ai.accept}>
                    接受
                  </Button>
                </Tooltip>
                <Tooltip title="换一种改法重新生成">
                  <Button size="small" icon={<ReloadOutlined />} onClick={() => void ai.regenerate()}>
                    重新生成
                  </Button>
                </Tooltip>
                <Button size="small" icon={<CloseOutlined />} onClick={ai.reject}>
                  放弃
                </Button>
              </Space>
            </>
          )}
        </div>
      )}
    </>
  )
}
