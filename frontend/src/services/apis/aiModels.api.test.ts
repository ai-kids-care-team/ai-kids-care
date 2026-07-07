import { describe, expect, it, vi, beforeEach } from 'vitest';

const getMock = vi.fn();
const postMock = vi.fn();
const putMock = vi.fn();
const deleteMock = vi.fn();

vi.mock('./apiClient', () => ({
  apiClient: {
    get: (...args: unknown[]) => getMock(...args),
    post: (...args: unknown[]) => postMock(...args),
    put: (...args: unknown[]) => putMock(...args),
    delete: (...args: unknown[]) => deleteMock(...args),
  },
}));

const { getAiModels, getAiModel, createAiModel, updateAiModel, deleteAiModel } = await import(
  './aiModels.api'
);

/**
 * wire-orphan-management-uis (UX-01): `aiModels.api.ts` 는 `apiClient`(axios) 를 그대로 쓴다
 * — CSRF 헤더 주입은 `apiClient.ts` 인터셉터(interceptors.request.use)에서 전역으로 처리되고
 * 여기서 재구현하지 않으므로, 이 스위트는 그 사실을 전제로 두고 대신 호출 형태(파라미터 조립,
 * 페이로드/URL)가 백엔드 `AiModelController` 계약과 어긋나지 않는지를 검증한다.
 */
describe('aiModels.api', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('getAiModels defaults page/size and omits keyword when absent', async () => {
    getMock.mockResolvedValue({ data: { content: [], totalPages: 0 } });
    await getAiModels();
    expect(getMock).toHaveBeenCalledWith('/ai_models', { params: { page: 0, size: 20 } });
  });

  it('getAiModels trims and includes a non-blank keyword', async () => {
    getMock.mockResolvedValue({ data: { content: [], totalPages: 0 } });
    await getAiModels({ keyword: '  vmae  ', page: 1, size: 10 });
    expect(getMock).toHaveBeenCalledWith('/ai_models', {
      params: { page: 1, size: 10, keyword: 'vmae' },
    });
  });

  it('getAiModels omits a whitespace-only keyword', async () => {
    getMock.mockResolvedValue({ data: { content: [], totalPages: 0 } });
    await getAiModels({ keyword: '   ' });
    expect(getMock).toHaveBeenCalledWith('/ai_models', { params: { page: 0, size: 20 } });
  });

  it('getAiModel fetches by id', async () => {
    getMock.mockResolvedValue({ data: { modelId: 5 } });
    await getAiModel(5);
    expect(getMock).toHaveBeenCalledWith('/ai_models/5');
  });

  it('createAiModel POSTs the payload as-is', async () => {
    postMock.mockResolvedValue({ data: { modelId: 1 } });
    const payload = { name: 'videomae-base', version: 'v1', status: 'ACTIVE' };
    await createAiModel(payload);
    expect(postMock).toHaveBeenCalledWith('/ai_models', payload);
  });

  it('updateAiModel PUTs to the id-scoped path', async () => {
    putMock.mockResolvedValue({ data: { modelId: 1 } });
    const payload = { name: 'videomae-base', version: 'v2', status: 'DISABLED' };
    await updateAiModel(1, payload);
    expect(putMock).toHaveBeenCalledWith('/ai_models/1', payload);
  });

  it('deleteAiModel DELETEs the id-scoped path', async () => {
    deleteMock.mockResolvedValue({});
    await deleteAiModel(7);
    expect(deleteMock).toHaveBeenCalledWith('/ai_models/7');
  });
});
