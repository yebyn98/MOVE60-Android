# v64 · 마이 탭 (Figma 10, 61, 62, 73)

상태: 구현 완료(코드) / 검증은 CI 결과 기준. 실기기·Watch4 검증 미수행.

## 데이터 (Room v6, 추가 전용)
- `workout_spaces(spaceId, kind HOME|GYM, name, isDefault, createdAtEpochMs)`
- `space_equipment(spaceId, equipmentKey, brand?, model?, updatedAtEpochMs)`, 공간 삭제 시 CASCADE
- 마이그레이션 5→6은 `CREATE TABLE IF NOT EXISTS` 두 개뿐. 기존 테이블·기록·정정 이력은 변경 없음. 스키마 `6.json`은 CI가 생성한 파일.
- 자동 생성(seed) 없음: 사용자가 저장한 공간·기구만 존재한다. Figma의 "3종", "1종"은 시안 값.

## 화면
| Figma | 구현 | 내용 |
|---|---|---|
| 10 마이 | `MyHomeScreen` | 누적 운동한 날(v58 날짜 ∪ 저장 세션 날짜, 같은 날짜는 1회), 내 운동 공간 행, 설정 바로가기(기존 설정 화면 유지) |
| 61 내 운동 공간 | `MySpacesScreen` | 기본 공간 카드, 공간 목록, 집 등록, 헬스장 **직접 등록**, 기본 공간 설정 |
| 62/73 보유 기구 | `SpaceEquipmentScreen` | 체크리스트(48dp), 유산소 모델(브랜드·모델 직접 입력, 선택), 저장 시에만 기록 |

## 이번 슬라이스에서 하지 않은 것 (근거·정책 없음)
LV 배지, 성취 흐름 차트, 운동 성과 리포트, 마일스톤, 운동 목적, 워치 자동 연동 상태, 헬스장 **검색**(제공처 미확정 → 직접 등록만).

## 검증 상태
- JVM: `SpaceServiceTest`, `MyGrowthTest`, 5→6 `RoutiveMigrationTest`
- 에뮬레이터: `ComposeMyTest` 3건(헬스장 등록·기구 저장 · 집 등록+유산소 모델 입력 · 기본 공간 변경+중복 이름 메시지, 모두 DB 확인), 업그레이드 계측 테스트 체인에 5→6 포함
- 기존 `ComposeArchitectureTest`의 설정 진입 경로가 마이 허브의 설정 바로가기(`my_settings_open`)를 거치도록 변경
