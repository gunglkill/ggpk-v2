# -*- coding: utf-8 -*-
"""Иконки сайта из иконки приложения."""
import os

from PIL import Image

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(HERE, 'app', 'res', 'mipmap-xxxhdpi', 'ic_launcher.png')
OUT = os.path.join(HERE, 'icons')


def main():
    os.makedirs(OUT, exist_ok=True)
    im = Image.open(SRC).convert('RGBA')
    for size in (192, 512):
        if im.size != (size, size):
            im2 = im.resize((size, size), Image.LANCZOS)
        else:
            im2 = im.copy()
        im2.save(os.path.join(OUT, 'icon-%d.png' % size), optimize=True)
    im.resize((32, 32), Image.LANCZOS).save(os.path.join(OUT, 'favicon-32.png'), optimize=True)
    im.resize((180, 180), Image.LANCZOS).save(os.path.join(OUT, 'apple-touch-icon.png'), optimize=True)
    print('иконки готовы')


if __name__ == '__main__':
    main()
