# v72 · ROUTIVE 무료 MVP OSS 표준 라이선스 원문 및 출시 고지 검증 (2026-11-19)

**상태: Draft PR #76 코드 반영, 신규 CI 검증 전. 라이선스 전문·저작권·NOTICE에 대한 릴리스 적합성은 미검증. 출고 차단 유지.**

## 1. 변경 목적과 범위

기존 앱 설정의 '오픈소스 라이선스' 팝업은 188개 라이브러리의 POM 라이선스 **분류명**만 보여주므로, 이용자가 앱에서 표준 라이선스 조건을 확인할 수 없었다.

이번 변경으로 휴대폰 앱에 [SPDX license-list-data](https://github.com/spdx/license-list-data)의 표준 텍스트 3개를 오프라인 assets로 포함하고, **앱 설정 → 오픈소스 라이선스 → 표준 라이선스 참고 원문**에서 열어볼 수 있도록 했다. 이 문서는 *참고용 표준 원문*이며 개별 배포 구성요소의 실제 법적 고지를 대체하지 않는다. MIT와 BSD 원문 안의 `<year>`, `<owner>` 등은 표준 서식의 자리표시자이며, **개별 라이브러리의 실제 저작권자로 치환됐다는 뜻이 아니다**.

| 역사적 v70 감사 분류 | 라이브러리 수 | 이번 배포 파일 | 남은 법적 증빙 |
| --- | ---: | --- | --- |
| Apache License 2.0 계열 | 180 | `app/src/main/assets/oss/license-texts/Apache-2.0.txt` | 컴포넌트별 정확한 라이선스 버전·저작권·NOTICE/필수 재배포 표시 |
| BSD-3-Clause | 3 | `app/src/main/assets/oss/license-texts/BSD-3-Clause.txt` | 실 저작권자·연도 및 각 컴포넌트의 고지·면책문구 |
| MIT | 1 | `app/src/main/assets/oss/license-texts/MIT.txt` | 실 저작권자·연도와 각 구성요소 고지 |
| Android SDK License (별도 약관) | 4 | **미포함** (오픈소스 표준 약관과 다름) | [Android SDK Terms](https://developer.android.com/studio/terms)와 실제 사용조건 검토 |
| **합계** | **188** | **표준 원문 3종만 추가** | **전체 출처/원문/저작권/NOTICE/현재 릴리스 패키지 검증 필요** |

원문 출처: SPDX 공개 라이선스 텍스트 [Apache-2.0](https://github.com/spdx/license-list-data/blob/main/text/Apache-2.0.txt), [BSD-3-Clause](https://github.com/spdx/license-list-data/blob/main/text/BSD-3-Clause.txt), [MIT](https://github.com/spdx/license-list-data/blob/main/text/MIT.txt). 최종 SHA 출처 및 텍스트 일치 검증은 차후 증빙할 것. **원문 사용권과 실제 라이브러리별 귀속·재배포 조건은 별도 검토 대상이다.**

## 2. 실제 구현과 QA 조건

- 읽기 전용 파일 로더 `OssLicenseReference`는 위 세 가지 SPDX ID만 읽는다. 허용 목록 밖 입력은 파일을 열지 않는다.
- 기존 188개 목록·라이선스 명칭·미검증 표시를 보존한다. 설정 팝업에서 원문을 선택하면 오프라인 상세 페이지가 열리고 '목록으로'를 눌러 돌아온다.
- MIT/BSD 표준 문구에는 저작권 자리표시자가 있으므로 전체 사용자 고지 완료라고 표시하지 않는다.
- `OssNoticeReader.audit.licenseTextsVerified`, `copyrightVerified`, `noticesVerified`, `operatorApproved` 및 `releaseGraphReverified`는 **모두 false**로 유지.
- 로컬 JVM/Robolectric 단위 테스트: 세 파일 로딩/크기/기본 문구 확인, 허용 목록 밖 문자열 차단, 법적 고지 승인값 false.
- CI 정적 스크립트: 세 원문 파일의 존재/본문/비승인 플래그 검사.
- Android 계측 테스트: 설정 OSS 팝업의 라이선스 감사 범위 확인 → Apache 참고 원문 열기 → 본문 및 검증 미완료 안내 → 목록 복귀.
- Wear OS 앱에 라이선스 조회 UI가 별도로 구현됐다고 주장하지 않는다. 워치에 어떤 필수 고지를 제공할지 출시 전 별도 확인.

## 3. 반드시 남길 출시 차단 조건

- [ ] 최신 최종 커밋의 Android/Wear releaseRuntimeClasspath 실제 의존성, 버전, 포함 아티팩트 목록 재산출 (기존 188개는 2026-11-17 **스냅샷**)
- [ ] 라이브러리 188개 각 출처·법적 라이선스/저작권자·연도 검증 및 필요한 사본 재배포
- [ ] 각 배포 컴포넌트의 NOTICE 또는 개별 파일 헤더 등 보존 요구사항 조사/적용
- [ ] Android SDK 이용약관 4개 별도 조건 확인
- [ ] 휴대폰 APK 및 Wear OS APK/AAB에 필요한 원문/NOTICE 실제 포함 검증
- [ ] 실제 기기에서 오프라인 라이선스 원문과 화면 가독성 확인
- [ ] 배포 담당자·대상 SHA·고지 스냅샷·승인 이력 등록 후 감사 플래그 확정
- [ ] 개인정보처리방침 실제 운영자/공개 HTTPS URL, Play Console Data Safety 및 헬스 앱 신고, 삭제·복원 실제 QA

**정책:** ROUTIVE 개인 개발자 무료 MVP 우선 검토, 광고/유료 구독/IAP 없음. 무료 제공이어도 OSS 고지와 개인정보 권리는 면제되지 않는다.

**관련:** [v71 개인정보·OSS 개발 보강](v71-release-privacy-oss-hardening.md), [ROUTIVE 출시 검토 대장](https://app.notion.com/p/41d3fd565b9f81aa87c1d01fa612f45e), [GitHub PR #76](https://github.com/yebyn98/MOVE60-Android/pull/76), [출시 QA 이슈 #58](https://github.com/yebyn98/MOVE60-Android/issues/58).
