import axios from 'axios'

export const client = axios.create({
  baseURL: '/api',
})

// 요청 보내기 전에 토큰을 헤더에 자동으로 붙임
client.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// TODO(인증): 지금은 localStorage 방식. 나중에 보안 강화 시 메모리 + RT 방식으로 리팩토링
// TODO(인증): accessToken 만료 시 refreshToken으로 재발급하는 응답 인터셉터 추가 (401 처리)
// TODO(인증): RT를 HttpOnly 쿠키로 받게 되면 withCredentials: true 다시 추가