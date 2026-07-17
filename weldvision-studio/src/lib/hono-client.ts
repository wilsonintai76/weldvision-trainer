/**
 * Hono RPC Client — Type-safe API client for the Worker backend.
 *
 * Usage:
 *   const client = createHonoClient();
 *   const sessions = await client.sessions.list({ studentId: "x" });
 */

import type { AppType } from "../workers/index";

type ClientOptions = {
  baseUrl?: string;
};

export function createHonoClient(opts: ClientOptions = {}) {
  const base = opts.baseUrl || "/api";

  async function request<T>(
    path: string,
    options: RequestInit = {}
  ): Promise<T> {
    const url = `${base}${path}`;
    const res = await fetch(url, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        ...options.headers,
      },
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ error: res.statusText }));
      throw new Error(err.error || `HTTP ${res.status}`);
    }
    return res.json();
  }

  return {
    // ── Roster ─────────────────────────────────────────────────────────
    roster: {
      list: () => request<{ students: any[]; count: number }>("/roster"),
      update: (seat: string, data: any) =>
        request(`/roster/${seat}`, { method: "PUT", body: JSON.stringify(data) }),
      remove: (seat: string) =>
        request(`/roster/${seat}`, { method: "DELETE" }),
    },

    // ── Sessions ───────────────────────────────────────────────────────
    sessions: {
      list: (params?: { studentId?: string; status?: string; limit?: number }) => {
        const qs = new URLSearchParams();
        if (params?.studentId) qs.set("studentId", params.studentId);
        if (params?.status) qs.set("status", params.status);
        if (params?.limit) qs.set("limit", String(params.limit));
        return request<{ sessions: any[]; count: number }>(
          `/sessions?${qs.toString()}`
        );
      },
      get: (id: string) => request<any>(`/sessions/${id}`),
      create: (data: any) =>
        request<{ id: string; status: string }>("/sessions", {
          method: "POST",
          body: JSON.stringify(data),
        }),
      updateScore: (id: string, data: any) =>
        request(`/sessions/${id}/score`, {
          method: "PUT",
          body: JSON.stringify(data),
        }),
    },

    // ── Telemetry ──────────────────────────────────────────────────────
    telemetry: {
      getUrl: (key: string) => `${base}/telemetry/${key}`,
      analyze: (data: { sessionId: string; telemetryKey: string }) =>
        request<any>("/telemetry/analyze", {
          method: "POST",
          body: JSON.stringify(data),
        }),
    },

    // ── Health ─────────────────────────────────────────────────────────
    health: () => request<{ status: string }>("/health"),
  };
}

export type HonoClient = ReturnType<typeof createHonoClient>;
