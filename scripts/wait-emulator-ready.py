"""Bounded preflight: do not run UI tests over Android startup/ANR dialogs."""
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

out = Path('smoke-debug/preflight')
out.mkdir(parents=True, exist_ok=True)
def adb(*args):
    return subprocess.run(['adb', *args], capture_output=True, text=True, timeout=25)

adb('shell', 'settings', 'put', 'global', 'hide_error_dialogs', '0')
adb('shell', 'settings', 'put', 'system', 'screen_off_timeout', '300000')
adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
adb('shell', 'wm', 'dismiss-keyguard')
# Google APIs emulator setup may hang independently of MOVE60 during first boot.
# Stop it before testing, while keeping Android/app crash dialogs enabled.
adb('shell', 'pm', 'disable-user', '--user', '0', 'com.google.android.googlesdksetup')
# Boot completion alone does not mean SystemUI and package manager are ready.
time.sleep(20)
started = time.monotonic()
stable = 0
recovered_setup_anrs = 0
while time.monotonic()-started < 180:
    try:
        boot = adb('shell', 'getprop', 'sys.boot_completed')
        package = adb('shell', 'pm', 'path', 'android')
        ui = adb('shell', 'uiautomator', 'dump', '/sdcard/move60-preflight.xml')
        if boot.stdout.strip() != '1' or 'package:' not in package.stdout or ui.returncode:
            stable = 0
            time.sleep(5)
            continue
        xml = adb('shell', 'cat', '/sdcard/move60-preflight.xml').stdout
        (out/'window.xml').write_text(xml)
        root = ET.fromstring(xml)
        texts = [n.get('text','') for n in root.iter('node')]
        if any("isn't responding" in t or 'keeps stopping' in t or '응답하지' in t for t in texts):
            # Recover only known launcher / Google setup FIRST-BOOT ANRs.
            # These are emulator infrastructure apps, never MOVE60 or an unknown app.
            google_setup = any('com.google.android.googlesdksetup' in t for t in texts)
            pixel_launcher = any('Pixel Launcher' in t for t in texts)
            if (google_setup or pixel_launcher) and recovered_setup_anrs < 2:
                recovered_setup_anrs += 1
                (out/f'emulator-first-boot-anr-{recovered_setup_anrs}.xml').write_text(xml)
                close = next((n for n in root.iter('node')
                              if n.get('text') in ('Close app', '앱 닫기')), None)
                if close is not None:
                    import re
                    coords = [int(x) for x in re.findall(r'\d+', close.get('bounds',''))]
                    if len(coords) == 4:
                        adb('shell','input','tap',str((coords[0]+coords[2])//2),
                            str((coords[1]+coords[3])//2))
                package = ('com.google.android.googlesdksetup' if google_setup
                           else 'com.google.android.apps.nexuslauncher')
                adb('shell','am','force-stop',package)
                stable = 0
                time.sleep(8)
                continue
            raise RuntimeError('Android preflight blocked by crash/ANR dialog: '+str(texts))
        stable += 1
        if stable >= 2:
            print('PASS: boot, package manager and UI hierarchy ready on two consecutive checks')
            break
    except (subprocess.TimeoutExpired, ET.ParseError):
        stable = 0
    time.sleep(5)
else:
    raise RuntimeError('Android readiness timeout; no test or publish bypass permitted')
for name,args in [('memory.txt',('shell','dumpsys','meminfo')),('activity.txt',('shell','dumpsys','activity','activities'))]:
    (out/name).write_text(adb(*args).stdout)
