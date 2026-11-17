# v68 · Phase 2 기록 정확성·데이터 안전성 점검 (2026-11-17, 통합 브랜치 `f02125f` 기준)

**코드·자동 테스트로 확인한 것과 실기기에서 확인한 것을 구분한다. 실기기·Watch4 확인은 하나도 하지 않았다.**

## 1. 점검 결과 (항목 ↔ 근거)
| 점검 대상 | 근거(자동 테스트) | 실행 증빙 | 실기기 |
|---|---|---|---|
| Room v1~v6 마이그레이션, 기존 기록 보존 | `RoutiveMigrationTest` 7개: v1→최신 전체 업그레이드+스키마 일치, 실제 Room으로 열어 옛 데이터 읽기, **실패 시 롤백·기존 데이터 유지**, v2→3, 3→4, 4→5, 5→6(기존 행 불변) | 로컬 JVM 313개 통과, CI JVM run 88 | 미검증 |
| 실제 Room 파일 업그레이드(에뮬레이터) | `RoutiveUpgradeInstrumentedTest` 2개 | CI 스모크 run 383: 96개 테스트에 포함, 통과 | 미검증 |
| v58 → v2 업그레이드 | `SessionStructureCompatTest`(업데이트 전 실행 중 세션을 옛 구조로 불러 진행 유지, 알 수 없는 표식도 초기화하지 않음), `LegacySetReaderTest`(v58 세트 기록을 값 그대로 표시, 추측 연결 금지, 읽기 반복해도 불변) | JVM 통과 | **실제 v58 앱 위에 덮어쓰기: 미검증**(v58 설치 파일 없음) |
| 4단계 시작·일시정지·재개·종료, 수동 이동, 총 활성시간 | `FourStageProgressTest` 13개(건너뛰기·뒤로 가기가 시간을 지우지 않음, 조기 종료 시 경과시간 보존), `RoutiveSessionStoreTest` | JVM 통과 | 미검증 |
| 유효 세션(60초 또는 근력 1세트), 도전 600초 | `WorkoutRecognitionPolicyTest`, `ChallengeRulesTest`(자정·DST·하루 상한), `SessionCommitterTest`(59초 폐기, 조작된 유효 값 거부) | JVM 통과 | 미검증 |
| 계획시간 `null`, 계획·실측 분리 | `FourStageProgressTest`(미정은 0이 아님), `RoutivePlanSnapshotTest`, `SessionStageSegmentsTest` | JVM 통과 | 미검증 |
| 중복 저장 방지·재시도 | `SessionCommitterTest`(같은 ID 재시도 멱등, 내용이 다르면 충돌로 저장 데이터 불변), `PendingSessionOutboxTest`, `RoutiveOverCommitTest`(화면·서비스 동시 커밋 시 기록 1개) | JVM 통과 | 워치 완료 시나리오 미검증 |
| 앱 종료·재실행·프로세스 종료 후 복구 | `RoutiveSessionStoreTest`(종료 후에도 시간 유지), outbox 영속, `RoutiveSessionStoreTest`의 임시 보관 실패 시 이전 기록 보존 | JVM 통과(새 인스턴스로 재현) | **실제 프로세스 종료: 미검증** |
| 원본·정정 이력 | `SessionCorrectionServiceTest`(원본 불변, 정정은 추가만, 이전 값 체인, 도전 결과 불변) | JVM 통과 | 미검증 |
| 구기록·신기록 중복 집계 방지 | `LegacySetReaderTest`(연결된 세션은 저장 세트 + 겹치지 않는 구값만), `RecordReaderTest`, `WorkoutResultReaderTest` | JVM 통과 | 미검증 |

## 2. 사용자 기록을 지우거나 덮어쓰는 코드 (정적 점검)
- Room: 삭제 쿼리는 `session_drafts`(임시본, draftId 기준)와 `space_equipment`(공간 기구를 트랜잭션 안에서 교체)뿐. `fallbackToDestructiveMigration`·`deleteDatabase`·`clearAllTables`·`@Delete`·`DROP TABLE` 없음.
- SharedPreferences: 기록 값을 지우는 곳은 임시 보관함(outbox) 항목 삭제뿐이며, 저장 확인(`Committed`/`AlreadyCommitted`) 또는 정책상 폐기(`Discarded`) 이후에만 일어난다. 나머지 `remove`는 업데이트 다운로드 상태·크래시 보고 상태다.
- 재발 방지: `scripts/verify-data-safety.py`를 만들어 스모크 CI에 넣었다(파괴적 호출, 검토되지 않은 DELETE, 마이그레이션 안의 DROP/RENAME, 스키마·마이그레이션 사슬 누락을 실패 처리). 4가지 변조 시험으로 위반 검출을 확인했다.

## 3. 이 점검으로 발견한 위험·한계
| 번호 | 내용 | 영향 | 처리 |
|---|---|---|---|
| D-1 | `versionCode 58`/`versionName 58.0` 그대로 | 이미 v58을 설치한 사용자에게 같은 코드로는 업데이트가 설치되지 않음 | **출시 전 올려야 함**(Phase 5, `verify-release-version.py` 기준) |
| D-2 | 실제 v58 앱 → v2 덮어쓰기 설치 테스트 없음 | 구형 SharedPreferences·Room 데이터가 실제 앱 업데이트에서 보존되는지 자동 증명 불가 | v58 서명 설치 파일이 있으면 에뮬레이터 업그레이드 테스트 작성 가능. 없으면 실기기 절차(아래) |
| D-3 | 프로세스 강제 종료는 새 인스턴스로 시뮬레이션만 함 | 실제 OS의 프로세스 종료 시점(쓰기 도중)은 미검증 | 실기기 절차 |
| D-4 | 유산소 실측값·강도 구간은 저장하지 않음(미구현) | 정정할 값 자체가 없음. 값을 만들어 채우지 않음 | Phase 3 |
| D-5 | 60초 미만·세트 없음 세션은 정책상 폐기 | 실수로 짧게 끝낸 운동은 남지 않음(확정 정책) | 변경 없음, 사용자 안내 문구 확인은 UX 점검 대상 |

## 4. 실기기 검증 절차 (사용자 수행, 결과는 아직 없음)
1. **v58 → v2 업그레이드**: v58 앱 설치 → 운동 3일치 기록(근력 세트 포함)·도전 1개 생성 → 앱 삭제 없이 v2 덮어쓰기 → 기록 달력의 날짜·세트 값·도전 진행이 그대로인지, 기록 화면에 중복 세션이 없는지 확인. 기대: 모든 기존 값 불변, 오늘 홈에서 kcal 숫자가 보이지 않고 기록 화면에서 배너가 1회만 나옴.
2. **프로세스 종료 복구**: 운동 중(근력 2세트 완료) 최근 앱 목록에서 앱 강제 종료 → 재실행 → 진행 중인 운동과 완료한 세트·경과 시간이 유지되는지. 휴식 타이머가 돌던 중이면 남은 시간이 이어지는지.
3. **워치로 완료**: 폰 앱을 닫은 채 워치에서 마지막 단계를 완료 → 폰을 열지 않고 알림 후 기록 달력에서 세션 1개, 도전 실적 반영 확인, 폰 화면을 열었을 때 같은 세션이 2개로 보이지 않는지.
4. **저장 실패 복구**: 저장 공간이 거의 찬 기기에서 운동 완료 → 재시도 후 기록 1개만 남는지.
