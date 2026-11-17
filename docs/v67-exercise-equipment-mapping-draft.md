# v67 · 운동↔기구 대응표 (초안, PO 검토 전)

**이 표는 승인되지 않았다.** `scripts/gen-equipment-mapping.py`가 `workouts.json`과 `Equipment` enum에서 자동으로 만든 초안이며, 원문 기구명이 `Equipment` 라벨과 정확히 일치하거나 PO 결정(2026-11-17: 기구 등록 범위 확대, 로프·미니밴드 포함, 벽·의자는 맨몸)에 따라 연결했다. 'A 또는 B'는 하나만 있어도 후보로 본다(PO 결정). 어느 행도 '검증 완료'가 아니다. 앱은 이 표를 사용하지 않는다(추천 목록 미구현).

기구 ID(`Equipment`): `INDOOR_CYCLE`=실내 사이클, `ELLIPTICAL`=일립티컬, `LEG_PRESS`=레그프레스, `LAT_PULLDOWN`=랫풀다운, `CABLE_MACHINE`=케이블 머신, `DUMBBELL`=덤벨, `YOGA_MAT`=요가 매트, `RESISTANCE_BAND`=저항 밴드, `HIP_THRUST_MACHINE`=힙쓰러스트 머신, `HIP_ABDUCTION_MACHINE`=힙 어브덕션 머신, `LEG_CURL_MACHINE`=레그컬 머신, `SEATED_ROW_MACHINE`=시티드 로우 머신, `CHEST_PRESS_MACHINE`=체스트프레스 머신, `BENCH`=벤치, `STEP_BOX`=스텝박스

| 운동 ID | 운동명 | 필요 기구 ID | 맨몸 가능 여부 | 데이터 출처 | 매핑 검증 상태 |
|---|---|---|---|---|---|
| `legPress` | 레그프레스 | LEG_PRESS | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.legPress[2] = "레그프레스 머신" | 미검증(초안: 원문 기구명과 정확히 일치) |
| `gobletSquat` | 고블릿 스쿼트 | DUMBBELL | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.gobletSquat[2] = "덤벨 1개" | 미검증(초안: 원문 기구명과 정확히 일치) |
| `boxSquat` | 박스 스쿼트 | BENCH | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.boxSquat[2] = "고정 벤치" | 미검증(초안: PO 결정 2026-11-17 반영) |
| `stepUp` | 낮은 스텝업 | STEP_BOX | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.stepUp[2] = "낮은 스텝박스" | 미검증(초안: PO 결정 2026-11-17 반영) |
| `gluteBridge` | 글루트브리지 | YOGA_MAT | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.gluteBridge[2] = "매트" | 미검증(초안: 원문 기구명과 정확히 일치) |
| `hipThrust` | 힙쓰러스트 | HIP_THRUST_MACHINE 또는 BENCH | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.hipThrust[2] = "힙쓰러스트 머신/벤치" | 미검증(초안: PO 결정 2026-11-17 — A 또는 B 는 하나만 있어도 후보) |
| `cableKickback` | 케이블 킥백 | CABLE_MACHINE | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.cableKickback[2] = "케이블 머신" | 미검증(초안: 원문 기구명과 정확히 일치) |
| `hipAbduction` | 힙 어브덕션 | HIP_ABDUCTION_MACHINE | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.hipAbduction[2] = "힙 어브덕션 머신" | 미검증(초안: PO 결정 2026-11-17 반영) |
| `legCurl` | 레그컬 | LEG_CURL_MACHINE | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.legCurl[2] = "레그컬 머신" | 미검증(초안: PO 결정 2026-11-17 반영) |
| `rdl` | 덤벨 루마니안 데드리프트 | DUMBBELL | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.rdl[2] = "덤벨 2개" | 미검증(초안: 원문 기구명과 정확히 일치) |
| `calfRaise` | 카프레이즈 | 없음(맨몸) 또는 머신(종류가 원문에 없어 기구 ID 연결 안 함) | 가능(원문에 '맨몸' 표기) | workouts.json › exercise.calfRaise[2] = "머신 또는 맨몸" | 미검증(초안: PO 결정 2026-11-17 — A 또는 B 는 하나만 있어도 후보) |
| `reverseLunge` | 리버스 런지 | 없음(맨몸) 또는 DUMBBELL | 가능(원문에 '맨몸' 표기) | workouts.json › exercise.reverseLunge[2] = "맨몸/덤벨" | 미검증(초안: PO 결정 2026-11-17 — A 또는 B 는 하나만 있어도 후보) |
| `latPulldown` | 랫풀다운 | LAT_PULLDOWN | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.latPulldown[2] = "랫풀다운 머신" | 미검증(초안: 원문 기구명과 정확히 일치) |
| `seatedRow` | 시티드 로우 | SEATED_ROW_MACHINE | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.seatedRow[2] = "시티드로우 머신" | 미검증(초안: PO 결정 2026-11-17 반영) |
| `cableRow` | 케이블 로우 | CABLE_MACHINE | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.cableRow[2] = "케이블 머신" | 미검증(초안: 원문 기구명과 정확히 일치) |
| `chestPress` | 체스트프레스 | CHEST_PRESS_MACHINE | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.chestPress[2] = "체스트프레스 머신" | 미검증(초안: PO 결정 2026-11-17 반영) |
| `dbChestPress` | 덤벨 체스트프레스 | BENCH + DUMBBELL (둘 다 필요) | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.dbChestPress[2] = "벤치·덤벨" | 미검증(초안: PO 결정 2026-11-17 반영) |
| `inclinePushup` | 인클라인 푸시업 | BENCH | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.inclinePushup[2] = "견고한 벤치" | 미검증(초안: PO 결정 2026-11-17 반영) |
| `facePull` | 페이스풀 | CABLE_MACHINE (로프 포함) | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.facePull[2] = "케이블·로프" | 미검증(초안: PO 결정 2026-11-17 반영) |
| `lateralRaise` | 덤벨 사이드 레이즈 | DUMBBELL | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.lateralRaise[2] = "가벼운 덤벨" | 미검증(초안: 원문 기구명과 정확히 일치) |
| `bicepCurl` | 덤벨 바이셉 컬 | DUMBBELL | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.bicepCurl[2] = "덤벨" | 미검증(초안: 원문 기구명과 정확히 일치) |
| `tricepPushdown` | 트라이셉 푸시다운 | CABLE_MACHINE | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.tricepPushdown[2] = "케이블 머신" | 미검증(초안: 원문 기구명과 정확히 일치) |
| `deadBug` | 데드버그 | YOGA_MAT | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.deadBug[2] = "매트" | 미검증(초안: 원문 기구명과 정확히 일치) |
| `birdDog` | 버드독 | YOGA_MAT | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.birdDog[2] = "매트" | 미검증(초안: 원문 기구명과 정확히 일치) |
| `plank` | 플랭크 | YOGA_MAT | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.plank[2] = "매트" | 미검증(초안: 원문 기구명과 정확히 일치) |
| `sidePlank` | 사이드 플랭크 | YOGA_MAT | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.sidePlank[2] = "매트" | 미검증(초안: 원문 기구명과 정확히 일치) |
| `pallofPress` | 팔로프 프레스 | CABLE_MACHINE 또는 RESISTANCE_BAND | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.pallofPress[2] = "케이블·밴드" | 미검증(초안: PO 결정 2026-11-17 — A 또는 B 는 하나만 있어도 후보) |
| `wallPushup` | 벽 푸시업 | 없음(맨몸으로 분류) | 가능(PO 결정: 벽·의자는 맨몸으로 분류) | workouts.json › exercise.wallPushup[2] = "벽" | 미검증(초안: PO 결정 2026-11-17 반영) |
| `chairSquat` | 의자 스쿼트 | 없음(맨몸으로 분류) | 가능(PO 결정: 벽·의자는 맨몸으로 분류) | workouts.json › exercise.chairSquat[2] = "의자" | 미검증(초안: PO 결정 2026-11-17 반영) |
| `bandRow` | 저항 밴드 로우 | RESISTANCE_BAND | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.bandRow[2] = "저항 밴드" | 미검증(초안: 원문 기구명과 정확히 일치) |
| `clamshell` | 클램셸 | YOGA_MAT 또는 RESISTANCE_BAND (미니밴드 포함) | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.clamshell[2] = "매트/미니밴드" | 미검증(초안: PO 결정 2026-11-17 — A 또는 B 는 하나만 있어도 후보) |
| `heelSlide` | 힐 슬라이드 | YOGA_MAT | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.heelSlide[2] = "매트" | 미검증(초안: 원문 기구명과 정확히 일치) |
| `bodySquat` | 맨몸 스쿼트 | 없음(맨몸) | 가능(원문에 '맨몸' 표기) | workouts.json › exercise.bodySquat[2] = "맨몸" | 미검증(초안: 맨몸 운동 데이터 검증 필요) |
| `march` | 스탠딩 니 리프트 | 없음(맨몸) | 가능(원문에 '맨몸' 표기) | workouts.json › exercise.march[2] = "맨몸" | 미검증(초안: 맨몸 운동 데이터 검증 필요) |
| `farmerCarry` | 파머 캐리 | DUMBBELL | 아니오(원문에 '맨몸' 표기 없음) | workouts.json › exercise.farmerCarry[2] = "가벼운 덤벨 2개" | 미검증(초안: 원문 기구명과 정확히 일치) |

합계 35개 운동. 동작 안내 이미지가 있는 운동은 29개(다른 문제이며 이 표와 무관).
