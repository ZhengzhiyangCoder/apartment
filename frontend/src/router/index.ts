import { createRouter, createWebHistory } from 'vue-router'
import { currentUser, refreshUser, workspacePath } from '../features/identity/session'
import LoginPage from '../features/identity/LoginPage.vue'
import ProfilePage from '../features/identity/ProfilePage.vue'
import WorkspacePage from '../features/identity/WorkspacePage.vue'
import PasswordPage from '../features/identity/PasswordPage.vue'
import AccessPage from '../features/identity/AccessPage.vue'
import AccountsPage from '../features/identity/AccountsPage.vue'
import HousingPage from '../features/housing/HousingPage.vue'
import ResidencyPage from '../features/housing/ResidencyPage.vue'
import MyResidencyPage from '../features/housing/MyResidencyPage.vue'
import RepairsPage from '../features/repair/RepairsPage.vue'
import InspectionsPage from '../features/hygiene/InspectionsPage.vue'
import SchemesPage from '../features/hygiene/SchemesPage.vue'
import AppLayout from '../layouts/AppLayout.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginPage },
    { path: '/', component: AppLayout, meta: { authenticated: true }, children: [
      { path: '', component: WorkspacePage },
      { path: 'workspace/:role', component: WorkspacePage },
      { path: 'profile', component: ProfilePage },
      { path: 'password', component: PasswordPage },
      { path: 'accounts', component: AccountsPage, meta: { role: 'SYSTEM_ADMIN' } },
      { path: 'residencies', component: ResidencyPage, meta: { role: 'SYSTEM_ADMIN' } },
      { path: 'my-residency', component: MyResidencyPage, meta: { role: 'STUDENT' } },
      { path: 'repairs', component: RepairsPage, meta: { roles: ['STUDENT', 'SYSTEM_ADMIN', 'DORM_MANAGER', 'MAINTAINER'] } },
      { path: 'inspections', component: InspectionsPage, meta: { roles: ['SYSTEM_ADMIN', 'DORM_MANAGER'] } },
      { path: 'schemes', component: SchemesPage, meta: { roles: ['SYSTEM_ADMIN', 'DORM_MANAGER'] } },
      { path: 'housing', component: HousingPage, meta: { roles: ['SYSTEM_ADMIN', 'DORM_MANAGER'] } },
      { path: 'forbidden', component: AccessPage },
      { path: ':pathMatch(.*)*', component: AccessPage },
    ] },
  ],
})

router.beforeEach(async (to) => {
  try { await refreshUser() }
  catch { return to.path === '/login' ? true : { path: '/login', query: { service: 'unavailable' } } }
  if (to.matched.some(route => route.meta.authenticated) && !currentUser.value) return '/login'
  if (to.path === '/' && currentUser.value) return workspacePath()
  if (to.path === '/login' && currentUser.value) return workspacePath()
  if (to.params.role && to.params.role !== currentUser.value?.role) return '/forbidden'
  if (to.meta.role && to.meta.role !== currentUser.value?.role) return '/forbidden'
  if (Array.isArray(to.meta.roles) && !to.meta.roles.includes(currentUser.value?.role)) return '/forbidden'
  return true
})
