// ============================================================
// Tasks — Main Application Entry
// ============================================================

import { api } from './api.js';
import { createElement, debounce, $ } from './utils.js';
import { renderLogin } from './auth.js';
import { Sidebar } from './components/sidebar.js';
import { TaskList } from './components/taskList.js';
import { TaskForm } from './components/taskForm.js';
import { TagManager } from './components/tagManager.js';
import { GroupManager } from './components/groupManager.js';

class App {
  constructor() {
    this.container = document.getElementById('app');
    this.sidebar = null;
    this.taskList = null;
    this.tagManager = null;
    this.groupManager = null;
    this.contentArea = null;
    this.currentView = 'all';

    // Listen for auth logout
    window.addEventListener('auth:logout', () => this.showLogin());

    this.init();
  }

  async init() {
    if (api.isAuthenticated()) {
      try {
        await api.getMe();
        this.showApp();
      } catch {
        this.showLogin();
      }
    } else {
      this.showLogin();
    }
  }

  showLogin() {
    this.container.innerHTML = '';
    this.container.style.display = 'block';
    renderLogin(this.container, () => this.showApp());
  }

  async showApp() {
    this.container.innerHTML = '';
    this.container.style.display = 'flex';

    // Initialize components
    this.sidebar = new Sidebar({
      onNavigate: (view) => this.navigate(view),
      onGroupSelect: (group) => this.navigateGroup(group),
    });

    this.taskList = new TaskList({
      onRefreshSidebar: () => this.sidebar.refreshGroups(),
    });

    this.tagManager = new TagManager();
    this.groupManager = new GroupManager({
      onRefreshSidebar: () => this.sidebar.refreshGroups(),
    });

    // Load sidebar groups
    await this.sidebar.loadGroups();

    // Render sidebar
    this.container.appendChild(this.sidebar.render());

    // Main content wrapper
    const main = createElement('div', { className: 'main-content', id: 'main-content' });
    this.container.appendChild(main);

    // Load tag types for advanced search dropdown
    const { tag_types = [] } = await api.getTagTypes().catch(() => ({ tag_types: [] }));
    const searchTypeOptions = tag_types.map(t => createElement('option', { value: `tag_${t.id}` }, t.name));

    // Header
    const header = createElement('div', { className: 'content-header', id: 'content-header' },
      createElement('div', { style: { display: 'flex', alignItems: 'center', gap: '12px' } },
        createElement('button', {
          className: 'mobile-menu-btn',
          onClick: () => this.sidebar.openMobile(),
        }, '☰ Menu'),
        createElement('h2', { id: 'view-title' }, 'All Tasks')
      ),
      createElement('div', { className: 'header-actions' },
        createElement('select', {
          id: 'search-type-select',
          className: 'form-select tag-filter-select',
          style: { width: '130px' },
          onChange: () => {
            const input = document.getElementById('search-input');
            if (input.value) {
              input.dispatchEvent(new Event('input'));
            }
          }
        },
          createElement('option', { value: 'task' }, 'Task Name'),
          createElement('option', { value: 'tag' }, 'Any Tag'),
          createElement('option', { value: 'date' }, 'Date'),
          ...searchTypeOptions
        ),
        createElement('div', { className: 'search-bar' },
          createElement('span', { className: 'search-icon' }, ''),
          createElement('input', {
            type: 'text',
            id: 'search-input',
            placeholder: 'Search...',
            onInput: debounce((e) => {
              const selectVal = document.getElementById('search-type-select').value;
              let searchType = 'task';
              if (selectVal === 'tag' || selectVal.startsWith('tag_')) {
                searchType = 'tag';
              } else if (selectVal === 'date') {
                searchType = 'date';
              }
              const tagTypeId = selectVal.startsWith('tag_') ? selectVal.split('_')[1] : null;
              this.taskList.setSearch(e.target.value, searchType, tagTypeId);
              this.refreshContent();
            }, 300),
          })
        )
      )
    );
    main.appendChild(header);

    // Content area
    this.contentArea = createElement('div', { id: 'content-area' });
    main.appendChild(this.contentArea);

    // FAB
    const fab = createElement('button', {
      className: 'fab',
      id: 'fab-new-task',
      title: 'New Task',
      onClick: () => this.taskList.openNewTaskForm(),
    }, '+');
    main.appendChild(fab);

    // Sidebar backdrop (mobile)
    document.getElementById('sidebar-backdrop')?.addEventListener('click', () => {
      this.sidebar.closeMobile();
    });

    // Navigate to default view
    this.navigate('all');

    // Register Push Notifications
    this.initPushNotifications().catch(err => console.error('Failed to init push notifications:', err));
  }

  async initPushNotifications() {
    if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
      console.warn('Push messaging is not supported in this browser');
      return;
    }
    try {
      const registration = await navigator.serviceWorker.register('sw.js');
      console.log('Service Worker registered with scope:', registration.scope);

      // Request notification permission if not already granted
      if (Notification.permission === 'default') {
        const permission = await Notification.requestPermission();
        if (permission !== 'granted') {
          console.log('Notification permission denied');
          return;
        }
      }

      if (Notification.permission === 'granted') {
        let subscription = await registration.pushManager.getSubscription();
        
        // If no subscription exists, subscribe the user
        if (!subscription) {
          const vapidPublicKey = 'BM9oz6TpvH1o-bd-A7McUJ8gD9oIHJWeUQu0OsXhzLw4So8LwYDhNmvy5YvOV1kqVX5ddLGf-nfINC0ttQpE0fg';
          const convertedKey = this.urlBase64ToUint8Array(vapidPublicKey);
          
          subscription = await registration.pushManager.subscribe({
            userVisibleOnly: true,
            applicationServerKey: convertedKey
          });
          
          // Send subscription to backend
          await api.request('/tasks/subscribe', {
            method: 'POST',
            body: JSON.stringify(subscription)
          });
          console.log('Successfully subscribed to Web Push notifications');
        }
      }
    } catch (err) {
      console.error('Error during push notification subscription:', err);
    }
  }

  urlBase64ToUint8Array(base64String) {
    const padding = '='.repeat((4 - base64String.length % 4) % 4);
    const base64 = (base64String + padding)
      .replace(/\-/g, '+')
      .replace(/_/g, '/');
    const rawData = window.atob(base64);
    const outputArray = new Uint8Array(rawData.length);
    for (let i = 0; i < rawData.length; ++i) {
      outputArray[i] = rawData.charCodeAt(i);
    }
    return outputArray;
  }

  async navigate(view) {
    this.currentView = view;
    this.sidebar?.closeMobile();

    const title = document.getElementById('view-title');
    const fab = document.getElementById('fab-new-task');

    if (view === 'tags') {
      if (title) title.textContent = 'Tags';
      if (fab) fab.style.display = 'none';
      await this.tagManager.render(this.contentArea);
    } else if (view === 'groups-manage') {
      if (title) title.textContent = 'Manage Groups';
      if (fab) fab.style.display = 'none';
      await this.groupManager.render(this.contentArea);
    } else {
      if (fab) fab.style.display = 'flex';
      this.taskList.setView(view);
      if (title) title.textContent = this.taskList.getViewTitle();
      await this.taskList.loadTasks();
      this.taskList.render(this.contentArea);
    }
  }

  async navigateGroup(group) {
    this.currentView = 'group';
    this.sidebar?.closeMobile();

    const title = document.getElementById('view-title');
    const fab = document.getElementById('fab-new-task');
    if (fab) fab.style.display = 'flex';

    this.taskList.setGroupFilter(group);
    if (title) title.textContent = group.name;
    await this.taskList.loadTasks();
    this.taskList.render(this.contentArea);
  }

  async refreshContent() {
    if (['tags', 'groups-manage'].includes(this.currentView)) return;
    await this.taskList.loadTasks();
    const body = document.getElementById('task-list-body');
    if (body) this.taskList._renderTaskList(body);
  }
}

// Boot the app
new App();
