# v70 · 오픈소스 의존성 전수 점검 (직접 + 전이, 2026-11-17)

**이 문서는 분석 결과다. 법적 고지 의무가 충족됐다는 뜻이 아니며, 앱 안의 오픈소스 화면과 법무 검토는 별도 작업이다.**

## 방법
- `gradle :app:dependencies :wear:dependencies --configuration releaseRuntimeClasspath`로 **릴리스에 실제로 들어가는 런타임 의존성**을 뽑았다(최종 해석 버전 기준, 테스트·디버그 전용 제외).
- 각 라이브러리의 **게시된 POM**(Google Maven → Maven Central, 상위 POM 포함)에서 라이선스 이름을 읽었다. POM에 적힌 것만 '확인'으로 센다.

## 결과 요약
- 전체 **188개**(앱 155개, 워치 142개, 둘 다 109개). 기존 앱 내 목록은 직접 의존성 32개뿐이었다.
- POM에 라이선스가 적힌 것 **187개**, 적히지 않은 것 **1개**.
- 라이선스 분포: The Apache Software License, Version 2.0 168개, Apache 2.0 5개, The Apache License, Version 2.0 5개, Android Software Development Kit License 4개, BSD-3-Clause 3개, Apache License, Version 2.0 1개, The MIT License 1개

## 사람이 확인해야 하는 예외
| 라이브러리 | 모듈 | POM의 라이선스 | 왜 확인이 필요한가 |
|---|---|---|---|
| androidx.health:health-services-client-external-protobuf:1.1.0 | wear | BSD-3-Clause | BSD-3-Clause: 저작권 표시 문구 재현 의무 |
| androidx.health.connect:connect-client-external-protobuf:1.1.0-alpha12 | app | BSD-3-Clause | BSD-3-Clause: 저작권 표시 문구 재현 의무 |
| androidx.wear.protolayout:protolayout-external-protobuf:1.4.2 | wear | BSD-3-Clause | BSD-3-Clause: 저작권 표시 문구 재현 의무 |
| com.google.android.gms:play-services-base:18.5.0 | app, wear | Android Software Development Kit License | Google Play 서비스 약관(오픈소스 라이선스 아님). 앱 고지와 배포 약관 대상 여부 확인 |
| com.google.android.gms:play-services-basement:18.4.0 | app, wear | Android Software Development Kit License | Google Play 서비스 약관(오픈소스 라이선스 아님). 앱 고지와 배포 약관 대상 여부 확인 |
| com.google.android.gms:play-services-tasks:18.2.0 | app, wear | Android Software Development Kit License | Google Play 서비스 약관(오픈소스 라이선스 아님). 앱 고지와 배포 약관 대상 여부 확인 |
| com.google.android.gms:play-services-wearable:19.0.0 | app, wear | Android Software Development Kit License | Google Play 서비스 약관(오픈소스 라이선스 아님). 앱 고지와 배포 약관 대상 여부 확인 |
| org.checkerframework:checker-qual:3.12.0 | app | The MIT License | MIT: 저작권 표시 문구 재현 의무 |
| javax.inject:javax.inject:1 | app | **POM에 없음(미확인)** | 앱 목록에 넣지 않는다. 공식 배포처에서 라이선스를 확인해야 한다 |

## 이 점검만으로는 충족되지 않는 것
- Apache-2.0(전체의 대부분)은 **라이선스 문구 사본 제공**과 일부 라이브러리의 **NOTICE 내용 표시** 의무가 있다. 지금 앱 화면은 라이선스 *이름*만 보여준다.
- BSD-3-Clause·MIT는 **저작권 표시 문구**를 함께 보여줘야 한다. POM에는 라이선스 이름과 URL만 있고 저작권 문구는 없다.
- 이 의무를 어떻게 충족할지(앱 안 전체 문구 vs 외부 고지 페이지)는 법무·PO 결정이다.

## 재현
`scripts/gen-oss-notice.py`는 아직 직접 의존성만 다룬다. 이 문서의 목록은 위 Gradle 명령과 POM 조회로 만들었고, 스크립트를 전이 의존성까지 확장하는 작업은 개발 브랜치의 OSS 게이트 작업과 겹치지 않게 따로 진행한다.

## 전체 목록
| 라이브러리 | 버전 | 모듈 | 라이선스(POM) |
|---|---|---|---|
| androidx.activity:activity | 1.9.3 | app,wear | The Apache Software License, Version 2.0 |
| androidx.activity:activity-compose | 1.9.3 | app,wear | The Apache Software License, Version 2.0 |
| androidx.activity:activity-ktx | 1.9.3 | app,wear | The Apache Software License, Version 2.0 |
| androidx.annotation:annotation | 1.8.1 | app,wear | The Apache Software License, Version 2.0 |
| androidx.annotation:annotation-experimental | 1.4.1 | app,wear | The Apache Software License, Version 2.0 |
| androidx.annotation:annotation-jvm | 1.8.1 | app,wear | The Apache Software License, Version 2.0 |
| androidx.appcompat:appcompat | 1.6.1 | app | The Apache Software License, Version 2.0 |
| androidx.appcompat:appcompat-resources | 1.6.1 | app | The Apache Software License, Version 2.0 |
| androidx.arch.core:core-common | 2.2.0 | app,wear | The Apache Software License, Version 2.0 |
| androidx.arch.core:core-runtime | 2.2.0 | app,wear | The Apache Software License, Version 2.0 |
| androidx.autofill:autofill | 1.0.0 | app,wear | The Apache Software License, Version 2.0 |
| androidx.cardview:cardview | 1.0.0 | app | The Apache Software License, Version 2.0 |
| androidx.collection:collection | 1.4.4 | app,wear | The Apache Software License, Version 2.0 |
| androidx.collection:collection-jvm | 1.4.4 | app,wear | The Apache Software License, Version 2.0 |
| androidx.collection:collection-ktx | 1.4.4 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.animation:animation | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.animation:animation-android | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.animation:animation-core | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.animation:animation-core-android | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.foundation:foundation | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.foundation:foundation-android | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.foundation:foundation-layout | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.foundation:foundation-layout-android | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.material3:material3-android | 1.3.1 | app | The Apache Software License, Version 2.0 |
| androidx.compose.material:material | 1.7.8 | wear | The Apache Software License, Version 2.0 |
| androidx.compose.material:material-android | 1.7.8 | wear | The Apache Software License, Version 2.0 |
| androidx.compose.material:material-icons-core | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.material:material-icons-core-android | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.material:material-icons-extended-android | 1.7.6 | app | The Apache Software License, Version 2.0 |
| androidx.compose.material:material-ripple | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.material:material-ripple-android | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.runtime:runtime | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.runtime:runtime-android | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.runtime:runtime-saveable | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.runtime:runtime-saveable-android | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-android | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-geometry | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-geometry-android | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-graphics | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-graphics-android | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-text | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-text-android | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-tooling-android | 1.7.8 | wear | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-tooling-data | 1.7.8 | wear | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-tooling-data-android | 1.7.8 | wear | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-tooling-preview | 1.7.8 | wear | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-tooling-preview-android | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-unit | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-unit-android | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-util | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-util-android | 1.7.6 | app,wear | The Apache Software License, Version 2.0 |
| androidx.compose:compose-bom | 2024.12.01 | app,wear | The Apache Software License, Version 2.0 |
| androidx.concurrent:concurrent-futures | 1.1.0 | app,wear | The Apache Software License, Version 2.0 |
| androidx.concurrent:concurrent-futures-ktx | 1.1.0 | app,wear | The Apache Software License, Version 2.0 |
| androidx.constraintlayout:constraintlayout | 2.0.1 | app | The Apache Software License, Version 2.0 |
| androidx.constraintlayout:constraintlayout-solver | 2.0.1 | app | The Apache Software License, Version 2.0 |
| androidx.coordinatorlayout:coordinatorlayout | 1.1.0 | app | The Apache Software License, Version 2.0 |
| androidx.core:core | 1.13.1 | app,wear | The Apache Software License, Version 2.0 |
| androidx.core:core-ktx | 1.13.1 | app,wear | The Apache Software License, Version 2.0 |
| androidx.cursoradapter:cursoradapter | 1.0.0 | app | The Apache Software License, Version 2.0 |
| androidx.customview:customview | 1.1.0 | app,wear | The Apache Software License, Version 2.0 |
| androidx.customview:customview-poolingcontainer | 1.0.0 | app,wear | The Apache Software License, Version 2.0 |
| androidx.documentfile:documentfile | 1.0.0 | app,wear | The Apache Software License, Version 2.0 |
| androidx.drawerlayout:drawerlayout | 1.1.1 | app | The Apache Software License, Version 2.0 |
| androidx.dynamicanimation:dynamicanimation | 1.0.0 | app,wear | The Apache Software License, Version 2.0 |
| androidx.emoji2:emoji2 | 1.3.0 | app,wear | The Apache Software License, Version 2.0 |
| androidx.emoji2:emoji2-views-helper | 1.3.0 | app | The Apache Software License, Version 2.0 |
| androidx.fragment:fragment | 1.5.1 | app,wear | The Apache Software License, Version 2.0 |
| androidx.graphics:graphics-path | 1.0.1 | app,wear | The Apache Software License, Version 2.0 |
| androidx.health.connect:connect-client | 1.1.0-alpha12 | app | The Apache Software License, Version 2.0 |
| androidx.health.connect:connect-client-external-protobuf | 1.1.0-alpha12 | app | BSD-3-Clause |
| androidx.health.connect:connect-client-proto | 1.1.0-alpha12 | app | The Apache Software License, Version 2.0 |
| androidx.health:health-services-client | 1.1.0 | wear | The Apache Software License, Version 2.0 |
| androidx.health:health-services-client-external-protobuf | 1.1.0 | wear | BSD-3-Clause |
| androidx.health:health-services-client-proto | 1.1.0 | wear | The Apache Software License, Version 2.0 |
| androidx.hilt:hilt-common | 1.2.0 | app | The Apache Software License, Version 2.0 |
| androidx.hilt:hilt-navigation | 1.2.0 | app | The Apache Software License, Version 2.0 |
| androidx.hilt:hilt-navigation-compose | 1.2.0 | app | The Apache Software License, Version 2.0 |
| androidx.hilt:hilt-work | 1.2.0 | app | The Apache Software License, Version 2.0 |
| androidx.interpolator:interpolator | 1.0.0 | app,wear | The Apache Software License, Version 2.0 |
| androidx.legacy:legacy-support-core-utils | 1.0.0 | app,wear | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-common | 2.8.7 | app,wear | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-common-java8 | 2.8.7 | app | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-common-jvm | 2.8.7 | app,wear | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-livedata | 2.8.7 | app,wear | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-livedata-core | 2.8.7 | app,wear | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-livedata-core-ktx | 2.8.7 | app,wear | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-process | 2.8.7 | app,wear | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-runtime | 2.8.7 | app,wear | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-runtime-android | 2.8.7 | app,wear | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-runtime-compose | 2.8.7 | app,wear | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-runtime-compose-android | 2.8.7 | app,wear | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.8.7 | app,wear | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-runtime-ktx-android | 2.8.7 | app,wear | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-service | 2.8.7 | app | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-viewmodel | 2.8.7 | app,wear | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-viewmodel-android | 2.8.7 | app,wear | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-viewmodel-compose | 2.8.7 | app,wear | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-viewmodel-compose-android | 2.8.7 | app,wear | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-viewmodel-ktx | 2.8.7 | app,wear | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-viewmodel-savedstate | 2.8.7 | app,wear | The Apache Software License, Version 2.0 |
| androidx.loader:loader | 1.0.0 | app,wear | The Apache Software License, Version 2.0 |
| androidx.localbroadcastmanager:localbroadcastmanager | 1.0.0 | app,wear | The Apache Software License, Version 2.0 |
| androidx.navigation:navigation-common | 2.8.5 | app,wear | The Apache Software License, Version 2.0 |
| androidx.navigation:navigation-common-ktx | 2.8.5 | app,wear | The Apache Software License, Version 2.0 |
| androidx.navigation:navigation-compose | 2.8.5 | app,wear | The Apache Software License, Version 2.0 |
| androidx.navigation:navigation-runtime | 2.8.5 | app,wear | The Apache Software License, Version 2.0 |
| androidx.navigation:navigation-runtime-ktx | 2.8.5 | app,wear | The Apache Software License, Version 2.0 |
| androidx.paging:paging-common | 3.3.6 | wear | The Apache Software License, Version 2.0 |
| androidx.paging:paging-common-android | 3.3.6 | wear | The Apache Software License, Version 2.0 |
| androidx.paging:paging-compose | 3.3.6 | wear | The Apache Software License, Version 2.0 |
| androidx.paging:paging-compose-android | 3.3.6 | wear | The Apache Software License, Version 2.0 |
| androidx.print:print | 1.0.0 | app,wear | The Apache Software License, Version 2.0 |
| androidx.profileinstaller:profileinstaller | 1.3.1 | app,wear | The Apache Software License, Version 2.0 |
| androidx.recyclerview:recyclerview | 1.1.0 | app,wear | The Apache Software License, Version 2.0 |
| androidx.resourceinspection:resourceinspection-annotation | 1.0.1 | app | The Apache Software License, Version 2.0 |
| androidx.room:room-common | 2.6.1 | app | The Apache Software License, Version 2.0 |
| androidx.room:room-ktx | 2.6.1 | app | The Apache Software License, Version 2.0 |
| androidx.room:room-runtime | 2.6.1 | app | The Apache Software License, Version 2.0 |
| androidx.savedstate:savedstate | 1.2.1 | app,wear | The Apache Software License, Version 2.0 |
| androidx.savedstate:savedstate-ktx | 1.2.1 | app,wear | The Apache Software License, Version 2.0 |
| androidx.sqlite:sqlite | 2.4.0 | app | The Apache Software License, Version 2.0 |
| androidx.sqlite:sqlite-framework | 2.4.0 | app | The Apache Software License, Version 2.0 |
| androidx.startup:startup-runtime | 1.1.1 | app,wear | The Apache Software License, Version 2.0 |
| androidx.swiperefreshlayout:swiperefreshlayout | 1.0.0 | wear | The Apache Software License, Version 2.0 |
| androidx.tracing:tracing | 1.2.0 | app,wear | The Apache Software License, Version 2.0 |
| androidx.tracing:tracing-ktx | 1.2.0 | app | The Apache Software License, Version 2.0 |
| androidx.transition:transition | 1.5.0 | app | The Apache Software License, Version 2.0 |
| androidx.vectordrawable:vectordrawable | 1.1.0 | app | The Apache Software License, Version 2.0 |
| androidx.vectordrawable:vectordrawable-animated | 1.1.0 | app | The Apache Software License, Version 2.0 |
| androidx.versionedparcelable:versionedparcelable | 1.1.1 | app,wear | The Apache Software License, Version 2.0 |
| androidx.viewpager2:viewpager2 | 1.0.0 | app | The Apache Software License, Version 2.0 |
| androidx.viewpager:viewpager | 1.0.0 | app,wear | The Apache Software License, Version 2.0 |
| androidx.wear.compose:compose-foundation | 1.4.1 | wear | The Apache Software License, Version 2.0 |
| androidx.wear.compose:compose-material | 1.4.1 | wear | The Apache Software License, Version 2.0 |
| androidx.wear.compose:compose-material-core | 1.4.1 | wear | The Apache Software License, Version 2.0 |
| androidx.wear.compose:compose-navigation | 1.4.1 | wear | The Apache Software License, Version 2.0 |
| androidx.wear.compose:compose-ui-tooling | 1.4.1 | wear | The Apache Software License, Version 2.0 |
| androidx.wear.protolayout:protolayout | 1.4.2 | wear | The Apache Software License, Version 2.0 |
| androidx.wear.protolayout:protolayout-expression | 1.4.2 | wear | The Apache Software License, Version 2.0 |
| androidx.wear.protolayout:protolayout-expression-pipeline | 1.4.2 | wear | The Apache Software License, Version 2.0 |
| androidx.wear.protolayout:protolayout-external-protobuf | 1.4.2 | wear | BSD-3-Clause |
| androidx.wear.protolayout:protolayout-material-core | 1.4.2 | wear | The Apache Software License, Version 2.0 |
| androidx.wear.protolayout:protolayout-material3 | 1.4.2 | wear | The Apache Software License, Version 2.0 |
| androidx.wear.protolayout:protolayout-proto | 1.4.2 | wear | The Apache Software License, Version 2.0 |
| androidx.wear.tiles:tiles | 1.6.2 | wear | The Apache Software License, Version 2.0 |
| androidx.wear.tiles:tiles-proto | 1.6.2 | wear | The Apache Software License, Version 2.0 |
| androidx.wear:wear | 1.3.0 | wear | The Apache Software License, Version 2.0 |
| androidx.wear:wear-ongoing | 1.0.0 | wear | The Apache Software License, Version 2.0 |
| androidx.wear:wear-tooling-preview | 1.0.0 | wear | The Apache Software License, Version 2.0 |
| androidx.work:work-runtime | 2.10.0 | app | The Apache Software License, Version 2.0 |
| androidx.work:work-runtime-ktx | 2.10.0 | app | The Apache Software License, Version 2.0 |
| com.google.android.gms:play-services-base | 18.5.0 | app,wear | Android Software Development Kit License |
| com.google.android.gms:play-services-basement | 18.4.0 | app,wear | Android Software Development Kit License |
| com.google.android.gms:play-services-tasks | 18.2.0 | app,wear | Android Software Development Kit License |
| com.google.android.gms:play-services-wearable | 19.0.0 | app,wear | Android Software Development Kit License |
| com.google.android.horologist:horologist-annotations | 0.6.23 | wear | The Apache Software License, Version 2.0 |
| com.google.android.horologist:horologist-compose-layout | 0.6.23 | wear | The Apache Software License, Version 2.0 |
| com.google.android.material:material | 1.12.0 | app | The Apache Software License, Version 2.0 |
| com.google.code.findbugs:jsr305 | 3.0.2 | app | The Apache Software License, Version 2.0 |
| com.google.dagger:dagger | 2.52 | app | Apache 2.0 |
| com.google.dagger:dagger-lint-aar | 2.52 | app | Apache 2.0 |
| com.google.dagger:hilt-android | 2.52 | app | Apache 2.0 |
| com.google.dagger:hilt-core | 2.52 | app | Apache 2.0 |
| com.google.errorprone:error_prone_annotations | 2.15.0 | app,wear | Apache 2.0 |
| com.google.guava:failureaccess | 1.0.1 | app,wear | The Apache Software License, Version 2.0 |
| com.google.guava:guava | 31.1-android | app,wear | Apache License, Version 2.0 |
| com.google.guava:listenablefuture | 9999.0-empty-to-avoid-conflict-with-guava | app,wear | The Apache Software License, Version 2.0 |
| com.google.j2objc:j2objc-annotations | 1.3 | app,wear | The Apache Software License, Version 2.0 |
| jakarta.inject:jakarta.inject-api | 2.0.1 | app | The Apache Software License, Version 2.0 |
| javax.inject:javax.inject | 1 | app | 미확인 |
| org.checkerframework:checker-qual | 3.12.0 | app | The MIT License |
| org.jetbrains.kotlin:kotlin-bom | 1.8.22 | app | The Apache Software License, Version 2.0 |
| org.jetbrains.kotlin:kotlin-stdlib | 2.0.21 | app,wear | The Apache License, Version 2.0 |
| org.jetbrains.kotlin:kotlin-stdlib-common | 2.0.21 | app,wear | The Apache License, Version 2.0 |
| org.jetbrains.kotlin:kotlin-stdlib-jdk7 | 1.8.22 | app | The Apache License, Version 2.0 |
| org.jetbrains.kotlin:kotlin-stdlib-jdk8 | 1.8.22 | app | The Apache License, Version 2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-android | 1.9.0 | app,wear | The Apache Software License, Version 2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-bom | 1.9.0 | app,wear | The Apache Software License, Version 2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-core | 1.9.0 | app,wear | The Apache Software License, Version 2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm | 1.9.0 | app,wear | The Apache Software License, Version 2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-guava | 1.9.0 | app | The Apache Software License, Version 2.0 |
| org.jetbrains.kotlinx:kotlinx-serialization-bom | 1.6.3 | app,wear | The Apache Software License, Version 2.0 |
| org.jetbrains.kotlinx:kotlinx-serialization-core | 1.6.3 | app,wear | The Apache Software License, Version 2.0 |
| org.jetbrains.kotlinx:kotlinx-serialization-core-jvm | 1.6.3 | app,wear | The Apache Software License, Version 2.0 |
| org.jetbrains:annotations | 23.0.0 | app,wear | The Apache Software License, Version 2.0 |
| org.jspecify:jspecify | 1.0.0 | app,wear | The Apache License, Version 2.0 |
