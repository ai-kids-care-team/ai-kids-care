'use client';

import { PieChart, Pie, Cell, ResponsiveContainer } from 'recharts';
import type { DashboardMetric } from '../../services/apis/metrics.api';

// 개별 파이 차트(게이지) 컴포넌트
function MetricGauge({ metric }: { metric: DashboardMetric }) {
  const maxValue = metric.unit === '%' ? 100 : Math.max(metric.value * 1.5, 100);
  const value = Math.min(metric.value, maxValue);
  const percent = (value / maxValue) * 100;
  const data = [
    { name: 'value', value: percent, color: '#8b5cf6' },
    { name: 'empty', value: 100 - percent, color: '#e5e7eb' },
  ];

  return (
    <div className="bg-white rounded-xl p-4 border border-gray-200 shadow-sm">
      <p className="text-gray-500 font-medium text-sm mb-2">{metric.metricName}</p>
      <div className="h-32 relative">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={data}
              cx="50%"
              cy="70%"
              startAngle={180}
              endAngle={0}
              innerRadius="60%"
              outerRadius="100%"
              dataKey="value"
              stroke="none"
            >
              {data.map((entry, index) => (
                <Cell key={index} fill={entry.color} />
              ))}
            </Pie>
          </PieChart>
        </ResponsiveContainer>
        <p className="absolute bottom-2 left-0 right-0 text-center text-gray-900 font-bold text-lg">
          {metric.value} <span className="text-sm text-gray-500 font-normal">{metric.unit}</span>
        </p>
      </div>
    </div>
  );
}

interface SystemMetricsProps {
  metrics?: DashboardMetric[];
  isError?: boolean;
}

export function SystemMetrics({ metrics, isError }: SystemMetricsProps) {
  if (isError) {
    return (
      <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg text-red-600 text-sm">
        메트릭 데이터를 불러오는데 실패했습니다.
      </div>
    );
  }

  if (!metrics || metrics.length === 0) {
    return null;
  }

  return (
    <>
      <section className="mb-8">
        <h2 className="text-lg font-bold text-gray-900 mb-4">시스템 리소스 현황</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {metrics.slice(0, 6).map((metric) => (
            <MetricGauge key={metric.id} metric={metric} />
          ))}
        </div>
      </section>

      <section className="mb-8">
        <h2 className="text-lg font-bold text-gray-900 mb-4">상세 데이터 그리드</h2>
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden shadow-sm">
          <table className="w-full text-sm text-left">
            <thead className="bg-gray-50 border-b border-gray-200 text-gray-600 font-medium">
              <tr>
                <th className="px-6 py-3">ID</th>
                <th className="px-6 py-3">메트릭명</th>
                <th className="px-6 py-3">값</th>
                <th className="px-6 py-3">단위</th>
                <th className="px-6 py-3">생성일시</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {metrics.map((metric) => (
                <tr key={metric.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-6 py-4 text-gray-500">{metric.id}</td>
                  <td className="px-6 py-4 font-medium text-gray-900">{metric.metricName}</td>
                  <td className="px-6 py-4 font-semibold text-purple-600">{metric.value}</td>
                  <td className="px-6 py-4 text-gray-500">{metric.unit}</td>
                  <td className="px-6 py-4 text-gray-400">
                    {new Date(metric.createdAt).toLocaleString('ko-KR')}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}