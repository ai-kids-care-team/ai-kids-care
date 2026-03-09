'use client'; // 👈 1. 클라이언트 컴포넌트 선언 추가

import { LogOut, Settings, UserCircle } from 'lucide-react';
import { useRouter } from 'next/navigation'; // 👈 2. Next.js 라우터로 변경
import { useAuth } from '@/app/context/AuthContext';
import { Button } from './ui/button';
import { Badge } from './ui/badge';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from './ui/dropdown-menu';
import type { UserRole } from '../types/anomaly';
import { roleLabels } from '../types/anomaly';

interface TopBarProps {
  currentRole: UserRole;
  username: string;
  onRoleChange?: (role: UserRole) => void;
}

const roles: UserRole[] = ['super_admin', 'system_admin', 'admin', 'teacher', 'guardian'];

export function TopBar({ currentRole, username, onRoleChange }: TopBarProps) {
  const router = useRouter(); // 👈 3. useNavigate 대신 useRouter 사용
  const { logout } = useAuth();

  const handleLogout = () => {
    logout();
    router.push('/login'); // 👈 4. navigate 대신 router.push 사용
  };

  return (
      <div className="bg-purple-600 text-white px-6 py-3 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <h1 className="text-lg font-semibold">햇살유치원 CCTV 관리</h1>
          <Badge className="bg-white/20 hover:bg-white/30 text-white border-white/40">
            {roleLabels[currentRole]}
          </Badge>
        </div>

        <div className="flex items-center gap-3">
          {onRoleChange && (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <button className="flex items-center gap-2 px-3 py-1.5 text-sm text-white hover:bg-white/20 rounded-md transition-colors">
                    <UserCircle className="w-4 h-4" />
                    {username}
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-56">
                  <DropdownMenuLabel>데모용 역할 전환</DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  {roles.map((role) => (
                      <DropdownMenuItem
                          key={role}
                          onClick={() => onRoleChange(role)}
                          className={currentRole === role ? 'bg-purple-50' : ''}
                      >
                        {roleLabels[role]}
                        {currentRole === role && ' ✓'}
                      </DropdownMenuItem>
                  ))}
                  <DropdownMenuSeparator />
                  <DropdownMenuLabel className="text-xs text-gray-500 font-normal">
                    실제 운영시 로그인 정보로 자동 결정됩니다
                  </DropdownMenuLabel>
                </DropdownMenuContent>
              </DropdownMenu>
          )}

          <div className="flex items-center gap-2">
            <Button variant="ghost" size="sm" className="text-white hover:bg-white/20">
              <Settings className="w-4 h-4" />
            </Button>
            <Button
                variant="ghost"
                size="sm"
                className="text-white hover:bg-white/20 gap-2"
                onClick={handleLogout}
            >
              <LogOut className="w-4 h-4" />
              로그아웃
            </Button>
          </div>
        </div>
      </div>
  );
}

// import { LogOut, Settings, UserCircle } from 'lucide-react';
// import { useNavigate } from 'react-router';
// import { useAuth } from '../contexts/AuthContext';
// import { Button } from './ui/button';
// import { Badge } from './ui/badge';
// import {
//   DropdownMenu,
//   DropdownMenuContent,
//   DropdownMenuItem,
//   DropdownMenuLabel,
//   DropdownMenuSeparator,
//   DropdownMenuTrigger,
// } from './ui/dropdown-menu';
// import type { UserRole } from '../types/anomaly';
// import { roleLabels } from '../types/anomaly';
//
// interface TopBarProps {
//   currentRole: UserRole;
//   username: string;
//   onRoleChange?: (role: UserRole) => void;
// }
//
// const roles: UserRole[] = ['super_admin', 'system_admin', 'admin', 'teacher', 'guardian'];
//
// export function TopBar({ currentRole, username, onRoleChange }: TopBarProps) {
//   const navigate = useNavigate();
//   const { logout } = useAuth();
//
//   const handleLogout = () => {
//     logout();
//     navigate('/login');
//   };
//
//   return (
//     <div className="bg-purple-600 text-white px-6 py-3 flex items-center justify-between">
//       <div className="flex items-center gap-4">
//         <h1 className="text-lg font-semibold">햇살유치원 CCTV 관리</h1>
//         <Badge className="bg-white/20 hover:bg-white/30 text-white border-white/40">
//           {roleLabels[currentRole]}
//         </Badge>
//       </div>
//
//       <div className="flex items-center gap-3">
//         {onRoleChange && (
//           <DropdownMenu>
//             <DropdownMenuTrigger asChild>
//               <button className="flex items-center gap-2 px-3 py-1.5 text-sm text-white hover:bg-white/20 rounded-md transition-colors">
//                 <UserCircle className="w-4 h-4" />
//                 {username}
//               </button>
//             </DropdownMenuTrigger>
//             <DropdownMenuContent align="end" className="w-56">
//               <DropdownMenuLabel>데모용 역할 전환</DropdownMenuLabel>
//               <DropdownMenuSeparator />
//               {roles.map((role) => (
//                 <DropdownMenuItem
//                   key={role}
//                   onClick={() => onRoleChange(role)}
//                   className={currentRole === role ? 'bg-purple-50' : ''}
//                 >
//                   {roleLabels[role]}
//                   {currentRole === role && ' ✓'}
//                 </DropdownMenuItem>
//               ))}
//               <DropdownMenuSeparator />
//               <DropdownMenuLabel className="text-xs text-gray-500 font-normal">
//                 실제 운영시 로그인 정보로 자동 결정됩니다
//               </DropdownMenuLabel>
//             </DropdownMenuContent>
//           </DropdownMenu>
//         )}
//
//         <div className="flex items-center gap-2">
//           <Button variant="ghost" size="sm" className="text-white hover:bg-white/20">
//             <Settings className="w-4 h-4" />
//           </Button>
//           <Button
//             variant="ghost"
//             size="sm"
//             className="text-white hover:bg-white/20 gap-2"
//             onClick={handleLogout}
//           >
//             <LogOut className="w-4 h-4" />
//             로그아웃
//           </Button>
//         </div>
//       </div>
//     </div>
//   );
// }
