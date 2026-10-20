# MOVE60 — Kotlin Android 운동 플래너

> **프로젝트 공통 개발 기준 / 장애 재발 방지 체크리스트 (필독)**
>
> 이 문서는 **프로젝트 루트 `/README.md`**에 위치합니다. MOVE60 코드를 수정하는 사람·AI·자동화는 **수정 전 이 문서 및 현재 소스를 확인**하고, 아래 검증 기준을 통과하기 전에는 사용자에게 "고쳤다", "정상 작동한다", "배포 완료"라고 단정하지 않습니다.
>
> **주의:** README 작성만으로 버그가 자동 방지되지는 않습니다. 테스트 자동화와 **삼성 갤럭시 실기기 검증**이 최종적으로 필요합니다.

## 1. 프로젝트 개요와 변경 불가 요구사항

- Android 패키지명: `com.move60.workout`; **Kotlin 네이티브 UI** (메인에 Java / WebView / HTML / JS 다시 도입하지 말 것).
- 삼성 갤럭시(Android 16 / One UI 포함)에서 사용할 개인용 운동 플래너.
- **월요일~금요일, 매일 총 60분**: 준비운동 + 실내 사이클 + 근력운동 + 마무리 스트레칭. 날짜마다 운동 부위·강도 차별화.
- 한 주의 운동 선택은 동일 주 안에서 고정되고 다음 주에 바뀜. 원본 운동 종류·운동방법·주의사항·세트·횟수·운동 데이터 **임의 삭제/변경 금지**.
- 화요일 실내 사이클: **30분, 18개 구간** (4분 워밍업 + [1분 빠르게 / 2분 회복] × 8 + 2분 쿨다운). 사이클 체크리스트와 실시간 타이머 구간은 일치해야 함.
- 운동 타이머는 앱 밖에서도 시간 흐름 유지; 일시정지·재개·이전/다음 단계·체크리스트·진행률·운동 상세 화면 보존.
- UI 레이아웃: 상단에 여백 확보, 요일별 단계 시간은 카드 중앙 정렬, **'오늘의 운동 체크 현황'은 '운동 순서 · 체크리스트' 바로 위**.
- 업데이트는 화면 상단의 **작은 버튼** 또는 정돈된 설정 영역에서 진입.
- **운동 중 알림창은 단순 텍스트가 아니라 카드형 타이머**(운동명, 남은 시간, 단계, RPE, 진행률 등). **잠금화면에도 가능하면 타이머 표시**, 단 Android/One UI 정책이 레이아웃과 표출을 제한할 수 있으며 실기기 검증 필요.
- 데이터를 지키기 위해 **업데이트 설치 시 기존 앱 삭제·데이터 삭제를 기본 해결책으로 제시하지 말 것**.

## 2. 주요 디렉터리와 책임

| 경로 | 역할 |
|---|---|
| `app/src/main/java/com/move60/workout/MainActivity.kt` | 메인 Kotlin UI, 사용자 동작, 알림 진단 진입 |
| `app/src/main/java/com/move60/workout/WorkoutService.kt` | Foreground Service 타이머, 알림 채널 및 RemoteViews |
| `app/src/main/java/com/move60/workout/SessionStore.kt` | 운동 진행·체크 기록, 앱 재실행 시 복구 |
| `app/src/main/java/com/move60/workout/WorkoutData.kt` | 요일별 계획 파싱, 주간 랜덤 선택 및 시간 검증 |
| `app/src/main/assets/workouts.json` | 변경 전후 동일하게 유지해야 할 운동 원본 데이터 |
| `app/src/main/java/com/move60/workout/UpdateManager.kt` | 공개 버전 확인, APK 다운로드·해시 검증·설치 안내 |
| `app/src/main/java/com/move60/workout/CrashReporter.kt` | 강제 종료 기록 및 진단 표시 |
| `app/src/main/java/com/move60/workout/Move60Application.kt` | Activity/Service 시작 전 오류 기록 초기화 |
| `app/src/main/res/layout/notification_workout_*.xml` | 카드형 진행 알림 레이아웃(사용할 수 있는 RemoteViews 위젯만) |
| `app/src/main/AndroidManifest.xml` | Android 권한, Service 선언, 앱 초기화 |
| `scripts/verify-plan.cjs` | 주간 운동 구성/시간 데이터 검증 |
| `scripts/smoke-android.sh` | 실제 에뮬레이터 설치·실행·운동 시작·알림 존재 점검 |
| `.github/workflows/android-smoke.yml` | Android 에뮬레이터 런타임 테스트 |
| `.github/workflows/build-apk.yml` | GitHub Actions 고정 서명 APK 빌드·공개 배포 |

## 3. 지금까지 발생했던 문제와 재발 방지 규칙

### A. 앱을 켜자마자 종료되거나, 운동 시작 직후 강제 종료

**실제 확인된 원인:** v12 이전의 카드형 알림에 들어 있던 **일반 `<View>`**는 `RemoteViews`에서 지원되지 않아 Android가 알림을 렌더링할 때 다음 예외가 발생했습니다.

```text
BadForegroundServiceNotificationException
Error inflating class android.view.View
```

- **Foreground Service 알림의 RemoteViews에는 Android가 지원하는 뷰만 사용**합니다. 일반 레이아웃에서 정상인 요소도 알림 레이아웃에서는 실패할 수 있습니다. 빈 공간은 지원되는 `TextView` 또는 부모 레이아웃의 여백·중력·패딩으로 구현합니다.
- `RemoteViews`를 사전에 `contentView?.apply(context, null)` 식으로 렌더링하지 않습니다. 이전 구현에서 이 부분이 예외/텍스트 폴백을 유발할 수 있었습니다.
- `NotificationManager.notify()`와 `startForeground()` 성공만으로 안전하다고 판단하지 않습니다. **Android SystemUI가 나중에 알림 뷰를 부풀리면서 앱 프로세스를 종료시킬 수 있습니다.** AndroidRuntime 로그, 실제 앱 프로세스 생존 여부까지 확인합니다.
- 실행 중 서비스가 비정상 종료됐을 때 이전 운동 기록을 **지우지 않고** 복구 화면/오류 로그를 확인합니다. 자동 서비스 재시작으로 재차 크래시가 나지 않도록 주의합니다.
- 예외를 무작정 삼키고 사용자를 텍스트 알림으로 되돌리는 방식은 **카드형 디자인 완료의 증거가 아닙니다**.

### B. 알림창에 텍스트만 보임 / 잠금화면에 알림이 없음

- 카드 XML 작성만으로 해결됐다고 주장하지 않습니다. **운동 시작 → 상단 알림창 열기 → 실제 카드 시각 확인 → 화면 잠금 → 알림 존재·타이머 시각 확인**을 별도로 검증합니다.
- Foreground Service가 살아 있는지, `POST_NOTIFICATIONS` 승인 여부, 앱 전체 알림 허용 여부, **운동 진행 채널 importance**, 잠금화면 노출 설정, 사용자 기기 제조사 정책을 구분합니다.
- Android 13+ 알림 권한, Android 14+ Foreground Service 타입 및 `FOREGROUND_SERVICE_SPECIAL_USE` 선언을 확인합니다.
- `Notification.VISIBILITY_PUBLIC`과 `setPublicVersion()`이 적용되더라도 **삼성 One UI가 전체 커스텀 레이아웃을 그대로 보여준다는 보장은 없습니다**.
- **알림 채널 ID를 매번 바꾸지 않습니다.** 변경이 꼭 필요할 때만 마이그레이션 목적·중요도·사용자 설정 영향까지 확인합니다. 이미 만든 채널의 중요도는 앱에서 일방적으로 올릴 수 없습니다.
- 알림이 보이지 않을 때에는 앱의 **알림 진단**에서 (1) 운동 진행 중인지, (2) OS에 실제 게시됐는지, (3) 카드 알림 오류 기록, (4) 채널 상태를 확인합니다.
- **카드 디자인과 잠금화면 표시 성공 여부는 각각 별개의 테스트 항목**입니다.

### C. 빌드는 성공하는데 실기기에서 계속 실패

- `assembleDebug` / `assembleRelease`의 성공은 **컴파일 결과일 뿐 앱 실행 보증이 아닙니다**.
- `.github/workflows/android-smoke.yml`에서 **연속 실행 3회 → 운동 시작 → Foreground Service 실행 → 알림 존재 → 운동 중 재실행**을 통과해야 합니다.
- 로그에 `FATAL EXCEPTION`, `AndroidRuntime`, `BadForegroundServiceNotificationException`, `RemoteServiceException`, `InflateException`이 없는지 검사합니다.
- 에뮬레이터의 알림 권한 팝업 등이 자동 UI 클릭을 막을 수 있으므로 테스트 스크립트에서 권한·UI 상호작용을 제어하고, **자동화 도구 오류와 앱 실제 크래시를 분리**합니다.
- 에뮬레이터 스크린샷과 `dumpsys notification`은 **알림 게시/화면 렌더링의 근거**이며, 삼성 갤럭시의 잠금화면 동작까지 입증하지는 못합니다.
- 앱이 실행되지 않으면 AndroidRuntime 스택트레이스, `CrashReporter` 기록을 먼저 확보합니다. **추측으로 채널 ID 변경·앱 삭제·반복 배포하지 않습니다**.

### D. APK 설치 실패 / 매번 재설치해야 함

- 구 버전의 임시 디버그 서명과 정식 고정 서명 APK는 **서명 불일치로 덮어쓰기가 불가능**했습니다.
- 이후 배포는 항상 동일한 **고정 서명 키의 `release` APK**만 사용합니다. CI의 `debug` APK는 테스트 전용입니다.
- `applicationId = com.move60.workout` 고정, **`versionCode` 증가**, release signingConfig 확인.
- 서명 키 자체, keystore, 암호, GitHub PAT를 코드·README·이슈·로그·공개 저장소에 올리지 않습니다.
- 사용 중인 운동 기록/설정 보호가 우선입니다. **삭제/데이터 초기화 전에는 사용자 확인을 받습니다**.

### E. GitHub 빌드 및 업데이트 배포의 문제

- 소스는 **비공개** `yebyn98/MOVE60-Android`; 공개 APK 배포는 **공개** `yebyn98/MOVE60-Updates`.
- GitHub Actions에서 동일한 서명 키를 쓰도록 **Repository Secrets** `MOVE60_SIGNING_BUNDLE_BASE64`을 사용합니다. ZIP 자체나 잘못된 Base64 값을 Secret에 넣으면 복원 실패합니다.
- 공개 저장소 갱신용 `MOVE60_UPDATES_PAT`를 `MOVE60-Android`의 **Repository Secrets**에 보관합니다. 토큰은 `MOVE60-Updates` 저장소에만 최소 쓰기 권한 부여.
- 공개 파일: `MOVE60-latest.apk`, `latest.json`. 매니페스트의 `versionCode`, `versionName`, `apkUrl`, `sha256` 확인.
- SHA-256은 실제 게시된 APK와 일치해야 하며, 사용자 단말의 설치 승인 절차는 유지됩니다.
- 구버전 빌드가 신버전 APK를 덮어쓰지 않도록 `build-apk.yml`의 동시 실행/구 커밋 배포 차단 규칙을 확인합니다.
- **현재 주의:** 빌드/배포 워크플로와 에뮬레이터 테스트 워크플로는 독립 실행됩니다. 따라서 **빌드 성공만으로 배포를 안전하다고 판단하지 말고 런타임 테스트 성공까지 반드시 확인**해야 합니다. 자동 배포를 실사용에 확대하기 전에는 런타임 테스트 통과를 배포의 기술적 필수 조건으로 연결하는 것이 좋습니다.

## 4. 작업 시 반드시 수행할 검증 게이트

### 코드 변경 전

- [ ] `README.md`, 수정 대상 Kotlin/레이아웃 파일, 관련 GitHub Actions 로그를 읽었다.
- [ ] 수정할 현상이 **기기에서 실제 발생했는지**와, 확인된 오류 로그/재현 조건이 무엇인지 구분했다.
- [ ] 운동 60분 구성·원본 데이터·저장소·서명 키·업데이트 흐름을 영향 없이 유지할 수 있는지 확인했다.
- [ ] 수정 범위를 최소화하고 사용자 기록 삭제가 필요한 조치를 제외했다.

### GitHub 반영 및 빌드

- [ ] 소스는 직접 **`yebyn98/MOVE60-Android`의 GitHub 저장소에 반영**했다. 로컬 ZIP만 전달하지 않는다.
- [ ] Kotlin 빌드 성공, 주중 운동 데이터 검증 통과, 고정 서명 릴리스 APK 생성 성공.
- [ ] **Android 에뮬레이터 런타임 테스트 성공** (운동 시작 포함). 실패 시 로그와 화면을 확인하고 수정 후 재실행.
- [ ] 알림 관련 수정은 알림 존재, 실제 카드 UI 스크린샷, 타이머 진행을 별도 확인.
- [ ] APK가 `MOVE60-Updates`에 게시되었고 매니페스트 버전/해시가 일치하는지 확인.
- [ ] vN → vN+1 덮어쓰기 경로를 확인한다. 기존 앱 삭제를 안내하지 않는다.
- [ ] **삼성 갤럭시 잠금화면 실기기 테스트 미실시라면 미검증 상태라고 명시**한다.

### 배포/사용자 안내

- [ ] 사용자에게 **GitHub 빌드 성공·에뮬레이터 실행 성공·삼성 실기기 정상 여부**를 서로 구분해 알린다.
- [ ] 테스트에 실패한 APK를 **'정상 버전'이라고 배포하지 않는다**.
- [ ] 사용자에게 별도 Android Studio 작업을 요구하지 않는다. 이 프로젝트의 변경은 **GitHub Actions로 APK 빌드**한다.
- [ ] 문제 지속 시 로그/화면 캡처를 받아 원인 기반으로 수정한다. 같은 설정 안내와 삭제/재설치를 반복하지 않는다.

## 5. 주요 실행·빌드 명령

```bash
# 원본 운동 데이터 검증
node scripts/verify-plan.cjs

# 로컬 SDK 설치 환경 또는 GitHub Actions에서
gradle --no-daemon :app:assembleDebug
gradle --no-daemon :app:assembleRelease

# Android 에뮬레이터가 켜진 테스트 환경에서
bash scripts/smoke-android.sh
```

- 정식 워크플로: [Build MOVE60 Kotlin Android APK](https://github.com/yebyn98/MOVE60-Android/actions/workflows/build-apk.yml)
- 실행 테스트 워크플로: [MOVE60 Android runtime smoke test](https://github.com/yebyn98/MOVE60-Android/actions/workflows/android-smoke.yml)
- 공개 버전 메타데이터: https://raw.githubusercontent.com/yebyn98/MOVE60-Updates/main/latest.json
- 공개 다운로드: https://raw.githubusercontent.com/yebyn98/MOVE60-Updates/main/MOVE60-latest.apk

## 6. 검증 이력 (2026-10-21 기준)

| 사건 | 확인된 사실 | 처리/남은 확인 |
|---|---|---|
| v5~v7 | 디버그 서명 교체로 설치 시 서명 불일치 발생 | 고정 릴리스 서명 체계로 변경 |
| v10 | 고정 서명/배포 Secrets 복원·공개 배포 연결에 오류 | 수정 후 v10 릴리스 및 버전 매니페스트 배포 성공 |
| v11~v12 | 갤럭시에서 실행 종료, 텍스트 알림 또는 잠금화면 미표시 문제 반복 | 실제 Android 에뮬레이터 로그로 원인 조사 |
| v12 에뮬레이터 | `BadForegroundServiceNotificationException`, `Error inflating class android.view.View` 실재 | 지원되지 않는 `View`를 알림 레이아웃에서 제거 |
| v13 | 정식 빌드 + Android 에뮬레이터 실행/운동 시작 테스트 성공 기록 존재 | **삼성 One UI에서 카드 UI·잠금화면·지속 안정성은 별도로 검증 필요** |

**다음 작업 우선순위:** (1) 삼성에서 앱 재실행/운동 시작 시 강제 종료 여부 재현·로그 확보 → (2) 실제 알림창 카드 렌더링 → (3) 잠금화면 표시·OS 정책 확인 → (4) 디자인 추가 개선. **안정성이 해결되기 전 시각 요소만 반복 수정하지 말 것.**
