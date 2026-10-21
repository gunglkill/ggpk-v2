# -*- coding: utf-8 -*-
"""Разбор расписания ГГПК: все группы, недели, изменения.

Структура PDF:
  • страница = курс (1–4 или коррекционные);
  • группа = широкая колонка с предметами + узкая колонка аудитории справа;
  • слева узкая колонка с номерами пар 0–6; дни идут блоками;
  • ячейка может быть разбита горизонтальной линией: верх — числитель, низ — знаменатель.
"""
import re
from datetime import date, timedelta

import pymupdf

DAYS = ['понедельник', 'вторник', 'среда', 'четверг', 'пятница', 'суббота']
GROUP_RE = re.compile(r'^[А-ЯЁ]{1,6}-\d{2}(\(\d+\))?т?$')
TEACHER_RE = re.compile(r'^[А-ЯЁ][а-яё]+(?:-[А-ЯЁа-яё]+)?\s+[А-ЯЁ]\.\s?[А-ЯЁ]\.')
EMPTY_RE = re.compile(r'^-{3,}$')


# ---------- геометрия ----------
def _lines(page):
    """Возвращает вертикальные и горизонтальные отрезки страницы."""
    vx, hy = [], []
    for it in page.get_drawings():
        for item in it['items']:
            if item[0] == 'l':
                a, b = item[1], item[2]
                if abs(a.x - b.x) < 0.6:
                    vx.append((a.x, min(a.y, b.y), max(a.y, b.y)))
                elif abs(a.y - b.y) < 0.6:
                    hy.append((a.y, min(a.x, b.x), max(a.x, b.x)))
            elif item[0] == 're':
                r = item[1]
                if r.width < 1.6 and r.height > 3:
                    vx.append((r.x0, r.y0, r.y1))
                elif r.height < 1.6 and r.width > 3:
                    hy.append((r.y0, r.x0, r.x1))
    return vx, hy


def _cluster(vals, tol=2.5):
    out = []
    for v in sorted(vals):
        if out and abs(v - out[-1]) <= tol:
            out[-1] = (out[-1] + v) / 2
        else:
            out.append(v)
    return out


def _wc(w):
    return ((w[0] + w[2]) / 2, (w[1] + w[3]) / 2)


def _lines_of(words, tol=2.6):
    """Группирует слова в строки по y и склеивает переносы."""
    rows = []
    for w in sorted(words, key=lambda w: ((w[1] + w[3]) / 2, w[0])):
        yc = (w[1] + w[3]) / 2
        if rows and abs(yc - rows[-1][0]) <= tol:
            rows[-1][1].append(w)
        else:
            rows.append([yc, [w]])
    texts = []
    for yc, ws in rows:
        ws.sort(key=lambda w: w[0])
        texts.append(' '.join(w[4] for w in ws))
    merged = []
    for t in texts:
        if merged and merged[-1].endswith('-') and not merged[-1].endswith('--'):
            merged[-1] = merged[-1][:-1] + t
        else:
            merged.append(t)
    return merged


def _split_subject_teacher(lines):
    if not lines:
        return '', ''
    idx = len(lines)
    for i, line in enumerate(lines):
        if TEACHER_RE.match(line) or line.strip().lower() == 'вакансия':
            idx = i
            break
    return ' '.join(lines[:idx]).strip(), ' '.join(lines[idx:]).strip()


def _page_course(text):
    if 'коррекционн' in text:
        return 'Коррекционные группы'
    m = re.search(r'(\d)\s*курс', text)
    return '%s курс' % m.group(1) if m else 'Прочее'


def _columns(page, words):
    """Список колонок групп: (left, right, room_left, room_right)."""
    vx, hy = _lines(page)
    xs = _cluster([x for x, _, _ in vx])
    if len(xs) < 4:
        return [], hy
    regions = list(zip(xs[:-1], xs[1:]))
    cols, cur = [], None
    for a, b in regions:
        if b - a >= 25:
            if cur and abs(cur[1] - a) < 1.5:
                cur = (cur[0], b)
            else:
                if cur:
                    cols.append((cur[0], cur[1], cur[1], cur[1]))
                cur = (a, b)
        elif cur:
            cols.append((cur[0], cur[1], a, b))
            cur = None
    if cur:
        cols.append((cur[0], cur[1], cur[1], cur[1]))
    return cols, hy


# ---------- основное расписание ----------
def parse_main(path):
    """-> {course: {group: {day_index: {pair: {...}}}}}"""
    doc = pymupdf.open(path)
    result = {}
    for page in doc:
        course = _page_course(page.get_text())
        words = page.get_text('words')
        cols, hy = _columns(page, words)
        if not cols:
            continue

        # номера пар — цифры в левой узкой полосе
        digits = [w for w in words if re.fullmatch(r'[0-6]', w[4].strip())
                  and _wc(w)[0] < 70]
        if not digits:
            continue
        nx = sorted(_wc(w)[0] for w in digits)[len(digits) // 2]
        vx, _ = _lines(page)
        xs = _cluster([x for x, _, _ in vx])
        regions = list(zip(xs[:-1], xs[1:]))
        nums_col = min(regions, key=lambda r: abs((r[0] + r[1]) / 2 - nx))

        nums = sorted((_wc(w)[1], int(w[4])) for w in digits
                      if nums_col[0] - 2 <= _wc(w)[0] <= nums_col[1] + 2)
        if not nums:
            continue
        row_bounds = _cluster([y for y, x0, x1 in hy
                               if x0 <= nums_col[0] + 1.5 and x1 >= nums_col[1] - 1.5])

        def band(yc):
            if len(row_bounds) < 2:
                return None
            top = max([b for b in row_bounds if b < yc], default=row_bounds[0])
            bot = min([b for b in row_bounds if b > yc], default=row_bounds[-1])
            return top, bot

        days, num_day = [], {}
        bands = {}
        for yc, n in nums:
            if not days or n <= days[-1][-1][1]:
                days.append([])
            days[-1].append((yc, n))
            num_day[(yc, n)] = len(days) - 1
            bands[(yc, n)] = band(yc)

        for a, b, room_l, room_r in cols:
            heads = [w for w in words if GROUP_RE.match(w[4])
                     and a <= _wc(w)[0] <= b and w[1] < 70]
            if not heads:
                continue
            group = heads[0][4]
            hy_col = _cluster([y for y, x0, x1 in hy
                               if x0 <= a + 1.5 and x1 >= b - 1.5])
            if len(hy_col) < 2:
                continue
            header_y = max(_wc(h)[1] for h in heads)
            below = [y for y in hy_col if y > header_y + 2]
            if not below:
                continue
            start = min(below)
            edges = [start] + [y for y in below if y > start]

            buckets = {}
            for i in range(len(edges) - 1):
                y0, y1 = edges[i], edges[i + 1]
                if y1 - y0 < 4:
                    continue
                ws = [w for w in words if y0 < _wc(w)[1] < y1
                      and a - 1 <= _wc(w)[0] <= room_r + 1]
                if not ws:
                    continue
                yc = (y0 + y1) / 2
                key = None
                for k, bnd in bands.items():
                    if bnd and bnd[0] - 1 <= yc <= bnd[1] + 1:
                        key = k
                        break
                if key is None:
                    key = min(num_day, key=lambda t: abs(t[0] - yc))
                    if abs(key[0] - yc) > (y1 - y0) / 2 + 8:
                        continue
                buckets.setdefault(key, []).append((y0, y1, ws))

            cells_by_day = {}
            for key, parts in buckets.items():
                day_i = num_day[key]
                if day_i > 5:
                    continue
                parts.sort()
                info = {}
                for i, (y0, y1, ws) in enumerate(parts[:2]):
                    subj_ws = [w for w in ws if _wc(w)[0] < room_l - 1]
                    room_ws = [w for w in ws if _wc(w)[0] >= room_l - 1]
                    subject, teacher = _split_subject_teacher(_lines_of(subj_ws))
                    room = ' '.join(_lines_of(room_ws)).strip()
                    if EMPTY_RE.match(subject) or not subject:
                        subject = teacher = room = ''
                    sfx = '' if i == 0 else '_den'
                    info['subject' + sfx] = subject
                    info['teacher' + sfx] = teacher
                    info['room' + sfx] = room
                if info.get('subject') or info.get('subject_den'):
                    cells_by_day.setdefault(day_i, {})[key[1]] = info
            for day_i, cells in cells_by_day.items():
                result.setdefault(course, {}).setdefault(group, {})[day_i] = cells
    return result


# ---------- изменения ----------
def _pairs(label):
    """'2,3' -> [2, 3]; '1-3' -> [1, 2, 3]; '*' -> ['*']."""
    s = (label or '').replace(' ', '')
    if s == '*':
        return ['*']
    out = []
    for part in re.split(r'[,;]', s):
        if not part:
            continue
        if '-' in part:
            a, b = part.split('-', 1)
            if a.isdigit() and b.isdigit():
                out.extend(range(int(a), int(b) + 1))
        elif part.isdigit():
            out.append(int(part))
    return out


def parse_changes(path):
    """Изменения на дату.

    -> {group: {pair | '*': {'subject', 'teachers', 'rooms', 'cancel'}}}
    """
    doc = pymupdf.open(path)
    out = {}
    for page in doc:
        words = page.get_text('words')
        names = [w for w in words if GROUP_RE.match(w[4])]
        if not names:
            continue
        vx, hy = _lines(page)
        xs = _cluster([x for x, _, _ in vx])
        if len(xs) < 5:
            continue
        g_l, g_r = xs[0], xs[1]
        p_l, p_r = xs[1], xs[2]
        s_l, s_r = xs[2], xs[3]
        t_l, t_r = xs[3], xs[4]
        r_l, r_r = xs[4], (xs[5] if len(xs) > 5 else xs[4] + 40)
        for name in names:
            yc = (name[1] + name[3]) / 2
            top = max([y for y, x0, x1 in hy
                       if x0 <= g_l + 1.5 and x1 >= g_r - 1.5 and y < yc], default=None)
            bot = min([y for y, x0, x1 in hy
                       if x0 <= g_l + 1.5 and x1 >= g_r - 1.5 and y > yc], default=None)
            if top is None or bot is None:
                continue
            rows = sorted((_wc(w)[1], w[4]) for w in words
                          if re.fullmatch(r'\d+(\s*[,;-]\s*\d+)*|\*', w[4].strip())
                          and p_l - 1 <= _wc(w)[0] <= p_r + 1 and top < _wc(w)[1] < bot)
            for yc_row, label in rows:
                rt = max([y for y, x0, x1 in hy
                          if x0 <= s_l + 1.5 and x1 >= s_r - 1.5 and y < yc_row], default=top)
                rb = min([y for y, x0, x1 in hy
                          if x0 <= s_l + 1.5 and x1 >= s_r - 1.5 and y > yc_row], default=bot)

                def grab(la, lb, _rt=rt, _rb=rb):
                    ws = [w for w in words if la - 1 <= _wc(w)[0] <= lb + 1
                          and _rt < _wc(w)[1] < _rb]
                    return re.sub(r'\s+', ' ', ' '.join(_lines_of(ws))).strip()

                subject = grab(s_l, s_r)
                teacher = grab(t_l, t_r)
                room = grab(r_l, r_r)
                cancel = is_cancel(subject)
                item = {
                    'subject': '' if cancel else subject,
                    'teachers': [] if cancel else clean_teachers(teacher),
                    'rooms': [] if cancel else split_rooms(room),
                    'cancel': cancel,
                }
                for key in _pairs(label):
                    out.setdefault(name[4], {})[key] = item
    return out


# ---------- имена групп ----------
def canon(name):
    s = re.sub(r'\s+', '', (name or '').upper())
    return s.replace('(', '').replace(')', '')


def find_group(names, name):
    """Находит каноническое имя группы среди известных (терпит опечатки в «т»)."""
    c = canon(name)
    if not c:
        return None
    for g in names:
        if canon(g) == c:
            return g
    for g in names:
        if canon(g).rstrip('Т') == c.rstrip('Т'):
            return g
    return None


# ---------- проверка ----------
def validate(cat):
    """Список замечаний по разобранному расписанию."""
    issues = []
    for course, groups in cat.items():
        if not groups:
            issues.append('%s: нет групп' % course)
        for g, days in groups.items():
            if not days:
                issues.append('%s: нет дней' % g)
                continue
            ds = sorted(days)
            if ds != list(range(min(ds), max(ds) + 1)):
                issues.append('%s: пропуск дней %s' % (g, ds))
            for d, cells in days.items():
                if not cells:
                    issues.append('%s/%s: пустой день' % (g, DAYS[d]))
                for p, c in cells.items():
                    if not (c.get('subject') or c.get('subject_den')):
                        issues.append('%s/%s/%d: пустая пара' % (g, DAYS[d], p))
                    if p < 0 or p > 6:
                        issues.append('%s/%s: пара %d' % (g, DAYS[d], p))
    return issues


# ---------- неделя ----------
def semester_start(d):
    """Понедельник недели, в которую попадает 1 сентября учебного года."""
    year = d.year if d.month >= 8 else d.year - 1
    sep1 = date(year, 9, 1)
    return sep1 - timedelta(days=sep1.weekday())


def week_info(d):
    n = (d - semester_start(d)).days // 7 + 1
    return n, ('числитель' if n % 2 == 1 else 'знаменатель')


# ---------- разбор полей ----------
NAME_RE = re.compile(r'[А-ЯЁ][а-яё]+(?:-[А-ЯЁ][а-яё]+)?\s+[А-ЯЁ]\.\s*[А-ЯЁ]\.')


def clean_teachers(text):
    """Все ФИО из строки, без повторов и мусора."""
    out = []
    for m in NAME_RE.finditer(text or ''):
        name = re.sub(r'\s+', ' ', m.group(0)).strip()
        name = re.sub(r'\.\s+', '.', name)
        if name not in out:
            out.append(name)
    return out


def split_rooms(text):
    """'209Ш 204Ш' -> ['209Ш', '204Ш']; 'с/з Ш' остаётся одной аудиторией."""
    s = re.sub(r'[\s\u00a0]+', ' ', text or '').strip()
    if not s:
        return []
    parts = s.split(' ')
    out = []
    i = 0
    while i < len(parts):
        p = parts[i]
        low = p.lower()
        if low in ('с/з', 'с/з.', 'спортзал', 'спортзал.') and i + 1 < len(parts) \
                and parts[i + 1].upper() in ('Ш', 'М'):
            out.append(p + ' ' + parts[i + 1])
            i += 2
            continue
        if p.upper() in ('Ш', 'М') and out and out[-1].lower().startswith('с/з'):
            out[-1] = out[-1] + ' ' + p
            i += 1
            continue
        if p not in ('—', '–', '-'):
            out.append(p)
        i += 1
    return out


def is_cancel(subject):
    s = re.sub(r'\s+', ' ', (subject or '')).strip().lower()
    return s in ('нет', 'нет пары', 'нет пар', 'нет уроков', 'отмена') or s.startswith('нет ')



def build_day(cells, denominator, changes):
    rows = []
    for pair in sorted(cells):
        c = cells[pair]
        subject, teacher, room = c.get('subject', ''), c.get('teacher', ''), c.get('room', '')
        if denominator and c.get('subject_den'):
            subject = c['subject_den']
            teacher = c.get('teacher_den', '')
            room = c.get('room_den', '')
        rows.append([pair, subject, room, teacher])
    if changes:
        for pair, ch in changes.items():
            row = next((r for r in rows if r[0] == pair), None)
            if row is None:
                row = [pair, '', '', '']
                rows.append(row)
            if ch['subject'] == 'НЕТ':
                row[1] = 'НЕТ ПАРЫ'
                row[2] = row[3] = ''
                continue
            if ch['subject']:
                row[1] = ch['subject']
            if ch['teacher']:
                row[3] = ch['teacher']
            if ch['room']:
                row[2] = ch['room']
        rows.sort(key=lambda r: r[0])
    return [tuple(r) for r in rows if r[1]]
