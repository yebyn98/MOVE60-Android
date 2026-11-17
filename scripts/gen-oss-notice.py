#!/usr/bin/env python3
"""Lists the DIRECT runtime dependencies of :app and :wear and reads each one's license from its published POM
(Google Maven, then Maven Central; a parent POM is followed). Only licenses that a POM actually states are written to
app/src/main/assets/oss_licenses.json (the in-app list). Anything not confirmed goes to the report as 'unconfirmed' and is NOT shown
as if it were known. Transitive libraries are not enumerated here: run `gradle :app:dependencies :wear:dependencies` to cover them."""
import json, pathlib, re, sys, urllib.request, xml.etree.ElementTree as ET
root = pathlib.Path(__file__).resolve().parent.parent
BASES = ["https://dl.google.com/dl/android/maven2/", "https://repo.maven.apache.org/maven2/"]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
def pom(g, a, v):
    path = f"{g.replace('.', '/')}/{a}/{v}/{a}-{v}.pom"
    for b in BASES:
        try:
            with urllib.request.urlopen(b + path, timeout=30) as r: return ET.fromstring(r.read()), b + path
        except Exception: pass
    return None, None
def text(el, path): n = el.find(path, NS); return n.text.strip() if n is not None and n.text else None
def licenses(g, a, v, depth=0):
    root_el, url = pom(g, a, v)
    if root_el is None: return None, None
    found = [(text(l, "m:name"), text(l, "m:url")) for l in root_el.findall("m:licenses/m:license", NS)]
    if found: return found, url
    parent = root_el.find("m:parent", NS)
    if parent is not None and depth < 3:
        return licenses(text(parent, "m:groupId"), text(parent, "m:artifactId"), text(parent, "m:version"), depth + 1)
    return None, url
deps = {}
bom = None
for mod in ("app", "wear"):
    src = (root / mod / "build.gradle").read_text()
    bom = re.search(r"platform\('([^']+)'\)", src).group(1)
    for line in src.splitlines():
        m = re.match(r"\s*implementation\s+'([^':]+):([^':]+)(?::([^':]+))?'", line)
        if m: deps.setdefault((m.group(1), m.group(2)), {"version": m.group(3), "modules": set()})["modules"].add(mod)
bg, ba, bv = bom.split(":")
bom_el, _ = pom(bg, ba, bv)
managed = {}
if bom_el is not None:
    for d in bom_el.findall("m:dependencyManagement/m:dependencies/m:dependency", NS):
        managed[(text(d, "m:groupId"), text(d, "m:artifactId"))] = text(d, "m:version")
out, unconfirmed = [], []
for (g, a), info in sorted(deps.items()):
    v = info["version"] or managed.get((g, a))
    lic, url = licenses(g, a, v) if v else (None, None)
    entry = {"group": g, "artifact": a, "version": v, "modules": sorted(info["modules"])}
    if lic:
        entry["licenses"] = [{"name": n, "url": u} for n, u in lic]; entry["source"] = url; out.append(entry)
    else: unconfirmed.append(entry)
(root / "app/src/main/assets/oss_licenses.json").write_text(json.dumps({"scope": "direct runtime dependencies of :app and :wear (not transitive)", "libraries": out}, ensure_ascii=False, indent=1) + "\n")
rep = ["# v67 · 오픈소스 라이선스 점검 결과 (직접 의존성)", "",
       "`scripts/gen-oss-notice.py`가 각 라이브러리의 **게시된 POM**에서 라이선스를 읽은 결과이다. POM에 명시된 라이선스만 앱 목록(`assets/oss_licenses.json`)에 들어간다.", "",
       "**범위 한계(출시 전 확인 필요):** 직접 의존성만 다뤘다. 전이 의존성(androidx·Kotlin·Guava 등이 가져오는 라이브러리)은 열거하지 않았다. "
       "`gradle :app:dependencies :wear:dependencies`로 전체 목록을 만들어 같은 방식으로 확인해야 한다. 법적 고지 의무(저작권 표시, NOTICE 파일 전문)는 "
       "법무/PO 검토 대상이며 이 문서가 그 충족을 보증하지 않는다.", "",
       "| 라이브러리 | 버전 | 모듈 | 라이선스(POM 명시) | 근거 |", "|---|---|---|---|---|"]
for e in out: rep.append(f"| {e['group']}:{e['artifact']} | {e['version']} | {', '.join(e['modules'])} | " + "; ".join(l['name'] or '?' for l in e['licenses']) + f" | {e['source']} |")
rep += ["", "## 확인하지 못한 항목 (앱 목록에 넣지 않음)"] + ([f"- {e['group']}:{e['artifact']}:{e['version']} ({', '.join(e['modules'])})" for e in unconfirmed] or ["- 없음"])
rep += ["", "## 라이브러리가 아닌 자산", "- 운동 가이드 이미지 29종: Free Exercise DB(Unlicense) — `docs/exercise-images-sources.md`. 원 저장소의 개별 이미지 권리는 이 점검에서 따로 확인하지 않았다."]
(root / "docs/v67-oss-licenses-report.md").write_text("\n".join(rep) + "\n")
print(len(out), "confirmed;", len(unconfirmed), "unconfirmed")
for e in unconfirmed: print("  unconfirmed:", e["group"], e["artifact"], e["version"])
