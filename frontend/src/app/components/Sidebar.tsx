import { Shield, User } from 'lucide-react';
import { Card } from './ui/card';
import { Badge } from './ui/badge';
import type { UserRole } from '../types/anomaly';
import { roleLabels } from '../types/anomaly';

interface SidebarProps {
  currentRole: UserRole;
  userName: string;
  cameraStats: {
    total: number;
    online: number;
    offline: number;
  };
  onCategoryFilter?: (category: 'all' | 'entrance' | 'classroom' | 'playground' | 'corridor' | 'office' | 'parking') => void;
  currentCategory?: 'all' | 'entrance' | 'classroom' | 'playground' | 'corridor' | 'office' | 'parking';
}

const roleColors: Record<UserRole, string> = {
  'super_admin': 'bg-purple-600',
  'system_admin': 'bg-indigo-600',
  'admin': 'bg-blue-600',
  'teacher': 'bg-green-600',
  'guardian': 'bg-orange-600'
};

export function Sidebar({ currentRole, userName, cameraStats, onCategoryFilter, currentCategory = 'all' }: SidebarProps) {
  return (
    <div className="w-64 bg-white border-r border-gray-200 h-full flex flex-col">
      {/* Header */}
      <div className="p-4 border-b border-gray-200">
        <div className="flex items-center gap-2 mb-1">
          <div className="w-8 h-8 bg-purple-600 rounded-lg flex items-center justify-center">
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path
                d="M12 6C8.5 6 5.5 8 4 11c1.5 3 4.5 5 8 5s6.5-2 8-5c-1.5-3-4.5-5-8-5z"
                fill="white"
              />
              <circle cx="12" cy="11" r="2.5" fill="#7C3AED" />
            </svg>
          </div>
          <div>
            <h2 className="font-semibold text-sm text-gray-900">CCTV 관리</h2>
            <p className="text-xs text-gray-500">모니터링 시스템</p>
          </div>
        </div>
      </div>

      {/* User Info */}
      <div className="p-4 border-b border-gray-200">
        <h3 className="text-xs font-semibold text-gray-500 uppercase mb-3">로그인 정보</h3>
        <Card className={`p-3 ${roleColors[currentRole]} text-white`}>
          <div className="flex items-center gap-3 mb-2">
            <div className="w-10 h-10 bg-white/20 rounded-full flex items-center justify-center">
              {currentRole === 'super_admin' || currentRole === 'system_admin' || currentRole === 'admin' ? (
                <Shield className="w-5 h-5" />
              ) : (
                <User className="w-5 h-5" />
              )}
            </div>
            <div>
              <p className="font-semibold text-sm">{userName}</p>
              <p className="text-xs opacity-90">{roleLabels[currentRole]}</p>
            </div>
          </div>
          <div className="pt-2 border-t border-white/20">
            <p className="text-xs opacity-75">권한 레벨</p>
            <p className="text-xs font-medium">{roleLabels[currentRole]}</p>
          </div>
        </Card>
      </div>

      {/* Camera Stats */}
      <div className="p-4 border-b border-gray-200">
        <h3 className="text-xs font-semibold text-gray-500 uppercase mb-3">카메라 현황</h3>
        <Card className="p-3 bg-gray-50">
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs text-gray-600">전체</span>
              <Badge variant="secondary">{cameraStats.total}대</Badge>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-gray-600">온라인</span>
              <Badge className="bg-green-500 hover:bg-green-600">{cameraStats.online}대</Badge>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-gray-600">오프라인</span>
              <Badge variant="destructive">{cameraStats.offline}대</Badge>
            </div>
          </div>
        </Card>
      </div>

      {/* Category Filter */}
      <div className="p-4 flex-1 overflow-y-auto">
        <h3 className="text-xs font-semibold text-gray-500 uppercase mb-3">커버리 목록</h3>
        <div className="space-y-2">
          <button
            onClick={() => onCategoryFilter?.('all')}
            className={`w-full flex items-center justify-between px-3 py-2 rounded-lg transition-colors ${
              currentCategory === 'all'
                ? 'bg-purple-50 border border-purple-200'
                : 'bg-gray-50 hover:bg-gray-100'
            }`}
          >
            <span className={`text-sm font-medium ${
              currentCategory === 'all' ? 'text-purple-900' : 'text-gray-700'
            }`}>
              전체
            </span>
            <Badge className={currentCategory === 'all' ? 'bg-purple-600 hover:bg-purple-700' : 'bg-gray-500'}>
              {cameraStats.total}
            </Badge>
          </button>
          <button
            onClick={() => onCategoryFilter?.('classroom')}
            className={`w-full flex items-center justify-between px-3 py-2 rounded-lg transition-colors ${
              currentCategory === 'classroom'
                ? 'bg-purple-50 border border-purple-200'
                : 'bg-gray-50 hover:bg-gray-100'
            }`}
          >
            <span className={`text-sm ${
              currentCategory === 'classroom' ? 'text-purple-900 font-medium' : 'text-gray-700'
            }`}>
              교실
            </span>
            <Badge variant="secondary" className="bg-green-100 text-green-700">3</Badge>
          </button>
          <button
            onClick={() => onCategoryFilter?.('playground')}
            className={`w-full flex items-center justify-between px-3 py-2 rounded-lg transition-colors ${
              currentCategory === 'playground'
                ? 'bg-purple-50 border border-purple-200'
                : 'bg-gray-50 hover:bg-gray-100'
            }`}
          >
            <span className={`text-sm ${
              currentCategory === 'playground' ? 'text-purple-900 font-medium' : 'text-gray-700'
            }`}>
              놀이터
            </span>
            <Badge variant="secondary" className="bg-green-100 text-green-700">2</Badge>
          </button>
          <button
            onClick={() => onCategoryFilter?.('entrance')}
            className={`w-full flex items-center justify-between px-3 py-2 rounded-lg transition-colors ${
              currentCategory === 'entrance'
                ? 'bg-purple-50 border border-purple-200'
                : 'bg-gray-50 hover:bg-gray-100'
            }`}
          >
            <span className={`text-sm ${
              currentCategory === 'entrance' ? 'text-purple-900 font-medium' : 'text-gray-700'
            }`}>
              출입구
            </span>
            <Badge variant="secondary" className="bg-orange-100 text-orange-700">2</Badge>
          </button>
          <button
            onClick={() => onCategoryFilter?.('corridor')}
            className={`w-full flex items-center justify-between px-3 py-2 rounded-lg transition-colors ${
              currentCategory === 'corridor'
                ? 'bg-purple-50 border border-purple-200'
                : 'bg-gray-50 hover:bg-gray-100'
            }`}
          >
            <span className={`text-sm ${
              currentCategory === 'corridor' ? 'text-purple-900 font-medium' : 'text-gray-700'
            }`}>
              복도
            </span>
            <Badge variant="secondary">2</Badge>
          </button>
          <button
            onClick={() => onCategoryFilter?.('office')}
            className={`w-full flex items-center justify-between px-3 py-2 rounded-lg transition-colors ${
              currentCategory === 'office'
                ? 'bg-purple-50 border border-purple-200'
                : 'bg-gray-50 hover:bg-gray-100'
            }`}
          >
            <span className={`text-sm ${
              currentCategory === 'office' ? 'text-purple-900 font-medium' : 'text-gray-700'
            }`}>
              사무실/기타
            </span>
            <Badge variant="secondary">3</Badge>
          </button>
        </div>
      </div>

      {/* Demo Role Switcher (개발/테스트용) */}
      <div className="p-4 border-t border-gray-200 bg-gray-50">
        <p className="text-xs text-gray-500 text-center mb-2">🔧 데모용 역할 전환</p>
        <details className="text-xs">
          <summary className="cursor-pointer text-purple-600 hover:text-purple-700 font-medium">
            다른 역할로 테스트
          </summary>
          <div className="mt-2 text-gray-600 space-y-1">
            <p>• 실제 운영시 이 기능은</p>
            <p className="ml-2">로그인 정보로 대체됩니다</p>
          </div>
        </details>
      </div>
    </div>
  );
}
