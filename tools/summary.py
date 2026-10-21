# -*- coding: utf-8 -*-
"""Сводка по PDF: группы по страницам, пары, дни."""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import schedule_core as sc  # noqa: E402

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

cat = sc.parse_main(os.path.join(HERE, 'main.pdf'))
print('КУРСЫ И ГРУППЫ (main.pdf):')
for course, groups in cat.items():
    print('  %s: %d групп' % (course, len(groups)))
    for g in groups:
        days = sorted(groups[g])
        pairs = sorted({p for d in groups[g].values() for p in d})
        print('     %-12s дни %s, пары %s' % (g, days, pairs))

issues = sc.validate(cat)
print('\nЗАМЕЧАНИЯ ВАЛИДАТОРА: %d' % len(issues))
for x in issues[:15]:
    print('  -', x)

print('\nИЗМЕНЕНИЯ:')
chdir = os.path.join(HERE, 'changes')
for f in sorted(os.listdir(chdir)):
    if not f.endswith('.pdf'):
        continue
    ch = sc.parse_changes(os.path.join(chdir, f))
    print('  %s: групп %d' % (f, len(ch)))
    for g, pairs in ch.items():
        print('     %-12s %s' % (g, {p: (v['subject'][:40], v['teacher'][:30], v['room']) for p, v in pairs.items()}))
