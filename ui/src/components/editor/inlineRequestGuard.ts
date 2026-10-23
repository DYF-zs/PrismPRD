/** 每个异步生成都绑定文档快照；取消、重新生成或正文变化会使旧响应失效。 */
export class InlineRequestGuard {
  private epoch = 0

  invalidate() { this.epoch += 1 }

  begin(document: object) {
    const epoch = ++this.epoch
    return { epoch, document }
  }

  isCurrent(request: { epoch: number; document: object }, document: object) {
    return request.epoch === this.epoch && request.document === document
  }
}
