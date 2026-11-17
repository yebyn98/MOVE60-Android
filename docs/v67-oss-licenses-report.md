# v67 · 오픈소스 라이선스 점검 결과 (직접 의존성)

`scripts/gen-oss-notice.py`가 각 라이브러리의 **게시된 POM**에서 라이선스를 읽은 결과이다. POM에 명시된 라이선스만 앱 목록(`assets/oss_licenses.json`)에 들어간다.

**범위 한계(출시 전 확인 필요):** 직접 의존성만 다뤘다. 전이 의존성(androidx·Kotlin·Guava 등이 가져오는 라이브러리)은 열거하지 않았다. `gradle :app:dependencies :wear:dependencies`로 전체 목록을 만들어 같은 방식으로 확인해야 한다. 법적 고지 의무(저작권 표시, NOTICE 파일 전문)는 법무/PO 검토 대상이며 이 문서가 그 충족을 보증하지 않는다.

| 라이브러리 | 버전 | 모듈 | 라이선스(POM 명시) | 근거 |
|---|---|---|---|---|
| androidx.activity:activity | 1.9.3 | wear | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/activity/activity/1.9.3/activity-1.9.3.pom |
| androidx.activity:activity-compose | 1.9.3 | app, wear | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/1.9.3/activity-compose-1.9.3.pom |
| androidx.compose.foundation:foundation | 1.7.6 | app, wear | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/compose/foundation/foundation/1.7.6/foundation-1.7.6.pom |
| androidx.compose.material:material-icons-extended | 1.7.6 | app | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/compose/material/material-icons-extended/1.7.6/material-icons-extended-1.7.6.pom |
| androidx.compose.material3:material3 | 1.3.1 | app | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/compose/material3/material3/1.3.1/material3-1.3.1.pom |
| androidx.compose.ui:ui | 1.7.6 | app, wear | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui/1.7.6/ui-1.7.6.pom |
| androidx.compose.ui:ui-tooling-preview | 1.7.6 | app | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-tooling-preview/1.7.6/ui-tooling-preview-1.7.6.pom |
| androidx.core:core | 1.13.1 | wear | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/core/core/1.13.1/core-1.13.1.pom |
| androidx.fragment:fragment | 1.8.5 | wear | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/fragment/fragment/1.8.5/fragment-1.8.5.pom |
| androidx.health:health-services-client | 1.1.0 | wear | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/health/health-services-client/1.1.0/health-services-client-1.1.0.pom |
| androidx.health.connect:connect-client | 1.1.0-alpha12 | app | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/health/connect/connect-client/1.1.0-alpha12/connect-client-1.1.0-alpha12.pom |
| androidx.hilt:hilt-navigation-compose | 1.2.0 | app | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/hilt/hilt-navigation-compose/1.2.0/hilt-navigation-compose-1.2.0.pom |
| androidx.hilt:hilt-work | 1.2.0 | app | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/hilt/hilt-work/1.2.0/hilt-work-1.2.0.pom |
| androidx.lifecycle:lifecycle-runtime-compose | 2.8.7 | app | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-compose/2.8.7/lifecycle-runtime-compose-2.8.7.pom |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.8.7 | app | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-ktx/2.8.7/lifecycle-runtime-ktx-2.8.7.pom |
| androidx.lifecycle:lifecycle-viewmodel-compose | 2.8.7 | app | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-viewmodel-compose/2.8.7/lifecycle-viewmodel-compose-2.8.7.pom |
| androidx.navigation:navigation-compose | 2.8.5 | app | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-compose/2.8.5/navigation-compose-2.8.5.pom |
| androidx.room:room-ktx | 2.6.1 | app | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/room/room-ktx/2.6.1/room-ktx-2.6.1.pom |
| androidx.room:room-runtime | 2.6.1 | app | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/2.6.1/room-runtime-2.6.1.pom |
| androidx.wear:wear | 1.3.0 | wear | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/wear/wear/1.3.0/wear-1.3.0.pom |
| androidx.wear:wear-ongoing | 1.0.0 | wear | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/wear/wear-ongoing/1.0.0/wear-ongoing-1.0.0.pom |
| androidx.wear.compose:compose-foundation | 1.4.1 | wear | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/wear/compose/compose-foundation/1.4.1/compose-foundation-1.4.1.pom |
| androidx.wear.compose:compose-material | 1.4.1 | wear | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/wear/compose/compose-material/1.4.1/compose-material-1.4.1.pom |
| androidx.wear.protolayout:protolayout | 1.4.2 | wear | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/wear/protolayout/protolayout/1.4.2/protolayout-1.4.2.pom |
| androidx.wear.tiles:tiles | 1.6.2 | wear | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/wear/tiles/tiles/1.6.2/tiles-1.6.2.pom |
| androidx.work:work-runtime-ktx | 2.10.0 | app | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/androidx/work/work-runtime-ktx/2.10.0/work-runtime-ktx-2.10.0.pom |
| com.google.android.gms:play-services-wearable | 19.0.0 | app, wear | Android Software Development Kit License | https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-wearable/19.0.0/play-services-wearable-19.0.0.pom |
| com.google.android.horologist:horologist-compose-layout | 0.6.23 | wear | The Apache Software License, Version 2.0 | https://repo.maven.apache.org/maven2/com/google/android/horologist/horologist-compose-layout/0.6.23/horologist-compose-layout-0.6.23.pom |
| com.google.android.material:material | 1.12.0 | app | The Apache Software License, Version 2.0 | https://dl.google.com/dl/android/maven2/com/google/android/material/material/1.12.0/material-1.12.0.pom |
| com.google.dagger:hilt-android | 2.52 | app | Apache 2.0 | https://repo.maven.apache.org/maven2/com/google/dagger/hilt-android/2.52/hilt-android-2.52.pom |
| com.google.guava:guava | 33.4.8-android | wear | Apache License, Version 2.0 | https://repo.maven.apache.org/maven2/com/google/guava/guava-parent/33.4.8-android/guava-parent-33.4.8-android.pom |
| org.jetbrains.kotlinx:kotlinx-coroutines-android | 1.9.0 | app | The Apache Software License, Version 2.0 | https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-android/1.9.0/kotlinx-coroutines-android-1.9.0.pom |

## 확인하지 못한 항목 (앱 목록에 넣지 않음)
- 없음

## 라이브러리가 아닌 자산
- 운동 가이드 이미지 29종: Free Exercise DB(Unlicense) — `docs/exercise-images-sources.md`. 원 저장소의 개별 이미지 권리는 이 점검에서 따로 확인하지 않았다.
