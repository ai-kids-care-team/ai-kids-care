# detectionEvents MCP Step 2 - Detail 화면 생성

## 목적
Figma 디자인 중 `detectionEvents` 상세 화면만 생성한다.

이 단계에서는 상세 화면 관련 파일만 생성한다.
전체 구조 공통 규칙은 반드시 `detectionEvents_mcp_master_guide.md` 를 따른다.

---

## 이번 단계에서 생성 대상

```text
app/detectionEvents/read/page.tsx

components/detectionEvents/DetectionEventsDetailPage.tsx
components/detectionEvents/functions/useDetectionEventDetail.ts
```

---

## 라우트 매핑

```text
/detectionEvents/read -> DetectionEventsDetailPage
```

---

## 생성 규칙

1. `app/detectionEvents/read/page.tsx` 는 라우트 진입점만 담당한다.
2. 실제 상세 화면은 `components/detectionEvents/DetectionEventsDetailPage.tsx` 에 작성한다.
3. 상세 조회, 로딩, 에러, 데이터 상태관리는 `useDetectionEventDetail.ts` 에 작성한다.
4. API는 반드시 `services/apis/detectionEvents.api.ts` 를 사용한다.
5. 타입은 반드시 `types/detectionEvents.ts` 를 사용한다.
6. 공통 UI는 `components/shared/ui` 를 재사용한다.
7. 스타일은 Tailwind CSS 기준으로 작성한다.

---

## 파일별 역할

### app/detectionEvents/read/page.tsx
- `DetectionEventsDetailPage` import
- 단순 return

예시 구조:

```tsx
import DetectionEventsDetailPage from '@/components/detectionEvents/DetectionEventsDetailPage';

export default function Page() {
  return <DetectionEventsDetailPage />;
}
```

### components/detectionEvents/DetectionEventsDetailPage.tsx
- 상세 화면 전체 구성
- hook 연결
- loading / error / empty state 표시
- 이벤트 기본 정보, 감지 정보, 시간, 상태, 메타 정보 등을 읽기 전용으로 표시

### components/detectionEvents/functions/useDetectionEventDetail.ts
- 상세 조회 API 호출
- 단건 데이터 상태
- 로딩 상태
- 에러 상태
- 상세 재조회 함수

---

## 필수 포함 항목

- loading state
- error state
- empty state
- 상세 정보 표시 영역
- 읽기 전용 상세 레이아웃

---

## 금지 사항

- 목록 검색 폼 새로 생성 금지
- 수정/등록 폼 생성 금지
- page.tsx 안에 상세 UI 전부 작성 금지
- component 내부 fetch/axios 직접 호출 금지

---

## 기대 결과

이 단계가 끝나면 아래가 가능해야 한다.

- `/detectionEvents/read` 상세 화면 진입 가능
- 단건 조회 구조 확인 가능
- Detail 패턴이 announcements 구조와 맞는지 검증 가능

---

## MCP 실행 지시문

```md
현재 프로젝트 구조를 따르며 detectionEvents 상세 화면만 생성하라.

반드시 기존 announcements 모듈 패턴을 따르고,
아래 파일만 생성 또는 갱신하라.

생성 대상:
- app/detectionEvents/read/page.tsx
- components/detectionEvents/DetectionEventsDetailPage.tsx
- components/detectionEvents/functions/useDetectionEventDetail.ts

규칙:
- page.tsx는 라우트 진입점만 담당
- 실제 상세 화면은 DetectionEventsDetailPage에 작성
- 상세 조회/상태관리는 useDetectionEventDetail에서 처리
- API는 services/apis/detectionEvents.api.ts 사용
- 타입은 types/detectionEvents.ts 사용
- components/shared/ui 재사용
- loading, error, empty state 포함
- 새로운 루트 폴더 생성 금지
- 목록/수정/등록 관련 파일은 생성하지 말 것
```
