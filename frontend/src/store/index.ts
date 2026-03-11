import { configureStore } from '@reduxjs/toolkit';
import { baseApi } from '../services/apis/base.api';
import userReducer from '@/store/slices/userSlice';

export const index = configureStore({
  reducer: {
    [baseApi.reducerPath]: baseApi.reducer,
    user: userReducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat(baseApi.middleware),
});

export type RootState = ReturnType<typeof index.getState>;
export type AppDispatch = typeof index.dispatch;