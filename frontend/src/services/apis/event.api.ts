import { baseApi } from '@/services/apis/base.api';

export const eventApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    getEvents: build.query<any, { kindergartenId: string; page?: number; size?: number; type?: string; status?: string; startDate?: string; endDate?: string }>({
      query: ({ kindergartenId, ...params }) => ({
        url: `/kindergartens/${kindergartenId}/events`,
        params,
      }),
      providesTags: ['Event'],
    }),
    updateEventStatus: build.mutation<void, { kindergartenId: string; eventId: string; status: string }>({
      query: ({ kindergartenId, eventId, status }) => ({
        url: `/kindergartens/${kindergartenId}/events/${eventId}`,
        method: 'PATCH',
        body: { status },
      }),
      invalidatesTags: ['Event'],
    }),
  }),
  overrideExisting: false,
});

export const { useGetEventsQuery, useUpdateEventStatusMutation } = eventApi;