import { baseApi } from '@/shared/api/baseApi';

export interface DashboardMetric {
  id: string;
  metricName: string;
  value: number;
  unit: string;
  createdAt: string;
}

export const metricsApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    getDashboardMetrics: build.query<DashboardMetric[], void>({
      query: () => '/metrics',
      providesTags: ['Metrics'],
    }),
  }),
  overrideExisting: false,
});

export const { useGetDashboardMetricsQuery } = metricsApi;