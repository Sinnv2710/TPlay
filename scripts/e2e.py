#!/usr/bin/env python3
"""End-to-end smoke of the installed app on a real device. Usage: e2e.py <serial> [release.apk]
Drives the UI over adb, checks texts, prints PASS/FAIL per step. Needs the tokens already saved in the app."""
import re, subprocess, sys, time

DEV = sys.argv[1]
APK = sys.argv[2] if len(sys.argv) > 2 else None
results = []

def adb(*a, timeout=60):
    return subprocess.run(['adb', '-s', DEV, *a], capture_output=True, text=True, timeout=timeout).stdout

def dump():
    adb('shell', 'uiautomator', 'dump', '/sdcard/ui.xml')
    return adb('shell', 'cat', '/sdcard/ui.xml')

def texts():
    xml = dump()
    return ' | '.join(t for t in re.findall(r'text="([^"]+)"', xml) if t)

def tap(label, exact=False, last=False):
    xml = dump()
    nodes = re.findall(r'<node [^>]*>', xml)
    hits = []
    for n in nodes:
        t = re.search(r'(?:text|content-desc)="([^"]*)"', n)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if t and b and ((t.group(1) == label) if exact else t.group(1).startswith(label)):
            hits.append(b.groups())
    if not hits:
        return False
    b = hits[-1] if last else hits[0]
    adb('shell', 'input', 'tap', str((int(b[0]) + int(b[2])) // 2), str((int(b[1]) + int(b[3])) // 2))
    return True

def wait(substr, timeout=20):
    end = time.time() + timeout
    while time.time() < end:
        t = texts()
        if substr in t:
            return t
        time.sleep(1.5)
    return None

def step(name, ok, detail=''):
    if not ok and not detail:
        detail = 'saw: ' + texts()[:160]
    results.append((name, ok, detail))
    print(('PASS ' if ok else 'FAIL ') + name + (f'  [{detail}]' if detail else ''), flush=True)
    return ok

def top():
    m = re.search(r'topResumedActivity=\S+ \S+ (\S+)', adb('shell', 'dumpsys', 'activity', 'activities'))
    return m.group(1) if m else ''

def crashes():
    return adb('shell', 'logcat', '-d', '-b', 'crash').count('FATAL EXCEPTION')

def tab(name):
    tap(name, exact=True, last=True)
    time.sleep(2)

def switch_on():
    """State of the first switch on screen (the adult switch while the Filters sheet is open), or None.
    Compose switches show up as views with checkable="true"."""
    for node in re.findall(r'<node [^>]*>', dump()):
        if 'checkable="true"' in node:
            return 'checked="true"' in node
    return None

def set_adult_off():
    """The adult switch persists between runs; start every run with it off."""
    tap('Filters', exact=True)
    time.sleep(2)
    if switch_on():
        tap('Show adult titles')
        time.sleep(1)
    adb('shell', 'input', 'keyevent', 'BACK')
    time.sleep(1)

def to_top():
    for _ in range(5):
        adb('shell', 'input', 'swipe', '1000', '500', '1000', '1500')

def scroll_find(substr, tries=7):
    """Scroll down until [substr] is on screen. Returns the screen text or None."""
    to_top()
    for _ in range(tries):
        t = texts()
        if substr in t:
            return t
        adb('shell', 'input', 'swipe', '1000', '1500', '1000', '500')
        time.sleep(0.8)
    return None

def ensure_source(prefix):
    """Open Discover and make sure the source whose name starts with [prefix] is the chosen one."""
    tab('Discover')
    parts = texts().split(' | ')
    current = parts[1] if len(parts) > 1 else ''
    if current.startswith(prefix):
        return True
    tap(current, exact=True)
    time.sleep(2)
    ok = tap(prefix)
    time.sleep(2)
    return ok

def wait_top(substr, timeout=25):
    end = time.time() + timeout
    while time.time() < end:
        if substr in top():
            return True
        time.sleep(1.5)
    return False

if APK:
    out = adb('install', '-r', APK, timeout=180)
    step('install release apk', 'Success' in out, out.strip()[-40:])
adb('shell', 'input', 'keyevent', 'WAKEUP')
adb('logcat', '-c')
adb('shell', 'am', 'force-stop', 'app.tplay')
adb('shell', 'am', 'start', '-n', 'app.tplay/.MainActivity')
time.sleep(5)
step('app launches', 'app.tplay' in top(), top())

# Settings: tokens persisted
tab('Settings')
t = scroll_find('PLAYBACK')
step('player choices shown, Built-in first', bool(t) and bool(re.search(r'PLAYBACK \| Built-in', t)))
t = scroll_find('SERVICES AND TOKENS')
if t and 'Real-Debrid API token' not in t:
    adb('shell', 'input', 'swipe', '1000', '1500', '1000', '700')
    t = texts()
step('tokens saved (Real-Debrid, TMDB, OpenSubtitles login)',
     bool(t) and all(re.search(re.escape(k) + r'[^|]*\| Saved', t) for k in ('Real-Debrid API token', 'TMDB API key', 'OpenSubtitles login')))
t = scroll_find('DIRECT DOWNLOADS')
step('direct downloads section', bool(t))
to_top()

# Library: Real-Debrid tab reaches the account
tab('Library')
tap('Real-Debrid', exact=True)
t = wait('on your account', 15)
step('Real-Debrid account list loads', t is not None, (t or '')[:60])
tap('All', exact=True)
time.sleep(2)

# Discover with the built-in source
ensure_source('YTS')
wait('Filters', 25)
set_adult_off()
t = wait('Filters', 10)
step('Discover loads movies (YTS source)', bool(t) and bool(re.search(r'\d{4}', t or '')) and 'seeds' not in (t or ''), (t or '')[:70])
tap('Filters', exact=True)
t = wait('Show adult titles', 6)
step('filters sheet: genre groups + adult switch off, 18+ hidden', bool(t) and 'Genre' in t and 'Show adult titles' in t and '18+' not in t)
tap('Comedy', exact=True)
adb('shell', 'input', 'keyevent', 'BACK')
t = wait('Comedy', 10)
step('genre filter applies (chip shown)', bool(t) and 'Filters (1)' in t)
tap('Comedy')
time.sleep(4)

# search + in-library tag + release picker + stream
tap('Search movies', exact=True)
time.sleep(1)
adb('shell', 'input', 'text', 'sintel')
adb('shell', 'input', 'keyevent', '66')
t = wait('Sintel', 15)
step('search finds Sintel with the in-library tag', bool(t) and 'in library' in t, (t or '')[:80])
tap('Sintel', exact=True)
time.sleep(2)
tap('Stream', exact=True)
t = wait('Choose a release', 15)
step('release picker lists magnet links', bool(t) and 'seeds' in t, (t or '')[:80])
adb('shell', 'input', 'keyevent', 'BACK')

# A title RD refuses to unlock must say why, in plain words
tab('Library')
tap('All', exact=True)
time.sleep(2)
if tap('Sintel (2010)'):
    t = wait('blocked', 12) or wait('no longer has', 3)
    step('unplayable title explains the Real-Debrid reason', bool(t) or 'PlayerActivity' in top(), 'plays' if 'PlayerActivity' in top() else '')
    if 'PlayerActivity' in top():
        adb('shell', 'input', 'keyevent', 'BACK')

# Library: play, resume
tab('Library')
tap('All', exact=True)
time.sleep(3)
tap('Big Buck Bunny')
started = time.time()
opened = wait_top('PlayerActivity', 45)
step('built-in player opens', opened, f'{time.time() - started:.0f}s' if opened else top())
time.sleep(3)
adb('shell', 'input', 'tap', '1000', '600')
time.sleep(1)
p1 = re.search(r'(\d+):(\d+) / ', texts())
time.sleep(4)
adb('shell', 'input', 'tap', '1000', '600')
time.sleep(1)
p2 = re.search(r'(\d+):(\d+) / ', texts())
sec = lambda m: int(m.group(1)) * 60 + int(m.group(2)) if m else -1
step('playback advances', sec(p2) > sec(p1) >= 0, f'{sec(p1)}s -> {sec(p2)}s')
adb('shell', 'input', 'keyevent', 'BACK')
time.sleep(2)
t = texts()
step('resume position saved', 'Stopped at' in t, '')

# Calendar replay
tab('Calendar')
step('calendar shows watched entry', 'Watched' in texts())
tap('Sintel', last=False)
step('calendar row plays the title', wait_top('PlayerActivity', 30), top())
adb('shell', 'input', 'keyevent', 'BACK')
time.sleep(2)

# Active: errors in plain words, direct download button
tab('Active')
t = texts()
step('failed jobs offer Download directly', 'Download directly' in t)

# Sources: manage + self test
ensure_source('YTS')
tap('YTS (en.yts-official.com)')
time.sleep(2)
tap('Manage sources')
t = wait('INSTALLED', 6)
step('Manage sources opens', bool(t))
tap('YTS (en.yts-official.com)')
time.sleep(1)
tap('Run a self test')
t = wait('cards read', 40)
step('self test reports cards, posters and magnets', bool(t) and 'magnet links' in t, re.search(r'\d+ cards read[^|]*', t or '').group(0) if t else '')
adb('shell', 'input', 'keyevent', 'BACK')
time.sleep(2)

# 1337x source: check panel or list, then TMDB posters
ensure_source('1337x')
t = wait('seeds', 30) or wait('quick check', 5)
step('1337x loads list or asks for the human check', bool(t), ('check panel' if t and 'quick check' in t else 'list') if t else '')

# Browse opens from the globe
tab('Discover')
tap('Open the browser', exact=True)
time.sleep(10)
t = texts()
step('browser opens a site', bool(re.search(r'\.(to|com|org|net)', t)) and 'Use in' not in t, t[:60])
adb('shell', 'input', 'keyevent', 'BACK')

step('no crashes during the whole run', crashes() == 0, f'{crashes()} crash entries')
bad = [r for r in results if not r[1]]
print(f'\n{len(results) - len(bad)}/{len(results)} passed')
with open('.scratch/e2e-last.txt', 'w') as out:
    for name, ok, detail in results:
        out.write(('PASS ' if ok else 'FAIL ') + name + ('  [' + detail + ']' if detail else '') + '\n')
sys.exit(1 if bad else 0)
