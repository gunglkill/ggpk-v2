# -*- coding: utf-8 -*-
"""Скачивает Manrope с Google Fonts в локальные файлы (для офлайна)."""
import os
import re
import urllib.request

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONTS = os.path.join(HERE, 'fonts')
URL = 'https://fonts.googleapis.com/css2?family=Manrope:wght@400..800&display=swap'
UA = ('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/124.0 Safari/537.36')


def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read()


def main():
    os.makedirs(FONTS, exist_ok=True)
    css = get(URL).decode('utf-8')

    blocks = re.findall(r'/\*\s*([a-z-]+)\s*\*/\s*@font-face\s*\{([^}]+)\}', css)
    keep = {'cyrillic', 'latin', 'latin-ext', 'cyrillic-ext'}
    out = []
    seen = {}
    for subset, body in blocks:
        if subset not in keep:
            continue
        src = re.search(r'url\((https://[^)]+\.woff2)\)', body)
        if not src:
            continue
        url = src.group(1)
        if url not in seen:
            name = 'manrope-%s.woff2' % subset
            with open(os.path.join(FONTS, name), 'wb') as f:
                f.write(get(url))
            seen[url] = name
        body = body.replace(url, 'fonts/%s' % seen[url])
        body = re.sub(r'\s+', ' ', body).strip()
        out.append('/* %s */\n@font-face {%s}' % (subset, body))

    with open(os.path.join(HERE, 'fonts.css'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(out) + '\n')
    print('шрифтов: %d, файлов: %d' % (len(out), len(seen)))
    for name in seen.values():
        print('  %s (%d КБ)' % (name, os.path.getsize(os.path.join(FONTS, name)) // 1024))


if __name__ == '__main__':
    main()
