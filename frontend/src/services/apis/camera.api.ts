import { baseApi } from '@/services/apis/base.api';

export const cameraApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    getCameras: build.query<any[], string>({
      query: (kindergartenId) => `/kindergartens/${kindergartenId}/cameras`,
      providesTags: ['Camera'],
    }),
  }),
  overrideExisting: false,
});

export const { useGetCamerasQuery } = cameraApi;