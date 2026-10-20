# MOVE60 Android

실내 사이클 중심 평일 60분 운동 플래너와 운동 중 잠금화면 알림을 제공하는 Android 앱입니다.

## APK 내려받기

[Actions 빌드 페이지](https://github.com/yebyn98/MOVE60-Android/actions/workflows/build-apk.yml)에서 성공한 빌드를 선택하고 **Artifacts → MOVE60-debug-APK**을 다운로드합니다.

APK는 Android 디버그 서명으로 제작되며, 설치 시 알림 권한 허용이 필요합니다. Android 절전 모드, 강제 종료, 재부팅 시 포그라운드 서비스가 중단될 수 있습니다.

## 소스와 검증

`app/src/main/assets/MOVE60.html`: 주간 운동 플래너. `app/src/main/java/com/move60/workout/WorkoutService.java`: 백그라운드 포그라운드 알림. `scripts/verify-plan.cjs`: 월~금 60분 및 화요일 사이클 8회 체크.
