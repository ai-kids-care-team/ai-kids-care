'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { CctvCameraVO } from '@/types/cctv.vo';
import { displayLocationLine } from '@/lib/cctvFormat';
import { resolveRealCameraTileEmbedUrl } from '@/components/cctv/gridSlotResolvers';

export type UseQuickPlaylistResult = {
  quickPlaylistIndex: number | null;
  setQuickPlaylistIndex: (index: number | null) => void;
  playlistFullscreenElRef: React.RefObject<HTMLDivElement | null>;
  playlistCamera: CctvCameraVO | null;
  playlistEmbedUrl: string | null;
  playlistSubLine: string;
  /** 카메라를 전용 전체화면 순회 플레이리스트로 열고, 해당 페이지로 이동한다. */
  openPlaylistAt: (index: number) => void;
  goQuickPlaylistStep: (delta: number) => void;
};

/**
 * "전체 화면 보기" 순회 플레이리스트 상태(원본 CctvDashboardPage :516-566 + 관련 effect들을
 * 통합). `filteredCameras`(현재 구역 필터 기준)가 바뀌면 인덱스를 클램프/닫고, 브라우저의
 * fullscreen 종료를 감지해 자동으로 닫는다. `itemsPerPage`/`setCurrentPage`는 그리드 페이지와
 * 플레이리스트 인덱스를 동기화하기 위해 호출측(`useCctvGridData`)에서 주입한다.
 */
export function useQuickPlaylist(
  filteredCameras: CctvCameraVO[],
  itemsPerPage: number,
  setCurrentPage: (updater: (prev: number) => number) => void,
  canViewLiveStreams: boolean,
): UseQuickPlaylistResult {
  const [quickPlaylistIndex, setQuickPlaylistIndex] = useState<number | null>(null);
  const playlistFullscreenElRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (typeof document === 'undefined') return;
    const onFullscreenChange = () => {
      const current = document.fullscreenElement as HTMLElement | null;
      // 전체 화면이 완전히 종료된 경우(어떤 요소도 fullscreen이 아닐 때)에만 순회
      // 전체화면 모드를 해제한다. 내부 플레이어(iframe 등)가 자체 fullscreen으로
      // 전환되는 경우는 그대로 유지한다.
      if (!current) {
        setQuickPlaylistIndex(null);
      }
    };
    document.addEventListener('fullscreenchange', onFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', onFullscreenChange);
  }, []);

  useEffect(() => {
    if (quickPlaylistIndex === null || filteredCameras.length > 0) return;
    void document.exitFullscreen?.();
    const timeoutId = window.setTimeout(() => setQuickPlaylistIndex(null), 0);
    return () => window.clearTimeout(timeoutId);
  }, [filteredCameras.length, quickPlaylistIndex]);

  useEffect(() => {
    if (quickPlaylistIndex === null || filteredCameras.length === 0) return;
    if (quickPlaylistIndex < filteredCameras.length) return;
    const timeoutId = window.setTimeout(() => setQuickPlaylistIndex(filteredCameras.length - 1), 0);
    return () => window.clearTimeout(timeoutId);
  }, [filteredCameras.length, quickPlaylistIndex, filteredCameras]);

  const playlistCamera = useMemo(() => {
    if (
      quickPlaylistIndex == null ||
      quickPlaylistIndex < 0 ||
      quickPlaylistIndex >= filteredCameras.length
    ) {
      return null;
    }
    return filteredCameras[quickPlaylistIndex] ?? null;
  }, [quickPlaylistIndex, filteredCameras]);

  const playlistEmbedUrl = useMemo(() => {
    if (!canViewLiveStreams || !playlistCamera || quickPlaylistIndex === null) return null;
    return resolveRealCameraTileEmbedUrl(quickPlaylistIndex);
  }, [canViewLiveStreams, playlistCamera, quickPlaylistIndex]);

  const playlistSubLine = useMemo(() => {
    if (!playlistCamera) return '—';
    return displayLocationLine(playlistCamera);
  }, [playlistCamera]);

  const openPlaylistAt = useCallback(
    (index: number) => {
      setQuickPlaylistIndex(index);
      setCurrentPage(() => Math.floor(index / itemsPerPage));
    },
    [itemsPerPage, setCurrentPage],
  );

  const goQuickPlaylistStep = useCallback(
    (delta: number) => {
      if (quickPlaylistIndex == null || filteredCameras.length === 0) return;
      const nextIdx = quickPlaylistIndex + delta;
      if (nextIdx < 0 || nextIdx >= filteredCameras.length) return;
      setQuickPlaylistIndex(nextIdx);
      setCurrentPage(() => Math.floor(nextIdx / itemsPerPage));
    },
    [quickPlaylistIndex, filteredCameras, itemsPerPage, setCurrentPage],
  );

  return {
    quickPlaylistIndex,
    setQuickPlaylistIndex,
    playlistFullscreenElRef,
    playlistCamera,
    playlistEmbedUrl,
    playlistSubLine,
    openPlaylistAt,
    goQuickPlaylistStep,
  };
}
