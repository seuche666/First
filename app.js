'use strict';

const STORAGE_KEY = 'aufgaben.v1';
const $ = (sel) => document.querySelector(sel);

let tasks = load();
let filter = 'all';

function load() {
  try {
    const data = JSON.parse(localStorage.getItem(STORAGE_KEY));
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

function save() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(tasks));
  } catch {
    /* Speicher nicht verfügbar (z. B. privater Modus) – App läuft trotzdem weiter. */
  }
}

function todayISO() {
  const d = new Date();
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().slice(0, 10);
}

function formatDate(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('de-DE', {
    weekday: 'short', day: '2-digit', month: '2-digit',
  });
}

function render() {
  const list = $('#list');
  list.innerHTML = '';

  const today = todayISO();
  const visible = tasks
    .filter((t) => filter === 'all' || (filter === 'open' ? !t.done : t.done))
    .sort((a, b) => (a.done - b.done) || (a.due || '9999').localeCompare(b.due || '9999') || b.created - a.created);

  for (const t of visible) {
    const li = document.createElement('li');
    li.className = 'item' + (t.done ? ' done' : '');

    const cb = document.createElement('input');
    cb.type = 'checkbox';
    cb.checked = t.done;
    cb.setAttribute('aria-label', 'Erledigt');
    cb.addEventListener('change', () => {
      t.done = cb.checked;
      save();
      render();
    });

    const text = document.createElement('span');
    text.className = 'text';
    const title = document.createElement('span');
    title.className = 'title';
    title.textContent = t.title;
    text.append(title);
    if (t.due) {
      const due = document.createElement('span');
      const overdue = !t.done && t.due < today;
      due.className = 'due' + (overdue ? ' overdue' : '');
      due.textContent = (t.due === today ? 'Heute' : formatDate(t.due)) + (overdue ? ' · überfällig' : '');
      text.append(due);
    }

    const del = document.createElement('button');
    del.className = 'del';
    del.textContent = '🗑';
    del.setAttribute('aria-label', 'Löschen');
    del.addEventListener('click', () => {
      tasks = tasks.filter((x) => x.id !== t.id);
      save();
      render();
    });

    li.append(cb, text, del);
    list.append(li);
  }

  const open = tasks.filter((t) => !t.done).length;
  const done = tasks.length - open;
  $('#summary').textContent = tasks.length
    ? `${open} offen · ${done} erledigt`
    : 'Leg los mit deiner ersten Aufgabe';
  $('#empty').hidden = visible.length > 0;
  $('#clearDone').hidden = done === 0;
}

$('#addForm').addEventListener('submit', (e) => {
  e.preventDefault();
  const title = $('#taskInput').value.trim();
  if (!title) return;
  tasks.push({
    id: Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
    title,
    due: $('#dueInput').value || null,
    done: false,
    created: Date.now(),
  });
  $('#taskInput').value = '';
  $('#dueInput').value = '';
  save();
  render();
});

document.querySelectorAll('.filters button').forEach((btn) => {
  btn.addEventListener('click', () => {
    filter = btn.dataset.filter;
    document.querySelectorAll('.filters button').forEach((b) => b.classList.toggle('active', b === btn));
    render();
  });
});

$('#clearDone').addEventListener('click', () => {
  if (!confirm('Alle erledigten Aufgaben löschen?')) return;
  tasks = tasks.filter((t) => !t.done);
  save();
  render();
});

/* ---------- Installation ---------- */
let deferredPrompt = null;
const isStandalone = window.matchMedia('(display-mode: standalone)').matches || navigator.standalone;

window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  deferredPrompt = e;
  $('#installBtn').hidden = false;
});

$('#installBtn').addEventListener('click', async () => {
  if (!deferredPrompt) return;
  deferredPrompt.prompt();
  await deferredPrompt.userChoice;
  deferredPrompt = null;
  $('#installBtn').hidden = true;
});

window.addEventListener('appinstalled', () => { $('#installBtn').hidden = true; });

// iPhone/iPad: Safari zeigt keinen Installations-Dialog, daher ein Hinweis.
const isIOS = /iphone|ipad|ipod/i.test(navigator.userAgent) ||
  (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
let hintDismissed = false;
try { hintDismissed = localStorage.getItem('aufgaben.iosHint') === '1'; } catch { /* ignorieren */ }
if (isIOS && !isStandalone && !hintDismissed) $('#iosHint').hidden = false;
$('#iosHintClose').addEventListener('click', () => {
  $('#iosHint').hidden = true;
  try { localStorage.setItem('aufgaben.iosHint', '1'); } catch { /* ignorieren */ }
});

/* ---------- Offline-Unterstützung ---------- */
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  });
}

render();
