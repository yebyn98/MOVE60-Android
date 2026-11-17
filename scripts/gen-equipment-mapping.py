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
rows = []
for ex_id, v in data.items():
    name, _area, text = v[0], v[1], v[2]
    bare = "가능(원문에 '맨몸' 표기)" if "맨몸" in text else "아니오(원문에 '맨몸' 표기 없음)"
    mapped = EXACT.get(text.strip())
    if mapped:
        req, status = mapped, "미검증(초안: 원문 기구명과 정확히 일치)"
    elif "맨몸" in text and text.strip() == "맨몸":
        req, status = "없음(맨몸)", "미검증(초안: 맨몸 운동 데이터 검증 필요)"
    else:
        req, status = "미확정", "미확정(PO 결정 필요: 대응 기구 없음 또는 원문이 복수/모호)"
    rows.append(f"| `{ex_id}` | {name} | {req} | {bare} | workouts.json › exercise.{ex_id}[2] = \"{text}\" | {status} |")
out = ["# v67 · 운동↔기구 대응표 (초안, PO 검토 전)", "",
       "**이 표는 승인되지 않았다.** `scripts/gen-equipment-mapping.py`가 `workouts.json`과 `Equipment` enum에서 자동으로 만든 초안이며, "
       "원문 기구명이 `Equipment` 라벨과 정확히 일치할 때만 기구 ID를 제안한다. 그 밖의 운동은 '미확정'으로 두었고 임의로 연결하지 않았다. "
       "어느 행도 '검증 완료'가 아니다. 앱은 이 표를 사용하지 않는다(추천 목록 미구현).", "",
       "기구 ID(`Equipment`): " + ", ".join(f"`{k}`={v}" for k, v in equip.items()), "",
       "| 운동 ID | 운동명 | 필요 기구 ID | 맨몸 가능 여부 | 데이터 출처 | 매핑 검증 상태 |", "|---|---|---|---|---|---|"] + rows + [
       "", f"합계 {len(rows)}개 운동. 동작 안내 이미지가 있는 운동은 {len(images & set(data))}개(다른 문제이며 이 표와 무관).", ""]
(root/"docs/v67-exercise-equipment-mapping-draft.md").write_text("\n".join(out))
print(len(rows), "rows;", sum("미확정" in r.split("|")[-2] for r in rows), "unresolved")
