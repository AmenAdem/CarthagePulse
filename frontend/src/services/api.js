import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:3000';

const api = axios.create({
  baseURL: `${API_BASE_URL}/api`,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add token to requests
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Handle response errors
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export const authAPI = {
  login: (email, password) => api.post('/auth/login', { email, password }),
  register: (userData) => api.post('/auth/register', userData),
  verify: () => api.get('/auth/verify'),
};

export const companiesAPI = {
  getAll: () => api.get('/companies'),
  getTracked: () => api.get('/companies/tracked'),
  track: (companyId) => api.post(`/companies/track/${companyId}`),
  untrack: (companyId) => api.delete(`/companies/track/${companyId}`),
  getPrices: (companyId, params) => api.get(`/companies/${companyId}/prices`, { params }),
  getNews: (companyId, params) => api.get(`/companies/${companyId}/news`, { params }),
};

export const userAPI = {
  getProfile: () => api.get('/users/profile'),
  getPreferences: () => api.get('/users/preferences'),
  updatePreferences: (data) => api.put('/users/preferences', data),
};

export const notificationsAPI = {
  getAll: (params) => api.get('/notifications', { params }),
  markAsRead: (notificationIds) => api.put('/notifications/read', { notificationIds }),
};

export default api;