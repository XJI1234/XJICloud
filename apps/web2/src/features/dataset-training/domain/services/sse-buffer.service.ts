export function extractSseJsonPayloads(buffer: string): { payloads: unknown[]; rest: string } {
  const chunks = buffer.replace(/\r\n/g, '\n').split('\n\n')
  const rest = chunks.pop() ?? ''
  const payloads: unknown[] = []

  for (const chunk of chunks) {
    const dataLine = chunk.split('\n').find((line) => line.startsWith('data:'))
    if (!dataLine) {
      continue
    }
    payloads.push(JSON.parse(dataLine.slice(5).trim()) as unknown)
  }

  return { payloads, rest }
}
