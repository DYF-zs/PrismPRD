import { useEffect, useState } from 'react'
import { Alert, Modal, Spin } from 'antd'
import { fetchRubric } from '../../api/rubric'
import type { RubricDetail } from '../../api/rubric'
import RubricViewer from './RubricViewer'

export default function RubricSnapshotModal({ id, onClose }: { id: string | null; onClose: () => void }) {
  const [rubric, setRubric] = useState<RubricDetail | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    setRubric(null); setError('')
    if (id) fetchRubric(id).then(result => { if (active) setRubric(result) }).catch(e => { if (active) setError((e as Error).message) })
    return () => { active = false }
  }, [id])
  return <Modal open={id !== null} title="本次评审使用的标准版本" width={760} footer={null} onCancel={onClose}>
    {error ? <Alert type="error" message={error} /> : rubric ? <RubricViewer rubric={rubric} /> : <Spin />}
  </Modal>
}
