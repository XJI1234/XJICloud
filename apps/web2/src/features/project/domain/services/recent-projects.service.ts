import { MAX_RECENT_PROJECTS, type RecentProjectEntry } from '../entities/project.entity'

export function recordRecentAccess(
  entries: RecentProjectEntry[],
  projectId: string,
  openedAt: number,
  max = MAX_RECENT_PROJECTS,
): RecentProjectEntry[] {
  return [{ id: projectId, openedAt }, ...entries.filter((entry) => entry.id !== projectId)].slice(0, max)
}

export function resolveRecentProjects<T extends { id: string }>(
  projects: T[],
  entries: RecentProjectEntry[],
): Array<T & { openedAt: number }> {
  const projectMap = new Map(projects.map((project) => [project.id, project]))
  return entries
    .filter((entry) => projectMap.has(entry.id))
    .sort((left, right) => right.openedAt - left.openedAt)
    .map((entry) => ({
      ...projectMap.get(entry.id)!,
      openedAt: entry.openedAt,
    }))
}
