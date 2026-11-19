# v71 · PRIV / OSS 릴리스 안전성 보강 (2026-11-19) — Draft PR 검증 문서

**상태: 소스 변경 반영 / 정적 검사·CI/실기기/운영 승인 미확인. 릴리스 금지.**

정책 링크:
- [ROUTIVE v2.1 출시 승인 검토 대장](https://app.notion.com/p/41d3fd565b9f81aa87c1d01fa612f45e)
- [개인정보처리방침 내부 초안](https://app.notion.com/p/41d3fd565b9f81df87c5f942a6842851)
- [기존 PRIV/OSS 정책](https://app.notion.com/p/41c3fd565b9f8169a7ade8e6c108cb27)

## 1. 데이터 백업 정책 — 구현안 (최종 실기기 검증 전)

- **사용자 운동 기록:** 기존 Room 데이터베이스와 운동 세션·도전 기록에 대한 Android OS 백업 가능성은 유지한다. 무조건 `allowBackup=false`로 바꿔 데이터 이전 기능을 끊지 않는다. 백업 성공·복원 무결성이 검증됐다는 의미는 아니다.
- **자동 백업에서 제외:** 폰 Health Connect 앱 동의 플래그 `move60_health_opt_in_v1`, 심박 위젯 캐시 `move60_widget_heart_v1`, 체중·당일 컨디션이 포함된 `preferences`, 최근 건강 갱신 메타데이터 `move60_health_refresh_v1`, 기기간 임시 발행 상태 `watch_publish_v26`를 파일 단위로 제외한다. 소리 설정 등도 동일 `preferences.xml`에 있어 복원되지 않을 수 있다. 기기 복원 후 사용자 Health Connect 재동의가 필요하다.
- **Android 11 이전:** `@xml/routive_full_backup_content`; **Android 12 이상:** `@xml/routive_data_extraction_rules`에서 **클라우드와 기기 간 전송 양쪽**에 동일한 제외 규칙 적용. 클라우드는 암호화 기능 미지원일 때 비활성화하도록 구성.
- **미해결:** 워치 자체 기기/센서 데이터 백업, 옛 버전 클라우드 백업의 잔존, 나머지 SharedPreferences·DB 안에 건강 정보가 포함되는지 전수 확인, 기기 제조사별 전송/복원 차이. 따라서 '심박 데이터는 클라우드에 절대 남지 않는다'고 공표 금지.
- **개인정보 삭제와 별개:** 앱 내 세션 원본 보존과 사용자 삭제 요구는 다른 이슈. 계정/서버/백업/폰/워치에 실제로 무엇이 남는지 밝히기 전 전체 삭제를 보장하거나 삭제 UI가 구현됐다고 안내하지 않는다.

### 백업 QA / 수용 기준

- [ ] API 30 이하 백업 및 복원에서 동의 OFF·심박 캐시 없음, Room 기존 운동 기록 유지
- [ ] API 31+ 클라우드 백업/복원에서 동일 조건 확인
- [ ] API 31+ 직접 기기 간 이전에서 동일 조건 확인
- [ ] 건강정보 권한이 OS에서 부여되어 있어도 앱 동의는 복원 후 OFF, 사용자 직접 승인 전 자동 조회/공유 없음
- [ ] Android v58 → v2 업그레이드 시 기존 기록의 정확성 및 중복 기록 없음
- [ ] Galaxy Watch4 연결/해제·워치 센서 권한과 휴대폰 심박 토글 분리 동작
- [ ] 사용자의 모든 설정과 기록에 대한 실제 저장·복원 범위 문서화

공식 근거: [Android Auto Backup](https://developer.android.com/identity/data/autobackup), [Android 12 데이터 추출 규칙](https://developer.android.com/about/versions/12/behavior-changes-12).

## 2. OSS 인벤토리 보강 — 고지/사용권 승인과 구분

- 기준 보고서 `docs/v70-oss-transitive-licenses.md`: 과거 Gradle 릴리스 런타임 의존성 **188개**, 그 중 기존 POM 조사로 187개, JSR-330 원저장소 README 및 Android 소스 POM을 추가로 확인한 `javax.inject:javax.inject:1` 1개를 포함해 **라이선스 유형 메타데이터 188개 확보**. 라이선스/저작권/NOTICE의 배포 충족 여부는 별개.
- 인앱 `app/src/main/assets/oss_licenses.json`에 **188개의 감사 및 출처 보완 메타데이터** 포함. `audit.unresolved=[]`로 갱신하고 `releaseGraphReverified/ licenseTextsVerified/ copyrightVerified/ noticesVerified/ operatorApproved`는 **전부 false**.
- `scripts/gen-oss-notice.py`: 직접 의존성 32개만 덮어쓰기하지 않고 v70의 188행 스냅샷을 파싱. 현재 Gradle을 재실행하는 코드는 아니며 실제 배포 아티팩트의 라이선스 사본과 NOTICE를 준비하는 스크립트가 아니다.
- `scripts/verify-release-legal.py`: 개인정보 URL 유효성에 더해 전이 목록 100%, 원문·저작권/NOTICE/운영자 확인값을 필수 조건으로 설정. 사용자가 실제 주소/근거를 넣기 전엔 릴리스 **실패가 정상**이다.
- 인앱 라이브러리 목록 확장은 **법적 고지 완료가 아님**. Apache-2.0 전문/필수 NOTICE, BSD-3-Clause/MIT 저작권·조건/면책 조항, SDK 별도 약관을 실제 배포 위치에서 제공해야 한다.

### OSS QA / 수용 기준

- [ ] 최종 SHA의 Gradle `:app:dependencies`, `:wear:dependencies --configuration releaseRuntimeClasspath`를 다시 해석해 188개 스냅샷과 모든 버전 일치 또는 승인된 최신 변경 검증
- [x] `javax.inject` 라이선스 종류(Apache-2.0) 원저장소/Android 소스의 근거 확인 (단, 바이너리 전체 저작권·NOTICE 검증은 미완료)
- [ ] 라이선스 전문/NOTICE/저작권 표시를 올바른 파일과 출처로 실제 패키징, 최종 AAB 및 워치 APK 검증
- [ ] 앱 설정의 오픈소스 화면에서 라이선스별 필수 고지 실물을 실제 확인
- [ ] 앱/워치 권리 고지가 마케팅 문구와 무관하게 접근 가능
- [ ] 공개 방침 HTTPS URL, Play Console 및 데이터 안전성·건강 앱 신고 검증
- [ ] 승인 플래그를 단순 수정하지 말고 승인자·대상 SHA·증빙 기록 연결

## 3. 빌드/출시 관계

- `android-smoke.yml`은 `scripts/verify-privacy-oss-structure.py`를 통해 구조와 인벤토리 범위를 검사. <b>이 검사 통과는 법적 고지 완료가 아님.</b>
- 수동 배포 `build-apk.yml`은 기존과 같이 `verify-release-legal.py` 성공 후만 배포하도록 유지. 개인정보 URL이 비어 있거나 188개 라이선스/NOTICE 증빙이 미완료이므로 현재 **릴리스 게이트 차단**.
- 실제 Android/Watch4 실기기 백업·복원과 화면/고지 QA는 이번 작업에서 수행하지 않았으며, 병합 전 별도 CI 실행 결과를 확인한다.

## 4. 출시 전 필요한 실운영 정보

1. 개인 개발자 계정의 실제 Google Play 계정 적격성 및 필요한 공개 연락처 확보
2. 개인정보처리자 법적 공개명, 서비스 지원용 이메일/연락 경로, 유효 HTTPS 게시 주소
3. 보유기간·사용자 삭제 절차·삭제 범위 및 Android/워치 데이터 백업/복원의 실제 검증
4. 전수 SDK·네트워크 송신 및 Data Safety 신고의 사실 관계
5. OSS 188개(또는 실제 최신 목록) 저작권·필수 고지/NOTICE 전문·번들 검증

**확정된 제품 방향:** 무료 MVP → 사용자 검증 → 별도 개인사업자·유료화 정책 검토. 현재 개인정보·OSS·폰/워치 출시 승인 상태는 **미완료**.
