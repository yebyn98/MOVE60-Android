# 세션 저장 확장 설계안 — 루틴 이름·단계별 실측 저장

**상태: 승인됨 — 사용자 "권고대로 진행"(2026-11-16)으로 7항 모두 권고안, 6번 칼로리는 정책 확정 전 미표시.**
**슬라이스 1(저장 경로) 코드 작성 완료, CI 검증 전.** 슬라이스 2(읽기·화면)와 정정 이력은 미착수.
구현 위치: `RoutivePlanSnapshot`, `TimerSnapshot`/`TimerStage`/`stageSegments`(`TimerSessionSync.kt`), `RoutiveSessionStore`(시작 시 계획 보관·`snapshot()`), `PendingSessionOutbox`(선택 필드), `SessionCommitter.sameContent`(D3).
테스트: `SessionStageSegmentsTest`(AC-S1~S7), `RoutivePlanSnapshotTest`. AC-S8(스키마 불변)은 변경 파일에 `app/schemas/`가 없음으로 확인.
목적: Figma 27(세션 상세)이 요구하는 루틴 이름·단계별 시간을 실제 저장 데이터로 보여줄 수 있게 한다. 근거와 한계는 `docs/v60-record-screens.md`.

## 1. 코드로 확인한 현재 구조
| 항목 | 확인 결과 | 위치 |
|---|---|---|
| 단계 진행 모델은 이미 단계별 실측을 가진다 | `elapsedSeconds[단계]`, `performed`, `skipped`, `plan[단계].plannedSeconds` | `domain/FourStageProgress.kt` |
| 그런데 커밋 입력이 그 정보를 버린다 | `TimerSnapshot`은 구간·총초·세트만 가짐. 루틴 이름·단계 정보 없음 | `TimerSessionSync.kt`, `RoutiveSessionStore.snapshot()` |
| 저장 시 세그먼트·계획은 항상 비어 있다 | `SessionBundle(session, rows, sets, emptyList())`, `planSnapshotJson = null` | `TimerSessionSync.bundleOrNull` |
| 스키마는 이미 준비돼 있다 | `session_segments`(kind·planned·actual), `sessions.planSnapshotJson`, `SegmentKind`, 검증기 | `RoutiveEntities.kt`, `SessionBundleValidator` |
| 운영 코드에 읽는 곳이 없다 | `segments`·`planSnapshotJson`의 읽기·쓰기 소비자 없음(커밋터의 일반 삽입·비교만) | grep 확인 |
| **멱등 판정이 전체 내용을 비교한다** | `sameContent`가 세션 행(계획 포함)·구간·분할·세트·세그먼트를 모두 비교 → 내용이 달라지면 `Conflict` | `SessionCommitter.sameContent` |
| 재시도 경로가 3개 있다 | 종료 즉시 동기화, 앱 포그라운드 재동기화, 아웃박스 drain | `Move60Application.syncTimerSessionAsync`, `PendingSessionOutbox` |
| 아웃박스는 고정 필드만 직렬화한다 | recordDate, day, startedAt, finished, endedEarly, seconds, intervals, sets | `PendingSessionOutbox.enqueue/read` |
| ROUTIVE_4 단계 순서 | 준비운동 → 근력 → 유산소 → 마무리 (`StageKind` enum 순서와 다름: 단계 인덱스는 계획 순서를 쓸 것) | `FourStageProgress` 주석 |

## 2. 제안
**스키마 변경 없음, Room 마이그레이션 없음.** 기존 컬럼만 채운다.

### D1. 계획 스냅샷 (`sessions.planSnapshotJson`)
세션 **시작 시점**에 만든 계획을 세션에 보관하고, 커밋 때 그대로 저장한다(루틴이 나중에 바뀌어도 실제로 한 계획이 남음).
```json
{"v":1,"structure":"ROUTIVE_4","routineId":"…","routineName":"하체 근력·유산소 루틴",
 "stages":[{"kind":"WARM_UP","plannedSeconds":300},{"kind":"STRENGTH","plannedSeconds":null}, …],
 "exercises":[{"stage":0,"id":"…","name":"제자리 걷기","plannedSeconds":120},
              {"stage":1,"id":"legPress","name":"레그프레스","targetSets":3,"targetReps":12,"restSeconds":60}, …]}
```
- 계획 시간 null은 null로 둔다(0이나 기본값 금지). 계획 값은 실측과 섞지 않는다(P-21).
- 시작 시 `RoutiveSessionStore`가 세션과 함께 이 JSON을 저장하는 새 선택 필드가 필요하다. 없는 세션은 null.

### D2. 단계별 실측 (`session_segments` 재사용)
| 필드 | 값 |
|---|---|
| `orderIndex` | 단계 인덱스 0..3 (계획 순서) |
| `kind` | WARM_UP→`WARMUP`, STRENGTH→`STRENGTH`, CARDIO→`CARDIO_STEADY`, COOL_DOWN→`COOLDOWN` |
| `plannedSeconds` | 계획 단계 시간(null 가능, 0 불가) |
| `actualSeconds` | 그 단계의 실제 활성 시간. **0초인 단계는 행을 만들지 않는다**(검증기가 0을 거부) |
| `equipment`, `metricsJson` | null (사용자 입력 수치용이므로 쓰지 않음) |
- 강도 구간(P-07/P-08, `CARDIO_WORK/RECOVERY`)은 후속 기능이므로 `orderIndex ≥ 100`을 예약해 충돌을 막는다.
- 단계 합 = 총 활성시간이어야 한다(`FourStageProgress.totalElapsedSeconds` = 세션 총 활성). **합이 다르면 세그먼트만 생략하고 세션은 그대로 저장**한다(불일치 데이터를 저장하지 않음).
- 60초 미만 + 완료 세트 0 세션은 지금처럼 아무것도 저장하지 않는다(D-01 B). 세그먼트·계획도 포함해서 저장하지 않는다.

### D3. 호환 규칙 (핵심 위험 대응)
`sameContent`를 다음처럼 바꾼다. **코어**(세션 행에서 `planSnapshotJson`·`committedAt` 제외, 구간, 분할, 세트)는 항상 비교하고, **보강**(계획 스냅샷, 세그먼트)은 **양쪽에 모두 있을 때만** 비교한다.
| 상황 | 결과 |
|---|---|
| 신규 세션 최초 커밋 | `Committed` |
| 동일 보강으로 재시도 | `AlreadyCommitted` |
| 이전 빌드가 보강 없이 저장한 세션을 새 빌드가 보강 포함으로 재커밋 | `AlreadyCommitted` (기존 행 불변, 보강 적용 안 함) |
| 보강이 있는 세션을 보강 없는 구형 아웃박스 항목이 재커밋 | `AlreadyCommitted` |
| 양쪽에 보강이 있는데 내용이 다름 | `Conflict` (지금과 같음) |
| 코어가 다름(초·구간·세트) | `Conflict` (지금과 같음, `SessionCommitterTest`가 고정) |
기존 세션에 보강을 **소급 적용(백필)하지 않는다**: 단계별 시간을 복원할 근거가 없고, 저장된 행의 UPDATE는 현재 DAO에 없다(추가 전용).

### D4. 아웃박스·스냅샷 포맷
- `TimerSnapshot`에 선택 필드 `stages`(종류·계획·실측)와 `planSnapshotJson`을 추가한다. 기본값 empty/null이라 레거시 5단계 경로는 그대로다.
- 아웃박스 JSON에 같은 키를 선택 필드로 추가하고 `optJSONArray`/`optString`으로 읽어 **기존 항목을 계속 읽는다**.

### D5. 화면 매핑 (Figma 26·27)
| Figma | 데이터 | 비고 |
|---|---|---|
| 세션 제목 | `routineName` | 없으면 "운동 세션"(현재와 같음) |
| 단계 아코디언 헤더(시간) | 세그먼트 `actualSeconds` | 0초 단계는 표시하지 않음 |
| 단계 항목(제자리 걷기 등) | 계획 스냅샷의 항목 | **"계획 2분"으로 표시**. 실측이 아니므로 "실제"라고 쓰지 않음 |
| 근력 종목·세트 | 현재처럼 `session_sets`(+정책 5) | 변화 없음 |
| 종목 수·세트 수 요약 | 계획 항목 수·완료 세트 수 | 항목 수는 "계획" 기준 |
| 예상 kcal | 저장하지 않음 | D6 |
| 레거시 5단계·구 세션 | 보강 없음 | 지금 화면 유지 |

### D6. 칼로리 (결정 필요)
**저장하지 않는다.** 칼로리는 측정값이 아니라 추정이라 저장하면 정정이 어렵다. 선택지:
- A. 계속 미표시(현재).
- B. 저장된 단계 실측시간 × 승인된 MET 정책 × 체중 설정으로 **표시 시점에 계산**하고 "예상"으로 표기. MET 값·체중 출처·측정 불가 시 문구에 대한 PO 정책이 먼저 필요하다. 레거시 `EnergyLedger`의 추정과 합산하지 않는다.

## 3. 저장하지 않는 것 (의도)
종목별 실측 시간(앱이 측정하지 않음), 휴식 시간(활성 시간이 아님), 단계 건너뜀/미완료 상태(0초 단계는 행이 없을 뿐 사유를 구분하지 않음), kcal, 기존 세션 백필.
> 건너뜀 상태까지 저장하려면 0초를 허용하는 신규 테이블(Room v6)이 필요하다. 이 환경에는 Android SDK가 없어 스키마 파일을 생성·검증할 수 없으므로 이번 안에서 제외했다(대안 B, 결정 항목 2).

## 4. 위험과 대응
| 위험 | 대응 |
|---|---|
| 재커밋 시 `Conflict`로 세션이 아웃박스에 영구 보관 | D3 호환 규칙 + AC-S3 테스트 |
| 단계 합과 총 활성시간 불일치 | 세그먼트 생략(세션 저장 유지), AC-S5 |
| 구형 아웃박스 항목을 읽지 못함 | 선택 필드로 읽기, AC-S7 |
| 계획을 커밋 시점 루틴에서 읽으면 변경된 계획이 저장됨 | 시작 시점에 캡처해 세션 저장소에 보관 |
| 유효 세션·도전 인정 계산에 영향 | 세그먼트·계획은 계산에 쓰지 않음, AC-S6 회귀 테스트 |
| androidTest 컴파일 오류가 JVM에서 안 잡힘 | 에뮬레이터 워크플로의 Gradle 단계를 필수 검증으로 삼음 |

## 5. 수용 기준 (AC-S)
- AC-S1: 새 ROUTIVE_4 세션이 루틴 이름·단계별 실측(0초 제외)·계획을 저장하고 26·27에 표시된다.
- AC-S2: 같은 세션을 재시도·프로세스 종료 후 재커밋해도 행이 중복되지 않는다(`AlreadyCommitted`).
- AC-S3: 보강 없이 저장된 세션을 새 빌드가 재커밋해도 `Conflict`가 아니고 기존 행이 변하지 않는다.
- AC-S4: 60초 미만 + 완료 세트 0 세션은 세그먼트·계획을 포함해 아무것도 저장하지 않는다.
- AC-S5: 단계 합 ≠ 총 활성시간이면 세그먼트만 생략되고 세션은 저장된다.
- AC-S6: 유효 세션 판정·도전 인정일·활성시간은 세그먼트·계획 유무와 무관하게 같다.
- AC-S7: 구형 아웃박스 항목이 계속 읽힌다.
- AC-S8: `app/schemas/`와 마이그레이션이 바뀌지 않는다.

## 6. 구현 슬라이스 (승인 후)
1. **저장 경로**: `RoutiveSession`·`RoutiveSessionStore`(시작 시 계획 스냅샷 보관), `TimerSnapshot`/`TimerSessionSync.bundleOrNull`(세그먼트·계획 생성), `PendingSessionOutbox`(선택 필드), `SessionCommitter.sameContent`(D3). JVM 테스트: AC-S2~S7.
2. **읽기·화면**: `RecordReader`가 계획·세그먼트를 읽고 26·27에 제목·단계 카드 표시. JVM + 에뮬레이터(캡처).
3. (선택) kcal 표시 — D6 정책 승인 후.
순서는 1 → 2. 1단계 병합 후에는 신규 세션부터 데이터가 쌓이고, 기존 세션은 현재 표시를 유지한다.

## 7. PO 결정 필요
| # | 결정 | 권고 |
|---|---|---|
| 1 | 시작 시점 계획 스냅샷(루틴 이름·단계·항목)을 세션에 저장 | 예 |
| 2 | 단계 실측을 `session_segments`에 저장 (대안: 건너뜀까지 담는 신규 테이블 v6 — SDK 환경 필요) | 기존 테이블 사용 |
| 3 | 건너뜀/미완료 상태를 이번에 저장할지 | 아니오(시간만) |
| 4 | 기존 세션 백필 안 함 + D3 호환 규칙 | 예 |
| 5 | 계획 항목은 "계획 N분"으로 표기(실측처럼 보이지 않게) | 예 |
| 6 | 칼로리: 미표시 유지 vs 표시 시점 계산(MET·체중 정책 필요) | 정책 확정 전 미표시 |
| 7 | 레거시 5단계 세션은 단계 데이터 없이 유지 | 예 |

## 8. 검증 상태
설계 문서만 작성. 코드·테스트·스키마 변경 없음. 구현은 미착수이며 위 결정이 승인된 뒤에 시작한다.
