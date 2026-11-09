'use strict';

const STORAGE_KEY = 'fahrtenbuch.v1';
const SETTINGS_KEY = 'fahrtenbuch.settings';
const $ = (sel) => document.querySelector(sel);

let trips = loadJSON(STORAGE_KEY, []);
let settings = loadJSON(SETTINGS_KEY, { driver: '' });
let editingId = null;

/* ---------- Speicher ---------- */
function loadJSON(key, fallback) {
  try {
    const v = JSON.parse(localStorage.getItem(key));
    return v ?? fallback;
  } catch {
    return fallback;
  }
}
function saveJSON(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    toast('Speichern nicht möglich (Browser-Speicher gesperrt?)');
  }
}
const saveTrips = () => saveJSON(STORAGE_KEY, trips);

/* ---------- Hilfsfunktionen ---------- */
const nf = new Intl.NumberFormat('de-DE');

function nowLocal() {
  const d = new Date();
  d.setSeconds(0, 0);
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().slice(0, 16); // für <input type="datetime-local">
}
const toDate = (local) => new Date(local);
const distance = (t) => (t.kmEnd != null && t.kmStart != null ? Math.max(0, t.kmEnd - t.kmStart) : 0);
const minutes = (t) => (t.end ? Math.max(0, Math.round((toDate(t.end) - toDate(t.start)) / 60000)) : 0);
const isRunning = (t) => !t.end || t.kmEnd == null;

function fmtDuration(min) {
  return `${Math.floor(min / 60)}:${String(min % 60).padStart(2, '0')}`;
}
function fmtDateTime(local) {
  return toDate(local).toLocaleString('de-DE', {
    weekday: 'short', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
  });
}
function fmtTime(local) {
  return toDate(local).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
}
const monthKey = (local) => local.slice(0, 7);
function monthLabel(key) {
  const [y, m] = key.split('-').map(Number);
  return new Date(y, m - 1, 1).toLocaleDateString('de-DE', { month: 'long', year: 'numeric' });
}
function lastTrip() {
  return [...trips].sort((a, b) => b.start.localeCompare(a.start))[0];
}

let toastTimer;
function toast(msg) {
  const el = $('#toast');
  el.textContent = msg;
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, 2500);
}

/* ---------- Anzeige ---------- */
function render() {
  const sorted = [...trips].sort((a, b) => b.start.localeCompare(a.start));
  const running = sorted.find(isRunning);

  // Laufende Fahrt
  $('#activeCard').hidden = !running;
  $('#startTripBtn').hidden = !!running;
  if (running) {
    $('#activeRoute').textContent = `${running.from}${running.to ? ' → ' + running.to : ' → …'}`;
    $('#activeInfo').textContent =
      `seit ${fmtDateTime(running.start)} · km ${nf.format(running.kmStart)}` +
      (running.plate ? ` · ${running.plate}` : '');
    $('#endTripBtn').onclick = () => openTrip(running.id, { finishing: true });
  }

  // Monatsstatistik
  const cur = monthKey(nowLocal());
  const monthTrips = trips.filter((t) => monthKey(t.start) === cur && !isRunning(t));
  $('#statKm').textContent = nf.format(monthTrips.reduce((s, t) => s + distance(t), 0));
  $('#statTrips').textContent = monthTrips.length;
  $('#statHours').textContent = fmtDuration(monthTrips.reduce((s, t) => s + minutes(t), 0));

  // Liste nach Monaten
  const list = $('#list');
  list.innerHTML = '';
  const groups = new Map();
  for (const t of sorted) {
    const k = monthKey(t.start);
    if (!groups.has(k)) groups.set(k, []);
    groups.get(k).push(t);
  }
  for (const [key, items] of groups) {
    const sec = document.createElement('section');
    sec.className = 'month';
    const head = document.createElement('div');
    head.className = 'month-head';
    const title = document.createElement('strong');
    title.textContent = monthLabel(key);
    const sum = document.createElement('span');
    sum.textContent = `${items.length} Fahrten · ${nf.format(items.reduce((s, t) => s + distance(t), 0))} km`;
    head.append(title, sum);

    const ul = document.createElement('ul');
    ul.className = 'trips';
    for (const t of items) {
      const li = document.createElement('li');
      const btn = document.createElement('button');
      btn.className = 'trip' + (isRunning(t) ? ' running' : '');
      btn.addEventListener('click', () => openTrip(t.id));

      const route = document.createElement('span');
      route.className = 'route';
      route.textContent = `${t.from} → ${t.to || '…'}`;
      const km = document.createElement('span');
      km.className = 'km';
      km.textContent = isRunning(t) ? 'läuft' : `${nf.format(distance(t))} km`;
      const meta = document.createElement('span');
      meta.className = 'meta';
      const parts = [fmtDateTime(t.start) + (t.end ? ` – ${fmtTime(t.end)}` : '')];
      if (t.end) parts.push(`${fmtDuration(minutes(t))} h`);
      if (t.plate) parts.push(t.plate);
      if (t.order) parts.push(t.order);
      meta.textContent = parts.join(' · ');

      btn.append(route, km, meta);
      li.append(btn);
      ul.append(li);
    }
    sec.append(head, ul);
    list.append(sec);
  }
  $('#empty').hidden = trips.length > 0;
}

/* ---------- Formular ---------- */
const form = $('#tripForm');
const dialog = $('#tripDialog');

function openTrip(id, { finishing = false, starting = false } = {}) {
  editingId = id;
  form.reset();
  $('#formError').hidden = true;
  const t = trips.find((x) => x.id === id);

  if (t) {
    form.plate.value = t.plate || '';
    form.from.value = t.from;
    form.to.value = t.to || '';
    form.start.value = t.start;
    form.end.value = t.end || '';
    form.kmStart.value = t.kmStart ?? '';
    form.kmEnd.value = t.kmEnd ?? '';
    form.order.value = t.order || '';
    form.note.value = t.note || '';
  } else {
    // Neue Fahrt: Werte der letzten Fahrt übernehmen
    const last = lastTrip();
    form.plate.value = last?.plate || '';
    form.from.value = last?.to || '';
    form.start.value = nowLocal();
    form.kmStart.value = last?.kmEnd ?? '';
  }
  if (finishing && !form.end.value) form.end.value = nowLocal();

  $('#tripTitle').textContent = finishing ? 'Fahrt beenden' : starting ? 'Fahrt starten' : t ? 'Fahrt bearbeiten' : 'Fahrt nachtragen';
  $('#deleteTripBtn').hidden = !t;
  dialog.showModal();

  const focusField = finishing ? form.kmEnd : !form.from.value ? form.from : starting ? form.to : null;
  if (focusField) setTimeout(() => focusField.focus(), 50);
}

function readNumber(input) {
  return input.value === '' ? null : Math.round(Number(input.value));
}

form.addEventListener('submit', (e) => {
  e.preventDefault();
  const data = {
    plate: form.plate.value.trim().toUpperCase(),
    from: form.from.value.trim(),
    to: form.to.value.trim(),
    start: form.start.value,
    end: form.end.value || null,
    kmStart: readNumber(form.kmStart),
    kmEnd: readNumber(form.kmEnd),
    order: form.order.value.trim(),
    note: form.note.value.trim(),
  };

  let err = '';
  if (!data.from) err = 'Bitte einen Startort eingeben.';
  else if (!data.start) err = 'Bitte die Abfahrtszeit eingeben.';
  else if (data.kmStart == null) err = 'Bitte den km-Stand bei Abfahrt eingeben.';
  else if (data.end && data.end <= data.start) err = 'Die Ankunft muss nach der Abfahrt liegen.';
  else if (data.kmEnd != null && data.kmEnd < data.kmStart) err = 'Der km-Stand am Ende ist kleiner als am Start.';
  if (err) {
    $('#formError').textContent = err;
    $('#formError').hidden = false;
    return;
  }

  if (editingId) {
    Object.assign(trips.find((t) => t.id === editingId), data);
  } else {
    trips.push({ id: Date.now().toString(36) + Math.random().toString(36).slice(2, 6), ...data });
  }
  saveTrips();
  dialog.close();
  render();
  toast(isRunning(data) ? 'Fahrt gespeichert – gute Fahrt!' : `Gespeichert: ${nf.format(distance(data))} km`);
});

$('#cancelBtn').addEventListener('click', () => dialog.close());
$('#deleteTripBtn').addEventListener('click', () => {
  if (!confirm('Diese Fahrt wirklich löschen?')) return;
  trips = trips.filter((t) => t.id !== editingId);
  saveTrips();
  dialog.close();
  render();
  toast('Fahrt gelöscht');
});

$('#startTripBtn').addEventListener('click', () => openTrip(null, { starting: true }));
$('#addTripBtn').addEventListener('click', () => openTrip(null));

/* ---------- Menü: Export & Sicherung ---------- */
const menu = $('#menuDialog');
$('#menuBtn').addEventListener('click', () => {
  $('#driverName').value = settings.driver || '';
  menu.showModal();
});
$('#driverName').addEventListener('input', () => {
  settings.driver = $('#driverName').value.trim();
  saveJSON(SETTINGS_KEY, settings);
});
$('#menuClose').addEventListener('click', () => menu.close());

function download(filename, content, type) {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function csvCell(v) {
  const s = v == null ? '' : String(v);
  return /[";\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

$('#exportCsvBtn').addEventListener('click', () => {
  if (!trips.length) return toast('Keine Fahrten zum Exportieren.');
  const header = ['Fahrer', 'Kennzeichen', 'Abfahrt', 'Ankunft', 'Startort', 'Zielort',
    'km Start', 'km Ende', 'Strecke km', 'Dauer h', 'Auftrag/Kunde', 'Notiz'];
  // "2026-11-03T05:30" -> "03.11.2026 05:30"
  const fmt = (local) => (local ? local.replace(/^(\d{4})-(\d{2})-(\d{2})T/, '$3.$2.$1 ') : '');
  const rows = [...trips]
    .sort((a, b) => a.start.localeCompare(b.start))
    .map((t) => [settings.driver, t.plate, fmt(t.start), fmt(t.end), t.from, t.to,
      t.kmStart, t.kmEnd, isRunning(t) ? '' : distance(t), t.end ? fmtDuration(minutes(t)) : '',
      t.order, t.note]);
  // Semikolon + BOM, damit Excel (deutsch) Spalten und Umlaute richtig erkennt
  const csv = '﻿' + [header, ...rows].map((r) => r.map(csvCell).join(';')).join('\r\n');
  download(`fahrtenbuch-${nowLocal().slice(0, 10)}.csv`, csv, 'text/csv;charset=utf-8');
});

$('#backupBtn').addEventListener('click', () => {
  const data = JSON.stringify({ app: 'fahrtenbuch', version: 1, settings, trips }, null, 2);
  download(`fahrtenbuch-sicherung-${nowLocal().slice(0, 10)}.json`, data, 'application/json');
});

$('#restoreInput').addEventListener('change', async (e) => {
  const file = e.target.files[0];
  e.target.value = '';
  if (!file) return;
  try {
    const data = JSON.parse(await file.text());
    if (data.app !== 'fahrtenbuch' || !Array.isArray(data.trips)) throw new Error();
    if (!confirm(`Sicherung mit ${data.trips.length} Fahrten laden? Die aktuellen Daten werden ersetzt.`)) return;
    trips = data.trips;
    settings = { ...settings, ...(data.settings || {}) };
    saveTrips();
    saveJSON(SETTINGS_KEY, settings);
    menu.close();
    render();
    toast('Sicherung geladen');
  } catch {
    toast('Diese Datei ist keine gültige Fahrtenbuch-Sicherung.');
  }
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

const isIOS = /iphone|ipad|ipod/i.test(navigator.userAgent) ||
  (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
if (isIOS && !isStandalone && !loadJSON('fahrtenbuch.iosHint', false)) $('#iosHint').hidden = false;
$('#iosHintClose').addEventListener('click', () => {
  $('#iosHint').hidden = true;
  saveJSON('fahrtenbuch.iosHint', true);
});

/* ---------- Offline ---------- */
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  });
}

render();
