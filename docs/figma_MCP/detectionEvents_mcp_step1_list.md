# detectionEvents MCP Step 1 - List 화면 생성

## 목적
Figma 디자인 중 `detectionEvents` 목록 화면만 먼저 생성한다.

이 단계에서는 목록 화면 관련 파일만 생성한다.
전체 구조 공통 규칙은 반드시 `detectionEvents_mcp_master_guide.md` 를 따른다.

---

## 이번 단계에서 생성 대상

```text
app/detectionEvents/page.tsx

components/detectionEvents/DetectionEventsListPage.tsx
components/detectionEvents/DetectionEventsListForm.tsx
components/detectionEvents/functions/useDetectionEvents.ts
```

---

## 라우트 매핑

```text
/detectionEvents -> DetectionEventsListPage
```

---

## 생성 규칙

1. `app/detectionEvents/page.tsx` 는 라우트 진입점만 담당한다.
2. 실제 목록 화면은 `components/detectionEvents/DetectionEventsListPage.tsx` 에 작성한다.
3. 검색/필터/목록 UI는 `DetectionEventsListForm.tsx` 로 분리한다.
4. 데이터 조회, 로딩, 에러, 목록 상태관리는 `useDetectionEvents.ts` 에 작성한다.
5. API는 반드시 `services/apis/detectionEvents.api.ts` 를 사용한다.
6. 타입은 반드시 `types/detectionEvents.ts` 를 사용한다.
7. 공통 UI는 `components/shared/ui` 를 재사용한다.
8. 스타일은 Tailwind CSS 기준으로 작성한다.

---

## 파일별 역할

### app/detectionEvents/page.tsx
- `DetectionEventsListPage` import
- 단순 return

예시 구조:

```tsx
import DetectionEventsListPage from '@/components/detectionEvents/DetectionEventsListPage';

export default function Page() {
  return <DetectionEventsListPage />;
}
```

### components/detectionEvents/DetectionEventsListPage.tsx
- 목록 페이지 전체 화면 조합
- hook 연결
- loading / error / empty state 표시
- ListForm 렌더링
- table, card, pagination 등 레이아웃 구성

### components/detectionEvents/DetectionEventsListForm.tsx
- 검색 영역
- 필터 UI
- 정렬 UI
- 필요시 기간 검색, 상태 검색, 키워드 입력 UI

### components/detectionEvents/functions/useDetectionEvents.ts
- 목록 조회 API 호출
- 검색 조건 상태
- 목록 데이터 상태
- 로딩 상태
- 에러 상태
- 목록 재조회 함수

---

## 필수 포함 항목

- loading state
- error state
- empty state
- 목록 영역
- 검색/필터 영역

---

## 금지 사항

- 상세/수정/등록 화면 파일 생성 금지
- edit/read/write 관련 코드 포함 금지
- page.tsx 안에 목록 UI 전부 작성 금지
- component 내부 fetch/axios 직접 호출 금지

---

## 기대 결과

이 단계가 끝나면 아래가 가능해야 한다.

- `/detectionEvents` 목록 화면 진입 가능
- 목록 조회 구조 확인 가능
- 화면 레이아웃 패턴 검증 가능
- announcements 목록 패턴과 비교 가능

---

## MCP 실행 지시문

```md
현재 프로젝트 구조를 따르며 detectionEvents 목록 화면만 생성하라.

반드시 기존 announcements 모듈 패턴을 따르고,
아래 파일만 생성 또는 갱신하라.

생성 대상:
- app/detectionEvents/page.tsx
- components/detectionEvents/DetectionEventsListPage.tsx
- components/detectionEvents/DetectionEventsListForm.tsx
- components/detectionEvents/functions/useDetectionEvents.ts

규칙:
- page.tsx는 라우트 진입점만 담당
- 실제 목록 화면은 DetectionEventsListPage에 작성
- 검색/필터 UI는 DetectionEventsListForm에 분리
- 조회/상태관리는 useDetectionEvents에서 처리
- API는 services/apis/detectionEvents.api.ts 사용
- 타입은 types/detectionEvents.ts 사용
- components/shared/ui 재사용
- loading, error, empty state 포함
- 새로운 루트 폴더 생성 금지
- edit/read/write 관련 파일은 생성하지 말 것
```
