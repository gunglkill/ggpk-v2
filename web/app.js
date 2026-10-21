/* Расписание ГГПК — логика интерфейса */
(function () {
  'use strict';

  var MONTHS = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня',
    'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'];
  var WEEKDAYS = ['Воскресенье', 'Понедельник', 'Вторник', 'Среда',
    'Четверг', 'Пятница', 'Суббота'];
  var CHECK = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" ' +
    'stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.6l4.4 4.4L19 7.6"/></svg>';

  function $(sel) { return document.querySelector(sel); }

  function closest(el, sel) {
    while (el && el.nodeType === 1) {
      if (el.matches ? el.matches(sel) : false) return el;
      el = el.parentNode;
    }
    return null;
  }

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }

  function pad(n) { return (n < 10 ? '0' : '') + n; }

  function isoDate(d) {
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  }

  function addDays(d, n) {
    var c = new Date(d.getTime());
    c.setDate(c.getDate() + n);
    return c;
  }

  function dayLabel(d) {
    return WEEKDAYS[d.getDay()] + ', ' + d.getDate() + ' ' + MONTHS[d.getMonth()];
  }

  /* ---------- общее: тема, звонки, тосты ---------- */
  function toast(text) {
    var el = document.getElementById('toast');
    if (!el) return;
    el.textContent = text;
    el.classList.add('on');
    if (window.__toastTimer) clearTimeout(window.__toastTimer);
    window.__toastTimer = setTimeout(function () { el.classList.remove('on'); }, 4000);
  }
  window.ggpkToast = toast;

  var th = document.getElementById('th');
  if (th) {
    th.addEventListener('click', function () {
      var root = document.documentElement;
      var next = root.getAttribute('data-theme') === 'light' ? 'dark' : 'light';
      root.setAttribute('data-theme', next);
      try { localStorage.setItem('ggpk.theme', next); } catch (e) {}
    });
  }

  var bells = document.getElementById('bells');
  var bellsModal = document.getElementById('bellsModal');
  if (bells && bellsModal) {
    var showBells = function () { bellsModal.classList.add('on'); };
    var hideBells = function () { bellsModal.classList.remove('on'); };
    bells.addEventListener('click', showBells);
    bellsModal.addEventListener('click', function (e) {
      if (e.target === bellsModal || closest(e.target, '#bellsClose')) hideBells();
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') hideBells();
    });
  }

  /* ---------- расписание ---------- */
  var CFG = window.__GGPK;
  if (!CFG) return;

  var state = {
    tab: 'today',
    parity: 'n',
    data: null,
    rows: [],
    timer: null,
    lastStatus: ''
  };
  var tabsEl = document.getElementById('tabs');
  var bodyEl = document.getElementById('body');
  var statusEl = null;
  var failed = false;

  try {
    var qs = new URLSearchParams(location.search);
    var tb = qs.get('tab');
    if (tb === 'today' || tb === 'tomorrow' || tb === 'week') state.tab = tb;
    if (qs.get('parity') === 'z') state.queryParity = 'z';
  } catch (e) {}

  function fail() {
    if (failed || state.data) return;
    failed = true;
    if (bodyEl) {
      bodyEl.innerHTML = '<div class="empty">Не удалось загрузить расписание. ' +
        'Проверьте подключение и обновите страницу.</div>';
    }
  }

  function table(rows, ch, off, mode) {
    if (!rows || !rows.length) return '<div class="empty">Пар нет.</div>';
    ch = ch || [];
    off = off || [];
    var h = '<div class="tbl' + (CFG.no_time ? ' no-time' : '') + '">' +
      '<div class="trow thead"><span class="c-n">№</span><span class="c-t">ВРЕМЯ</span>' +
      '<span>ПАРА</span><span class="c-r">АУД.</span></div>';
    for (var i = 0; i < rows.length; i++) {
      var r = rows[i];
      var pair = r[0];
      var time = r[1] || '—';
      var subject = r[2] || '';
      var room = r[3] || '';
      var teacher = r[4] || '';
      var cls = 'trow';
      if (off.indexOf(pair) >= 0) cls += ' off';
      h += '<div class="' + cls + '">';
      h += '<span class="c-n">' + esc(pair) + '</span>';
      h += '<span class="c-t">' + (time === '—' ? '—' : time) + '</span>';
      if (mode === 't') {
        h += '<span class="c-s"><span class="gb">' + esc(r[2]) + '</span>' + esc(r[3]) + '</span>';
        h += '<span class="c-r">' + esc(r[4] || '—') + '</span>';
      } else {
        h += '<span class="c-s">' + esc(subject) +
          (teacher ? '<span class="tt">' + esc(teacher) + '</span>' : '') + '</span>';
        h += '<span class="c-r">' + esc(room || '—') + '</span>';
      }
      h += '</div>';
    }
    return h + '</div>';
  }

  function tabsHTML() {
    var t = [['today', 'Сегодня'], ['tomorrow', 'Завтра'], ['week', 'Неделя']];
    var h = '';
    for (var i = 0; i < t.length; i++) {
      h += '<button data-tab="' + t[i][0] + '" class="' +
        (state.tab === t[i][0] ? 'on' : '') + '">' + t[i][1] + '</button>';
    }
    return h;
  }

  function parityTabs() {
    return '<div class="tabs two">' +
      '<button data-parity="n" class="' + (state.parity === 'n' ? 'on' : '') + '">Числитель</button>' +
      '<button data-parity="z" class="' + (state.parity === 'z' ? 'on' : '') + '">Знаменатель</button>' +
      '</div>';
  }

  function renderDay(offset) {
    var d = addDays(new Date(), offset);
    var key = isoDate(d);
    var e = state.data.days ? state.data.days[key] : null;
    state.rows = [];

    if (d.getDay() === 0) {
      return '<div class="day-h">' + dayLabel(d) + '</div>' +
        '<div class="empty">Воскресенье. Занятий нет.</div>';
    }
    if (!e) {
      return '<div class="day-h">' + dayLabel(d) + '</div>' +
        '<div class="empty">Расписание на эту дату ещё не опубликовано.</div>';
    }
    state.rows = e.rows || [];
    var h = '<div class="day-h">' + esc(e.label || dayLabel(d)) + '</div>' +
      '<div class="meta">' + esc(CFG.name) + ' · неделя ' + e.week + ' · ' + esc(e.kind) + '</div>';
    if (offset === 0) h += '<div id="status"></div>';
    h += '<div class="notice' + (e.status === 'changed' ? ' changed' : '') + '">' +
      (e.status === 'changed' ? 'Учтены изменения к расписанию.' : 'Без изменений.') + '</div>';
    h += table(e.rows, e.ch, e.off, CFG.mode);
    return h;
  }

  function renderWeek() {
    var days = (state.data.week && state.data.week[state.parity]) || [];
    var h = parityTabs();
    var any = false;
    for (var i = 0; i < days.length; i++) {
      var d = days[i];
      if (!d.rows || !d.rows.length) continue;
      any = true;
      h += '<div class="day-h">' + esc(d.day) + ', ' + esc(d.date) + '</div>';
      h += table(d.rows, [], [], CFG.mode);
    }
    if (!any) h += '<div class="empty">Пар нет.</div>';
    return h;
  }

  /* ---------- индикатор пары ---------- */
  function parseSlots(rows) {
    var out = [];
    for (var i = 0; i < rows.length; i++) {
      var raw = String(rows[i][1] || '');
      if (!raw || raw === '—') continue;
      var parts = raw.replace(/\u2013/g, '-').split('<br>');
      for (var j = 0; j < parts.length; j++) {
        var m = parts[j].match(/^(\d{1,2}):(\d{2})-(\d{1,2}):(\d{2})$/);
        if (m) {
          out.push({
            pair: rows[i][0],
            a: (+m[1]) * 60 + (+m[2]),
            b: (+m[3]) * 60 + (+m[4])
          });
        }
      }
    }
    out.sort(function (x, y) { return x.a - y.a; });
    return out;
  }

  function clock(seconds) {
    var s = Math.max(0, Math.round(seconds));
    var h = Math.floor(s / 3600);
    var m = Math.floor((s % 3600) / 60);
    var r = s % 60;
    return h > 0 ? h + ':' + pad(m) + ':' + pad(r) : pad(m) + ':' + pad(r);
  }

  function statusHTML() {
    var slots = parseSlots(state.rows || []);
    if (!slots.length) return '';
    var now = new Date();
    var cur = now.getHours() * 60 + now.getMinutes() + now.getSeconds() / 60;

    if (cur < slots[0].a) {
      return pill('До начала занятий', slots[0].a - cur);
    }
    var last = slots[slots.length - 1];
    if (cur >= last.b) return done();

    for (var i = 0; i < slots.length; i++) {
      var s = slots[i];
      if (cur >= s.a && cur < s.b) {
        var next = slots[i + 1];
        var label = (next && next.pair === s.pair) ? 'До перемены' : 'До конца пары';
        return pill(label, s.b - cur);
      }
    }
    for (i = 0; i < slots.length; i++) {
      if (slots[i].a > cur) return pill('До ' + slots[i].pair + '-й пары', slots[i].a - cur);
    }
    return done();
  }

  function pill(label, minutes) {
    return '<div class="status"><span class="pill"><span>' + esc(label) +
      '</span><b>' + clock(minutes * 60) + '</b></span></div>';
  }

  function done() {
    return '<div class="status"><span class="ok">' + CHECK + '</span><span>Занятия окончены</span></div>';
  }

  function tick() {
    if (!statusEl || state.tab !== 'today') return;
    var html = statusHTML();
    if (html && html !== state.lastStatus) {
      statusEl.innerHTML = html;
      state.lastStatus = html;
    }
  }

  function startTimer() {
    statusEl = document.getElementById('status');
    state.lastStatus = '';
    tick();
    if (state.timer) clearInterval(state.timer);
    state.timer = setInterval(tick, 1000);
  }

  function stopTimer() {
    if (state.timer) clearInterval(state.timer);
    state.timer = null;
    statusEl = null;
  }

  function paint() {
    if (!bodyEl) return;
    if (tabsEl) tabsEl.innerHTML = tabsHTML();
    if (state.tab === 'week') {
      stopTimer();
      bodyEl.innerHTML = renderWeek();
      return;
    }
    bodyEl.innerHTML = renderDay(state.tab === 'today' ? 0 : 1);
    if (state.tab === 'today') startTimer();
    else stopTimer();
  }

  if (tabsEl) {
    tabsEl.addEventListener('click', function (e) {
      var btn = closest(e.target, 'button[data-tab]');
      if (!btn) return;
      state.tab = btn.getAttribute('data-tab');
      paint();
    });
  }
  if (bodyEl) {
    bodyEl.addEventListener('click', function (e) {
      var btn = closest(e.target, 'button[data-parity]');
      if (!btn) return;
      state.parity = btn.getAttribute('data-parity');
      paint();
    });
  }

  /* ---------- загрузка ---------- */
  var xhr = new XMLHttpRequest();
  xhr.open('GET', CFG.url + '?v=' + encodeURIComponent(CFG.ver || ''), true);
  xhr.timeout = 12000;
  xhr.onload = function () {
    if (xhr.status < 200 || xhr.status >= 300) return fail();
    try {
      state.data = JSON.parse(xhr.responseText);
      state.parity = state.queryParity || (state.data.parity_default === 'z' ? 'z' : 'n');
      paint();
    } catch (e) {
      fail();
    }
  };
  xhr.onerror = fail;
  xhr.ontimeout = fail;
  xhr.send();
  setTimeout(fail, 9000);
})();
