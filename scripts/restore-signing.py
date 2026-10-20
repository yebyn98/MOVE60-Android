import os
import io
import sys
import pathlib
import zipfile
import base64
import binascii

value = os.environ.get('MOVE60_SIGNING_BUNDLE_BASE64', '').strip()
if not value:
    print('::error::MOVE60_SIGNING_BUNDLE_BASE64 is unavailable in MOVE60-Android Actions secrets.')
    sys.exit(1)
if value.startswith('MOVE60_SIGNING_BUNDLE_BASE64='):
    value = value.split('=', 1)[1].strip()
value = value.strip(chr(34) + chr(39) + chr(96))
data = value.encode('utf-8')
status = 'unrecognized'
for attempt in range(5):
    if zipfile.is_zipfile(io.BytesIO(data)):
        with zipfile.ZipFile(io.BytesIO(data)) as bundle:
            entries = {n.rsplit('/', 1)[-1]: n for n in bundle.namelist()}
            if {'move60.p12', 'signing.env'} <= entries.keys():
                target = pathlib.Path(os.environ['RUNNER_TEMP']) / 'move60-signing'
                target.mkdir(parents=True, exist_ok=True)
                for name in ('move60.p12', 'signing.env'):
                    (target / name).write_bytes(bundle.read(entries[name]))
                status = 'restored'
                break
            if 'MOVE60_SIGNING_BUNDLE_BASE64.txt' in entries:
                data = bundle.read(entries['MOVE60_SIGNING_BUNDLE_BASE64.txt']).strip()
                continue
            status = 'zip-without-expected-files'
        break
    try:
        data = base64.b64decode(b''.join(data.split()), validate=True)
    except (binascii.Error, ValueError):
        status = 'not-a-valid-base64-bundle'
        break
if status != 'restored':
    print('::error::Signing secret exists but it does not contain a valid MOVE60 signing bundle: ' + status)
    sys.exit(1)
print('Stable signing bundle restored from Actions secret.')
