'use client';

import { X } from 'lucide-react';
import { ScrollArea } from '@/components/shared/ui/scroll-area';
import { PushSubscriptionManager } from '@/components/notifications/PushSubscriptionManager';

interface SettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
}

/**
 * 개인설정 모달.
 *
 * 우상단 사용자 드롭다운의 「개인설정」 진입점이 연다. 현재는 본인 PUSH(Pushover)
 * 구독 관리(`PushSubscriptionManager`)를 담는다 — 홈 화면이 아니라 여기서만 노출한다.
 * 향후 설정 항목(비밀번호 변경 등)이 늘면 이 모달 안에 카드로 추가한다.
 *
 * 손수 만든 모달 패턴은 `LoginModal` 과 동일하다(고정 오버레이 + 배경 클릭 닫기 + X 버튼).
 */
export function SettingsModal({ isOpen, onClose }: SettingsModalProps) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/30 backdrop-blur-sm" onClick={onClose} />

      <div className="relative w-full max-w-lg rounded-2xl bg-white p-8 shadow-2xl animate-in fade-in zoom-in duration-200">
        <button
          onClick={onClose}
          className="absolute right-4 top-4 rounded-full p-2 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600"
          aria-label="개인설정 모달 닫기"
        >
          <X className="h-6 w-6" />
        </button>

        <div className="mb-6">
          <h2 className="text-2xl">개인설정</h2>
        </div>

        <ScrollArea className="max-h-[70vh] pr-2">
          <PushSubscriptionManager />
        </ScrollArea>
      </div>
    </div>
  );
}
