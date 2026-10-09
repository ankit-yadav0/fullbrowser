#!/usr/bin/env python3
"""Post-build check of the MERGED manifest (library manifests can add components/permissions that the
source manifest does not show). Usage after a build:

    python3 tools/verify_merged_manifest.py $(find app/build -path '*merged_manifest*' -name AndroidManifest.xml)

Exit status 1 on any violation. This script does not build anything; it only inspects the XML you give it.
"""
import sys
import xml.etree.ElementTree as ET

A = '{http://schemas.android.com/apk/res/android}'
EXPECTED_PERMS = {
    'android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE',
    'android.permission.FOREGROUND_SERVICE', 'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK',
    'android.permission.POST_NOTIFICATIONS', 'android.permission.CAMERA', 'android.permission.RECORD_AUDIO',
}

def check(path):
    problems, info = [], []
    root = ET.parse(path).getroot()
    pkg = root.get('package') or ''
    app = root.find('application')
    if app is None:
        return ['no <application>'], info
    if app.get(A + 'allowBackup') != 'false': problems.append('allowBackup is not false')
    if app.get(A + 'usesCleartextTraffic') != 'false': problems.append('usesCleartextTraffic is not false')
    if app.get(A + 'debuggable') == 'true': info.append('debuggable=true (acceptable for debug variants only)')
    for tag in ('activity', 'activity-alias', 'service', 'receiver', 'provider'):
        for e in app.iter(tag):
            name, exported = e.get(A + 'name'), e.get(A + 'exported')
            own = name.startswith('.') or name.startswith('com.example')
            if exported == 'true':
                if name in ('.MainActivity', 'com.example.MainActivity'):
                    continue
                (problems if own else info).append(f'exported {tag}: {name}' + ('' if own else ' (library-contributed; review)'))
            elif exported is None and e.find('intent-filter') is not None:
                problems.append(f'{tag} {name} has an intent-filter but no explicit android:exported')
    perms = {p.get(A + 'name') for p in root.iter('uses-permission')}
    extra = {p for p in perms - EXPECTED_PERMS if not (p.endswith('.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'))}
    if extra: problems.append('unexpected uses-permission: ' + ', '.join(sorted(extra)))
    return problems, info

if __name__ == '__main__':
    paths = sys.argv[1:]
    if not paths:
        print(__doc__); sys.exit(2)
    bad = False
    for p in paths:
        problems, info = check(p)
        print(f'== {p}')
        for i in info: print('  info:', i)
        for pr in problems: print('  VIOLATION:', pr)
        if not problems: print('  no violations found in this file')
        bad |= bool(problems)
    sys.exit(1 if bad else 0)
