"""Regression checks for release authorization and the exact workflow job condition."""
import ast
import itertools
import json
from pathlib import Path
import yaml
from xml.etree import ElementTree as ET

root = Path(__file__).resolve().parents[1]
def read(name):
    return yaml.load((root/'.github/workflows'/name).read_text(), Loader=yaml.BaseLoader)
release = read('build-apk.yml')
smoke = read('android-smoke.yml')
jobs = release['jobs']
assert jobs['runtime-smoke']['uses'] == './.github/workflows/android-smoke.yml'
assert 'workflow_call' in smoke['on']
assert 'main' not in smoke['on']['push']['branches'], 'Main must have a single authoritative test graph'
assert set(jobs['publish']['needs']) == {'build','runtime-smoke','wear-gate'}
assert not jobs['runtime-smoke'].get('if'), 'Basic test cannot be conditionally bypassed'
assert not jobs['build'].get('if'), 'Full test cannot be conditionally bypassed'
assert not jobs['wear-gate'].get('if'), 'Wear test cannot be conditionally bypassed'
condition = jobs['publish']['if']
# The publish job is intentionally manual-only; all three required checks must
# succeed on main for an explicit workflow_dispatch. Validate non-release triggers.
count = 0
for build, basic, wear, branch in itertools.product(
    ['success','failure','cancelled','skipped',''], repeat=4
):
    ref = 'refs/heads/main' if branch == 'success' else 'refs/heads/feature/test'
    for event_name in ('workflow_dispatch', 'push', 'pull_request', ''):
        expr = (
            condition.replace('needs.build.result', repr(build))
                .replace('needs.runtime-smoke.result', repr(basic))
                .replace('needs.wear-gate.result', repr(wear))
                .replace('github.event_name', repr(event_name))
                .replace('github.ref', repr(ref))
                .replace('&&', ' and ').replace('||', ' or ')
        )
        tree = ast.parse(expr, mode='eval')
        allowed = (
            ast.Expression, ast.BoolOp, ast.And, ast.Or, ast.Compare,
            ast.Eq, ast.NotEq, ast.Constant
        )
        assert all(isinstance(n, allowed) for n in ast.walk(tree)), 'Unexpected/bypass expression'
        actual = eval(compile(tree, '<release condition>', 'eval'), {'__builtins__': {}}, {})
        expected = (
            event_name == 'workflow_dispatch'
            and build == basic == wear == 'success'
            and ref == 'refs/heads/main'
        )
        assert actual == expected, (build, basic, wear, ref, event_name)
        count += 1
assert any(
    'python3 scripts/verify-release-version.py' in step.get('run', '')
    for step in jobs['publish']['steps']
), 'Manual publish must re-check the published version manifest'
assert not any(
    'python3 scripts/verify-release-version.py' in step.get('run', '')
    for step in smoke['jobs']['runtime-smoke']['steps']
), 'Development PR should not require a release version increment'
# Legal verification must run before the actual public distribution step, and must never be bypassed.
publish_steps = jobs['publish']['steps']
legal_steps = [i for i, step in enumerate(publish_steps) if 'python3 scripts/verify-release-legal.py' in step.get('run', '')]
distribution_steps = [i for i, step in enumerate(publish_steps) if 'scripts/publish_selected.py' in step.get('run', '')]
assert len(legal_steps) == 1 and len(distribution_steps) == 1, 'Missing/duplicate legal or publication gate'
assert legal_steps[0] < distribution_steps[0], 'Privacy policy check must run before distribution'
assert publish_steps[legal_steps[0]].get('continue-on-error', 'false') == 'false', 'Cannot bypass privacy policy validation'
legal_verifier = (root/'scripts/verify-release-legal.py').read_text()
assert 'oss_licenses.json' in legal_verifier and '"not transitive" in scope' in legal_verifier, 'Do not bypass the known incomplete OSS dependency notice'

for workflow in [release,smoke]:
    for job in workflow['jobs'].values():
        assert job.get('continue-on-error','false') == 'false'
        for step in job.get('steps',[]):
            assert step.get('continue-on-error','false') == 'false'
            if 'MOVE60_UPDATES_PAT' in step.get('env',{}):
                assert workflow is release and job is jobs['publish'], 'Publication credentials outside gated job'
assert 'bash scripts/smoke-android.sh' in str(smoke['jobs']['runtime-smoke'])
assert 'bash scripts/smoke-phone-features.sh' in str(smoke['jobs']['runtime-smoke'])
assert 'bash scripts/smoke-watch-layout.sh' in str(jobs['wear-gate'])
assert 'android-emulator-runner@v2' not in str(jobs['build']), 'Signed build must not mix two emulators'
assert 'reactivecircus/android-emulator-runner@v2' in str(jobs['wear-gate'])
assert 'reactivecircus/android-emulator-runner@v2' in str(smoke['jobs']['runtime-smoke'])
assert 'scripts/publish_selected.py' in str(jobs['publish']), 'Publish must respect per-device release targets'
assert 'scripts/test_publish_selected.py' in str(jobs['build']), 'Selective publish cases must be tested'
targets = json.loads((root/'scripts/release-targets.json').read_text())
assert set(targets) == {'phone','watch'} and all(isinstance(v,bool) for v in targets.values())

phone = next(s for s in smoke['jobs']['runtime-smoke']['steps'] if s.get('uses','').startswith('reactivecircus/'))['with']
watch = next(s for s in jobs['wear-gate']['steps'] if s.get('uses','').startswith('reactivecircus/'))['with']
assert phone['target'] == 'google_apis' and phone['api-level'] == '35'
assert watch['target'] == 'android-wear' and watch['api-level'] == '34'
assert phone['profile'] == 'pixel_6' and watch['profile'] == 'wearos_small_round'
basic = phone
assert '-memory 4096' in basic['emulator-options'] and '-no-snapshot' in basic['emulator-options']
# Do not mistake any calorie/informational text containing "운동 시작" for the play button.
selector = (root/'scripts/smoke-android.sh').read_text()
assert 'desc.strip() == "운동 시작 또는 일시정지"' in selector
assert '"운동 시작" in desc' not in selector, 'Never accept fuzzy informational-text selectors'

# ROUTIVE is the approved user-facing brand. Do not change the applicationId;
# that would make v58 exercise history inaccessible to an in-place update.
android = '{http://schemas.android.com/apk/res/android}'
phone_app = ET.parse(root/'app/src/main/AndroidManifest.xml').getroot().find('application')
watch_app = ET.parse(root/'wear/src/main/AndroidManifest.xml').getroot().find('application')
assert phone_app is not None and phone_app.get(android+'label') == 'ROUTIVE', 'Phone launcher name must be ROUTIVE'
assert watch_app is not None and watch_app.get(android+'label') == 'ROUTIVE Watch', 'Wear launcher name must show ROUTIVE'
widget_labels = {receiver.get(android+'name'): receiver.get(android+'label')
                 for receiver in phone_app.findall('receiver')}
assert widget_labels.get('.WorkoutWidget') == 'ROUTIVE 운동 (작은)', 'Small widget label must show ROUTIVE'
assert widget_labels.get('.WorkoutWidgetLarge') == 'ROUTIVE 운동 (큰)', 'Large widget label must show ROUTIVE'
wear_strings = {node.get('name'): node.text for node in ET.parse(root/'wear/src/main/res/values/strings.xml').getroot()}
assert wear_strings.get('tile_label') == 'ROUTIVE 운동', 'Wear tile label must show ROUTIVE'
assert "applicationId 'com.move60.workout'" in (root/'app/build.gradle').read_text()
assert "applicationId 'com.move60.workout'" in (root/'wear/build.gradle').read_text()

print(f'PASS: {count} publish-condition cases, mandatory dependencies, credential isolation and matching emulator configuration')
