# MOVE60 — Kotlin Native Android

실내 사이클 중심의 월~금 60분 운동 루틴, 체크리스트, 자세 설명, 카드형 운동 알림과 잠금화면 타이머를 갖춘 Kotlin 앱입니다.

## 앱 내 업데이트

앱 실행 시 12시간 간격으로 새 버전을 확인하며, 설정의 '새 버전 확인 · 업데이트' 버튼으로 즉시 확인할 수 있습니다. 새 버전이 있으면 업데이트 버튼을 누르고, 앱에서 APK를 다운로드한 뒤 SHA-256을 검증합니다. Android 설치 화면에서 최종 설치 승인을 받습니다. 앱 설치 과정에 GitHub 웹페이지 방문은 필요하지 않습니다.

## 처음 한 번 필요한 설정

1. https://github.com/new 에서 이름 MOVE60-Updates, 공개 범위 Public으로 새 저장소를 만들고 README로 초기화합니다. 앱 소스인 MOVE60-Android 저장소는 Private으로 유지합니다. 공개 저장소에는 APK 바이너리와 latest.json만 게시됩니다.
2. https://github.com/yebyn98/MOVE60-Android/settings/secrets/actions 에서 Repository secret 이름 MOVE60_SIGNING_BUNDLE_BASE64를 만들고, 앞서 전달한 개인 서명 키 보관 ZIP 안의 MOVE60_SIGNING_BUNDLE_BASE64.txt 파일 내용(한 줄 전체)을 값으로 붙여 넣습니다. 비밀 ZIP, p12 파일 또는 암호를 저장소에 직접 업로드하지 마세요.
3. https://github.com/settings/personal-access-tokens/new 에서 Fine-grained PAT를 생성합니다. Resource owner는 yebyn98, Only select repositories로 MOVE60-Updates만 선택, Repository permissions의 Contents만 Read and write로 지정합니다. 발급받은 토큰을 위 비공개 소스 저장소의 Secrets에 MOVE60_UPDATES_PAT 이름으로 저장합니다. 토큰을 채팅에 붙여 넣지 마세요.
4. https://github.com/yebyn98/MOVE60-Android/actions/workflows/build-apk.yml 에서 Run workflow를 실행합니다. 성공하면 MOVE60-signed-release-APK와 공개 저장소에 최신 버전 APK 및 latest.json이 생성됩니다.

## 공개 업데이트 파일

- 버전 정보: https://raw.githubusercontent.com/yebyn98/MOVE60-Updates/main/latest.json
- 최신 앱: https://raw.githubusercontent.com/yebyn98/MOVE60-Updates/main/MOVE60-latest.apk

## 고정 서명 주의

릴리스 설치 및 업데이트에는 계속 같은 개인 서명 키를 사용합니다. GitHub Actions의 디버그 APK는 임시 키이므로 업데이트용으로 설치하지 마세요. 기존 v5~v9 앱이 다른 서명 키를 사용했다면 고정 서명판으로 바꾸는 첫 1회는 앱 삭제가 필요할 수 있으며 로컬 운동 기록이 사라질 수 있습니다. 이후 버전부터는 앱을 삭제하지 않고 덮어쓸 수 있습니다. Android 정책상 시스템 설치 승인 화면은 항상 표시될 수 있습니다.

## 소스

- MainActivity.kt: Kotlin 네이티브 메인 UI, 업데이트 버튼
- UpdateManager.kt: 버전 확인, APK 다운로드, SHA-256 확인, 시스템 설치 승인
- WorkoutService.kt: 카드형 알림 및 잠금화면 타이머
- SessionStore.kt: 타이머 및 운동 기록 보존
- WorkoutData.kt / workouts.json: 원본 운동 루틴과 매주 조합

배포 인프라 설정이 완료되기 전에는 공개 manifest가 404를 반환하므로 앱 내 새 버전 다운로드는 활성화되지 않습니다.
