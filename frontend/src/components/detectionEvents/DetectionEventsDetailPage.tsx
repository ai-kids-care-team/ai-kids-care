"use client";

import Link from "next/link";
import { Suspense, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { Html } from "@react-three/drei";
import {
    AlertTriangle,
    ClipboardList,
    CheckSquare,
    Search,
    Send,
    CheckCircle2,
    XCircle,
    Megaphone,
} from "lucide-react";
import { useDetectionEventDetail } from "@/components/detectionEvents/functions/useDetectionEventDetail";
import { EventReviewFlow } from "@/components/detectionEvents/EventReviewFlow";
import {
    createEventReview,
    getLatestEventReview,
    type CreateEventReviewDTO,
    type EventReview,
} from "@/services/apis/eventReviews.api";
import { updateDetectionEvent } from "@/services/apis/detectionEvents.api";
import { useAppSelector } from "@/store/hook";
import {
    getParentCommonCodeList,
    type CommonCode,
} from "@/services/apis/commonCodes.api";
import { searchChildrenByName } from "@/services/apis/children.api";
import { getChildGraph, type ChildGraph } from "@/services/apis/graph.api";
import {
    GraphCanvas,
    lightTheme,
    type GraphCanvasRef,
    type GraphEdge,
    type GraphNode,
    type NodeRendererProps,
} from "reagraph";
import type { Child } from "@/types/child";

type ChildGraphViewMode = "reagraph2d" | "reagraph3d";

/** 그래프 노드·관계 라벨 글자 크기 (기준 대비 비율) */
const GRAPH_LABEL_SIZE_SCALE = 0.8;
/** Reagraph 노드 기준 반지름 (시각적 반지름, Html 라벨과 함께 조정) */
const CHILD_NODE_SIZE = 7.92;
/** 중심(아이)에서 유치원·반·교사까지 거리 — 엣지 길이·관계 라벨 겹침 완화 */
const GRAPH_SATELLITE_OFFSET = 92;
/** 보호자 반원 배치 반지름 */
const GRAPH_GUARDIAN_RING_RADIUS = 100;
/** 관계(엣지) 라벨: 노드 반지름 대비 (월드 단위 troika Text) */
const REAGRAPH_EDGE_LABEL_FONT_FACTOR = 0.38;
/** 관계 그래프 모달 안 캔버스 높이 (큰 노드·줌 여유) */
const GRAPH_MODAL_VIEWPORT_HEIGHT_PX = 560;
const NODE_FONT_PLUS_PX = 1.5;
/** 노드 내부 이름 폰트: 기준 대비 배율 (요청: 6배) */
const REAGRAPH_NODE_LABEL_FONT_MULTIPLIER = 6;
/** 아이(중심) 노드만 시각적으로 확대할 배율 (sizingType none 유지 시 render에서 적용) */
const CHILD_NODE_VISUAL_SCALE = 1.5;
/** Reagraph 아이 노드 글로우: 바깥 반투명 구 스케일(본체 대비) */
const REAGRAPH_CHILD_GLOW_LAYERS: { scale: number; opacity: number }[] = [
    { scale: 1.5, opacity: 0.11 },
    { scale: 1.28, opacity: 0.22 },
];
/** Reagraph 3D: fit 직후 카메라를 한 번 더 당겨 여백 확보 (그래프가 화면 중앙·작게 보이게) */
const REAGRAPH_3D_POST_FIT_DOLLY_OUT = 220;

/** Reagraph: 기본 반지름 (아이는 renderNode에서 CHILD_NODE_VISUAL_SCALE 적용) */
function reagraphNodeSizeByType(_nodeType: string): number {
    return CHILD_NODE_SIZE;
}

function toKoreanGuardianLabel(value?: string | null): string {
    if (!value) return "보호자";
    const normalized = value.trim().toLowerCase();
    if (normalized === "mother" || normalized === "mom") return "엄마";
    if (normalized === "father" || normalized === "dad") return "아빠";
    return value;
}

function formatDateTime(value: string | null): string {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '-';
    const yyyy = date.getFullYear();
    const mm = String(date.getMonth() + 1).padStart(2, '0');
    const dd = String(date.getDate()).padStart(2, '0');
    const hours24 = date.getHours();
    const period = hours24 < 12 ? '오전' : '오후';
    const hours12Raw = hours24 % 12;
    const hours12 = hours12Raw === 0 ? 12 : hours12Raw;
    const mi = String(date.getMinutes()).padStart(2, '0');
    const ss = String(date.getSeconds()).padStart(2, '0');
    return `${yyyy}.${mm}.${dd} (${period}) ${hours12}시 ${mi}분 ${ss}초`;
}

export function DetectionEventsDetailPage() {
    const { id, detail, loading, error, reload } = useDetectionEventDetail();
    const { user } = useAppSelector((state) => state.user);
    const [latestReview, setLatestReview] = useState<EventReview | null>(null);
    const [memo, setMemo] = useState('');
    const [refreshKey, setRefreshKey] = useState(0);
    const [eventTypeLabel, setEventTypeLabel] = useState<string | null>(null);
    const [isChildModalOpen, setIsChildModalOpen] = useState(false);
    const [childNameKeyword, setChildNameKeyword] = useState('');
    const [childSearchResults, setChildSearchResults] = useState<Child[]>([]);
    const [isChildSearching, setIsChildSearching] = useState(false);
    const [childSearchError, setChildSearchError] = useState('');
    const [selectedChild, setSelectedChild] = useState<Child | null>(null);
    const [isGraphModalOpen, setIsGraphModalOpen] = useState(false);
    const [childGraph, setChildGraph] = useState<ChildGraph | null>(null);
    const [isGraphLoading, setIsGraphLoading] = useState(false);
    const [graphError, setGraphError] = useState('');
    const [graphMode, setGraphMode] = useState<ChildGraphViewMode>("reagraph2d");
    const reagraphRef = useRef<GraphCanvasRef | null>(null);
    const graphWrapRef = useRef<HTMLDivElement | null>(null);
    const [graphSize, setGraphSize] = useState({
        width: 896,
        height: GRAPH_MODAL_VIEWPORT_HEIGHT_PX,
    });

    const graphData = useMemo(() => {
        if (!childGraph || !childGraph.child) return { nodes: [], links: [] };

        type Node = {
            id: string;
            label: string;
            type: string;
            color: string;
            x: number;
            y: number;
            z: number;
            fx: number;
            fy: number;
            fz: number;
        };
        type Link = { source: string; target: string; label?: string };

        const nodes: Node[] = [];
        const links: Link[] = [];

        // 중앙 아이 노드
        const childId = `child-${childGraph.child.childId}`;
        nodes.push({
            id: childId,
            label: childGraph.child.name,
            type: 'child',
            color: '#6366f1',
            x: 0,
            y: 0,
            z: 0,
            fx: 0,
            fy: 0,
            fz: 0,
        });

        // 유치원: 위쪽
        if (childGraph.kindergarten) {
            const kId = `kg-${childGraph.kindergarten.kindergartenId}`;
            nodes.push({
                id: kId,
                label: childGraph.kindergarten.name,
                type: 'kindergarten',
                color: '#1d4ed8',
                x: 0,
                y: -GRAPH_SATELLITE_OFFSET,
                z: 0,
                fx: 0,
                fy: -GRAPH_SATELLITE_OFFSET,
                fz: 0,
            });
            links.push({ source: childId, target: kId, label: '유치원' });
        }

        // 반: 왼쪽
        if (childGraph.classInfo) {
            const cId = `class-${childGraph.classInfo.classId}`;
            nodes.push({
                id: cId,
                label: childGraph.classInfo.name,
                type: 'class',
                color: '#10b981',
                x: -GRAPH_SATELLITE_OFFSET,
                y: 0,
                z: 0,
                fx: -GRAPH_SATELLITE_OFFSET,
                fy: 0,
                fz: 0,
            });
            links.push({ source: childId, target: cId, label: '소속 반' });
        }

        // 교사: 오른쪽
        if (childGraph.teacher) {
            const tId = `teacher-${childGraph.teacher.teacherId}`;
            nodes.push({
                id: tId,
                label: childGraph.teacher.name,
                type: 'teacher',
                color: '#f97316',
                x: GRAPH_SATELLITE_OFFSET,
                y: 0,
                z: 0,
                fx: GRAPH_SATELLITE_OFFSET,
                fy: 0,
                fz: 0,
            });
            links.push({ source: childId, target: tId, label: '담임 교사' });
        }

        // 보호자: 아래쪽 반원
        const guardians = childGraph.guardians;
        guardians.forEach((g, index) => {
            const gId = `guardian-${g.guardianId}`;
            const count = guardians.length || 1;
            const angleStart = 210;
            const angleEnd = -30;
            const angleDeg =
                count === 1
                    ? 270
                    : angleStart +
                    ((angleEnd - angleStart) * index) / Math.max(count - 1, 1);
            const rad = (angleDeg * Math.PI) / 180;
            const radius = GRAPH_GUARDIAN_RING_RADIUS;
            const x = radius * Math.cos(rad);
            const y = radius * Math.sin(rad);

            nodes.push({
                id: gId,
                label: g.name,
                type: g.isPrimary ? 'guardian-primary' : 'guardian',
                color: g.isPrimary ? '#b91c1c' : '#0f766e',
                x,
                y,
                z: 0,
                fx: x,
                fy: y,
                fz: 0,
            });
            links.push({
                source: childId,
                target: gId,
                label: toKoreanGuardianLabel(g.relationship),
            });
        });

        return { nodes, links };
    }, [childGraph]);

    const reagraphGraph = useMemo(() => {
        if (!graphData.nodes.length) {
            return { nodes: [] as GraphNode[], edges: [] as GraphEdge[] };
        }
        const nodes: GraphNode[] = (graphData.nodes as any[]).map((n) => ({
            id: n.id as string,
            label: n.label as string,
            fill: n.color as string,
            size: reagraphNodeSizeByType(String(n.type)),
            fx: n.fx as number,
            fy: n.fy as number,
            fz: n.fz as number,
            labelVisible: false,
        }));
        const edges: GraphEdge[] = graphData.links.map((l, i) => ({
            id: `e-${String(l.source)}-${String(l.target)}-${i}`,
            source: String(l.source),
            target: String(l.target),
            label: l.label,
        }));
        return { nodes, edges };
    }, [graphData]);

    const reagraphTheme = useMemo(() => ({
        ...lightTheme,
        canvas: {
            ...lightTheme.canvas,
            background: '#f8fafc',
            fog: null,
        },
        node: {
            ...lightTheme.node,
            label: {
                ...lightTheme.node.label,
                color: '#0f172a',
                stroke: '#ffffff',
                fontSize: 13 * GRAPH_LABEL_SIZE_SCALE,
                strokeWidth: 3,
            },
        },
        edge: {
            ...lightTheme.edge,
            fill: '#94a3b8',
            label: {
                ...lightTheme.edge.label,
                color: '#475569',
                stroke: '#f8fafc',
                fontSize: Math.max(2.4, CHILD_NODE_SIZE * REAGRAPH_EDGE_LABEL_FONT_FACTOR) + 2,
            },
        },
        arrow: {
            ...lightTheme.arrow,
            fill: '#64748b',
        },
    }), []);

    const reagraphRenderNode = useCallback(({ color, size, opacity, id, node }: NodeRendererProps) => {
        const label = (node.label ?? '').trim();
        const isChildNode = String(id).startsWith('child-');
        const r = isChildNode ? size * CHILD_NODE_VISUAL_SCALE : size;
        const basePx = Math.max(5, Math.min(14, 3 + r * 0.5 + NODE_FONT_PLUS_PX * 0.3));
        const fontPx = Math.min(160, basePx * REAGRAPH_NODE_LABEL_FONT_MULTIPLIER);
        return (
            <group userData={{ id, type: 'node' }}>
                {isChildNode &&
                    REAGRAPH_CHILD_GLOW_LAYERS.map(({ scale: gScale, opacity: gOpacity }, i) => (
                        <mesh
                            key={`glow-${i}`}
                            scale={[r * gScale, r * gScale, r * gScale]}
                            renderOrder={i}
                        >
                            <sphereGeometry args={[1, 20, 20]} />
                            <meshBasicMaterial
                                color={color}
                                transparent
                                opacity={gOpacity * (opacity ?? 1)}
                                depthWrite={false}
                                toneMapped={false}
                            />
                        </mesh>
                    ))}
                <mesh scale={[r, r, r]} renderOrder={REAGRAPH_CHILD_GLOW_LAYERS.length}>
                    <sphereGeometry args={[1, 25, 25]} />
                    <meshBasicMaterial color={color} transparent opacity={opacity} />
                </mesh>
                <Html
                    center
                    transform
                    occlude={false}
                    distanceFactor={32}
                    pointerEvents="none"
                    zIndexRange={[200, 0]}
                    style={{
                        color: '#ffffff',
                        fontSize: `${fontPx}px`,
                        fontWeight: 700,
                        lineHeight: 1.2,
                        textAlign: 'center',
                        whiteSpace: 'nowrap',
                        maxWidth: 'min(24em, 90vw)',
                        overflow: 'visible',
                        textShadow: '0 1px 4px rgba(0,0,0,0.95)',
                        letterSpacing: '-0.02em',
                    }}
                >
                    {label}
                </Html>
            </group>
        );
    }, []);

    useEffect(() => {
        const loadLatest = async () => {
            if (!Number.isFinite(id) || id <= 0) return;
            try {
                const review = await getLatestEventReview(id);
                setLatestReview(review);
            } catch (e) {
                console.error('최신 처리 상태 조회 실패:', e);
            }
        };

        void loadLatest();
    }, [id]);

    // 사건 유형 코드 → 한글 명칭 매핑
    useEffect(() => {
        const loadEventTypeLabel = async () => {
            if (!detail?.eventType) {
                setEventTypeLabel(null);
                return;
            }
            try {
                const codes: CommonCode[] = await getParentCommonCodeList('detection_events', 'event_type');
                const normalized = detail.eventType.trim().toLowerCase();
                const matched = codes.find((c) => c.code.trim().toLowerCase() === normalized);
                const label = (matched?.codeName ?? matched?.code_name)?.trim();
                setEventTypeLabel(label || null);
            } catch (e) {
                console.error('사건 유형 공통코드 로딩 실패:', e);
                setEventTypeLabel(null);
            }
        };

        void loadEventTypeLabel();
    }, [detail?.eventType]);

    const latestStatus = latestReview?.result_status ?? null;
    const showConfirm = latestStatus === 'OPEN';
    const showReview = latestStatus === 'ACKNOWLEDGED';
    const showResolve = latestStatus === 'IN_REVIEW';
    const showDismiss =
        latestStatus === 'ACKNOWLEDGED' || latestStatus === 'IN_REVIEW';
    const showEscalate = latestStatus === 'RESOLVED';
    const hasAnyAction = showConfirm || showReview || showResolve || showDismiss || showEscalate;

    const openChildSearchModal = () => {
        setChildSearchResults([]);
        setChildSearchError('');
        setIsChildModalOpen(true);
    };

    const closeChildSearchModal = () => {
        setIsChildModalOpen(false);
    };

    const handleChildSearch = async () => {
        const keyword = childNameKeyword.trim();
        if (!keyword) {
            setChildSearchError('아이 이름을 입력해주세요.');
            setChildSearchResults([]);
            return;
        }

        setChildSearchError('');
        setIsChildSearching(true);
        try {
            const page = await searchChildrenByName(keyword, 0, 20);
            setChildSearchResults(page.content ?? []);
            if (!page.content || page.content.length === 0) {
                setChildSearchError('검색 결과가 없습니다.');
            }
        } catch (err) {
            console.error('아이 이름 검색 실패:', err);
            setChildSearchResults([]);
            setChildSearchError('아이 조회에 실패했습니다.');
        } finally {
            setIsChildSearching(false);
        }
    };

    /** 모달을 열고 현재 키워드로 검색 (인라인 입력·아이 찾기) */
    const submitChildSearch = () => {
        openChildSearchModal();
        void handleChildSearch();
    };

    const handleSelectChild = (child: Child) => {
        setSelectedChild(child);
        setIsChildModalOpen(false);
    };

    const openGraphModal = async () => {
        if (!selectedChild || !Number.isFinite(selectedChild.childId)) return;

        setIsGraphModalOpen(true);
        setChildGraph(null);
        setGraphError('');
        setIsGraphLoading(true);
        try {
            const graph = await getChildGraph(selectedChild.childId);
            setChildGraph(graph);
        } catch (err) {
            console.error('아이 관계 그래프 조회 실패:', err);
            setGraphError('아이 관계 정보를 불러오지 못했습니다.');
        } finally {
            setIsGraphLoading(false);
        }
    };

    const closeGraphModal = () => {
        setIsGraphModalOpen(false);
    };

    useEffect(() => {
        if (!isGraphModalOpen || !childGraph?.child) return;
        if (!reagraphGraph.nodes.length) return;

        let dollyTimer: ReturnType<typeof setTimeout> | undefined;
        const timerFit = setTimeout(() => {
            try {
                reagraphRef.current?.fitNodesInView?.(undefined, {
                    animated: true,
                    fitOnlyIfNodesNotInView: false,
                });
                if (graphMode === 'reagraph3d') {
                    dollyTimer = setTimeout(() => {
                        try {
                            reagraphRef.current?.dollyOut?.(REAGRAPH_3D_POST_FIT_DOLLY_OUT);
                        } catch {
                            // ignore
                        }
                    }, 450);
                }
            } catch {
                // ignore
            }
        }, 400);

        return () => {
            clearTimeout(timerFit);
            if (dollyTimer) clearTimeout(dollyTimer);
        };
    }, [isGraphModalOpen, childGraph, graphMode, reagraphGraph]);

    // Reagraph 캔버스에 컨테이너 실측 크기 전달
    useLayoutEffect(() => {
        if (!isGraphModalOpen || !childGraph || isGraphLoading) return;
        const el = graphWrapRef.current;
        if (!el) return;

        const update = () => {
            const { width, height } = el.getBoundingClientRect();
            setGraphSize({
                width: Math.max(1, Math.floor(width)),
                height: Math.max(1, Math.floor(height)),
            });
        };
        update();
        const ro = new ResizeObserver(update);
        ro.observe(el);
        return () => ro.disconnect();
    }, [isGraphModalOpen, childGraph, isGraphLoading, graphMode]);

    const handleStatusChange = async (
        nextStatus: 'ACKNOWLEDGED' | 'IN_REVIEW' | 'RESOLVED' | 'DISMISSED' | 'ESCALATED',
    ) => {
        if (!Number.isFinite(id) || id <= 0) return;
        if (!user || user.id === '') {
            console.warn('로그인한 사용자 정보가 없습니다.');
            return;
        }

        const reviewDto: CreateEventReviewDTO = {
            event_id: id,
            user_id: user.id,
            from_status: latestStatus ?? null,
            result_status: nextStatus,
            comment: memo.trim() || null,
        };

        const detectionUpdateDto = {
            status: nextStatus,
        } as const;

        try {
            await Promise.all([
                createEventReview(reviewDto),
                updateDetectionEvent(id, detectionUpdateDto),
            ]);
            // 상세 정보 전체 재조회
            await reload();
            const refreshed = await getLatestEventReview(id);
            setLatestReview(refreshed);
            setRefreshKey((prev) => prev + 1);
        } catch (e) {
            console.error('상태 변경 처리 실패:', e);
        }
    };

    if (loading) {
        return <div className="min-h-screen bg-gray-50 p-5 text-center text-gray-500">불러오는 중입니다.</div>;
    }

    return (
        <div className="min-h-screen bg-gray-50 px-6 py-8">
            <main className="mx-auto w-full max-w-6xl" style={{ zoom: 1 }}>
                <div className="rounded-2xl bg-white p-8 shadow-lg">
                    <div className="mb-4 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                            <AlertTriangle className="h-7 w-7 text-[#d97706]" />
                            <h2 className="text-3xl text-slate-900">이상 탐지 상세 정보</h2>
                        </div>
                        <Link
                            href="/detectionEvents"
                            className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-4 py-2 text-sm font-medium text-slate-700 shadow-sm hover:bg-slate-100 hover:border-slate-300"
                        >
                            <ClipboardList className="h-4 w-4 text-slate-600" />
                            목록으로
                        </Link>
                    </div>

                    {error && <p className="mb-3 rounded-lg bg-red-50 p-2 text-sm text-red-600">{error}</p>}

                    {!error && !detail && (
                        <p className="rounded-lg bg-slate-50 p-3 text-sm text-slate-600">이벤트 정보를 찾을 수 없습니다.</p>
                    )}

                    {!error && detail && (
                        <div className="space-y-6">
                            {/* 처리 프로세스 플로우 */}
                            <EventReviewFlow key={refreshKey} eventId={id} />

                            {/* 기본 정보 */}
                            <section>
                                <h3 className="mb-3 text-lg font-semibold text-slate-900">기본 정보</h3>
                                <div className="overflow-hidden rounded border border-slate-200">
                                    <div className="grid grid-cols-4 text-sm">
                                        {/* 1행 */}
                                        <div className="border-b border-r border-slate-200 bg-slate-50 px-4 py-2.5 font-medium text-slate-700">
                                            유치원명
                                        </div>
                                        <div className="border-b border-r border-slate-200 px-4 py-2.5 text-slate-800">
                                            {detail.kindergartenName ?? '-'}
                                        </div>
                                        <div className="border-b border-r border-slate-200 bg-slate-50 px-4 py-2.5 font-medium text-slate-700">
                                            교실명
                                        </div>
                                        <div className="border-b border-slate-200 px-4 py-2.5 text-slate-800">
                                            {detail.roomName ?? '-'}
                                        </div>

                                        {/* 2행 */}
                                        <div className="border-b border-r border-slate-200 bg-slate-50 px-4 py-2.5 font-medium text-slate-700">
                                            사건 유형
                                        </div>
                                        <div className="border-b border-r border-slate-200 px-4 py-2.5 text-slate-800">
                                            {eventTypeLabel ?? detail.eventType ?? '이벤트 유형 미지정'}
                                        </div>
                                        <div className="border-b border-r border-slate-200 bg-slate-50 px-4 py-2.5 font-medium text-slate-700">
                                            탐지 발생 일시
                                        </div>
                                        <div className="border-b border-slate-200 px-4 py-2.5 text-slate-800">
                                            {formatDateTime(detail.detectedAt)}
                                        </div>

                                        {/* 3행 */}
                                        <div className="border-r border-slate-200 bg-slate-50 px-4 py-2.5 font-medium text-slate-700">
                                            사건 시작 시간
                                        </div>
                                        <div className="border-r border-slate-200 px-4 py-2.5 text-slate-800">
                                            {formatDateTime(detail.startTime)}
                                        </div>
                                        <div className="border-r border-slate-200 bg-slate-50 px-4 py-2.5 font-medium text-slate-700">
                                            사건 종료 시간
                                        </div>
                                        <div className="px-4 py-2.5 text-slate-800">
                                            {formatDateTime(detail.endTime)}
                                        </div>
                                    </div>
                                </div>
                            </section>

                            {/* 위험도 분석 */}
                            <section>
                                <h3 className="mb-3 text-lg font-semibold text-slate-900">위험도 분석</h3>
                                <div className="rounded-xl border border-[#cfe0ff] bg-[#f7fbff] p-4">
                                    <div className="mb-2 flex items-center justify-between text-sm text-slate-700">
                    <span>
                      심각도 (Level {detail.severity ?? '-'}/10)
                    </span>
                                    </div>
                                    <div className="mb-2 flex gap-2">
                                        {Array.from({ length: 10 }).map((_, idx) => {
                                            const level = (detail.severity ?? 0) || 0;
                                            const isCurrent = level > 0 && idx === level - 1;

                                            // 피그마 팔레트: 빨강 3개, 연빨강 2개, 노랑 2개, 연초록 3개
                                            const baseColors = [
                                                '#f44336',
                                                '#f97373',
                                                '#fbb6b6',
                                                '#ffe0e0',
                                                '#ffecec',
                                                '#ffe6a3',
                                                '#ffefb3',
                                                '#b8f5d0',
                                                '#a8f0c5',
                                                '#a0ebbf',
                                            ];

                                            const backgroundColor = baseColors[idx] ?? '#ffffff';

                                            if (!isCurrent) {
                                                return (
                                                    <span
                                                        // eslint-disable-next-line react/no-array-index-key
                                                        key={idx}
                                                        className="h-6 w-6 rounded-full border border-transparent shadow-sm"
                                                        style={{ backgroundColor }}
                                                    />
                                                );
                                            }

                                            return (
                                                <span
                                                    // eslint-disable-next-line react/no-array-index-key
                                                    key={idx}
                                                    className="relative flex h-6 w-6 items-center justify-center"
                                                >
                          <span className="absolute inline-flex h-7 w-7 rounded-full bg-red-500 opacity-40 animate-ping" />
                          <span
                              className="relative h-6 w-6 rounded-full border border-transparent shadow-sm"
                              style={{ backgroundColor }}
                          />
                        </span>
                                            );
                                        })}
                                    </div>

                                    {detail.severity != null && detail.severity > 0 && (
                                        <p className="mb-3 flex items-center gap-2 text-xs text-slate-700">
                                            <span className="inline-block h-3 w-3 rounded-full bg-red-600" />
                                            <span>긴급상황 - 즉시 대응 필요</span>
                                        </p>
                                    )}

                                    <div className="mb-2 flex items-center justify-between text-sm text-slate-700">
                                        <span>신뢰도</span>
                                        <span className="text-xs text-emerald-700">확정 수준</span>
                                    </div>
                                    <div className="h-5 w-full overflow-hidden rounded-full bg-slate-200">
                                        {(() => {
                                            const percent =
                                                detail.confidence == null
                                                    ? 0
                                                    : Math.round(Math.max(0, Math.min(1, detail.confidence)) * 100);
                                            const barColorClass = percent > 80 ? 'bg-emerald-500' : 'bg-yellow-400';
                                            return (
                                                <div
                                                    className={`flex h-full items-center justify-end rounded-full pr-3 text-xs font-semibold text-white ${barColorClass}`}
                                                    style={{ width: `${percent}%` }}
                                                >
                                                    {percent > 0 ? `${percent}%` : ''}
                                                </div>
                                            );
                                        })()}
                                    </div>
                                </div>
                            </section>

                            {/* 비상 연락 · 아이 관련자 찾기 */}
                            <section>
                                <h3 className="mb-3 text-lg font-semibold text-slate-900">
                                    비상 연락-아이 관련자 찾기
                                </h3>
                                <div className="overflow-hidden rounded-lg border border-slate-200">
                                    <table className="w-full table-fixed border-collapse text-sm">
                                        <tbody>
                                            <tr>
                                                <td className="w-1/2 align-middle border-r border-slate-200 p-3">
                                                    <div className="flex min-w-0 items-center gap-2">
                                                        <input
                                                            type="text"
                                                            value={childNameKeyword}
                                                            onChange={(e) => setChildNameKeyword(e.target.value)}
                                                            onKeyDown={(e) => {
                                                                if (e.key === 'Enter') {
                                                                    e.preventDefault();
                                                                    submitChildSearch();
                                                                }
                                                            }}
                                                            placeholder="아이 이름"
                                                            aria-label="아이 이름"
                                                            className="min-w-0 flex-1 rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-transparent focus:outline-none focus:ring-2 focus:ring-emerald-500"
                                                        />
                                                        <button
                                                            type="button"
                                                            onClick={submitChildSearch}
                                                            className="inline-flex shrink-0 items-center justify-center rounded-full border border-emerald-500 px-3 py-1.5 text-xs font-medium text-emerald-700 hover:bg-emerald-50"
                                                        >
                                                            <Search className="mr-1 h-3.5 w-3.5" aria-hidden />
                                                            아이 찾기
                                                        </button>
                                                    </div>
                                                </td>
                                                <td className="w-1/2 align-middle p-3 text-[1.2rem] leading-snug text-slate-500">
                                                    {selectedChild ? (
                                                        <span>
                                                            <button
                                                                type="button"
                                                                onClick={() => void openGraphModal()}
                                                                className="font-semibold text-slate-800 underline-offset-2 hover:underline"
                                                            >
                                                                {selectedChild.name}
                                                            </button>{' '}
                                                            <span className="text-slate-400">
                                                                (ID: {selectedChild.childId}, 원아번호:{' '}
                                                                {selectedChild.childNo ?? '없음'})
                                                            </span>
                                                        </span>
                                                    ) : (
                                                        <span>처리 대상 아이를 선택하면 여기 표시됩니다.</span>
                                                    )}
                                                </td>
                                            </tr>
                                        </tbody>
                                    </table>
                                </div>
                            </section>

                            {/* 처리 메모 */}
                            <section>
                                <h3 className="mb-3 text-lg font-semibold text-slate-900">처리 메모</h3>
                                <textarea
                                    className="h-28 w-full resize-none rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm leading-relaxed text-slate-700"
                                    placeholder="처리 내용이나 특이사항을 입력하세요..."
                                    value={memo}
                                    onChange={(e) => setMemo(e.target.value)}
                                    readOnly={!hasAnyAction}
                                />
                            </section>

                            {/* 상태 변경 (UI만, 동작 없음) */}
                            <section>
                                <div className="flex flex-wrap justify-center gap-3">
                                    {showConfirm && (
                                        <button
                                            type="button"
                                            className="inline-flex items-center gap-2 rounded-full border border-slate-300 bg-white px-5 py-2 text-sm text-slate-700 shadow-sm"
                                            onClick={() => handleStatusChange('ACKNOWLEDGED')}
                                        >
                                            <CheckSquare className="h-4 w-4 text-emerald-600" />
                                            <span>확인</span>
                                        </button>
                                    )}
                                    {showReview && (
                                        <button
                                            type="button"
                                            className="inline-flex items-center gap-2 rounded-full border border-slate-300 bg-white px-5 py-2 text-sm text-slate-700 shadow-sm"
                                            onClick={() => handleStatusChange('IN_REVIEW')}
                                        >
                                            <Search className="h-4 w-4 text-slate-700" />
                                            <span>검토</span>
                                        </button>
                                    )}
                                    {showResolve && (
                                        <button
                                            type="button"
                                            className="inline-flex items-center gap-2 rounded-full border border-slate-300 bg-white px-5 py-2 text-sm text-slate-700 shadow-sm"
                                            onClick={() => handleStatusChange('RESOLVED')}
                                        >
                                            <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                                            <span>완료</span>
                                        </button>
                                    )}
                                    {showDismiss && (
                                        <button
                                            type="button"
                                            className="inline-flex items-center gap-2 rounded-full border border-slate-300 bg-white px-5 py-2 text-sm text-slate-700 shadow-sm"
                                            onClick={() => handleStatusChange('DISMISSED')}
                                        >
                                            <XCircle className="h-4 w-4 text-red-500" />
                                            <span>오탐</span>
                                        </button>
                                    )}
                                    {showEscalate && (
                                        <button
                                            type="button"
                                            className="inline-flex items-center gap-2 rounded-full border border-slate-300 bg-white px-5 py-2 text-sm text-slate-700 shadow-sm"
                                            onClick={() => handleStatusChange('ESCALATED')}
                                        >
                                            <Megaphone className="h-4 w-4 text-red-500" />
                                            <span>보고</span>
                                        </button>
                                    )}
                                </div>
                            </section>
                        </div>
                    )}
                </div>
            </main>

            {isChildModalOpen && (
                <div className="fixed inset-0 z-50 overflow-y-auto bg-black/50">
                    <div className="flex min-h-full items-center justify-center p-4 sm:p-6">
                        <div
                            role="dialog"
                            aria-modal="true"
                            aria-labelledby="child-search-modal-title"
                            className="my-4 w-full max-h-[min(90vh,calc(100vh-2rem))] max-w-5xl overflow-y-auto rounded-2xl border border-slate-200 bg-white p-5 shadow-2xl sm:my-8 sm:p-6"
                            style={{ zoom: 1 }}
                        >
                        <div className="mb-3 flex items-center justify-between">
                            <h2 id="child-search-modal-title" className="text-lg font-semibold text-slate-900">
                                아이 이름으로 찾기
                            </h2>
                            <button
                                type="button"
                                onClick={closeChildSearchModal}
                                className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-100"
                            >
                                닫기
                            </button>
                        </div>

                        <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:items-center">
                            <input
                                type="text"
                                value={childNameKeyword}
                                onChange={(e) => setChildNameKeyword(e.target.value)}
                                onKeyDown={(e) => {
                                    if (e.key === 'Enter') {
                                        e.preventDefault();
                                        void handleChildSearch();
                                    }
                                }}
                                className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-base text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
                                placeholder="아이 이름을 입력하세요"
                            />
                            <button
                                type="button"
                                onClick={() => void handleChildSearch()}
                                disabled={isChildSearching}
                                className="inline-flex items-center justify-center rounded-lg bg-emerald-600 px-5 py-2 text-base font-medium text-white hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-60 sm:ml-2"
                            >
                                {isChildSearching ? '검색 중...' : '검색'}
                            </button>
                        </div>

                        {childSearchError && (
                            <p className="mb-1.5 text-xs text-amber-600">
                                {childSearchError}
                            </p>
                        )}

                        <div className="max-h-80 min-h-[11rem] overflow-auto rounded-lg border border-slate-200">
                            <table className="min-w-full text-base text-slate-700">
                                <thead className="bg-slate-100 text-sm uppercase text-slate-600">
                                <tr>
                                    <th className="px-3 py-1 text-left">ID</th>
                                    <th className="px-3 py-1 text-left">이름</th>
                                    <th className="px-3 py-1 text-left">원아번호</th>
                                    <th className="px-3 py-1 text-left">생년월일</th>
                                    <th className="px-3 py-1 text-left">성별</th>
                                    <th className="px-3 py-1 text-left">선택</th>
                                </tr>
                                </thead>
                                <tbody>
                                {childSearchResults.map((child) => (
                                    <tr key={child.childId} className="border-t border-slate-200">
                                        <td className="px-3 py-1">{child.childId}</td>
                                        <td className="px-3 py-1">{child.name}</td>
                                        <td className="px-3 py-1">{child.childNo ?? '-'}</td>
                                        <td className="px-3 py-1">{child.birthDate ?? '-'}</td>
                                        <td className="px-3 py-1">{child.gender ?? '-'}</td>
                                        <td className="px-3 py-1">
                                            <button
                                                type="button"
                                                onClick={() => handleSelectChild(child)}
                                                className="rounded-md bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-500"
                                            >
                                                선택
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                                {childSearchResults.length === 0 && !isChildSearching && childSearchError && (
                                    <tr>
                                        <td className="h-28 px-3 py-6 align-middle text-center text-slate-500" colSpan={6}>
                                            검색 결과가 없습니다.
                                        </td>
                                    </tr>
                                )}
                                {childSearchResults.length === 0 && !isChildSearching && !childSearchError && (
                                    <tr>
                                        <td className="px-3 py-3 text-center text-slate-500" colSpan={6}>
                                            아직 검색 결과가 없습니다. 아이 이름을 입력하고 검색을 눌러주세요.
                                        </td>
                                    </tr>
                                )}
                                </tbody>
                            </table>
                        </div>
                        </div>
                    </div>
                </div>
            )}



            {isGraphModalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4 py-3">
                    <div className="max-h-[90vh] w-full max-w-4xl overflow-y-auto rounded-2xl border border-slate-200 bg-white p-3 shadow-2xl">
                        <div className="mb-2 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                            <div className="flex flex-wrap items-center gap-3">
                                <h2 className="text-lg font-semibold text-slate-900">
                                    {selectedChild?.name
                                        ? `${selectedChild.name} 관련자`
                                        : '아이 관계 그래프'}
                                </h2>
                                <button
                                    type="button"
                                    title="비상연락 전송"
                                    aria-label="비상연락 전송"
                                    className="inline-flex shrink-0 items-center justify-center rounded-full border-2 border-emerald-500 bg-white px-6 py-3 text-sm font-medium text-emerald-700 shadow-sm hover:bg-emerald-50"
                                    onClick={() => {
                                        /* 비상연락 전송 API 연동 시 구현 */
                                    }}
                                >
                                    <Send className="mr-2 h-7 w-7 shrink-0" aria-hidden />
                                    비상연락 전송
                                </button>
                            </div>
                            <div className="flex flex-wrap items-center gap-1 text-xs text-slate-600 sm:gap-1.5">
                                <div className="flex flex-wrap gap-1 rounded-full border border-slate-200 bg-slate-50 p-0.5">
                                    {(
                                        [
                                            {
                                                mode: 'reagraph2d' as const,
                                                label: 'Reagraph 2D',
                                                hint: 'Reagraph 2D',
                                            },
                                            {
                                                mode: 'reagraph3d' as const,
                                                label: 'Reagraph 3D',
                                                hint: 'Reagraph 3D',
                                            },
                                        ] as const
                                    ).map(({ mode, label, hint }) => (
                                        <button
                                            key={mode}
                                            type="button"
                                            title={hint}
                                            onClick={() => setGraphMode(mode)}
                                            className={`rounded-full px-2.5 py-1 text-[11px] font-medium ${
                                                graphMode === mode
                                                    ? 'bg-slate-900 text-white shadow-sm'
                                                    : 'text-slate-700 hover:bg-slate-100'
                                            }`}
                                        >
                                            {label}
                                        </button>
                                    ))}
                                </div>
                                <button
                                    type="button"
                                    onClick={closeGraphModal}
                                    className="rounded-md border border-slate-300 px-3 py-1 text-xs text-slate-700 hover:bg-slate-100"
                                >
                                    닫기
                                </button>
                            </div>
                        </div>

                        {isGraphLoading && (
                            <p className="text-sm text-slate-600">관계 정보를 불러오는 중입니다...</p>
                        )}

                        {!isGraphLoading && graphError && (
                            <p className="text-sm text-amber-600">{graphError}</p>
                        )}

                        {!isGraphLoading && !graphError && childGraph && (
                            <div
                                ref={graphWrapRef}
                                className="relative w-full min-w-0 overflow-hidden rounded-lg bg-slate-50"
                                style={{
                                    minHeight: GRAPH_MODAL_VIEWPORT_HEIGHT_PX,
                                    height: GRAPH_MODAL_VIEWPORT_HEIGHT_PX,
                                }}
                            >
                                <div
                                    className="absolute inset-0 z-0 min-w-0 overflow-hidden"
                                    style={
                                        graphSize.width > 0 && graphSize.height > 0
                                            ? {
                                                  width: graphSize.width,
                                                  height: graphSize.height,
                                              }
                                            : { width: '100%', height: '100%' }
                                    }
                                >
                                    <Suspense
                                        fallback={
                                            <div className="flex h-full min-h-[min(520px,70vh)] w-full items-center justify-center text-sm text-slate-500">
                                                그래프를 불러오는 중…
                                            </div>
                                        }
                                    >
                                        <GraphCanvas
                                            ref={reagraphRef}
                                            theme={reagraphTheme}
                                            renderNode={reagraphRenderNode}
                                            layoutType={
                                                graphMode === 'reagraph2d'
                                                    ? 'forceDirected2d'
                                                    : 'forceDirected3d'
                                            }
                                            cameraMode={graphMode === 'reagraph2d' ? 'pan' : 'orbit'}
                                            nodes={reagraphGraph.nodes}
                                            edges={reagraphGraph.edges}
                                            animated
                                            labelType="edges"
                                            sizingType="none"
                                            defaultNodeSize={CHILD_NODE_SIZE}
                                            minNodeSize={CHILD_NODE_SIZE}
                                            maxNodeSize={CHILD_NODE_SIZE}
                                            edgeArrowPosition="end"
                                            edgeInterpolation="curved"
                                            edgeLabelPosition="inline"
                                            minDistance={graphMode === 'reagraph3d' ? 80 : undefined}
                                            maxDistance={graphMode === 'reagraph3d' ? 1600 : undefined}
                                        />
                                    </Suspense>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            )}



        </div>
    );
}

