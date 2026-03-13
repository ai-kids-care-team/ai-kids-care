## 피그마에 https://www.figma.com/make/ZVdEScs28ACUUSM7gOidJE/AI-Kids-Care-%ED%99%88%ED%8E%98%EC%9D%B4%EC%A7%80?t=QdveUj8eeerkeXDO-1 있는 home를 해당 프로젝트에 반영해줘

절대 지켜야 하는 사항은 피그마에 있는 레이아웃 유지가 필요해!

## Frontend 기술 스택

- Framework: Next.js 16 (App Router), React 19, TypeScript 5
- Styling: Tailwind CSS v4, PostCSS (`@tailwindcss/postcss`)
- UI: Radix UI 기반 공통 UI 컴포넌트 (`src/components/shared/ui`)
- State: Redux Toolkit + RTK Query (`src/store`, `src/services/apis/base.api.ts`)
- Data Fetching: RTK Query + Axios(`apiClient.ts`) 혼용
- Form/Utility: react-hook-form, clsx, class-variance-authority, tailwind-merge
- Routing: `src/app` 기준 파일 라우팅 (`(auth)`, `dashboard`, `events`, `profile`)
- Build/Deploy 특징: `next.config.ts`의 `output: 'export'` 사용(정적 export 지향)

## 참고

- 현재 `src/app/page.tsx`는 `/login`으로 리다이렉트됨
- 문서 트리에는 `dashboard.api.ts`가 있으나 실제 `src/services/apis`에는 `metrics.api.ts`가 존재

## 피그마 -> 코드 변환 가이드 (Home 페이지)

### 1) 구현 위치 규칙

- 페이지 엔트리: `src/app/page.tsx` (현재 리다이렉트 로직을 Home 렌더링으로 교체)
- Home 전용 섹션 컴포넌트: `src/components/home/*` 신규 생성 권장
- 공통 UI 재사용: `src/components/shared/ui/*` 우선 사용
- 공통 유틸: `cn()`은 `src/components/shared/ui/utils.ts` 사용

### 2) 변환 순서

1. 피그마를 섹션 단위(Hero, KPI, 카드 리스트, CTA, Footer)로 분해
   Footer 부분은 layout 폴더안에 으로  Footer.tsx 으로 생성해줘

2. 각 섹션을 `shared/ui` 조합으로 먼저 스켈레톤 구현
3. Tailwind 유틸 클래스로 간격/폰트/색상 미세 조정
4. 더미 데이터로 UI 완성 후 RTK Query 데이터 연결
5. 반응형(모바일/태블릿/데스크톱) 순서로 레이아웃 보정

### 3) 컴포넌트 매핑 표 (권장)

| Figma 요소 | 코드 컴포넌트(권장) | 비고 |
|---|---|---|
| 상단 네비게이션 | `NavigationMenu` / `Menubar` | 메뉴가 단순하면 `Button` + `Link` 조합 가능 |
| Hero 타이틀/설명 | `Card` + `Button` + `Badge` | 강조 텍스트는 Tailwind 타이포그래피로 처리 |
| 주요 액션 버튼 | `Button` | variant/size로 스타일 통일 |
| 검색/입력창 | `Input` / `Select` | 조건 검색은 `Select` + `Input` 조합 |
| 탭 전환 영역 | `Tabs` | 섹션 전환, 카드 필터 등에 적합 |
| 통계 카드(KPI) | `Card` + `Badge` | 아이콘은 `lucide-react` 권장 |
| 리스트/테이블 | `Table` / `Card` 반복 | 데이터 구조에 맞춰 선택 |
| 상세 보기 모달 | `Dialog` / `Sheet` | 모바일은 `Sheet`가 UX에 유리 |
| 알림/토스트 | `sonner` | 성공/실패 피드백 통일 |
| 아코디언 FAQ | `Accordion` | 정보 접기/펼치기 용도 |
| 프로필/아바타 | `Avatar` | 사용자 정보 요약 표시 |
| 로딩 상태 | `Skeleton` | API 로딩 구간에 기본 적용 |

### 4) 데이터/상태 연결 규칙

- 서버 데이터: RTK Query endpoint에 추가 (`src/services/apis/*.api.ts`)
- 전역 인증/유저 상태: Redux Slice(`src/store/slices/userSlice.ts`) 사용
- 일회성 UI 상태(모달 열림 등): 컴포넌트 로컬 상태로 관리
- API base URL: `NEXT_PUBLIC_API_BASE_URL` 우선, 없으면 로컬 기본값 사용

### 5) 품질 체크리스트

- 텍스트/간격/색상 토큰이 피그마와 일치하는가
- 모바일(최소 375px)에서 레이아웃 깨짐이 없는가
- 버튼/입력 포커스 스타일이 보장되는가(접근성)
- 로딩/에러/빈 상태(empty state) 화면이 준비되었는가
- `npm run lint` 기준 오류 없이 통과하는가

---



