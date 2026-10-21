# -*- coding: utf-8 -*-
import glob
import json
import os
import re
import sys

ROOT = 'site'
bad = []
pages = 0
links = 0
for dirpath, _, files in os.walk(ROOT):
    for f in files:
        if not f.endswith('.html'):
            continue
        p = os.path.join(dirpath, f)
        pages += 1
        text = open(p, encoding='utf-8').read()
        for m in re.finditer(r'(?:href|src)="([^"]+)"', text):
            u = m.group(1).split('?')[0].split('#')[0]
            if not u or u.startswith(('http', 'mailto:', 'tel:', 'data:', '#')):
                continue
            links += 1
            full = os.path.normpath(os.path.join(dirpath, u))
            if not os.path.exists(full):
                bad.append((p, u))
print('страниц', pages, 'ссылок', links, 'битых', len(bad))
for b in bad[:20]:
    print(' ', b)

issues = []
for f in glob.glob('site/data/g/*.json'):
    d = json.load(open(f, encoding='utf-8'))
    if len(d['days']) != 10:
        issues.append((f, 'days', len(d['days'])))
    for k, e in d['days'].items():
        for r in e['rows']:
            if r[0] in (0, 5, '*') and r[1] != '—':
                issues.append((f, k, r))
            if d['no_time'] and r[1] != '—':
                issues.append((f, k, r))
    for k in ('n', 'z'):
        if len(d['week'][k]) != 6:
            issues.append((f, 'week', k, len(d['week'][k])))
print('проблем данных', len(issues))
for i in issues[:10]:
    print(' ', i)
