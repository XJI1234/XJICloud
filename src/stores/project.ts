import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as projectsApi from '@/api/projects'
import type { ProjectSummary } from '@/api/projects'

const RECENT_PROJECTS_KEY = 'xjicloud_recent_projects'
const MAX_RECENT_PROJECTS = 8

interface RecentProjectEntry {
  id: string
  openedAt: number
}

function readRecentEntries(): RecentProjectEntry[] {
  try {
    const raw = localStorage.getItem(RECENT_PROJECTS_KEY)
    if (!raw) {
      return []
    }
    const parsed = JSON.parse(raw) as RecentProjectEntry[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function writeRecentEntries(entries: RecentProjectEntry[]) {
  localStorage.setItem(RECENT_PROJECTS_KEY, JSON.stringify(entries.slice(0, MAX_RECENT_PROJECTS)))
}

export const useProjectStore = defineStore('project', () => {
  const projects = ref<ProjectSummary[]>([])
  const activeProjectId = ref<string | null>(localStorage.getItem('xjicloud_active_project_id'))
  const loading = ref(false)
  const recentEntries = ref<RecentProjectEntry[]>(readRecentEntries())

  const activeProject = computed(() =>
    projects.value.find((project) => project.id === activeProjectId.value) ?? null,
  )

  const recentProjects = computed(() => {
    const projectMap = new Map(projects.value.map((project) => [project.id, project]))
    return recentEntries.value
      .filter((entry) => projectMap.has(entry.id))
      .sort((left, right) => right.openedAt - left.openedAt)
      .map((entry) => ({
        ...projectMap.get(entry.id)!,
        openedAt: entry.openedAt,
      }))
  })

  function recordRecentAccess(projectId: string) {
    const nextEntries = [
      { id: projectId, openedAt: Date.now() },
      ...recentEntries.value.filter((entry) => entry.id !== projectId),
    ]
    recentEntries.value = nextEntries.slice(0, MAX_RECENT_PROJECTS)
    writeRecentEntries(recentEntries.value)
  }

  async function fetchProjects() {
    loading.value = true
    try {
      projects.value = await projectsApi.listProjects()
      recentEntries.value = readRecentEntries()
    } finally {
      loading.value = false
    }
  }

  async function createProject(name: string, description = '') {
    const project = await projectsApi.createProject(name, description)
    projects.value = [project, ...projects.value]
    setActiveProject(project.id)
    return project
  }

  function setActiveProject(projectId: string | null) {
    activeProjectId.value = projectId
    if (projectId) {
      localStorage.setItem('xjicloud_active_project_id', projectId)
      recordRecentAccess(projectId)
    } else {
      localStorage.removeItem('xjicloud_active_project_id')
    }
  }

  function openProject(projectId: string) {
    setActiveProject(projectId)
  }

  return {
    projects,
    activeProjectId,
    activeProject,
    recentProjects,
    loading,
    fetchProjects,
    createProject,
    setActiveProject,
    openProject,
    recordRecentAccess,
  }
})
