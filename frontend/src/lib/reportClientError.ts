type ClientErrorPayload = {
  source: string;
  message: string;
  detail?: unknown;
};

export const reportClientError = async (source: string, message: string, detail?: unknown): Promise<void> => {
  const payload: ClientErrorPayload = {
    source: source || 'unknown',
    message: message || 'client error',
    detail: detail ?? null,
  };

  try {
    await fetch('/api/client-log', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
      keepalive: true,
    });
  } catch {
    // Logging utility must never interrupt the user flow.
  }
};
