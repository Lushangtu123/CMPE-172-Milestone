import { afterEach, expect, test, vi } from "vitest";
import { ApiError, api, getSession } from "./api";

afterEach(() => vi.unstubAllGlobals());

test("uses the rotated session token for writes and preserves a 409 without replaying it", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          account: null,
          csrf: { headerName: "X-CSRF-TOKEN", token: "first" },
        }),
      ),
    )
    .mockResolvedValueOnce(new Response(null, { status: 204 }))
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          account: { id: 1 },
          csrf: { headerName: "X-CSRF-TOKEN", token: "rotated" },
        }),
      ),
    )
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({ message: "Slot is no longer available." }),
        { status: 409 },
      ),
    );
  vi.stubGlobal("fetch", fetch);
  await getSession();
  await api("/login", {
    data: { username: "maya@example.com", password: "example" },
  });
  await getSession();
  await expect(
    api("/api/customer/appointments", { data: { slotId: 1, serviceId: 1 } }),
  ).rejects.toMatchObject({
    status: 409,
    message: "Slot is no longer available.",
  });
  expect(fetch).toHaveBeenCalledTimes(4);
  const options = fetch.mock.calls[3][1];
  expect(new Headers(options.headers).get("X-CSRF-TOKEN")).toBe("rotated");
  expect(options.credentials).toBe("same-origin");
  expect(String(options.body)).toBe("slotId=1&serviceId=1");
});

test("network failure reports uncertainty instead of claiming a write was rolled back", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockRejectedValue(new TypeError("Failed to fetch")),
  );
  await expect(
    api("/api/customer/appointments", { data: { slotId: 1, serviceId: 1 } }),
  ).rejects.toThrow(/Check your appointments/);
});

test("invalid credentials preserve the server status", async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValue(
        new Response('{"message":"Invalid email or password."}', {
          status: 401,
        }),
      ),
  );
  await expect(
    api("/login", { data: { username: "invalid", password: "invalid" } }),
  ).rejects.toBeInstanceOf(ApiError);
});
