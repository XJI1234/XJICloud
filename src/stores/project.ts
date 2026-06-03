import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as projectsApi from '@/api/projects'
import type { ProjectSummary } from '@/api/projects'

export const useProjectStore = defineStore('project', () => {
  const projects = ref<ProjectSummary[]>([])
  const activeProjectId = ref<string | null>(localStorage.getItem('xjicloud_active_project_id'))
  const loading = ref(false)

  async function fetchProjects() {
    loading.value = true
    try {
      projects.value = await projectsApi.listProjects()
      if (!activeProjectId.value && projects.value.length > 0) {
        setActiveProject(projects.value[0].id)
      }
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
    } else {
      localStorage.removeItem('xjicloud_active_project_id')
    }
  }

  return {
    projects,
    activeProjectId,
    loading,
    fetchProjects,
    createProject,
    setActiveProject,
  }
})
