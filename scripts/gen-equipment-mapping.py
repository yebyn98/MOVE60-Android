#!/usr/bin/env python3
"""Writes the exercise <-> equipment mapping DRAFT (docs/v67) from the real data. Nothing here is approved: every row is
"미검증(초안)" until the PO reviews it. Mapping is only proposed where the source text says the equipment name itself."""
import json, pathlib, re
root = pathlib.Path(__file__).resolve().parent.parent
data = json.loads((root/"app/src/main/assets/workouts.json").read_text())["exercise"]
equip = dict(re.findall(r'([A-Z_]+)\("([^"]+)",\s*(?:true|false)\)', (root/"app/src/main/java/com/move60/workout/architecture/SpaceModel.kt").read_text()))
label_to_id = {v: k for k, v in equip.items()}
images = {p.name[:-6] for p in (root/"app/src/main/assets/exercise").glob("*_0.jpg")}
EXACT = {"레그프레스 머신": "LEG_PRESS", "랫풀다운 머신": "LAT_PULLDOWN", "케이블 머신": "CABLE_MACHINE", "덤벨 1개": "DUMBBELL",
         "덤벨 2개": "DUMBBELL", "가벼운 덤벨": "DUMBBELL", "가벼운 덤벨 2개": "DUMBBELL", "덤벨": "DUMBBELL", "매트": "YOGA_MAT", "저항 밴드": "RESISTANCE_BAND"}
# PO decisions of 2026-11-17 (equipment widened; rope and mini band count as cable machine / resistance band; wall and chair = body weight).
# These rows are still DRAFT mapping: the exercise<->equipment relation is not approved until the PO reviews the table.
PO_RULES = {
    "boxSquat": ("BENCH", False), "stepUp": ("STEP_BOX", False), "hipAbduction": ("HIP_ABDUCTION_MACHINE", False),
    "legCurl": ("LEG_CURL_MACHINE", False), "seatedRow": ("SEATED_ROW_MACHINE", False), "chestPress": ("CHEST_PRESS_MACHINE", False),
    "inclinePushup": ("BENCH", False), "dbChestPress": ("BENCH + DUMBBELL (둘 다 필요)", False), "facePull": ("CABLE_MACHINE (로프 포함)", False),
    "wallPushup": ("없음(맨몸으로 분류)", True), "chairSquat": ("없음(맨몸으로 분류)", True),
}
# "A or B" sources: the PO has not yet decided whether one of them is enough.
OR_RULES = {
    "hipThrust": "HIP_THRUST_MACHINE 또는 BENCH", "reverseLunge": "없음(맨몸) 또는 DUMBBELL", "pallofPress": "CABLE_MACHINE 또는 RESISTANCE_BAND",
    "clamshell": "YOGA_MAT 또는 RESISTANCE_BAND (미니밴드 포함)", "calfRaise": "원문에 '머신'의 종류 없음 → 확정 불가",
}
rows = []
for ex_id, v in data.items():
    name, _area, text = v[0], v[1], v[2]
    bare = "가능(원문에 '맨몸' 표기)" if "맨몸" in text else "아니오(원문에 '맨몸' 표기 없음)"
    if ex_id in PO_RULES:
        req, body = PO_RULES[ex_id]
        if body: bare = "가능(PO 결정: 벽·의자는 맨몸으로 분류)"
        status = "미검증(초안: PO 결정 2026-11-17 반영)"
    elif ex_id in OR_RULES:
        req, status = OR_RULES[ex_id], "PO 결정 대기('A 또는 B'에서 하나만 있으면 후보로 볼지)"
    elif EXACT.get(text.strip()):
        req, status = EXACT[text.strip()], "미검증(초안: 원문 기구명과 정확히 일치)"
    elif text.strip() == "맨몸":
        req, status = "없음(맨몸)", "미검증(초안: 맨몸 운동 데이터 검증 필요)"
    else:
        req, status = "미확정", "미확정(PO 결정 필요)"
    rows.append(f"| `{ex_id}` | {name} | {req} | {bare} | workouts.json › exercise.{ex_id}[2] = \"{text}\" | {status} |")
out = ["# v67 · 운동↔기구 대응표 (초안, PO 검토 전)", "",
       "**이 표는 승인되지 않았다.** `scripts/gen-equipment-mapping.py`가 `workouts.json`과 `Equipment` enum에서 자동으로 만든 초안이며, "
       "원문 기구명이 `Equipment` 라벨과 정확히 일치하거나 PO 결정(2026-11-17: 기구 등록 범위 확대, 로프·미니밴드 포함, 벽·의자는 맨몸)에 따라 연결했다. 'A 또는 B'는 PO 결정 대기로 남겼다. "
       "어느 행도 '검증 완료'가 아니다. 앱은 이 표를 사용하지 않는다(추천 목록 미구현).", "",
       "기구 ID(`Equipment`): " + ", ".join(f"`{k}`={v}" for k, v in equip.items()), "",
       "| 운동 ID | 운동명 | 필요 기구 ID | 맨몸 가능 여부 | 데이터 출처 | 매핑 검증 상태 |", "|---|---|---|---|---|---|"] + rows + [
       "", f"합계 {len(rows)}개 운동. 동작 안내 이미지가 있는 운동은 {len(images & set(data))}개(다른 문제이며 이 표와 무관).", ""]
(root/"docs/v67-exercise-equipment-mapping-draft.md").write_text("\n".join(out))
print(len(rows), "rows;", sum("대기" in r.split("|")[-2] or "미확정" in r.split("|")[-2] for r in rows), "still open")
