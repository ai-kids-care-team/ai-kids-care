'use client';

import { useState } from 'react';
import {
  useGetChildGraphQuery,
  useGetTeacherGraphQuery,
} from '@/services/apis/graph.api';

type Mode = 'child' | 'teacher';

/**
 * Minimal consumer of the relationship-graph read API. Lets staff look up a child's or a teacher's
 * relationship graph by id; tenant scope is enforced server-side from the session (the client never
 * sends a kindergartenId), and an id outside the caller's kindergarten returns 404 (shown as
 * "찾을 수 없습니다"). Rendered as a plain structured view; a reagraph canvas visualization is left as
 * a follow-up (the dependency is present but the node/edge projection here is intentionally lean).
 */
export function ChildGraphViewer() {
  const [mode, setMode] = useState<Mode>('child');
  const [input, setInput] = useState('');
  const [queryId, setQueryId] = useState<number | null>(null);

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

  return (
    <div className="mx-auto max-w-3xl p-6">
      <h1 className="mb-4 text-xl font-semibold text-gray-800">관계 그래프 조회</h1>

      <div className="mb-4 flex gap-2">
        <button
          type="button"
          onClick={() => { setMode('child'); setQueryId(null); }}
          className={`rounded px-3 py-1 text-sm ${mode === 'child' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600'}`}
        >
          원아 기준
        </button>
        <button
          type="button"
          onClick={() => { setMode('teacher'); setQueryId(null); }}
          className={`rounded px-3 py-1 text-sm ${mode === 'teacher' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600'}`}
        >
          교사 기준
        </button>
      </div>

      <form onSubmit={onSubmit} className="mb-6 flex gap-2">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          inputMode="numeric"
          placeholder={mode === 'child' ? '원아 ID' : '교사 ID'}
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
