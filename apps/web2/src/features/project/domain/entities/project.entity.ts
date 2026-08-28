export type Project = {
  id: string
  name: string
  description: string
  createdAt: string
}

export type RecentProjectEntry = {
  id: string
  openedAt: number
}

export const MAX_RECENT_PROJECTS = 8
