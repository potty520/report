export function getToken() {
  return localStorage.getItem('token');
}

export function setToken(token) {
  localStorage.setItem('token', token);
}

export function clearToken() {
  localStorage.removeItem('token');
}

export function getCurrentUser() {
  const user = localStorage.getItem('currentUser');
  return user ? JSON.parse(user) : null;
}

export function setCurrentUser(user) {
  localStorage.setItem('currentUser', JSON.stringify(user));
}

export function clearCurrentUser() {
  localStorage.removeItem('currentUser');
}

export function setRememberMe(username, password) {
  localStorage.setItem('rememberedUser', JSON.stringify({ username, password }));
}

export function getRememberMe() {
  const data = localStorage.getItem('rememberedUser');
  return data ? JSON.parse(data) : null;
}

export function clearRememberMe() {
  localStorage.removeItem('rememberedUser');
}
