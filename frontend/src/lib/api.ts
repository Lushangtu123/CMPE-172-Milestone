import type { Session } from "./types";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

let csrf: Session["csrf"] | undefined;
export async function getSession(): Promise<Session> {
  const session = await api<Session>("/api/session");
  csrf = session.csrf;
  return session;
}

type Options = {
  data?: Record<string, string | number>;
  signal?: AbortSignal;
  timeout?: number;
};
export async function api<T = void>(
  path: string,
  options: Options = {},
): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), options.timeout ?? 15000);
  const headers = new Headers({ Accept: "application/json" });
  if (options.data) {
    headers.set("Content-Type", "application/x-www-form-urlencoded");
    if (csrf) headers.set(csrf.headerName, csrf.token);
  }
  try {
    const response = await fetch(path, {
      method: options.data ? "POST" : "GET",
      headers,
      credentials: "same-origin",
      cache: "no-store",
      signal: options.signal
        ? AbortSignal.any([options.signal, controller.signal])
        : controller.signal,
      body: options.data
        ? new URLSearchParams(
            Object.entries(options.data).map(([k, v]) => [k, String(v)]),
          )
        : undefined,
    });
    const body =
      response.status === 204
        ? undefined
        : await response.json().catch(() => undefined);
    if (!response.ok) {
      if (
        response.status === 401 &&
        path !== "/login" &&
        typeof window !== "undefined"
      )
        window.dispatchEvent(new Event("session-expired"));
      const message =
        body?.message ||
        (response.status === 403
          ? "Your session or permissions changed. Refresh the page and try again."
          : "Unable to complete this request. Please try again.");
      throw new ApiError(response.status, message);
    }
    if (response.status !== 204 && body === undefined)
      throw new ApiError(
        502,
        "The server returned an unexpected response. Please refresh the page.",
      );
    return body as T;
  } catch (error) {
    if (error instanceof ApiError || options.signal?.aborted) throw error;
    throw new ApiError(
      0,
      options.data
        ? "The connection was interrupted. Check your appointments before trying this action again."
        : "Cannot reach TutorConnect. Check your connection and try again.",
    );
  } finally {
    clearTimeout(timer);
  }
}
