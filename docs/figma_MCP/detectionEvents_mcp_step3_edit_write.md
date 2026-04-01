# detectionEvents MCP Step 3 - Edit / Write 화면 생성

## 목적
Figma 디자인 중 `detectionEvents` 수정/등록 화면을 생성한다.

이 단계에서는 수정 및 등록 관련 파일만 생성한다.
전체 구조 공통 규칙은 반드시 `detectionEvents_mcp_master_guide.md` 를 따른다.

---

## 이번 단계에서 생성 대상

```text
app/detectionEvents/edit/page.tsx
app/detectionEvents/write/page.tsx

components/detectionEvents/DetectionEventsEditPage.tsx
components/detectionEvents/DetectionEventsWritePage.tsx

components/detectionEvents/(detection)/DetectionEventsEditForm.tsx
components/detectionEvents/(detection)/DetectionEventsWriteForm.tsx

components/detectionEvents/functions/useDetectionEventsEdit.ts
```

---

## 라우트 매핑

```text
/detectionEvents/edit  -> DetectionEventsEditPage
/detectionEvents/write -> DetectionEventsWritePage
```

---

## 생성 규칙

1. `app/detectionEvents/edit/page.tsx` 와 `app/detectionEvents/write/page.tsx` 는 라우트 진입점만 담당한다.
2. 실제 수정 화면은 `DetectionEventsEditPage.tsx` 에 작성한다.
3. 실제 등록 화면은 `DetectionEventsWritePage.tsx` 에 작성한다.
4. 수정 폼은 `components/detectionEvents/(detection)/DetectionEventsEditForm.tsx` 에 작성한다.
5. 등록 폼은 `components/detectionEvents/(detection)/DetectionEventsWriteForm.tsx` 에 작성한다.
6. 수정/등록 상태, submit 처리, API 연동은 `useDetectionEventsEdit.ts` 에 작성한다.
7. API는 반드시 `services/apis/detectionEvents.api.ts` 를 사용한다.
8. 타입은 반드시 `types/detectionEvents.ts` 를 사용한다.
9. 공통 UI는 `components/shared/ui` 를 재사용한다.
10. 스타일은 Tailwind CSS 기준으로 작성한다.

---

## 파일별 역할

### app/detectionEvents/edit/page.tsx
- `DetectionEventsEditPage` import
- 단순 return

### app/detectionEvents/write/page.tsx
- `DetectionEventsWritePage` import
- 단순 return

### components/detectionEvents/DetectionEventsEditPage.tsx
- 수정 페이지 전체 구성
- edit hook 연결
- EditForm 렌더링
- loading / error / empty state 처리

### components/detectionEvents/DetectionEventsWritePage.tsx
- 등록 페이지 전체 구성
- write 흐름 연결
- WriteForm 렌더링
- loading / error state 처리

### components/detectionEvents/(detection)/DetectionEventsEditForm.tsx
- 수정 입력 UI
- validation
- submit 연결

### components/detectionEvents/(detection)/DetectionEventsWriteForm.tsx
- 등록 입력 UI
- validation
- submit 연결

### components/detectionEvents/functions/useDetectionEventsEdit.ts
- 수정/등록 API 처리
- 초기값 로딩
- submit 상태 관리
- loading / error 처리

---

## 필수 포함 항목

- loading state
- error state
- empty state(수정 화면에서 필요 시)
- form validation
- submit 처리
- 저장 버튼 / 취소 버튼 등 액션 UI

---

## 금지 사항

- 목록 화면 파일 재생성 금지
- 상세 화면 파일 재생성 금지
- page.tsx 안에 폼 UI 전부 작성 금지
- component 내부 fetch/axios 직접 호출 금지

---

## 기대 결과

이 단계가 끝나면 아래가 가능해야 한다.

- `/detectionEvents/edit` 수정 화면 진입 가능
- `/detectionEvents/write` 등록 화면 진입 가능
- 폼 구조 및 submit 흐름 검증 가능
- announcements edit/write 패턴과 비교 가능

---

## MCP 실행 지시문

```md
현재 프로젝트 구조를 따르며 detectionEvents 수정/등록 화면만 생성하라.

반드시 기존 announcements 모듈 패턴을 따르고,
아래 파일만 생성 또는 갱신하라.

생성 대상:
- app/detectionEvents/edit/page.tsx
- app/detectionEvents/write/page.tsx
- components/detectionEvents/DetectionEventsEditPage.tsx
- components/detectionEvents/DetectionEventsWritePage.tsx
- components/detectionEvents/(detection)/DetectionEventsEditForm.tsx
- components/detectionEvents/(detection)/DetectionEventsWriteForm.tsx
- components/detectionEvents/functions/useDetectionEventsEdit.ts

규칙:
- page.tsx는 라우트 진입점만 담당
- 실제 수정 화면은 DetectionEventsEditPage에 작성
- 실제 등록 화면은 DetectionEventsWritePage에 작성
- 수정 폼은 DetectionEventsEditForm에 분리
- 등록 폼은 DetectionEventsWriteForm에 분리
- 수정/등록 상태관리는 useDetectionEventsEdit에서 처리
- API는 services/apis/detectionEvents.api.ts 사용
- 타입은 types/detectionEvents.ts 사용
- components/shared/ui 재사용
- loading, error 상태 포함
- 새로운 루트 폴더 생성 금지
- 목록/상세 파일은 생성하지 말 것
```
