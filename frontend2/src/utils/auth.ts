/**
 * Check if user has a specific permission by key
 */
export function hasPermission(permission: string): boolean {
  const stored = localStorage.getItem('permissions');
  if (!stored) return false;
  try {
    const perms: string[] = JSON.parse(stored);
    return perms.includes(permission);
  } catch {
    return false;
  }
}

/**
 * Check if user has any of the given permissions
 */
export function hasAnyPermission(permissions: string[]): boolean {
  return permissions.some((p) => hasPermission(p));
}

/**
 * Check if user has all of the given permissions
 */
export function hasAllPermissions(permissions: string[]): boolean {
  return permissions.every((p) => hasPermission(p));
}

/**
 * Get current user info from localStorage
 */
export function getCurrentUser() {
  const stored = localStorage.getItem('userInfo');
  if (!stored) return null;
  try {
    return JSON.parse(stored);
  } catch {
    return null;
  }
}

/**
 * Get token
 */
export function getToken(): string | null {
  return localStorage.getItem('token');
}

/**
 * Clear all auth data
 */
export function clearAuth(): void {
  localStorage.removeItem('token');
  localStorage.removeItem('userInfo');
  localStorage.removeItem('permissions');
  localStorage.removeItem('menus');
}
