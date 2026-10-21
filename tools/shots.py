# -*- coding: utf-8 -*-
"""Скриншоты сайта в заданных ширинах через CDP Edge (без Selenium).

Использование:
    python tools/shots.py [имя=URL ...]
по умолчанию снимает index.html во всех ширинах.
"""
import base64
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request

import websocket

EDGE = r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(HERE, 'tools', 'shots')
PORT = 9333
BASE = 'http://127.0.0.1:8765/'
WIDTHS = [320, 360, 390, 430, 768, 1024, 1440]
HEIGHT = 900


class CDP:
    def __init__(self, url):
        self.ws = websocket.create_connection(url, timeout=30)
        self.n = 0

    def call(self, method, params=None):
        self.n += 1
        mid = self.n
        self.ws.send(json.dumps({'id': mid, 'method': method, 'params': params or {}}))
        while True:
            msg = json.loads(self.ws.recv())
            if msg.get('id') == mid:
                if 'error' in msg:
                    raise RuntimeError('%s: %s' % (method, msg['error']))
                return msg.get('result', {})

    def close(self):
        try:
            self.ws.close()
        except Exception:  # noqa: BLE001
            pass


def http_json(path):
    req = urllib.request.Request('http://127.0.0.1:%d%s' % (PORT, path),
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=15) as r:
        return json.load(r)


def wait_page(tries=60):
    last = None
    for _ in range(tries):
        try:
            for t in http_json('/json/list'):
                if t.get('type') == 'page' and t.get('webSocketDebuggerUrl'):
                    return t
        except Exception as e:  # noqa: BLE001
            last = e
        time.sleep(0.2)
    raise RuntimeError('CDP не поднялся: %s' % last)


def metrics(cdp, width, height):
    cdp.call('Emulation.setDeviceMetricsOverride', {
        'width': width,
        'height': height,
        'deviceScaleFactor': 2,
        'mobile': width < 700,
        'screenWidth': width,
        'screenHeight': height,
    })


def shot(cdp, name, url, width, full=True):
    metrics(cdp, width, HEIGHT)
    cdp.call('Page.navigate', {'url': url})
    time.sleep(1.5)
    height = HEIGHT
    if full:
        m = cdp.call('Page.getLayoutMetrics')
        height = int(m['cssContentSize']['height'])
        height = min(max(height, HEIGHT), 14000)
        metrics(cdp, width, height)
        time.sleep(0.35)
    data = cdp.call('Page.captureScreenshot', {
        'format': 'png',
        'captureBeyondViewport': full,
        'fromSurface': True,
    })
    path = os.path.join(OUT, '%s-%d.png' % (name, width))
    with open(path, 'wb') as f:
        f.write(base64.b64decode(data['data']))
    return path


def main(argv):
    os.makedirs(OUT, exist_ok=True)
    pages = argv[1:] or ['index']
    profile = tempfile.mkdtemp(prefix='ggpk-cdp-')
    proc = subprocess.Popen([
        EDGE, '--headless=new', '--disable-gpu', '--hide-scrollbars',
        '--remote-debugging-port=%d' % PORT, '--remote-allow-origins=*',
        '--user-data-dir=%s' % profile,
        '--no-first-run', '--no-default-browser-check', 'about:blank',
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        tab = wait_page()
        cdp = CDP(tab['webSocketDebuggerUrl'])
        try:
            cdp.call('Page.enable')
            for page in pages:
                if '=' in page:
                    name, url = page.split('=', 1)
                else:
                    name, url = page, BASE + page + '.html'
                if not url.startswith('http'):
                    url = BASE + url
                for w in WIDTHS:
                    print(shot(cdp, name, url, w))
        finally:
            cdp.close()
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=10)
        except Exception:  # noqa: BLE001
            proc.kill()
        shutil.rmtree(profile, ignore_errors=True)


if __name__ == '__main__':
    main(sys.argv)
