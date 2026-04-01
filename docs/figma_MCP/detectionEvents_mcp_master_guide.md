# detectionEvents MCP Master Guide


피그마 URL : https://www.figma.com/make/gzXtt56oREtYITJTwCp5Dd/AI-Kids-Care---AI-%EC%95%8C%EB%A6%BC?t=pVUI8KxW954gG6Wp-1&preview-route=%2Fai-alerts


## 목적
Figma MCP를 사용해 `detectionEvents` 디자인을 현재 프로젝트 구조에 맞게 생성할 때,
전체적으로 따라야 하는 공통 기준 문서입니다.

이 문서는 **전체 규칙**만 정의합니다.
실제 작업은 아래 step 문서를 각각 따릅니다.

- Step 1: `detectionEvents_mcp_step1_list.md`
- Step 2: `detectionEvents_mcp_step2_detail.md`
- Step 3: `detectionEvents_mcp_step3_edit_write.md`

---

## 기준 모듈명
- `detectionEvents`

반드시 위 이름으로만 사용합니다.

---

## 기준 프로젝트 구조

```text
app
├─detectionEvents
│  │  page.tsx
│  ├─read
│  │      page.tsx
│  ├─edit
│  │      page.tsx
│  └─write
│          page.tsx

components
├─detectionEvents
│  │  DetectionEventsDetailPage.tsx
│  │  DetectionEventsEditPage.tsx
│  │  DetectionEventsListForm.tsx
│  │  DetectionEventsListPage.tsx
│  │  DetectionEventsWritePage.tsx
│  ├─(detection)
│  │      DetectionEventsEditForm.tsx
│  │      DetectionEventsWriteForm.tsx
│  └─functions
│          useDetectionEventDetail.ts
│          useDetectionEvents.ts
│          useDetectionEventsEdit.ts

services
└─apis
        detectionEvents.api.ts

types
    detectionEvents.ts
```

---

## 반드시 따라야 하는 최상위 원칙

### 1. announcements 패턴 유지
- `detectionEvents`는 반드시 기존 `announcements` 모듈과 같은 분리 방식을 따른다.
- 새로운 아키텍처를 만들지 않는다.

### 2. 기존 폴더 구조에 삽입
- 새 프로젝트처럼 생성하지 않는다.
- 이미 존재하는 현재 구조 안에 끼워 넣는 방식으로 생성한다.

### 3. app은 라우트 진입점만 담당
- `app/detectionEvents/**/page.tsx` 는 얇게 유지한다.
- UI, 비즈니스 로직, API 호출을 page 파일에 몰아넣지 않는다.

### 4. 실제 화면은 components에 생성
- 실제 페이지 화면은 `components/detectionEvents/*Page.tsx` 에 둔다.

### 5. 입력 폼은 분리
- 수정/등록용 폼은 `components/detectionEvents/(detection)` 에 둔다.

### 6. 상태/조회/수정 로직은 functions에 분리
- hook, 상태, API 연결은 `components/detectionEvents/functions` 에 둔다.

### 7. API / Type 재사용
- API는 반드시 `services/apis/detectionEvents.api.ts` 사용
- 타입은 반드시 `types/detectionEvents.ts` 사용

### 8. 공통 UI 재사용
- 반드시 `components/shared/ui` 컴포넌트를 우선 사용한다.
- 공통 버튼, 카드, 입력창, 테이블 등을 새로 만들지 않는다.

---

## 네이밍 규칙

### 컴포넌트
- PascalCase
- 예: `DetectionEventsListPage`

### hook
- camelCase
- 예: `useDetectionEvents`

### 폴더/모듈명
- 반드시 `detectionEvents`
- 다른 표기 금지

---

## 금지 규칙

아래는 절대 금지합니다.

- `features`, `modules`, `domain`, `hooks` 같은 새 루트 폴더 생성
- `detection-events`, `DetectionEvents`, `detection_event` 등 다른 이름 혼용
- `page.tsx` 한 파일에 모든 코드 몰아넣기
- 컴포넌트 내부에서 직접 fetch / axios 호출
- `shared/ui` 밖에 공통 UI 새로 생성
- 기존 announcements 구조와 다른 책임 분리 방식 사용

---

## 필수 상태

모든 페이지에는 아래 상태가 포함되어야 합니다.

- loading state
- error state
- empty state

---

## 라우트 매핑

```text
/detectionEvents       -> DetectionEventsListPage
/detectionEvents/read  -> DetectionEventsDetailPage
/detectionEvents/edit  -> DetectionEventsEditPage
/detectionEvents/write -> DetectionEventsWritePage
```

---

## 파일 역할 기준

### app/detectionEvents/**/page.tsx
- 페이지 진입점
- 컴포넌트 import 후 return

### components/detectionEvents/*Page.tsx
- 화면 구성
- hook 연결
- form 연결
- 레이아웃 조합

### components/detectionEvents/*Form.tsx
- 입력 UI
- validation
- submit 연결

### components/detectionEvents/functions/*.ts
- API 호출
- 상태 관리
- 데이터 조회/가공
- loading / error 처리

---

## 작업 순서

반드시 아래 순서로 작업합니다.

1. Step 1: 목록 화면
2. Step 2: 상세 화면
3. Step 3: 수정/등록 화면

한 번에 전체 생성하지 말고, step 단위로 검증하며 진행합니다.

---

## Step 문서 안내

### Step 1
목록 화면 작업용 문서  
파일명: `detectionEvents_mcp_step1_list.md`

### Step 2
상세 화면 작업용 문서  
파일명: `detectionEvents_mcp_step2_detail.md`

### Step 3
수정/등록 화면 작업용 문서  
파일명: `detectionEvents_mcp_step3_edit_write.md`

---

## 최종 성공 조건

- 현재 프로젝트에 바로 붙여넣을 수 있어야 한다.
- announcements 구조와 일관성이 있어야 한다.
- detectionEvents 명칭이 정확해야 한다.
- 파일 역할 분리가 명확해야 한다.
- step별 생성 후 바로 검증 가능해야 한다.
