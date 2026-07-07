'use client';

import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';
import { getApiErrorMessage } from '@/components/letters/api-error-message';

const DEFAULT_PAGE_SIZE = 10;

/** Params passed to a `list` loader: keyword is only meaningful when `hasKeyword` is true. */
export interface CrudResourceListParams {
  keyword?: string;
  page: number;
  size: number;
}

/**
 * Shape a `list` loader must resolve to. `extra` is an escape hatch for resources whose load
 * also needs a side-channel fetch (e.g. camera streams also loading the camera catalog) —
 * generic over `TExtra` so resources without one simply never set it.
 */
export interface CrudResourceListResult<TItem, TExtra = undefined> {
  content: TItem[];
  totalPages: number;
  extra?: TExtra;
}

export interface CrudResourceLabels {
  /** console.warn prefix on load failure. */
  loadErrorLog: string;
  /** toast/error fallback text on load failure. */
  loadErrorToast: string;
  createSuccess?: string;
  createErrorToast?: string;
  updateSuccess?: string;
  updateErrorToast?: string;
  /** window.confirm prompt before delete; omit to skip the confirm gate entirely. */
  deleteConfirm?: string;
  deleteSuccess?: string;
  deleteErrorToast?: string;
}

export interface UseCrudResourceConfig<TItem, TCreate = unknown, TUpdate = unknown, TExtra = undefined> {
  list: (params: CrudResourceListParams) => Promise<CrudResourceListResult<TItem, TExtra>>;
  create?: (payload: TCreate) => Promise<unknown>;
  update?: (id: number, payload: TUpdate) => Promise<unknown>;
  remove?: (id: number) => Promise<unknown>;
  labels: CrudResourceLabels;
  pageSize?: number;
  /** Default true. When false, the loader never applies a keyword filter (camera streams has no search UI). */
  hasKeyword?: boolean;
  /** Default true. When false, `handleDelete` is a no-op (camera streams has no delete action). */
  hasDelete?: boolean;
}

/**
 * refactor-cross-cutting-debt (QLT-01 / D3): generalizes the near-identical CRUD +
 * search + pagination orchestration previously duplicated line-for-line across
 * `useClassesManagement` / `useRoomsManagement` / `useCameraStreamsManagement`.
 *
 * Callers get back a superset of state/callbacks; each thin wrapper hook picks only the
 * fields its original outward shape had, so consuming components need zero changes.
 */
export function useCrudResource<TItem, TCreate = unknown, TUpdate = unknown, TExtra = undefined>(
  config: UseCrudResourceConfig<TItem, TCreate, TUpdate, TExtra>,
) {
  const { list, create, update, remove, labels, pageSize = DEFAULT_PAGE_SIZE, hasKeyword = true, hasDelete = true } =
    config;

  const [items, setItems] = useState<TItem[]>([]);
  const [extra, setExtra] = useState<TExtra | undefined>(undefined);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await list({
        keyword: hasKeyword ? appliedKeyword || undefined : undefined,
        page,
        size: pageSize,
      });
      setItems(data.content);
      setTotalPages(data.totalPages);
      setExtra(data.extra);
    } catch (e) {
      console.warn(labels.loadErrorLog, e);
      setError(getApiErrorMessage(e, labels.loadErrorToast));
    } finally {
      setLoading(false);
    }
    // `list`/`labels` are config held stable by the caller (mirrors original hooks' deps).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasKeyword, appliedKeyword, page, pageSize]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleSearch = useCallback(() => {
    setAppliedKeyword(keyword.trim());
    setPage(0);
  }, [keyword]);

  const handleCreate = useCallback(
    async (payload: TCreate) => {
      if (!create) return false;
      setSubmitting(true);
      try {
        await create(payload);
        if (labels.createSuccess) toast.success(labels.createSuccess);
        setPage(0);
        await load();
        return true;
      } catch (e) {
        toast.error(getApiErrorMessage(e, labels.createErrorToast ?? labels.loadErrorToast));
        return false;
      } finally {
        setSubmitting(false);
      }
    },
    [create, load, labels.createSuccess, labels.createErrorToast, labels.loadErrorToast],
  );

  const handleUpdate = useCallback(
    async (id: number, payload: TUpdate) => {
      if (!update) return false;
      setSubmitting(true);
      try {
        await update(id, payload);
        if (labels.updateSuccess) toast.success(labels.updateSuccess);
        await load();
        return true;
      } catch (e) {
        toast.error(getApiErrorMessage(e, labels.updateErrorToast ?? labels.loadErrorToast));
        return false;
      } finally {
        setSubmitting(false);
      }
    },
    [update, load, labels.updateSuccess, labels.updateErrorToast, labels.loadErrorToast],
  );

  // Note: matches the original hooks — delete does NOT toggle `submitting`.
  const handleDelete = useCallback(
    async (id: number) => {
      if (!hasDelete || !remove) return;
      if (labels.deleteConfirm && !window.confirm(labels.deleteConfirm)) return;
      try {
        await remove(id);
        if (labels.deleteSuccess) toast.success(labels.deleteSuccess);
        await load();
      } catch (e) {
        toast.error(getApiErrorMessage(e, labels.deleteErrorToast ?? labels.loadErrorToast));
      }
    },
    [hasDelete, remove, load, labels.deleteConfirm, labels.deleteSuccess, labels.deleteErrorToast, labels.loadErrorToast],
  );

  return {
    items,
    extra,
    loading,
    error,
    keyword,
    setKeyword,
    appliedKeyword,
    handleSearch,
    page,
    totalPages,
    setPage,
    submitting,
    handleCreate,
    handleUpdate,
    handleDelete,
    load,
  };
}
