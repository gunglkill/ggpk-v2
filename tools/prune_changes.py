# -*- coding: utf-8 -*-
"""Удаляет PDF с изменениями старше 4 дней (main.pdf не трогает)."""
import os
import re
import sys
from datetime import datetime, timedelta, timezone

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CHANGES = os.path.join(HERE, 'changes')
MSK = timezone(timedelta(hours=5))
KEEP_DAYS = 4


def file_date(name):
    m = re.match(r'(\d{4})-(\d{2})-(\d{2})', name)
    if m:
        return datetime(int(m.group(1)), int(m.group(2)), int(m.group(3))).date()
    m = re.match(r'(\d{2})\.(\d{2})\.(\d{4})', name)
    if m:
        return datetime(int(m.group(3)), int(m.group(2)), int(m.group(1))).date()
    return None


def main():
    if not os.path.isdir(CHANGES):
        return
    cutoff = datetime.now(MSK).date() - timedelta(days=KEEP_DAYS)
    removed = []
    for name in sorted(os.listdir(CHANGES)):
        if not name.lower().endswith('.pdf'):
            continue
        d = file_date(name)
        if d and d < cutoff:
            os.remove(os.path.join(CHANGES, name))
            removed.append(name)
    if removed:
        print('удалено: %s' % ', '.join(removed))
    else:
        print('старых изменений нет')


if __name__ == '__main__':
    sys.exit(main())
