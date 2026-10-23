/** Invalidate in-flight results on a newer request or component cleanup. */
export class RequestEpoch {
  private value = 0
  next(): number { return ++this.value }
  isCurrent(token: number): boolean { return token === this.value }
  invalidate(): void { this.value++ }
}
