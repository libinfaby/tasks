// ============================================================
// Tasks — Auth Module
// ============================================================

import { api } from './api.js';
import { createElement, showToast } from './utils.js';

export function renderLogin(container, onSuccess) {
  container.innerHTML = '';

  const loginContainer = createElement('div', { className: 'login-container' },
    createElement('div', { className: 'login-card' },
      createElement('div', { className: 'login-logo' },
        createElement('img', { src: 'logo.svg', alt: 'tasks', className: 'login-logo-img' }),
        createElement('p', {}, 'Personal Task Manager')
      ),
      createElement('div', { className: 'login-error', id: 'login-error' }),
      createElement('form', {
        className: 'login-form',
        id: 'login-form',
        onSubmit: async (e) => {
          e.preventDefault();
          await handleLogin(onSuccess);
        }
      },
        createElement('div', { className: 'form-group' },
          createElement('label', { for: 'login-username' }, 'Username'),
          createElement('input', {
            type: 'text',
            id: 'login-username',
            className: 'form-input',
            placeholder: 'Enter username',
            autocomplete: 'username',
            required: 'true',
            value: 'admin',
          })
        ),
        createElement('div', { className: 'form-group' },
          createElement('label', { for: 'login-password' }, 'Password'),
          createElement('input', {
            type: 'password',
            id: 'login-password',
            className: 'form-input',
            placeholder: 'Enter password',
            autocomplete: 'current-password',
            required: 'true',
          })
        ),
        createElement('button', {
          type: 'submit',
          className: 'btn btn-primary',
          id: 'login-submit',
          style: { width: '100%', marginTop: '8px', padding: '12px' },
        }, 'Sign In')
      ),
      createElement('div', {
        style: { marginTop: '16px', textAlign: 'center' }
      },
        createElement('p', {
          style: { fontSize: '0.75rem', color: 'var(--text-tertiary)' }
        }, 'API: '),
        createElement('input', {
          type: 'text',
          id: 'api-url-input',
          className: 'form-input',
          style: { marginTop: '4px', fontSize: '0.75rem', textAlign: 'center', width: '100%' },
          value: api.getBaseUrl(),
          placeholder: 'https://api-tasks.libinfaby.dev',
        })
      )
    )
  );

  container.appendChild(loginContainer);
  
  // Focus password since username is pre-filled
  setTimeout(() => {
    document.getElementById('login-password')?.focus();
  }, 100);
}

async function handleLogin(onSuccess) {
  const username = document.getElementById('login-username').value.trim();
  const password = document.getElementById('login-password').value;
  const apiUrl = document.getElementById('api-url-input').value.trim();
  const errorEl = document.getElementById('login-error');
  const submitBtn = document.getElementById('login-submit');

  if (!username || !password) {
    errorEl.textContent = 'Please enter username and password';
    errorEl.classList.add('visible');
    return;
  }

  // Update API URL if changed
  if (apiUrl && apiUrl !== api.getBaseUrl()) {
    api.setBaseUrl(apiUrl);
  }

  submitBtn.textContent = 'Signing in...';
  submitBtn.disabled = true;
  errorEl.classList.remove('visible');

  try {
    await api.login(username, password);
    showToast('Welcome back!', 'success');
    onSuccess();
  } catch (error) {
    errorEl.textContent = error.message;
    errorEl.classList.add('visible');
    submitBtn.textContent = 'Sign In';
    submitBtn.disabled = false;
  }
}
