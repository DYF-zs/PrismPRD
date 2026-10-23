import { useEffect, useRef, useState } from 'react'
import { Button, Dropdown, message } from 'antd'
import { ExportOutlined } from '@ant-design/icons'
import { fetchVersionExport } from '../api/version'
import type { ExportFormat } from '../api/version'

export default function VersionExportButton({ versionId, compact = false }: { versionId: string; compact?: boolean }) {
  const [busy, setBusy] = useState(false)
  const busyRef = useRef(false)
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  const download = async (format: ExportFormat) => {
    if (busyRef.current) return
    busyRef.current = true; setBusy(true)
    try {
      const blob = await fetchVersionExport(versionId, format)
      if (!mounted.current) return
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url; anchor.download = `prd-version-${versionId}.${format === 'pdf' ? 'pdf' : 'md'}`
      document.body.appendChild(anchor)
      try { anchor.click() } finally { anchor.remove(); setTimeout(() => URL.revokeObjectURL(url), 60000) }
    } catch (e) { if (mounted.current) message.error((e as Error).message) }
    finally { busyRef.current = false; if (mounted.current) setBusy(false) }
  }
  return <span onClick={e => e.stopPropagation()}>
    <Dropdown disabled={busy} trigger={['click']} menu={{ items: [{ key: 'markdown', label: '导出 Markdown' }, { key: 'pdf', label: '导出 PDF' }],
      onClick: ({ key }) => void download(key as ExportFormat) }}>
      <Button aria-label="导出版本" size={compact ? 'small' : 'middle'} type={compact ? 'text' : 'default'} icon={<ExportOutlined />} loading={busy}>导出</Button>
    </Dropdown>
  </span>
}
