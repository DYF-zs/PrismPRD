export function documentHeadingSlug(text: string): string {
  return text.trim().toLowerCase().replace(/[^\p{L}\p{N}_\s-]/gu, '').replace(/\s/g, '-')
}

export function headingAnchors(texts: string[]): string[] {
  const used = new Set<string>()
  return texts.map(text => {
    const base = documentHeadingSlug(text)
    let slug = base, suffix = 0
    while (used.has(slug)) slug = `${base}-${++suffix}`
    used.add(slug)
    return slug
  })
}
