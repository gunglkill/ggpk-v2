# -*- coding: utf-8 -*-
"""Служебный скрипт: выгружает текст и картинки из PDF, чтобы понять структуру."""
import os
import sys

import pymupdf

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(HERE, 'tools', 'out')


def main():
    os.makedirs(OUT, exist_ok=True)
    targets = [('main.pdf', 1)]
    chdir = os.path.join(HERE, 'changes')
    if os.path.isdir(chdir):
        for f in sorted(os.listdir(chdir)):
            if f.lower().endswith('.pdf'):
                targets.append((os.path.join('changes', f), 1))

    for rel, zoom_pages in targets:
        path = os.path.join(HERE, rel)
        doc = pymupdf.open(path)
        base = os.path.splitext(os.path.basename(rel))[0]
        info = ['=== %s === страниц: %d, размер: %.0f x %.0f' % (
            rel, doc.page_count, doc[0].rect.width, doc[0].rect.height)]

        text_all = []
        for i, page in enumerate(doc):
            text_all.append('\n----- страница %d -----\n' % (i + 1))
            text_all.append(page.get_text('text'))
        with open(os.path.join(OUT, base + '.txt'), 'w', encoding='utf-8') as fh:
            fh.write(''.join(text_all))

        for i in range(min(zoom_pages, doc.page_count)):
            pix = doc[i].get_pixmap(matrix=pymupdf.Matrix(2.0, 2.0))
            img = os.path.join(OUT, '%s_p%d.png' % (base, i + 1))
            pix.save(img)
            info.append('  p%d -> %s' % (i + 1, img))

        first = doc[0].get_text('text').strip().splitlines()
        info.append('  первые строки: ' + ' | '.join(x.strip() for x in first[:8]))
        print('\n'.join(info))

        # краткая карта страниц: ищем заголовки курса
        for i, page in enumerate(doc):
            t = page.get_text('text')
            heads = [line.strip() for line in t.splitlines()
                     if 'курс' in line.lower() or 'коррекционн' in line.lower()]
            print('    стр.%d: %s' % (i + 1, ', '.join(heads[:3]) or '(без заголовка курса)'))


if __name__ == '__main__':
    sys.exit(main())
