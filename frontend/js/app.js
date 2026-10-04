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
import { DailyLog } from './components/dailyLog.js';
import { icon } from './icons.js';

class App {
  constructor() {
    this.container = document.getElementById('app');
    this.sidebar = null;
    this.taskList = null;
    this.tagManager = null;
    this.groupManager = null;
    this.dailyLog = new DailyLog();
    this.contentArea = null;
    this.currentView = 'all';

    // Listen for auth logout
    window.addEventListener('auth:logout', () => this.showLogin());
    window.addEventListener('theme:change', () => {
      if (!this.taskList) return;
      if (['tags', 'groups-manage'].includes(this.currentView)) this.navigate(this.currentView);
      else this.refreshContent();
    });

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
      createElement('div', { className: 'header-title' },
        createElement('button', {
          className: 'icon-btn mobile-menu-btn',
          title: 'Menu',
          'aria-label': 'Open menu',
          onClick: () => this.sidebar.openMobile(),
        }, icon('menu', { size: 18 })),
        createElement('h2', { id: 'view-title' }, 'All Tasks'),
        createElement('span', { className: 'header-count', id: 'view-count' })
      ),
      createElement('div', { className: 'header-actions' },
        createElement('div', { className: 'search-group' },
        createElement('select', {
          id: 'search-type-select',
          className: 'search-type',
          'aria-label': 'Search by',
          onChange: (e) => {
            const input = document.getElementById('search-input');
            const wantsDate = e.target.value === 'date';
            const hadValue = !!input.value;
            // Date search uses a native date picker; other searches use free text
            if ((input.type === 'date') !== wantsDate) {
              input.type = wantsDate ? 'date' : 'text';
              input.placeholder = wantsDate ? '' : 'Search…';
              input.value = '';
            }
            if (hadValue || input.value) {
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
          icon('search', { size: 15 }),
          createElement('input', {
            type: 'text',
            id: 'search-input',
            placeholder: 'Search…',
            'aria-label': 'Search tasks',
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
        ),
        createElement('button', {
          className: 'btn btn-primary new-task-btn',
          id: 'new-task-btn',
          onClick: () => this.taskList.openNewTaskForm(),
        }, icon('plus'), 'New task')
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
      title: 'New task',
      'aria-label': 'New task',
      onClick: () => this.taskList.openNewTaskForm(),
    }, icon('plus', { size: 22 }));
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
          const vapidPublicKey = 'BFVyvXzPSMJ9BZG4yw3elgHT7w6H8MTqT60eKfr2o1nIaLfmLyh_vc4C8BQ11QyLNnWdpQP-ZntIhHwrDLtiC7Y';
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
    const isTaskView = !['daily-entry', 'daily-report', 'tags', 'groups-manage'].includes(view);
    this._setTaskChrome(isTaskView);

    if (view === 'daily-entry') {
      if (title) title.textContent = 'Daily Tasks';
      await this.dailyLog.renderEntry(this.contentArea);
    } else if (view === 'daily-report') {
      if (title) title.textContent = 'Daily Report';
      await this.dailyLog.renderReport(this.contentArea);
    } else if (view === 'tags') {
      if (title) title.textContent = 'Tags';
      await this.tagManager.render(this.contentArea);
    } else if (view === 'groups-manage') {
      if (title) title.textContent = 'Manage Groups';
      await this.groupManager.render(this.contentArea);
    } else {
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
    this._setTaskChrome(true);

    this.taskList.setGroupFilter(group);
    if (title) title.textContent = group.name;
    await this.taskList.loadTasks();
    this.taskList.render(this.contentArea);
  }

  // Search, task count and new-task buttons only apply to the task views
  _setTaskChrome(visible) {
    document.querySelector('.header-actions')?.classList.toggle('hidden', !visible);
    document.getElementById('fab-new-task')?.classList.toggle('hidden', !visible);
    if (!visible) {
      const count = document.getElementById('view-count');
      if (count) count.textContent = '';
    }
  }

  async refreshContent() {
    const view = this.currentView;
    if (['tags', 'groups-manage', 'daily-entry', 'daily-report'].includes(view)) return;
    await this.taskList.loadTasks();
    if (this.currentView !== view) return; // navigated away while loading
    const body = document.getElementById('task-list-body');
    if (body) this.taskList._renderTaskList(body);
  }
}

// Boot the app
new App();
