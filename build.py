# -*- coding: utf-8 -*-
"""Сборка сайта расписания ГГПК (новая версия).

Берёт main.pdf и changes/*.pdf, собирает статические страницы и JSON.
Запускается в GitHub Actions, результат уходит на GitHub Pages.
"""
import html
import json
import os
import re
import shutil
import sys
from datetime import date, datetime, timedelta, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import schedule_core as sc  # noqa: E402

OUT = os.path.join(HERE, 'site')
WEB = os.path.join(HERE, 'web')
MSK = timezone(timedelta(hours=5))
VER = datetime.now(MSK).strftime('%Y%m%d%H%M')
UPDATED = datetime.now(MSK).strftime('%d.%m %H:%M')
HORIZON = 10

VK = 'https://vk.ru/club216202806'
TG = 'https://t.me/+p3brK1bafQplNmNi'
DONATE = 'https://dalink.to/mori_iuvenis'
RELEASES = 'https://github.com/gunglkill/ggpk/releases/latest'

MONTHS = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля',
          'августа', 'сентября', 'октября', 'ноября', 'декабря']
WEEKDAYS = ['Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота']

BELLS = {
    'Ш': {
        'week': {1: [('8:30', '9:15'), ('9:20', '10:05')],
                 2: [('10:15', '11:00'), ('11:20', '12:05')],
                 3: [('12:15', '13:00'), ('13:20', '14:05')],
                 4: [('14:15', '15:00'), ('15:05', '15:50')]},
        'sat': {1: [('8:30', '9:10'), ('9:15', '9:55')],
                2: [('10:05', '10:45'), ('11:00', '11:40')],
                3: [('11:50', '12:30'), ('12:35', '13:15')]},
    },
    'М': {
        'week': {1: [('8:30', '9:15'), ('9:20', '10:05')],
                 2: [('10:30', '11:15'), ('11:20', '12:05')],
                 3: [('12:30', '13:15'), ('13:20', '14:05')],
                 4: [('14:15', '15:00'), ('15:05', '15:50')]},
        'sat': {1: [('8:30', '9:10'), ('9:15', '9:55')],
                2: [('10:20', '11:00'), ('11:05', '11:45')],
                3: [('11:50', '12:30'), ('12:35', '13:15')]},
    },
}

TRANS = {
    'а': 'a', 'б': 'b', 'в': 'v', 'г': 'g', 'д': 'd', 'е': 'e', 'ё': 'e',
    'ж': 'zh', 'з': 'z', 'и': 'i', 'й': 'y', 'к': 'k', 'л': 'l', 'м': 'm',
    'н': 'n', 'о': 'o', 'п': 'p', 'р': 'r', 'с': 's', 'т': 't', 'у': 'u',
    'ф': 'f', 'х': 'h', 'ц': 'c', 'ч': 'ch', 'ш': 'sh', 'щ': 'sch', 'ъ': '',
    'ы': 'y', 'ь': '', 'э': 'e', 'ю': 'yu', 'я': 'ya',
}


def slug(text):
    out = []
    for ch in (text or '').lower():
        if ch in TRANS:
            out.append(TRANS[ch])
        elif ch.isalnum() and ord(ch) < 128:
            out.append(ch)
        else:
            out.append('-')
    s = re.sub(r'-+', '-', ''.join(out)).strip('-')
    return s or 'x'


def building(room):
    r = (room or '').strip()
    if not r:
        return None
    ch = r[-1].upper()
    return ch if ch in ('М', 'Ш') else None


def time_html(pair, rooms, weekday, no_time):
    """Время пары. У 0-й и 5-й пар времени нет — прочерк."""
    if no_time or pair == '*' or not isinstance(pair, int) or pair in (0, 5):
        return '—'
    if not rooms:
        return '—'
    b = building(rooms[0])
    if not b:
        return '—'
    slots = BELLS[b]['sat' if weekday == 5 else 'week'].get(pair)
    if not slots:
        return '—'
    return '<br>'.join('%s–%s' % s for s in slots)


# ---------------- данные ----------------
def label(d):
    name = WEEKDAYS[d.weekday()] if d.weekday() < 6 else 'Воскресенье'
    return '%s, %d %s' % (name, d.day, MONTHS[d.month - 1])


def load_changes(folder):
    out = {}
    if not os.path.isdir(folder):
        return out
    for f in sorted(os.listdir(folder)):
        if not f.lower().endswith('.pdf'):
            continue
        m = re.match(r'(\d{4})-(\d{2})-(\d{2})', f)
        if m:
            iso = '%s-%s-%s' % (m.group(1), m.group(2), m.group(3))
        else:
            m = re.match(r'(\d{2})\.(\d{2})\.(\d{4})', f)
            if not m:
                continue
            iso = '%s-%s-%s' % (m.group(3), m.group(2), m.group(1))
        try:
            out[iso] = sc.parse_changes(os.path.join(folder, f))
        except Exception as e:  # noqa: BLE001
            print('изменения %s не разобраны: %s' % (f, e))
    return out


def group_patch(changes, name):
    if not changes:
        return None
    c = sc.canon(name)
    for key, val in changes.items():
        if sc.canon(key) == c:
            return val
    return None


def group_cell(cells, pair, den):
    c = cells.get(pair)
    if not c:
        return None
    if den and c.get('subject_den'):
        return c.get('subject_den', ''), c.get('teacher_den', ''), c.get('room_den', '')
    return c.get('subject', ''), c.get('teacher', ''), c.get('room', '')


def merge_day(cells, den, patch, weekday, no_time):
    """День из основного расписания + изменения.

    -> rows [[pair, время, предмет, аудитория, преподаватель]],
       ch [номера изменённых пар], off [номера отменённых пар]
    """
    rows = {}
    for pair in sorted(cells):
        s, t, r = group_cell(cells, pair, den)
        if not s:
            continue
        rows[pair] = {
            's': s,
            't': sc.clean_teachers(t),
            'r': sc.split_rooms(r),
            'ch': False,
            'off': False,
        }

    if patch:
        for key, p in patch.items():
            if key == '*':
                rows = {'*': {
                    's': p.get('subject') or '—',
                    't': p.get('teachers') or [],
                    'r': p.get('rooms') or [],
                    'ch': True,
                    'off': bool(p.get('cancel')),
                }}
                continue
            pair = int(key)
            cur = rows.get(pair)
            if p.get('cancel'):
                rows[pair] = {'s': 'НЕТ ПАРЫ', 't': [], 'r': [], 'ch': False, 'off': True}
                continue
            if cur is None:
                rows[pair] = {
                    's': p.get('subject') or '—',
                    't': p.get('teachers') or [],
                    'r': p.get('rooms') or [],
                    'ch': True,
                    'off': False,
                }
                continue
            changed = False
            if p.get('subject') and p['subject'] != cur['s']:
                cur['s'] = p['subject']
                changed = True
            if p.get('teachers') and p['teachers'] != cur['t']:
                cur['t'] = p['teachers']
                changed = True
            if p.get('rooms') and p['rooms'] != cur['r']:
                cur['r'] = p['rooms']
                changed = True
            cur['ch'] = changed

    order = sorted(rows, key=lambda x: (x != '*', x if isinstance(x, int) else 0))
    out = []
    for pair in order:
        row = rows[pair]
        t = '—' if row['off'] else time_html(pair, row['r'], weekday, no_time)
        out.append([pair, t, row['s'], ' / '.join(row['r']), ', '.join(row['t'])])
    ch = [p for p in order if rows[p]['ch'] and not rows[p]['off']]
    off = [p for p in order if rows[p]['off']]
    return out, ch, off


def week_block(cells, den, monday, no_time, with_changes=None):
    days = []
    for i in range(6):
        d = monday + timedelta(days=i)
        rows, _, _ = merge_day(cells.get(i) or {}, den, None, i, no_time)
        days.append({
            'day': WEEKDAYS[i],
            'date': '%d %s' % (d.day, MONTHS[d.month - 1]),
            'rows': rows,
        })
    return days


# ---------------- html ----------------
THEME_JS = ("(function(){var t='dark';try{t=localStorage.getItem('ggpk.theme')||'dark';}catch(e){}"
            "try{var q=new URLSearchParams(location.search).get('theme');"
            "if(q==='light'||q==='dark')t=q;}catch(e){}"
            "document.documentElement.setAttribute('data-theme',t==='light'?'light':'dark');})();")


def ic(path, size=18):
    return ('<svg viewBox="0 0 24 24" width="%d" height="%d" fill="none" stroke="currentColor" '
            'stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">%s</svg>'
            % (size, size, path))


I_GLOBE = ('<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" '
           'stroke-linecap="round"><circle cx="12" cy="12" r="9.2"/>'
           '<ellipse cx="12" cy="12" rx="4.3" ry="9.2"/>'
           '<path d="M3.1 9h17.8M3.1 15h17.8"/></svg>')
I_CLOCK = ic('<circle cx="12" cy="12" r="9"/><path d="M12 7.5V12l3 1.8"/>', 16)
I_SUN = ic('<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M4.9 4.9l1.4 1.4'
           'M17.7 17.7l1.4 1.4M2.5 12h2M19.5 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>')
I_MOON = ic('<path d="M20.5 14.6A8.6 8.6 0 0 1 9.4 3.5a8.6 8.6 0 1 0 11.1 11.1z"/>')
I_SEARCH = ic('<circle cx="11" cy="11" r="6.2"/><path d="M15.6 15.6L20 20"/>')
I_CHEV = ic('<path d="M9 5.5l6.5 6.5L9 18.5"/>', 15)
I_GRID = ic('<rect x="3" y="3" width="7.5" height="7.5" rx="2.2"/>'
            '<rect x="13.5" y="3" width="7.5" height="7.5" rx="2.2"/>'
            '<rect x="3" y="13.5" width="7.5" height="7.5" rx="2.2"/>'
            '<rect x="13.5" y="13.5" width="7.5" height="7.5" rx="2.2"/>', 17)
I_TEACH = ic('<circle cx="12" cy="8" r="3.6"/>'
             '<path d="M4.5 20.5c0-3.6 3.2-6.1 7.5-6.1s7.5 2.5 7.5 6.1"/>', 17)
I_TG = ('<svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" style="color:#2aabee">'
        '<path d="M9.04 15.47l-.38 5.34c.54 0 .78-.23 1.06-.51l2.55-2.46 5.29 3.91'
        'c.97.54 1.66.26 1.92-.9L22.9 4.2c.31-1.45-.52-2.02-1.47-1.66L2.36 9.86'
        'c-1.39.55-1.37 1.33-.24 1.68l4.87 1.52L18.3 6.2c.53-.35 1.02-.16.62.19z"/></svg>')
I_RUB = ('<svg viewBox="0 0 220 220" width="18" height="18" fill="#2ecc71">'
         '<path d="M110,0C49.346,0,0,49.346,0,110s49.346,110,110,110s110-49.346,110-110S170.654,0,110,0z '
         'M110,210c-55.141,0-100-44.86-100-100S54.859,10,110,10s100,44.86,100,100S165.141,210,110,210z"/>'
         '<path d="M110,19.5c-49.902,0-90.5,40.598-90.5,90.5s40.598,90.5,90.5,90.5s90.5-40.598,90.5-90.5'
         'S159.902,19.5,110,19.5z M110,197.5c-48.248,0-87.5-39.252-87.5-87.5S61.752,22.5,110,22.5'
         's87.5,39.252,87.5,87.5S158.248,197.5,110,197.5z"/>'
         '<polygon points="103.382,133.87 93.382,133.87 93.382,144.887 84.632,144.887 84.632,154.887 '
         '112.132,154.887 112.132,144.887 103.382,144.887"/>'
         '<path d="M123.062,57.773H83.077v12h10.305v33.876H83.077v12h10.305v2.22h10v-2.22h19.68'
         'c15.957,0,28.938-12.981,28.938-28.938S139.019,57.773,123.062,57.773z M123.062,103.65h-19.68V69.773h19.68'
         'c9.34,0,16.938,7.599,16.938,16.938S132.401,103.65,123.062,103.65z"/>'
         '<rect x="83.077" y="119.87" width="53.989" height="12"/></svg>')


def bells_html():
    cols = [('Корпус Ш', 'Ш', 'week'), ('Корпус М', 'М', 'week'),
            ('Суббота, Ш', 'Ш', 'sat'), ('Суббота, М', 'М', 'sat')]
    out = []
    for title, b, mode in cols:
        rows = []
        for pair in sorted(BELLS[b][mode]):
            slots = BELLS[b][mode][pair]
            line = '<br>'.join('<span>%s–%s</span>' % s for s in slots)
            rows.append('<p><b>%d.</b> %s</p>' % (pair, line))
        out.append('<div class="bcol"><h4>%s</h4>%s</div>' % (title, ''.join(rows)))
    return ('<div class="modal" id="bellsModal"><div class="sheet">'
            '<h3>Расписание звонков</h3><div class="bells">%s</div>'
            '<p class="meta" style="margin:0 0 14px">0-я и 5-я пары идут без времени.</p>'
            '<button class="mbtn" id="bellsClose">Закрыть</button></div></div>' % ''.join(out))


def head(title, up=''):
    return ('<!doctype html><html lang="ru" data-theme="dark"><head>'
            '<meta charset="utf-8">'
            '<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'
            '<meta name="color-scheme" content="dark light">'
            '<meta name="theme-color" content="#000000">'
            '<meta name="ggpk-ver" content="%s">'
            '<title>%s</title>'
            '<meta name="description" content="Расписание занятий Губкинского горно-политехнического колледжа">'
            '<link rel="manifest" href="%smanifest.webmanifest">'
            '<link rel="icon" href="%sicons/icon-192.png">'
            '<link rel="apple-touch-icon" href="%sicons/icon-192.png">'
            '<link rel="stylesheet" href="%sfonts.css">'
            '<link rel="stylesheet" href="%sassets/style.css?v=%s">'
            '<script>%s</script></head><body data-root="%s">'
            % (VER, html.escape(title), up, up, up, up, up, VER, THEME_JS, up))


def header(home='index.html'):
    return ('<header class="hdr"><div class="hin">'
            '<a class="brand" href="%s"><span class="mark">%s</span><b>Расписание</b></a>'
            '<div class="tools">'
            '<button class="ic" id="bells" title="Расписание звонков">%s</button>'
            '<a class="ic" href="%s" target="_blank" rel="noopener" title="Telegram">%s</a>'
            '<a class="ic" href="%s" target="_blank" rel="noopener" title="Поддержать проект">%s</a>'
            '<button class="ic" id="th" title="Сменить тему">'
            '<span class="i-sun">%s</span><span class="i-moon">%s</span></button>'
            '</div></div></header>'
            % (home, I_GLOBE, I_CLOCK, TG, I_TG, DONATE, I_RUB, I_SUN, I_MOON))


def foot():
    return ('<div class="foot">данные <a href="%s" target="_blank" rel="noopener">сообщества ВК</a>'
            ' · обновлено %s</div>' % (VK, UPDATED))


def bottom_nav(active, up=''):
    items = [('groups', up + 'index.html', I_GRID, 'Группы'),
             ('teachers', up + 'prep.html', I_TEACH, 'Преподаватели')]
    out = ['<nav class="bottom">']
    for key, href, icon, text in items:
        out.append('<a class="%s" href="%s">%s<span>%s</span></a>'
                   % ('on' if key == active else '', href, icon, text))
    out.append('</nav>')
    return ''.join(out)


def page(title, body, home='index.html', up='', nav='groups'):
    tail = ('<div class="toast" id="toast"></div>'
            '<script>'
            '(function(){try{if(\'serviceWorker\' in navigator&&location.protocol===\'https:\')'
            '{navigator.serviceWorker.register(\'%ssw.js\').catch(function(){});}}catch(e){}})();'
            '(function(){try{fetch(\'%sversion.txt?_=\'+Date.now(),{cache:\'no-store\'})'
            '.then(function(r){return r.text()}).then(function(v){v=(v||\'\').trim();'
            'if(v&&v!==\'%s\'&&!sessionStorage.getItem(\'ggpk.reload\')){'
            'sessionStorage.setItem(\'ggpk.reload\',\'1\');var u=new URL(location.href);'
            'u.searchParams.set(\'v\',v);location.replace(u.toString());}})'
            '.catch(function(){});}catch(e){}})();'
            '</script>' % (up, up, VER))
    return (head(title, up) + header(home) +
            '<div class="wrap">' + body + foot() + '</div>' +
            bottom_nav(nav, up) + bells_html() + tail + '</body></html>')


def index_page(cat, mine):
    courses = sorted(cat, key=lambda c: (0, int(re.search(r'(\d)', c).group(1)))
                     if re.search(r'(\d)', c) else (1, 0))
    parts = ['<h1>Расписание занятий</h1>', '<div id="mine"></div>',
             '<div class="search">%s<input id="q" placeholder="Найти группу" '
             'autocomplete="off" inputmode="search"></div>' % I_SEARCH,
             '<div class="chips" id="chips">']
    for i, course in enumerate(courses):
        parts.append('<button class="chip%s" data-c="%d">%s</button>'
                     % (' on' if i == 0 else '', i, html.escape(course)))
    parts.append('</div><div class="grid" id="grid">')
    for i, course in enumerate(courses):
        for g in sorted(cat[course]):
            parts.append('<a class="gcard" href="g/%s.html?v=%s" data-c="%d" data-n="%s">'
                         '<span>%s</span>%s</a>'
                         % (slug(g), VER, i, html.escape(g.lower()), html.escape(g), I_CHEV))
    parts.append('</div>')
    parts.append('<h2 class="sec">Ещё</h2><div class="rows">'
                 '<a class="row" href="%s" target="_blank" rel="noopener">'
                 'Сообщество ВК<span>расписание</span></a>'
                 '<a class="row" href="%s" target="_blank" rel="noopener">'
                 'Канал с обновлениями<span>Telegram</span></a>'
                 '<a class="row" href="%s" target="_blank" rel="noopener">'
                 'Приложение для Android<span>APK</span></a>'
                 '<a class="row" href="%s" target="_blank" rel="noopener">'
                 'Поддержать проект<span>DonationAlerts</span></a></div>'
                 % (VK, TG, RELEASES, DONATE))
    js = ('<script>(function(){'
          'var names=%s,q=document.getElementById("q"),grid=document.getElementById("grid"),'
          'chips=document.getElementById("chips"),cur=0;'
          'function apply(){var v=(q.value||"").toLowerCase().trim();'
          'for(var i=0;i<grid.children.length;i++){var c=grid.children[i];'
          'var okC=v?true:(c.getAttribute("data-c")===String(cur));'
          'var okT=c.getAttribute("data-n").indexOf(v)>-1;c.hidden=!(okC&&okT);}}'
          'chips.addEventListener("click",function(e){var t=e.target.closest?'
          'e.target.closest(".chip"):null;if(!t)return;cur=+t.getAttribute("data-c");'
          'for(var i=0;i<chips.children.length;i++){'
          'chips.children[i].classList.toggle("on",chips.children[i]===t);}'
          't.scrollIntoView({block:"nearest",inline:"nearest"});apply();});'
          'q.addEventListener("input",apply);apply();'
          'try{var g=localStorage.getItem("ggpk.group");if(g&&names[g]){'
          'var box=document.getElementById("mine");'
          'box.innerHTML=\'<a class="mine" href="g/\'+g+\'.html?v=%s"><span>\'+'
          'names[g]+\'<small>ваша группа</small></span>%s</a>\';}}catch(e){}'
          '})();</script>'
          % (json.dumps(mine, ensure_ascii=False), VER, I_CHEV))
    return page('Расписание ГГПК', ''.join(parts) + js)


def prep_index(teachers):
    items = sorted(teachers)
    rows = ['<a class="gcard" data-n="%s" href="p/%s.html?v=%s">'
            '<span>%s</span>%s</a>'
            % (html.escape(t.lower()), slug(t), VER, html.escape(t), I_CHEV) for t in items]
    body = ('<h1>Преподаватели</h1>'
            '<div class="search">%s<input id="q" placeholder="Найти преподавателя" '
            'autocomplete="off" inputmode="search"></div>'
            '<div class="grid prep" id="grid">%s</div>'
            '<script>(function(){var q=document.getElementById("q"),'
            'g=document.getElementById("grid");'
            'q.addEventListener("input",function(){var v=q.value.toLowerCase().trim();'
            'for(var i=0;i<g.children.length;i++){'
            'g.children[i].hidden=g.children[i].getAttribute("data-n").indexOf(v)<0;}});'
            '})();</script>' % (I_SEARCH, ''.join(rows)))
    return page('Преподаватели — расписание ГГПК', body, nav='teachers')


def app_page(name, slug_, mode, no_time=False):
    if mode == 't':
        back = '<a class="back" href="../prep.html?v=%s">← преподаватели</a>' % VER
        nav = 'teachers'
        url = '../data/p/%s.json' % slug_
    else:
        back = '<a class="back" href="../index.html?v=%s">← все группы</a>' % VER
        nav = 'groups'
        url = '../data/g/%s.json' % slug_
    body = ('%s<h1>%s</h1><div class="tabs" id="tabs"></div>'
            '<div id="body"><div class="empty">Загрузка…</div></div>' % (back, html.escape(name)))
    cfg = json.dumps({'mode': mode, 'name': name, 'url': url, 'ver': VER,
                      'no_time': bool(no_time)}, ensure_ascii=False)
    body += ('<script>window.__GGPK=%s;</script>'
             '<script src="../assets/app.js?v=%s"></script>' % (cfg, VER))
    return page('%s — расписание' % name, body, home='../index.html', up='../', nav=nav)


# ---------------- сборка ----------------
def write(path, text):
    full = os.path.join(OUT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)


def write_json(path, payload):
    full = os.path.join(OUT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(payload, f, ensure_ascii=False, separators=(',', ':'))


def main():
    main_pdf = os.path.join(HERE, 'main.pdf')
    if not os.path.exists(main_pdf):
        raise SystemExit('нет main.pdf')
    cat = sc.parse_main(main_pdf)
    total = sum(len(v) for v in cat.values())
    print('групп: %d' % total)

    changes = load_changes(os.path.join(HERE, 'changes'))
    print('дат с изменениями: %d' % len(changes))

    today = datetime.now(MSK).date()
    tomorrow = today + timedelta(days=1)
    dates = [today + timedelta(days=i) for i in range(HORIZON)]

    # склеенные дни для всех групп
    merged = {}
    for d in dates:
        iso = d.isoformat()
        patch_day = changes.get(iso) if d <= tomorrow else None
        merged[iso] = {}
        for course in cat:
            for g in cat[course]:
                no_time = course.lower().startswith('корр')
                rows, ch, off = merge_day(cat[course][g].get(d.weekday()) or {},
                                          sc.week_info(d)[1] == 'знаменатель',
                                          group_patch(patch_day, g), d.weekday(), no_time)
                merged[iso][g] = {'rows': rows, 'ch': ch, 'off': off}

    # страницы и JSON групп
    mine = {}
    for course in cat:
        for g in sorted(cat[course]):
            sl = slug(g)
            mine[sl] = g
            no_time = course.lower().startswith('корр')
            payload = {
                'name': g,
                'slug': sl,
                'course': course,
                'no_time': no_time,
                'today': today.isoformat(),
                'parity_default': 'z' if sc.week_info(today)[1] == 'знаменатель' else 'n',
                'days': {},
                'week': {},
            }
            for d in dates:
                iso = d.isoformat()
                m = merged[iso][g]
                payload['days'][iso] = {
                    'label': label(d),
                    'week': sc.week_info(d)[0],
                    'kind': sc.week_info(d)[1],
                    'status': 'changed' if (m['ch'] or m['off']) else 'none',
                    'rows': m['rows'],
                    'ch': m['ch'],
                    'off': m['off'],
                }
            monday = today - timedelta(days=today.weekday())
            if today.weekday() == 6:
                monday = today + timedelta(days=1)
            payload['week']['n'] = week_block(cat[course][g], False, monday, no_time)
            payload['week']['z'] = week_block(cat[course][g], True, monday, no_time)
            write_json('data/g/%s.json' % sl, payload)
            write('g/%s.html' % sl, app_page(g, sl, 'g', no_time))

    write('index.html', index_page(cat, mine))

    # преподаватели — в том числе те, кто появился только в изменениях
    teachers = set()
    for course in cat:
        for g in cat[course]:
            for cells in cat[course][g].values():
                for c in cells.values():
                    for field in ('teacher', 'teacher_den'):
                        for name in sc.clean_teachers(c.get(field) or ''):
                            teachers.add(name)
    for day in changes.values():
        for patch in day.values():
            for p in patch.values():
                for name in p.get('teachers') or []:
                    teachers.add(name)

    print('преподавателей: %d' % len(teachers))
    for name in sorted(teachers):
        sl = slug(name)
        payload = {
            'name': name,
            'slug': sl,
            'today': today.isoformat(),
            'parity_default': 'z' if sc.week_info(today)[1] == 'знаменатель' else 'n',
            'days': {},
            'week': {},
        }
        for d in dates:
            iso = d.isoformat()
            rows = []
            changed = False
            for course in cat:
                for g in cat[course]:
                    m = merged[iso][g]
                    for r in m['rows']:
                        if name in r[4]:
                            rows.append([r[0], r[1], g, r[2], r[3]])
                            if r[0] in m['ch'] or r[0] in m['off']:
                                changed = True
            rows.sort(key=lambda x: (x[0] if isinstance(x[0], int) else 99, x[2]))
            payload['days'][iso] = {
                'label': label(d),
                'week': sc.week_info(d)[0],
                'kind': sc.week_info(d)[1],
                'status': 'changed' if changed else 'none',
                'rows': rows,
                'ch': [],
                'off': [],
            }

        monday = today - timedelta(days=today.weekday())
        if today.weekday() == 6:
            monday = today + timedelta(days=1)
        for key, den in (('n', False), ('z', True)):
            days = []
            for i in range(6):
                dd = monday + timedelta(days=i)
                rows = []
                for course in cat:
                    for g in cat[course]:
                        no_time = course.lower().startswith('корр')
                        day_rows, _, _ = merge_day(cat[course][g].get(i) or {}, den, None, i, no_time)
                        for r in day_rows:
                            if name in r[4]:
                                rows.append([r[0], r[1], g, r[2], r[3]])
                rows.sort(key=lambda x: (x[0] if isinstance(x[0], int) else 99, x[2]))
                days.append({
                    'day': WEEKDAYS[i],
                    'date': '%d %s' % (dd.day, MONTHS[dd.month - 1]),
                    'rows': rows,
                })
            payload['week'][key] = days

        write_json('data/p/%s.json' % sl, payload)
        write('p/%s.html' % sl, app_page(name, sl, 't'))

    write('prep.html', prep_index(teachers))

    # статика
    if os.path.isdir(WEB):
        shutil.copytree(WEB, os.path.join(OUT, 'assets'), dirs_exist_ok=True)
    for f in ('fonts.css',):
        src = os.path.join(HERE, f)
        if os.path.exists(src):
            shutil.copyfile(src, os.path.join(OUT, f))
    for d in ('fonts', 'icons'):
        src = os.path.join(HERE, d)
        if os.path.isdir(src):
            shutil.copytree(src, os.path.join(OUT, d), dirs_exist_ok=True)

    write('manifest.webmanifest', json.dumps({
        'name': 'Расписание ГГПК',
        'short_name': 'Расписание',
        'description': 'Расписание занятий Губкинского горно-политехнического колледжа',
        'start_url': './',
        'scope': './',
        'display': 'standalone',
        'orientation': 'portrait',
        'background_color': '#000000',
        'theme_color': '#000000',
        'lang': 'ru',
        'icons': [
            {'src': 'icons/icon-192.png', 'sizes': '192x192', 'type': 'image/png'},
            {'src': 'icons/icon-512.png', 'sizes': '512x512', 'type': 'image/png',
             'purpose': 'any maskable'},
        ],
    }, ensure_ascii=False))

    write('sw.js', """const CACHE = 'ggpk-v2';
self.addEventListener('install', function(){ self.skipWaiting(); });
self.addEventListener('activate', function(e){
  e.waitUntil(caches.keys().then(function(keys){
    return Promise.all(keys.map(function(k){
      if (k !== CACHE) return caches.delete(k);
    }));
  }).then(function(){ return self.clients.claim(); }));
});
self.addEventListener('fetch', function(e){
  const req = e.request;
  if (req.method !== 'GET') return;
  const u = new URL(req.url);
  if (u.origin !== location.origin) return;
  e.respondWith(
    fetch(req).then(function(res){
      if (res && res.status === 200) {
        const copy = res.clone();
        caches.open(CACHE).then(function(c){ c.put(new Request(u.origin + u.pathname), copy); }).catch(function(){});
      }
      return res;
    }).catch(function(){
      return caches.match(req, {ignoreSearch: true}).then(function(m){
        return m || caches.match('./index.html', {ignoreSearch: true});
      });
    })
  );
});
""")
    write('robots.txt', 'User-agent: *\nAllow: /\n')
    write('version.txt', VER)
    print('готово: %s' % OUT)


if __name__ == '__main__':
    main()
