# MOVE60 · Kotlin Android 체중 감량 운동 플래너

월~금 60분 운동 데이터와 운동 자세·주의사항·대체 운동, 매주 변경되는 운동 종목과 타이머 체크리스트를 Kotlin 네이티브 화면으로 구현했습니다. 앱에는 Java/HTML/WebView UI 코드가 없습니다.

## 운동과 잠금화면 알림

- 실내 사이클 중심 + 준비/근력/정리 운동, 5개 요일의 운동 부위와 강도 구분
- 화요일 8회 인터벌, 18개 체크 가능한 세부 구간과 카운트다운 동기화
- 앱을 닫아도 벽시계 시간 기준 타이머와 포그라운드 서비스 유지
- 알림창의 작은 카드/펼친 카드에서 운동명, 남은 시간, RPE, 구간 번호, 전체 진행률 표시
- 잠금화면 알림은 Android/삼성 갤럭시 개인 설정에 따라 카드 전체 대신 요약 형태로 표시될 수 있습니다. 앱 설정의 잠금화면 알림 바로가기를 확인하세요.

## 공개 업데이트 기능

Kotlin `UpdateManager`는 앱 실행 시 하루 두 번 이내로 `https://raw.githubusercontent.com/yebyn98/MOVE60-Updates/main/latest.json`에서 새 버전을 확인합니다. 새 버전이 있으면 팝업에서 APK를 다운로드하고 SHA-256 검증 뒤 Android 설치 화면을 엽니다. **자동 설치는 Android 보안상 지원되지 않으며 사용자 승인이 필요**합니다.

**현재 주의:** 별도 공개 배포 저장소 `yebyn98/MOVE60-Updates`가 만들어져야 앱 내 업데이트 버튼이 작동합니다. 저장소가 없으면 안내만 표시합니다. 개발 소스 저장소 `yebyn98/MOVE60-Android`는 계속 비공개로 유지됩니다.

## 같은 키로 서명되는 배포 APK

GitHub Actions secret `MOVE60_SIGNING_BUNDLE_BASE64`를 한번 등록해야 합니다. 이 비밀값은 `move60.p12` PKCS12 서명 파일과 `signing.env`가 들어 있는 ZIP을 Base64 인코딩한 문자열입니다. GitHub Actions는 이후 매 빌드마다 같은 서명키로 release APK를 생성합니다. **서명 파일이나 비밀번호를 GitHub 소스 파일에 커밋하지 마세요.**

Secret이 없으면 컴파일 검증용 임시 디버그 APK만 생성됩니다(영구 업데이트용 아님).

별도 공개 저장소에 자동 업로드하려면 `yebyn98/MOVE60-Updates` public 저장소를 만들고 해당 저장소에만 Contents Read/Write 권한이 있는 fine-grained PAT을 Secret `MOVE60_UPDATES_PAT`으로 등록해야 합니다. 그 후 빌드가 성공하면 Signed release APK와 `latest.json`을 공개 저장소에 자동 게시합니다. 공개 저장소에는 바이너리/버전 정보만 공개되며 앱 소스는 비공개입니다.

## Actions

https://github.com/yebyn98/MOVE60-Android/actions/workflows/build-apk.yml

Kotlin 네이티브 컴파일용 Debug APK는 자동으로 생성됩니다. 고정 서명 Secret이 설정된 후에는 추가로 `MOVE60-signed-release-APK` artifact가 생성됩니다. 기존 다른 임시 debug 키로 서명된 v5/v7은 처음 새 서명 방식으로 전환할 때 한 번 삭제 후 재설치가 필요할 수 있습니다. 이후에는 서명을 계속 재사용하여 데이터 보존 업데이트가 가능합니다.
