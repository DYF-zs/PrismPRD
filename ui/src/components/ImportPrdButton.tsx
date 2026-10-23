import { useRef, useState } from 'react'
import { Button, Upload, message } from 'antd'
import { UploadOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { createPrdFromFile } from '../api/knowledge'

export default function ImportPrdButton({ disabled = false }: { disabled?: boolean }) {
  const navigate = useNavigate()
  const busy = useRef(false)
  const [importing, setImporting] = useState(false)
  const upload = async (file: File) => {
    if (busy.current || disabled) return
    if (!/\.(md|markdown|txt|docx)$/i.test(file.name)) { message.warning('请选择 Markdown、TXT 或 DOCX 文件'); return }
    if (!file.size || file.size > 10 * 1024 * 1024) { message.warning('请选择大小在 1 字节至 10 MiB 之间的文件'); return }
    busy.current = true
    setImporting(true)
    try {
      const created = await createPrdFromFile(file)
      message.success('PRD 导入成功')
      navigate(`/prds/${created.prdId}`)
    } catch (e) { message.error((e as Error).message) }
    finally { busy.current = false; setImporting(false) }
  }
  return <Upload accept=".md,.markdown,.txt,.docx" showUploadList={false} disabled={disabled || importing}
    beforeUpload={file => { void upload(file); return false }}>
    <Button icon={<UploadOutlined />} loading={importing} disabled={disabled}>导入 PRD</Button>
  </Upload>
}
