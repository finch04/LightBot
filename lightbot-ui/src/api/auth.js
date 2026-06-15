import request from '../utils/request'

export function register(data) {
  return request.post('/auth/register', data)
}

export function login(data) {
  return request.post('/auth/login', data)
}

export function logout() {
  return request.post('/auth/logout')
}

export function getMe() {
  return request.get('/auth/me')
}

export function getUsersByIds(ids) {
  return request.get('/auth/users/batch', {
    params: { ids },
    paramsSerializer: params => params.ids.map(id => `ids=${id}`).join('&'),
  })
}

export function searchUsers(keyword) {
  return request.get('/auth/users/search', { params: { keyword } })
}

export function updateProfile(data) {
  return request.put('/auth/profile', data)
}

export function updateAvatarFrame(avatarFrame) {
  return request.put('/auth/profile', { avatarFrame })
}

export function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/auth/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function changePassword(data) {
  return request.put('/auth/password', data)
}
