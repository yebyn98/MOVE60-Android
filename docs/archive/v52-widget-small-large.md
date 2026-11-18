# v52 홈 위젯 작은/큰 (Figma 05:5 · 07 확정 시안)

- 위젯 2종: `WorkoutWidget`(작은, 기존 프로바이더 유지 → 업그레이드 호환) / `WorkoutWidgetLarge`(큰). 런처 위젯 선택 목록에 별도 항목으로 노출된다.
- 표시: 단계(예: 유산소 ① · 사이클), 상태 칩(진행 중/일시정지/완료/대기), 남은 시간, 심박수(숫자 + 측정 시각). 큰 위젯은 추가로 "구간명 · RPE" 한 줄.
- 심박수: Health Connect의 **과거 샘플**이다. 사용자가 연동을 켠 경우에만, 마지막으로 읽은 실제 샘플을 `move60_widget_heart_v1`에 저장해 표시한다. 오늘이 아니면 "11/7 오후 3:12"처럼 날짜를 붙이고, 샘플이 없거나 연동을 끄면 "연결 대기"로 표시하며 값을 만들어내지 않는다.
- 가장자리 끊김 방지: 배경은 선(stroke) 없는 채움만 사용, 모서리는 Android 12+에서 시스템 위젯 반경(`system_app_widget_background_radius`), 그 이하는 28dp. 내용은 16dp 안쪽 여백, 제목은 1줄 말줄임, 시간은 고정 폭 숫자.
- 모델은 `WorkoutWidgetModel`(순수 함수)로 분리해 `WorkoutWidgetModelTest`로 검증한다. 렌더 캡처는 `LayoutEvidenceTest`가 `widget-small.png`, `widget-large.png`로 남긴다.
- 실기기(갤럭시 런처) 가장자리/마스크 확인은 미검증.
