"""Regression checks for release authorization and the exact workflow job condition."""
import ast
import itertools
import json
from pathlib import Path
import yaml

root = Path(__file__).resolve().parents[1]
def read(name):
    return yaml.load((root/'.github/workflows'/name).read_text(), Loader=yaml.BaseLoader)
release = read('build-apk.yml')
smoke = read('android-smoke.yml')
jobs = release['jobs']
assert jobs['runtime-smoke']['uses'] == './.github/workflows/android-smoke.yml'
assert 'workflow_call' in smoke['on']
assert 'main' not in smoke['on']['push']['branches'], 'Main must have a single authoritative test graph'
assert set(jobs['publish']['needs']) == {'build','runtime-smoke'}
assert not jobs['runtime-smoke'].get('if'), 'Basic test cannot be conditionally bypassed'
assert not jobs['build'].get('if'), 'Full test cannot be conditionally bypassed'
condition = jobs['publish']['if']
count = 0
for build, basic, branch in itertools.product(['success','failure','cancelled','skipped',''], repeat=3):
    # Third axis is remapped to a real branch only in the positive case.
    ref = 'refs/heads/main' if branch == 'success' else 'refs/heads/feature/test'
    expr = condition.replace('needs.build.result',repr(build)).replace('needs.runtime-smoke.result',repr(basic)).replace('github.ref',repr(ref)).replace('&&',' and ').replace('||',' or ')
    tree = ast.parse(expr,mode='eval')
    allowed = (ast.Expression,ast.BoolOp,ast.And,ast.Or,ast.Compare,ast.Eq,ast.NotEq,ast.Constant)
    assert all(isinstance(n,allowed) for n in ast.walk(tree)), 'Unexpected/bypass expression'
    actual = eval(compile(tree,'<release condition>','eval'),{'__builtins__':{}},{})
    assert actual == (build == basic == 'success' and ref == 'refs/heads/main'), (build,basic,ref)
    count += 1
for workflow in [release,smoke]:
    for job in workflow['jobs'].values():
        assert job.get('continue-on-error','false') == 'false'
        for step in job.get('steps',[]):
            assert step.get('continue-on-error','false') == 'false'
            if 'MOVE60_UPDATES_PAT' in step.get('env',{}):
                assert workflow is release and job is jobs['publish'], 'Publication credentials outside gated job'
assert 'bash scripts/smoke-android.sh' in str(jobs['build'])
assert 'bash scripts/smoke-phone-features.sh' in str(jobs['build'])
assert 'bash scripts/smoke-watch-layout.sh' in str(jobs['build'])
assert 'scripts/publish_selected.py' in str(jobs['publish']), 'Publish must respect per-device release targets'
assert 'scripts/test_publish_selected.py' in str(jobs['build']), 'Selective publish cases must be tested'
targets = json.loads((root/'scripts/release-targets.json').read_text())
assert set(targets) == {'phone','watch'} and all(isinstance(v,bool) for v in targets.values())

phone = next(s for s in jobs['build']['steps'] if 'rendered notification' in s.get('name',''))['with']
basic = next(s for s in smoke['jobs']['runtime-smoke']['steps'] if s.get('uses','').startswith('reactivecircus/'))['with']
for key in ['api-level','arch','target','profile','emulator-options','emulator-boot-timeout']:
    assert phone[key] == basic[key], key
assert '-memory 4096' in basic['emulator-options'] and '-no-snapshot' in basic['emulator-options']
# Do not mistake any calorie/informational text containing "운동 시작" for the play button.
selector = (root/'scripts/smoke-android.sh').read_text()
assert 'desc.strip() == "운동 시작 또는 일시정지"' in selector
assert '"운동 시작" in desc' not in selector, 'Never accept fuzzy informational-text selectors'

print(f'PASS: {count} publish-condition cases, mandatory dependencies, credential isolation and matching emulator configuration')
