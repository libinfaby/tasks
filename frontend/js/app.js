// ============================================================
// Tasks — App shell: navigation, page transitions, the menu page
// Mirrors the Android app: a back stack of pages, the bottom bar on phones (a navigation drawer
// on wide screens), and Material shared-axis motion between pages.
// ============================================================

import { api } from './api.js';
import { createElement as h, setChildren, tonal } from './utils.js';
import { icon } from './icons.js';
import { brandMark, citem, listRow, topBar } from './ui.js';
import { store } from './store.js';
import { renderLogin } from './auth.js';
import { createTaskListPage } from './components/taskList.js';
import { openTaskEditor } from './components/taskForm.js';
import { createTagsPage } from './components/tagManager.js';
import { createGroupsPage } from './components/groupManager.js';
import { createDailyPage } from './components/dailyLog.js';
import { createSettingsPage } from './components/settings.js';

const TABS = [
  { name: 'all', label: 'All tasks', icon: 'inbox', selectedIcon: 'inboxFilled' },
  { name: 'today', label: 'Today', icon: 'today', selectedIcon: 'todayFilled' },
  { name: 'upcoming', label: 'Upcoming', icon: 'upcoming', selectedIcon: 'upcomingFilled' },
  { name: 'daily', label: 'Daily log', icon: 'dailyLog', selectedIcon: 'dailyLogFilled' },
];
const ROOTS = TABS.map(t => t.name);
const START = { name: 'all' };

const isRoot = (dest) => ROOTS.includes(dest.name);
const sameDest = (a, b) => a.name === b.name && (a.group?.id ?? null) === (b.group?.id ?? null);

// Material emphasized easing (decelerate for what arrives, accelerate for what leaves)
const DECELERATE = 'cubic-bezier(0.05, 0.7, 0.1, 1)';

class App {
  constructor() {
    this.container = document.getElementById('app');
    this.stack = []; // [{ dest, page }]
    this.ignorePops = 0;
    window.addEventListener('auth:logout', () => this.showLogin());
    window.addEventListener('popstate', () => this.onPop());
    this.init();
  }

  async init() {
    if (api.isAuthenticated()) {
      try {
        await api.getMe();
        return this.showApp();
      } catch { /* fall through to sign-in */ }
    }
    this.showLogin();
  }

  showLogin() {
    this.stack = [];
    document.body.classList.add('no-bar');
    setChildren(this.container, );
    renderLogin(this.container, () => this.showApp());
  }

  async showApp() {
    setChildren(this.container, );
    document.body.classList.remove('no-bar');

    this.drawer = h('nav', { className: 'nav-drawer', 'aria-label': 'Navigation' });
    this.host = h('div', { className: 'page-host' });
    this.bar = h('nav', { className: 'bottom-bar', 'aria-label': 'Tabs' },
      ...TABS.map(t => h('button', {
        type: 'button',
        className: 'bar-item',
        dataset: { tab: t.name },
        onClick: () => this.go({ name: t.name }),
      }, h('span', { className: 'indicator' }, icon(t.icon)), h('span', { className: 'label' }, t.label))),
    );
    this.container.append(this.drawer, h('div', { className: 'main' }, this.host, this.bar));

    store.on(() => this.renderDrawer());
    history.replaceState({ tasks: 0 }, '');
    this.go(START);
    await store.loadAll();
    this.initPushNotifications().catch(err => console.error('Failed to init push notifications:', err));
  }

  // ==================== Navigation ====================

  get current() { return this.stack[this.stack.length - 1]; }

  /** A tab replaces the whole stack; any other page opens on top of the current one. */
  go(dest) {
    const cur = this.current;
    if (cur && sameDest(cur.dest, dest)) return;
    const page = this.createPage(dest);
    if (isRoot(dest)) {
      const dropped = this.stack;
      // Forget the history entries of the pages being dropped
      if (dropped.length > 1) { this.ignorePops++; history.go(-(dropped.length - 1)); }
      this.stack = [{ dest, page }];
      this.transition(page, cur?.page, 'fade', dropped.slice(0, -1).map(e => e.page));
    } else {
      this.stack.push({ dest, page });
      history.pushState({ tasks: this.stack.length - 1 }, '');
      this.transition(page, cur?.page, 'forward');
    }
    this.afterNavigate();
  }

  /** Back: close the top page, or return from another tab to All tasks. */
  back() {
    if (this.stack.length > 1) history.back(); // onPop does the work, so the browser's back agrees
    else if (!sameDest(this.current.dest, START)) this.go(START);
  }

  onPop() {
    if (this.ignorePops > 0) { this.ignorePops--; return; }
    if (this.stack.length <= 1) return;
    const leaving = this.stack.pop();
    const showing = this.current;
    this.transition(showing.page, leaving.page, 'back');
    showing.page.onShow?.();
    this.afterNavigate();
  }

  afterNavigate() {
    const dest = this.current.dest;
    const root = isRoot(dest);
    this.bar.classList.toggle('away', !root);
    document.body.classList.toggle('no-bar', !root);
    this.bar.querySelectorAll('.bar-item').forEach(b => {
      const active = b.dataset.tab === dest.name;
      b.classList.toggle('active', active);
      const tab = TABS.find(t => t.name === b.dataset.tab);
      b.querySelector('.indicator').replaceChildren(icon(active ? tab.selectedIcon : tab.icon));
      if (active) b.setAttribute('aria-current', 'page'); else b.removeAttribute('aria-current');
    });
    this.renderDrawer();
  }

  /**
   * Material shared-axis motion: opening a page slides it in from the right over the old one, going
   * back slides it away to the right; switching tabs fades through. Pages share one fixed-size
   * area, so nothing resizes mid-transition.
   */
  transition(incoming, outgoing, kind, alsoRemove = []) {
    const inEl = incoming.el;
    inEl.classList.remove('covered');
    inEl.inert = false;
    if (!inEl.isConnected) this.host.appendChild(inEl);
    if (kind === 'back') this.host.insertBefore(inEl, outgoing?.el || null);
    alsoRemove.forEach(p => { p.el.remove(); p.destroy?.(); });
    if (!outgoing) return;
    const outEl = outgoing.el;
    outEl.inert = true;
    const dur = 400;
    const shift = (sign) => `translateX(${sign * 25}%)`;
    let inFrames, outFrames, inOpts, outOpts;
    if (kind === 'fade') {
      inFrames = [{ opacity: 0, transform: 'scale(0.96)' }, { opacity: 1, transform: 'none' }];
      outFrames = [{ opacity: 1 }, { opacity: 0 }];
      inOpts = { duration: 220, delay: 90, easing: DECELERATE, fill: 'backwards' };
      outOpts = { duration: 90, easing: 'linear', fill: 'forwards' };
    } else {
      const s = kind === 'forward' ? 1 : -1;
      inFrames = [{ transform: shift(s), opacity: 0 }, { transform: 'none', opacity: 1 }];
      outFrames = [{ transform: 'none', opacity: 1 }, { transform: shift(-s), opacity: 0 }];
      inOpts = { duration: dur, easing: DECELERATE };
      outOpts = { duration: dur, easing: DECELERATE, fill: 'forwards' };
    }
    inEl.animate(inFrames, inOpts);
    const anim = outEl.animate(outFrames, outOpts);
    let done = false;
    const cleanup = () => {
      if (done) return;
      done = true;
      anim.cancel();
      // A quick back-and-forth can bring the page back before this runs
      const showingAgain = this.current?.page === outgoing;
      if (kind === 'forward') {
        // Kept (hidden) so going back restores it with its scroll position
        if (!showingAgain) outEl.classList.add('covered');
      } else if (!this.stack.some(e => e.page === outgoing)) {
        outEl.remove();
        outgoing.destroy?.();
      }
    };
    // Finish events can lag in background tabs; the timer makes sure the old page goes
    anim.finished.then(cleanup, cleanup);
    setTimeout(cleanup, (outOpts.duration || 0) + 100);
  }

  createPage(dest) {
    const nav = {
      go: (d) => this.go(d),
      back: () => this.back(),
      openTask: (task) => this.openEditor(task),
      newTask: () => this.openEditor(null),
      openMenu: () => this.go({ name: 'menu' }),
    };
    switch (dest.name) {
      case 'all': case 'today': case 'upcoming':
        return createTaskListPage({ view: dest.name, nav });
      case 'group':
        return createTaskListPage({ view: 'group', group: dest.group, nav });
      case 'daily': return createDailyPage({ nav });
      case 'menu': return this.createMenuPage(nav);
      case 'tags': return createTagsPage({ nav });
      case 'groups': return createGroupsPage({ nav });
      case 'settings': return createSettingsPage({ nav });
      default: return createTaskListPage({ view: 'all', nav });
    }
  }

  openEditor(task) {
    openTaskEditor(task, {
      onSaved: () => {
        this.stack.forEach(e => e.page.refresh?.());
        store.loadGroups(); // task counts
      },
    });
  }

  // ==================== Drawer & menu ====================

  renderDrawer() {
    if (!this.drawer || !this.current) return;
    const dest = this.current.dest;
    const item = (label, iconName, d, { active = false, trailing = null, lead = null } = {}) => h('button', {
      type: 'button',
      className: `drawer-item interactive${active ? ' active' : ''}`,
      'aria-current': active ? 'page' : null,
      onClick: () => this.go(d),
    }, lead || icon(iconName), h('span', { className: 'label label-large' }, label), trailing);
    setChildren(this.drawer,
      h('div', { className: 'drawer-brand' }, brandMark(40), h('span', { className: 'headline-small' }, 'Tasks')),
      ...TABS.map(t => {
        const active = dest.name === t.name;
        return item(t.label, active ? t.selectedIcon : t.icon, { name: t.name }, { active });
      }),
      store.groups.length ? h('div', { className: 'drawer-label title-small muted' }, 'Your groups') : null,
      ...store.groups.map(g => {
        const t = tonal(g.color);
        return item(g.name, null, { name: 'group', group: g }, {
          active: dest.name === 'group' && dest.group?.id === g.id,
          lead: h('span', { className: `group-tile tile-tonal ${t.className}`, style: t.style }, icon('groupFilled', { size: 18 })),
          trailing: h('span', { className: 'count' }, String(g.active_task_count || 0)),
        });
      }),
      h('div', { className: 'drawer-label title-small muted' }, 'Manage'),
      item('Tags', 'tagFilled', { name: 'tags' }, { active: dest.name === 'tags' }),
      item('Groups', 'groupFilled', { name: 'groups' }, { active: dest.name === 'groups' }),
      item('Settings', 'settings', { name: 'settings' }, { active: dest.name === 'settings' }),
    );
  }

  /** Everything the drawer holds, as a full page on phones: groups, then Tags, Groups and Settings. */
  createMenuPage(nav) {
    const inner = h('div', { className: 'page-inner' });
    const el = h('div', { className: 'page' }, topBar('', nav.back), inner);
    const render = () => {
      const menuRow = (title, supporting, iconName, d) => citem(listRow({ title, supporting, icon: iconName }), { onClick: () => nav.go(d) });
      setChildren(inner,
        h('div', { className: 'brand-row' }, brandMark(52), h('span', { className: 'display-small' }, 'Tasks')),
        ...(store.groups.length ? [
          h('div', { className: 'menu-label title-small muted' }, 'Your groups'),
          h('div', { className: 'connected' }, ...store.groups.map(g => {
            const t = tonal(g.color);
            return citem(listRow({
              title: g.name,
              icon: 'groupFilled',
              tile: `group-shape tile-tonal ${t.className}`,
              tileStyle: t.style,
              trailing: h('span', { className: 'label-large' }, String(g.active_task_count || 0)),
            }), { onClick: () => nav.go({ name: 'group', group: g }) });
          })),
          h('div', { style: { height: '28px' } }),
        ] : []),
        h('div', { className: 'menu-label title-small muted' }, 'Manage'),
        h('div', { className: 'connected' },
          menuRow('Tags', 'Tag types and their colours', 'tagFilled', { name: 'tags' }),
          menuRow('Groups', 'Create, rename and recolour', 'groupFilled', { name: 'groups' }),
          menuRow('Settings', 'Theme and default group', 'settings', { name: 'settings' }),
        ),
      );
    };
    render();
    const off = store.on(render);
    return { el, destroy: off };
  }

  // ==================== Push notifications ====================

  async initPushNotifications() {
    if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
      console.warn('Push messaging is not supported in this browser');
      return;
    }
    const registration = await navigator.serviceWorker.register('sw.js');

    // Request notification permission if not already granted
    if (Notification.permission === 'default') {
      const permission = await Notification.requestPermission();
      if (permission !== 'granted') return;
    }

    if (Notification.permission === 'granted') {
      let subscription = await registration.pushManager.getSubscription();
      // If no subscription exists, subscribe the user
      if (!subscription) {
        const vapidPublicKey = 'BFVyvXzPSMJ9BZG4yw3elgHT7w6H8MTqT60eKfr2o1nIaLfmLyh_vc4C8BQ11QyLNnWdpQP-ZntIhHwrDLtiC7Y';
        subscription = await registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: urlBase64ToUint8Array(vapidPublicKey),
        });
        // Send subscription to backend
        await api.request('/tasks/subscribe', { method: 'POST', body: JSON.stringify(subscription) });
      }
    }
  }
}

function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - base64String.length % 4) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const rawData = window.atob(base64);
  return Uint8Array.from(rawData, c => c.charCodeAt(0));
}

// Boot the app
new App();
