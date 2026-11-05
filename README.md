# MOVE60 — Android · Wear OS 운동 앱

MOVE60은 60분 운동을 관리하는 Android 앱입니다. 휴대폰 `app`과 Galaxy Watch4용 `wear` 모듈을 별도로 빌드합니다.

## 현재 화면과 기능
- **첫 화면(Compose):** 요일/상체·하체 선택, 운동 계획, 진행 상태, 설정 및 운동 기록
- **운동 상세 화면(Android View):** 단계·타이머·세트 중량/횟수/완료 기록, 운동 교체 및 동작 안내. 상세 화면은 Compose로 완전히 이전된 상태가 아닙니다.
- **워치:** 실시간 센서 심박수, 운동 단계, 타이머/휴식·진행 상태, 운동 타일
- **Health Connect:** 사용자가 읽기 권한을 승인한 심박수 기록만 조회합니다. 삼성 헬스 데이터를 직접 읽는 SDK 연동이 아닌 Health Connect 연동입니다. 기록이 없거나 동기화되지 않으면 값을 만들어 표시하지 않습니다.
- **운동 영상:** 로컬 자세 도식과 외부 가이드 검색만 제공합니다. 실제 내장 영상 플레이어와 검증된 콘텐츠 URL은 미구현 상태입니다.

## 프로젝트 구조
- `app/src/main/java/com/move60/workout/architecture/`: Compose 첫 화면·뷰모델·Room·리포지터리
- `app/src/main/java/com/move60/workout/MainActivity.kt`: 기존 상세 운동 UI 및 운동 진행
- `app/src/main/java/com/move60/workout/health/`: Health Connect 권한 및 읽기/워치 공유
- `app/src/main/assets/workouts.json`: 운동별 구성과 자세 텍스트
- `wear/src/main/`: Wear OS 센서, UI, 동기화
- `design/tokens.json`: 공통 디자인 토큰
- `.github/workflows/android-smoke.yml`: PR 빌드/Lint/휴대폰·워치 검사
- `.github/workflows/build-apk.yml`: main 검사, APK 서명, 배포
- `scripts/`: 검사·에뮬레이터 스모크·선택 배포

## 검증
```bash
python3 scripts/verify-v36-ui.py
python3 scripts/verify-compose-architecture.py
python3 scripts/verify-design-tokens.py
node scripts/verify-plan.cjs
gradle --no-daemon :app:lintVitalRelease :wear:lintVitalRelease :app:assembleDebug :wear:assembleDebug :app:assembleDebugAndroidTest :wear:assembleDebugAndroidTest
```
Android SDK·JDK 17·Gradle 8.9 환경이 필요합니다. PR 검사는 휴대폰 에뮬레이터와 작은 원형 Wear OS 에뮬레이터에서 수행합니다.

**검증이 성공하기 전 main 병합·배포 금지.** CI 통과만으로 실제 Galaxy Watch4의 심박 센서, Health Connect 연동, 실물 화면 자르기나 APK 설치까지 검증됐다고 말하지 않습니다.

## 시각적 검증
코드를 바꾼 뒤 변경된 화면의 스크린샷을 PR 검사 아티팩트에 보관합니다. 완료 보고 시 실제 화면 이미지 또는 다운로드 가능한 캡처/프로토타입 링크, 검증한 기기·해상도, 남은 사항을 함께 제공합니다. 추정 목업을 실제 캡처라고 표시하지 않습니다.

작업자 공통 지침은 [CLAUDE.md](CLAUDE.md)를 확인하세요. 지난 운동·칼로리·세트 기록은 사용자 데이터이므로 정리 작업을 이유로 임의 삭제하지 않습니다.
