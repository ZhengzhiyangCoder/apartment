import { shallowRef } from 'vue'
import type { Router } from 'vue-router'
import { HttpError, request, write } from '../../lib/http'

export type Role = 'SYSTEM_ADMIN' | 'DORM_MANAGER' | 'STUDENT' | 'MAINTAINER'
export interface Profile { id: number; username: string; displayName: string; role: Role; roleLabel: string }
export const currentUser = shallowRef<Profile | null>(null)

export async function refreshUser() {
  try { currentUser.value = await request<Profile>('/api/auth/me') }
  catch (error) {
    if (error instanceof HttpError && error.status === 401) currentUser.value = null
    else throw error
  }
  return currentUser.value
}

export async function login(username: string, password: string) {
  await write('/api/auth/login', new URLSearchParams({ username, password }), 'application/x-www-form-urlencoded')
  return refreshUser()
}

export async function logout() {
  await write('/api/auth/logout')
  currentUser.value = null
}

export function workspacePath() { return `/workspace/${currentUser.value?.role ?? 'STUDENT'}` }


export async function handleAuthorizationFailure(failure: unknown, router: Router) {
  if (failure instanceof HttpError && failure.status === 401) {
    currentUser.value = null
    await router.replace({ path: '/login', query: { accountChanged: '1' } })
    return true
  }
  if (failure instanceof HttpError && failure.status === 403) {
    await router.replace('/forbidden')
    return true
  }
  return false
}
