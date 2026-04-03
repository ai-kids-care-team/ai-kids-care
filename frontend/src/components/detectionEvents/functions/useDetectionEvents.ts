/* eslint-disable @typescript-eslint/no-misused-promises */
'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  DETECTION_EVENTS_LIST_PAGE_SIZE,
  GetDetectionEventsParams,
  getDetectionEvents,
  type DetectionEventListItem,
} from '@/services/apis/detectionEvents.api';
import { getParentCommonCodeList } from '@/services/apis/commonCodes.api';
import { useAppSelector } from '@/store/hook';
import { rolePermissions } from '@/types/user-role';
import type { DetectionEventItem } from '@/types/detectionEvents';

const normalizeCodeKey = (value: string | null | undefined): string =>
  (value ?? '')
    .trim()
    .toLowerCase()
    .replace(/[\s_-]+/g, '');

function formatDateTime(value: string | null): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  const hh = String(date.getHours()).padStart(2, '0');
  const mi = String(date.getMinutes()).padStart(2, '0');
  const ss = String(date.getSeconds()).padStart(2, '0');
  return `${yyyy}.${mm}.${dd} ${hh}:${mi}:${ss}`;
}

function formatDateTimeNoSeconds(value: string | null): string {
  // 사건시간 표기용: 초는 제거 (yyyy.mm.dd hh:mm)
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  const hh = String(date.getHours()).padStart(2, '0');
  const mi = String(date.getMinutes()).padStart(2, '0');
  return `${yyyy}.${mm}.${dd} ${hh}:${mi}`;
}

function buildListItem(
  vo: DetectionEventListItem,
  eventTypeCodeNameMap: Record<string, string>,
  statusCodeNameMap: Record<string, string>,
): DetectionEventItem {
  const confidence = vo.confidence;
  const normalizedEventTypeCode = normalizeCodeKey(vo.eventType);
  const mappedEventTypeName = normalizedEventTypeCode ? eventTypeCodeNameMap[normalizedEventTypeCode] : undefined;
  const normalizedStatusCode = normalizeCodeKey(vo.status);
  const mappedStatusName = normalizedStatusCode ? statusCodeNameMap[normalizedStatusCode] : undefined;

  return {
    id: vo.eventId,
    kindergartenId: vo.kindergartenId,
    kindergartenName: vo.kindergartenName,
    cameraId: vo.cameraId,
    cameraName: vo.cameraName,
    roomId: vo.roomId,
    roomName: vo.roomName,
    sessionId: vo.sessionId,
    eventType: vo.eventType,
    eventTypeName: mappedEventTypeName ?? null,
    severity: vo.severity,
    confidence,
    detectedAt: formatDateTime(vo.detectedAt),
    startTime: formatDateTimeNoSeconds(vo.startTime),
    endTime: formatDateTimeNoSeconds(vo.endTime),
    status: vo.status,
    statusName: mappedStatusName ?? null,
    createdAt: vo.createdAt,
    updatedAt: vo.updatedAt,
    href: `/detectionEvents/read?id=${vo.eventId}`,
  };
}

export function useDetectionEvents() {
  const { user, token, isAuthenticated } = useAppSelector((state) => state.user);
  const [events, setEvents] = useState<DetectionEventItem[]>([]);
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [eventTypeCodeNameMap, setEventTypeCodeNameMap] = useState<Record<string, string>>({});
  const [statusCodeNameMap, setStatusCodeNameMap] = useState<Record<string, string>>({});

  const canWrite = useMemo(() => {
    if (!isAuthenticated || !user || !token) return false;
    const perms = rolePermissions[user.role];
    return Boolean(perms?.canResolveAnomaly);
  }, [isAuthenticated, user, token]);

  useEffect(() => {
    const loadCodeMaps = async () => {
      try {
        const [eventTypeCodes, statusCodes] = await Promise.all([
          getParentCommonCodeList('detection_events', 'event_type'),
          getParentCommonCodeList('detection_events', 'status'),
        ]);

        const eventTypeMap = eventTypeCodes.reduce<Record<string, string>>((acc, item) => {
          const normalizedCode = normalizeCodeKey(item.code);
          const normalizedCodeName = (item.codeName ?? item.code_name)?.trim();
          if (normalizedCode && normalizedCodeName) {
            acc[normalizedCode] = normalizedCodeName;
          }
          return acc;
        }, {});

        const statusMap = statusCodes.reduce<Record<string, string>>((acc, item) => {
          const normalizedCode = normalizeCodeKey(item.code);
          const normalizedCodeName = (item.codeName ?? item.code_name)?.trim();
          if (normalizedCode && normalizedCodeName) {
            acc[normalizedCode] = normalizedCodeName;
          }
          return acc;
        }, {});

        setEventTypeCodeNameMap(eventTypeMap);
        setStatusCodeNameMap(statusMap);
      } catch (e) {
        console.error('이벤트 유형/상태 공통코드 조회 실패:', e);
      }
    };

    void loadCodeMaps();
  }, []);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError('');

      try {
        const params: GetDetectionEventsParams = {
          keyword: appliedKeyword || undefined,
          page,
          size: DETECTION_EVENTS_LIST_PAGE_SIZE,
        };

        const pageData = await getDetectionEvents(params);
        setTotalPages(pageData.totalPages);
        setEvents(pageData.content.map((item) => buildListItem(item, eventTypeCodeNameMap, statusCodeNameMap)));
      } catch (e) {
        console.error('탐지 이벤트 목록 조회 실패:', e);
        setError('탐지 이벤트 목록을 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [appliedKeyword, page, eventTypeCodeNameMap, statusCodeNameMap]);

  const handleSearch = () => {
    setAppliedKeyword(keyword.trim());
    setPage(0);
  };

  return {
    events,
    keyword,
    setKeyword,
    handleSearch,
    canWrite,
    loading,
    error,
    page,
    totalPages,
    setPage,
  };
}

