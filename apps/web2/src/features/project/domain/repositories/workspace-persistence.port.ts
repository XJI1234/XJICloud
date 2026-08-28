import type { RecentProjectEntry } from '../entities/project.entity'

export interface WorkspacePersistence {
  getActiveProjectId(): string | null
  setActiveProjectId(id: string | null): void
  readRecentEntries(): RecentProjectEntry[]
  writeRecentEntries(entries: RecentProjectEntry[]): void
  subscribe(listener: () => void): () => void
  clear(): void
}
