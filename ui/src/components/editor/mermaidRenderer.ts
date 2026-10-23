export function validateMermaidSource(source: string): void {
  if (!source.trim()) throw new Error('请输入 Mermaid 图形代码')
  if (source.length > 20000 || source.split('\n').length > 500) throw new Error('图形代码最多 20,000 字符、500 行，请拆分图形')
  if (/%%\s*\{|^\s*---(?:\r?\n)/m.test(source)) throw new Error('图形使用工作台统一配置，请移除图形中的配置指令')
  if (/<\s*(?:script|iframe|object|embed|style|link|img|image|foreignObject)\b|!\[|\b(?:img|image)\s*:|@import|url\s*\(/i.test(source))
    throw new Error('图形支持文本与连线，图片和外部资源请放在正文中')
}

let engine: Promise<typeof import('mermaid')['default']> | undefined

export async function renderMermaid(source: string): Promise<string> {
  validateMermaidSource(source)
  engine ??= import('mermaid').then(({ default: mermaid }) => {
    mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', suppressErrorRendering: true,
      maxTextSize: 20000, maxEdges: 300, htmlLabels: false, theme: 'default', fontFamily: 'system-ui, sans-serif' })
    return mermaid
  }).catch(error => { engine = undefined; throw error })
  const mermaid = await engine
  const { svg } = await mermaid.render(`mermaid-${crypto.randomUUID()}`, source)
  const document = new DOMParser().parseFromString(svg, 'image/svg+xml')
  const root = document.documentElement
  const bounds = (root.getAttribute('viewBox') ?? '').trim().split(/[ ,]+/).map(Number)
  if (root.localName !== 'svg' || document.querySelector('parsererror')) throw new Error('图形格式不正确')
  if (bounds.length === 4 && bounds.every(Number.isFinite) && bounds[2] > 0 && bounds[3] > 0) {
    root.setAttribute('width', String(Math.ceil(bounds[2])))
    root.setAttribute('height', String(Math.ceil(bounds[3])))
  }
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(new XMLSerializer().serializeToString(root))}`
}
