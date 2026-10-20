# MOVE60 Android — GitHub Actions 자동 APK 빌드

헬스장 운동 플래너: 실내 사이클 중심 60분, 요일별 운동 부위/RPE, 매주 자동 운동 조합, 통일된 체크박스, 운동 자세 상세 패널 및 Android 잠금화면 운동 카운트다운.

## 초보자를 위한 APK 빌드

1. GitHub에서 **비공개(private)** 저장소(예: `MOVE60-Android`)를 만듭니다. 처음에는 README 자동 생성을 끄면 업로드하기 간단합니다.
2. 이 폴더의 **모든 파일과 숨김 폴더 `.github`까지** GitHub 저장소의 기본 브랜치(main)에 업로드합니다. GitHub 연결 및 저장소 쓰기 권한이 활성화되어 있으면 ChatGPT에서 파일 업로드를 도울 수 있습니다.
3. GitHub 상단 **Actions → Build MOVE60 Android APK**를 클릭해 첫 빌드 상태를 확인합니다. 첫 `push`로 자동 시작하거나 **Run workflow** 버튼으로 수동 실행할 수 있습니다.
4. 성공(초록 체크)한 실행을 열고 페이지 하단 **Artifacts → MOVE60-debug-APK**을 누르면 압축 ZIP으로 내려받습니다.
5. 내려받은 ZIP 압축을 풀고 `app-debug.apk`를 갤럭시 휴대폰으로 옮겨 실행하면 설치할 수 있습니다. 휴대폰에서 해당 파일 관리자에 대한 '알 수 없는 앱 설치'를 한 번 허용해야 할 수 있습니다.
6. 앱 최초 실행 시 **알림 권한 허용**, 휴대폰 설정에서 해당 앱의 **잠금화면 알림 표시**를 켭니다.

### 빌드 기술 구성

- Android Gradle Plugin **8.7.3**, Gradle **8.9**, Java **17**, compile/target SDK **35**, min SDK **26**.
- `gradle/actions/setup-gradle@v4`가 CI에 Gradle을 준비하기 때문에 **Gradle Wrapper가 없어도 GitHub Actions에서는 자동 빌드**됩니다. 로컬 Android Studio에서 열 경우 Gradle 8.9를 지정하거나 Gradle Wrapper를 생성하세요.
- GitHub가 빌드하는 **debug APK**는 디버그 키로 서명되어 폰에 직접 설치할 수 있지만 Google Play 출시용으로 쓰지 않습니다.
- 빌드 성공 여부와 실기기 정상 작동 여부는 별개의 단계입니다. 설치 후 백그라운드/잠금화면의 표시를 직접 검증해야 합니다.

## 운동/알림 동작

- 고정 60분: 준비운동 + 실내 사이클 + 근력운동 + 마무리 스트레칭.
- 화요일 사이클: 4분 적응 + 1분 빠르게 / 2분 회복 × 8회 + 2분 정리 = 총 30분이며, 총 18개 세부 안내/체크 항목입니다.
- 앱 내 운동 타이머를 시작하면 Android 포그라운드 서비스가 실행되어 화면을 닫거나 잠가도 알림창에서 현재 인터벌·강도(RPE)·해당 구간의 남은 시간을 표시합니다.
- 기기 제조사의 절전 정책/강제 종료/재부팅/알림 권한 설정에 따라 지속 표시가 제한될 수 있습니다. **휴대폰 화면을 꺼도 모든 상황에서 100% 표시가 보장되는 것은 아닙니다.**
- 로컬 데이터 저장 방식으로 기기 간 동기화는 되지 않습니다.
- `/app/src/main/assets/MOVE60.html`이 앱 UI의 원본입니다. 새 버전을 만들 때 UI와 Android 서비스가 사용하는 사이클 구간 데이터가 동일한지 확인하세요.

## 파일 구성

- `.github/workflows/build-apk.yml` — GitHub Actions APK 빌드 및 자동 업로드
- `scripts/verify-plan.cjs` — 모든 요일 60분/사이클 세부 구간/화요일 8회차 검증
- `app/src/main/assets/MOVE60.html` — 운동 UI, 주간 랜덤, 인터벌·체크리스트, 체크박스 통일, 상세 패널
- `app/src/main/java/com/move60/workout/WorkoutService.java` — 진행 중 알림·잠금화면 카운트다운
- `app/src/main/java/com/move60/workout/MainActivity.java` — WebView UI 및 네이티브 서비스 호출

## 앱 빌드 실패 시

Actions에서 실패한 작업을 열어 **Build debug APK** 단계의 오류를 확인하세요. JDK, Android SDK, Gradle 버전 및 권한 상태도 확인하세요.

## v7: 잠금화면 카드형 타이머
- 잠금화면 가시성 PUBLIC, 별도 DEFAULT 중요도 채널, 무음 상시 알림
- 공개 알림에도 카드형 타이머/진행바 표시, 펼친 카드형 디자인 유지
- 앱의 '잠금화면 알림 설정 열기' 버튼을 눌러 해당 채널을 직접 설정
- 삼성 One UI에서는 휴대폰 설정 → 알림 → 잠겨 있을 때 알림 내용 숨기기: 항상 표시, 앱 알림 → MOVE60에서 잠금 상태 내용 표시: 항상 표시도 필요할 수 있음
- 기기 보안·제조사 정책에 따라 잠금화면의 카드 레이아웃 및 표시 여부는 달라질 수 있음
