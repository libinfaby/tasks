// ============================================================
// Tasks — API Client
// ============================================================

const isLocalhost = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
const defaultApiUrl = isLocalhost ? 'http://localhost:8787' : 'https://tasks-api.libinfaby.dev';

const CONFIG = {
  API_URL: localStorage.getItem('tasks_api_url') || defaultApiUrl,
};

class ApiClient {
  constructor() {
    this.baseUrl = CONFIG.API_URL;
  }

  /**
   * Set the API base URL (useful for dev/prod switching)
   */
  setBaseUrl(url) {
    this.baseUrl = url;
    localStorage.setItem('tasks_api_url', url);
  }

  getBaseUrl() {
    return this.baseUrl;
  }

  /**
   * Get auth token from localStorage
   */
  getToken() {
    return localStorage.getItem('tasks_token');
  }

  /**
   * Make an authenticated API request
   */
  async request(path, options = {}) {
    const url = `${this.baseUrl}${path}`;
    const token = this.getToken();

    const headers = {
      'Content-Type': 'application/json',
      ...options.headers,
    };

    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    try {
      const response = await fetch(url, {
        ...options,
        headers,
      });

      // Handle 401 - unauthorized (token expired or invalid)
      if (response.status === 401 && path !== '/auth/login') {
        localStorage.removeItem('tasks_token');
        window.dispatchEvent(new CustomEvent('auth:logout'));
        throw new Error('Session expired. Please login again.');
      }

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.error || `Request failed with status ${response.status}`);
      }

      return data;
    } catch (error) {
      if (error.message === 'Failed to fetch') {
        throw new Error('Cannot connect to server. Please check your connection.');
      }
      throw error;
    }
  }

  // ==================== Auth ====================
  async login(username, password) {
    const data = await this.request('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    });
    if (data.token) {
      localStorage.setItem('tasks_token', data.token);
    }
    return data;
  }

  logout() {
    localStorage.removeItem('tasks_token');
  }

  async getMe() {
    return this.request('/auth/me');
  }

  isAuthenticated() {
    return !!this.getToken();
  }

  // ==================== Tasks ====================
  async getTasks(filters = {}) {
    const params = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        params.append(key, value);
      }
    });
    const query = params.toString();
    return this.request(`/tasks${query ? '?' + query : ''}`);
  }

  async getTask(id) {
    return this.request(`/tasks/${id}`);
  }

  async createTask(data) {
    return this.request('/tasks', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async updateTask(id, data) {
    return this.request(`/tasks/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async deleteTask(id) {
    return this.request(`/tasks/${id}`, {
      method: 'DELETE',
    });
  }

  async toggleTask(id) {
    return this.request(`/tasks/${id}/toggle`, {
      method: 'PATCH',
    });
  }

  // ==================== Subtasks ====================
  async createSubtask(data) {
    return this.request('/subtasks', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async updateSubtask(id, data) {
    return this.request(`/subtasks/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async deleteSubtask(id) {
    return this.request(`/subtasks/${id}`, {
      method: 'DELETE',
    });
  }

  async toggleSubtask(id) {
    return this.request(`/subtasks/${id}/toggle`, {
      method: 'PATCH',
    });
  }

  // ==================== Tag Types ====================
  async getTagTypes() {
    return this.request('/tag-types');
  }

  async createTagType(data) {
    return this.request('/tag-types', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async updateTagType(id, data) {
    return this.request(`/tag-types/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async deleteTagType(id) {
    return this.request(`/tag-types/${id}`, {
      method: 'DELETE',
    });
  }

  // ==================== Tags ====================
  async getTags() {
    return this.request('/tag-types/tags');
  }

  async createTag(data) {
    return this.request('/tag-types/tags', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async updateTag(id, data) {
    return this.request(`/tag-types/tags/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async deleteTag(id) {
    return this.request(`/tag-types/tags/${id}`, {
      method: 'DELETE',
    });
  }

  // ==================== Groups ====================
  async getGroups() {
    return this.request('/groups');
  }

  async createGroup(data) {
    return this.request('/groups', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async updateGroup(id, data) {
    return this.request(`/groups/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async deleteGroup(id) {
    return this.request(`/groups/${id}`, {
      method: 'DELETE',
    });
  }
}

export const api = new ApiClient();
