import type { RubricDimension, RubricInput, RubricDetail } from '../../api/rubric'

export const severityLabels: Record<string, string> = { CRITICAL: '严重', MAJOR: '主要', MINOR: '次要', SUGGESTION: '建议' }
export function newDimension(index: number): RubricDimension {
  return {
    dimensionKey: `CUSTOM_${index}`, name: '', description: '', checklist: [''], applicabilityRule: '',
    scoreAnchor: [
      { min: 0, max: 2.9, description: '关键要求缺失，无法支撑开发与验收' },
      { min: 3, max: 4.9, description: '仅覆盖部分要求，存在较大缺口' },
      { min: 5, max: 6.9, description: '主要要求有描述，重要细节仍需补充' },
      { min: 7, max: 8.9, description: '要求基本完整，存在少量局部问题' },
      { min: 9, max: 10, description: '要求完整明确，可直接支撑开发与验收' },
    ],
    severityAnchor: {
      CRITICAL: '阻塞核心功能或存在必须修复的重大风险', MAJOR: '关键要求缺失，会造成实现歧义或返工',
      MINOR: '局部细节不完善，影响范围可控', SUGGESTION: '不阻塞实现的完善性建议',
    },
  }
}
export function rubricInput(detail: RubricDetail): RubricInput {
  return { name: detail.name, description: detail.description, dimensions: detail.dimensions.map(d => ({
    dimensionKey: d.dimensionKey, name: d.name, description: d.description ?? '', checklist: [...d.checklist],
    scoreAnchor: d.scoreAnchor.map(b => ({ ...b })), severityAnchor: { ...d.severityAnchor }, applicabilityRule: d.applicabilityRule ?? '',
  })) }
}
export function validateRubric(input: RubricInput): string | null {
  if (!input.name.trim() || input.name.length > 128) return '标准名称需为 1–128 字符'
  if ((input.description?.length ?? 0) > 4000) return '标准说明至多 4000 字符'
  if (input.dimensions.length < 1 || input.dimensions.length > 24) return '需配置 1–24 个维度'
  const keys = new Set<string>()
  for (const [index, d] of input.dimensions.entries()) {
    const prefix = `第 ${index + 1} 个维度：`
    if (!/^[A-Z][A-Z0-9_]{0,63}$/.test(d.dimensionKey)) return prefix + '标识需使用大写英文字母、数字和下划线，并以字母开头'
    if (keys.has(d.dimensionKey)) return prefix + '维度标识重复'
    keys.add(d.dimensionKey)
    if (!d.name.trim() || d.name.length > 128) return prefix + '名称需为 1–128 字符'
    if ((d.description?.length ?? 0) > 4000 || (d.applicabilityRule?.length ?? 0) > 4000) return prefix + '维度说明和适用条件至多 4000 字符'
    if (!d.checklist.length || d.checklist.length > 50 || d.checklist.some(text => !text.trim() || text.length > 500)) return prefix + '需有 1–50 个非空检查项，每项至多 500 字符'
    if (['CRITICAL', 'MAJOR', 'MINOR', 'SUGGESTION'].some(key => !d.severityAnchor[key]?.trim())) return prefix + '请填写四种严重程度规则'
    if (Object.values(d.severityAnchor).some(text => text.length > 1000)) return prefix + '每种严重程度规则至多 1000 字符'
    if (!d.scoreAnchor.length || d.scoreAnchor.length > 20) return prefix + '需有 1–20 个评分区间'
    const covered = new Set<number>()
    for (const band of d.scoreAnchor) {
      if (band.min === null || band.max === null || !Number.isFinite(band.min) || !Number.isFinite(band.max) ||
        Math.abs(band.min * 10 - Math.round(band.min * 10)) > 1e-8 || Math.abs(band.max * 10 - Math.round(band.max * 10)) > 1e-8 ||
        band.min < 0 || band.max > 10 || band.min > band.max) return prefix + '评分区间需为 0–10 分，至多一位小数，起始分不得大于结束分'
      if (!band.description.trim() || band.description.length > 1000) return prefix + '每个评分区间的判定说明需为 1–1000 字符'
      for (let score = Math.round(band.min * 10); score <= Math.round(band.max * 10); score++) {
        if (covered.has(score)) return prefix + '评分区间重叠'
        covered.add(score)
      }
    }
    if (covered.size !== 101) return prefix + '评分区间需完整覆盖 0–10 分，精确到 0.1 分'
  }
  return null
}
