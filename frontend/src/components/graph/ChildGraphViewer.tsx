'use client';

import { useEffect, useState } from 'react';
import {
  useGetChildGraphQuery,
  useGetTeacherGraphQuery,
} from '@/services/apis/graph.api';
import { searchChildrenByName, type GuardianChildVO } from '@/services/apis/children.api';
import { searchTeachers, type TeacherVO } from '@/services/apis/teachers.api';

type Mode = 'child' | 'teacher';

/**
 * Minimal consumer of the relationship-graph read API. Lets staff look up a child's or a teacher's
 * relationship graph by id; tenant scope is enforced server-side from the session (the client never
 * sends a kindergartenId), and an id outside the caller's kindergarten returns 404 (shown as
 * "찾을 수 없습니다"). Rendered as a plain structured view; a reagraph canvas visualization is left as
 * a follow-up (the dependency is present but the node/edge projection here is intentionally lean).
 *
 * wire-orphan-management-uis (UX-02): 'child' 모드는 원래 raw 숫자 PK 를 직접 입력해야만 조회할
 * 수 있는 유일한 진입점이었다(orphan UX). 기존에 있었지만 어디에서도 쓰이지 않던
 * `children.api.ts`(`GET /api/v1/children`, GUARDIAN 은 본인 관계-scoped 아이, TEACHER 는
 * 담당 학급 배정-scoped 아이 목록)를 재사용해 실제 이름 목록에서 고르는 선택기를 추가한다.
 * 새 백엔드 엔드포인트는 만들지 않는다(Non-goal). KINDERGARTEN_ADMIN 은 이 엔드포인트가
 * 빈 목록을 반환하도록 설계돼 있어(`ChildrenService.listRelatedChildren` 의 role switch 가
 * GUARDIAN/TEACHER 외 역할은 전부 빈 리스트로 폴백) 목록이 비면 안내 문구만 보이고, 직접 ID
 * 입력(fallback, 기존 동작 유지)으로 계속 조회할 수 있다 — design D2 의 "메뉴 진입점 + 그래프
 * 페이지 명시적 선택기" 퇴행안에 해당(별도 아이 명부 페이지는 없어 그래프 페이지 자체에 선택기를 둠).
 * 'teacher' 모드 역시 같은 잔여 orphan UX였다(raw teacherId 직접 입력만 가능) — 기존에 있던
 * `teachers.api.ts`(`GET /api/v1/teachers`, `searchTeachers`, TENANT_S2_READ =
 * KINDERGARTEN_ADMIN + TEACHER, 활성 유치원으로 세션 스코프)를 재사용해 이름 목록 선택기를
 * 추가한다(새 백엔드 엔드포인트 없음). 목록이 비는 역할/상황을 대비해 직접 ID 입력은
 * fallback 으로 계속 유지한다.
 */
export function ChildGraphViewer() {
  const [mode, setMode] = useState<Mode>('child');
  const [input, setInput] = useState('');
  const [queryId, setQueryId] = useState<number | null>(null);

  const [children, setChildren] = useState<GuardianChildVO[]>([]);
  const [childrenLoading, setChildrenLoading] = useState(true);
  const [selectedChildId, setSelectedChildId] = useState('');

  const [teachers, setTeachers] = useState<TeacherVO[]>([]);
  const [teachersLoading, setTeachersLoading] = useState(true);
  const [selectedTeacherId, setSelectedTeacherId] = useState('');

  useEffect(() => {
    let cancelled = false;
    // Initial `childrenLoading` state is already `true` (effect runs once on mount, empty deps) —
    // no need to set it again here (avoids react-hooks/set-state-in-effect: calling setState
    // directly/synchronously in the effect body, as opposed to inside the promise callbacks below).
    searchChildrenByName('')
      .then((list) => {
        if (!cancelled) setChildren(list);
      })
      .catch(() => {
        if (!cancelled) setChildren([]);
      })
      .finally(() => {
        if (!cancelled) setChildrenLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    // Same rationale as the children-roster effect above: `teachersLoading` already starts
    // `true`, so no synchronous setState is needed in the effect body itself.
    searchTeachers({ size: 100 })
      .then((page) => {
        if (!cancelled) setTeachers(page.content ?? []);
      })
      .catch(() => {
        if (!cancelled) setTeachers([]);
      })
      .finally(() => {
        if (!cancelled) setTeachersLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const childGraph = useGetChildGraphQuery(queryId ?? 0, {
    skip: queryId === null || mode !== 'child',
  });
  const teacherGraph = useGetTeacherGraphQuery(queryId ?? 0, {
    skip: queryId === null || mode !== 'teacher',
  });

  const active = mode === 'child' ? childGraph : teacherGraph;

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    const parsed = Number(input.trim());
    setQueryId(Number.isFinite(parsed) && parsed > 0 ? parsed : null);
  }

  function onSelectChild(e: React.ChangeEvent<HTMLSelectElement>) {
    const value = e.target.value;
    setSelectedChildId(value);
    const parsed = Number(value);
    setQueryId(Number.isFinite(parsed) && parsed > 0 ? parsed : null);
  }

  function onSelectTeacher(e: React.ChangeEvent<HTMLSelectElement>) {
    const value = e.target.value;
    setSelectedTeacherId(value);
    const parsed = Number(value);
    setQueryId(Number.isFinite(parsed) && parsed > 0 ? parsed : null);
  }

  return (
    <div className="mx-auto max-w-3xl p-6">
      <h1 className="mb-4 text-xl font-semibold text-gray-800">관계 그래프 조회</h1>

      <div className="mb-4 flex gap-2">
        <button
          type="button"
          onClick={() => { setMode('child'); setQueryId(null); setSelectedChildId(''); setInput(''); }}
          className={`rounded px-3 py-1 text-sm ${mode === 'child' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600'}`}
        >
          원아 기준
        </button>
        <button
          type="button"
          onClick={() => { setMode('teacher'); setQueryId(null); setSelectedTeacherId(''); setInput(''); }}
          className={`rounded px-3 py-1 text-sm ${mode === 'teacher' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600'}`}
        >
          교사 기준
        </button>
      </div>

      {mode === 'child' && (
        <div className="mb-4">
          <label className="mb-1 block text-sm text-gray-600">내가 접근 가능한 원아 목록에서 선택</label>
          {childrenLoading ? (
            <p className="text-sm text-gray-500">원아 목록을 불러오는 중입니다.</p>
          ) : children.length === 0 ? (
            <p className="text-sm text-gray-400">선택 가능한 원아가 없습니다. 아래에서 원아 ID를 직접 입력해 조회하세요.</p>
          ) : (
            <select
              value={selectedChildId}
              onChange={onSelectChild}
              className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
            >
              <option value="">원아를 선택하세요</option>
              {children.map((c) => (
                <option key={c.childId} value={c.childId}>
                  {c.name} (#{c.childId})
                </option>
              ))}
            </select>
          )}
        </div>
      )}

      {mode === 'teacher' && (
        <div className="mb-4">
          <label className="mb-1 block text-sm text-gray-600">교사 목록에서 선택</label>
          {teachersLoading ? (
            <p className="text-sm text-gray-500">교사 목록을 불러오는 중입니다.</p>
          ) : teachers.length === 0 ? (
            <p className="text-sm text-gray-400">선택 가능한 교사가 없습니다. 아래에서 교사 ID를 직접 입력해 조회하세요.</p>
          ) : (
            <select
              value={selectedTeacherId}
              onChange={onSelectTeacher}
              className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
            >
              <option value="">교사를 선택하세요</option>
              {teachers.map((t) => (
                <option key={t.teacherId} value={t.teacherId}>
                  {t.name} (#{t.teacherId})
                </option>
              ))}
            </select>
          )}
        </div>
      )}

      <form onSubmit={onSubmit} className="mb-6 flex gap-2">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          inputMode="numeric"
          placeholder={mode === 'child' ? '원아 ID 직접 입력' : '교사 ID'}
          className="w-40 rounded border border-gray-300 px-3 py-1 text-sm"
        />
        <button type="submit" className="rounded bg-blue-600 px-4 py-1 text-sm text-white hover:bg-blue-700">
          조회
        </button>
      </form>

      {queryId !== null && active.isLoading && (
        <p className="text-sm text-gray-500">불러오는 중입니다.</p>
      )}
      {queryId !== null && active.isError && (
        <p className="text-sm text-red-600">찾을 수 없습니다.</p>
      )}

      {mode === 'child' && childGraph.data && (
        <ChildGraphView data={childGraph.data} />
      )}
      {mode === 'teacher' && teacherGraph.data && (
        <TeacherGraphView data={teacherGraph.data} />
      )}
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mb-4 rounded border border-gray-200 p-4">
      <h2 className="mb-2 text-sm font-semibold text-gray-700">{title}</h2>
      {children}
    </section>
  );
}

function ChildGraphView({ data }: { data: import('@/services/apis/graph.api').ChildGraph }) {
  return (
    <div>
      <Section title="원아">
        <p className="text-sm text-gray-700">
          {data.child?.name ?? '-'} <span className="text-gray-400">({data.child?.childNo ?? '-'})</span>
        </p>
      </Section>
      <Section title="학급 / 담임">
        <p className="text-sm text-gray-700">
          {data.classInfo?.name ?? '-'} / {data.teacher?.name ?? '-'}
        </p>
      </Section>
      <Section title="유치원">
        <p className="text-sm text-gray-700">{data.kindergarten?.name ?? '-'}</p>
      </Section>
      <Section title="보호자">
        <ul className="space-y-1">
          {data.guardians.map((g) => (
            <li key={g.guardianId} className="text-sm text-gray-700">
              {g.name} <span className="text-gray-400">({g.relationship ?? '-'}{g.isPrimary ? ', 주' : ''})</span>
            </li>
          ))}
          {data.guardians.length === 0 && <li className="text-sm text-gray-400">없음</li>}
        </ul>
      </Section>
    </div>
  );
}

function TeacherGraphView({ data }: { data: import('@/services/apis/graph.api').TeacherGraph }) {
  return (
    <div>
      <Section title="교사">
        <p className="text-sm text-gray-700">
          {data.teacher?.name ?? '-'} <span className="text-gray-400">({data.kindergarten?.name ?? '-'})</span>
        </p>
      </Section>
      {data.classes.map((cls, idx) => (
        <Section key={cls.classInfo?.classId ?? `class-${idx}`} title={`학급: ${cls.classInfo?.name ?? '-'}`}>
          <ul className="space-y-1">
            {cls.children.map((ch) => (
              <li key={ch.childId} className="text-sm text-gray-700">
                {ch.name} <span className="text-gray-400">({ch.childNo ?? '-'})</span>
              </li>
            ))}
            {cls.children.length === 0 && <li className="text-sm text-gray-400">원아 없음</li>}
          </ul>
        </Section>
      ))}
      {data.classes.length === 0 && <p className="text-sm text-gray-400">담당 학급이 없습니다.</p>}
    </div>
  );
}
