// ============================================================
// Tasks — Sign in
// ============================================================

import { api } from './api.js';
import { createElement as h } from './utils.js';
import { brandMark, textField } from './ui.js';

export function renderLogin(container, onSuccess) {
  const { el: passwordField, input: password } = textField({
    label: 'Password',
    type: 'password',
    icon: 'lock',
    attrs: { autocomplete: 'current-password', id: 'login-password', required: true },
  });
  const error = h('div', { className: 'login-error hidden', role: 'alert' });
  const submit = h('button', { type: 'submit', className: 'btn btn-filled btn-lg btn-block interactive', disabled: true }, 'Sign in');
  password.addEventListener('input', () => { submit.disabled = !password.value; });

  const form = h('form', {
    className: 'login-column',
    onSubmit: async (e) => {
      e.preventDefault();
      if (!password.value) return;
      submit.disabled = true;
      submit.textContent = 'Signing in…';
      error.classList.add('hidden');
      try {
        // Single user: the API only accepts "admin"
        await api.login('admin', password.value);
        onSuccess();
      } catch (err) {
        error.textContent = err.message;
        error.classList.remove('hidden');
        submit.textContent = 'Sign in';
        submit.disabled = false;
      }
    },
  },
    h('div', { style: { height: '48px' } }),
    brandMark(80),
    h('div', { className: 'login-heading' },
      h('h1', { className: 'display-small' }, 'Welcome back'),
      h('p', { className: 'body-large muted' }, 'Sign in to pick up where you left off.'),
    ),
    error,
    passwordField,
    submit,
  );

  container.appendChild(h('div', { className: 'login-screen' }, form));
  setTimeout(() => password.focus(), 100);
}
