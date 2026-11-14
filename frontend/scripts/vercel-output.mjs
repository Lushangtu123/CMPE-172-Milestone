import { cp, mkdir, rm, writeFile } from "node:fs/promises";

const target = new URL(process.env.BACKEND_URL || "");
if (
  target.protocol !== "https:" ||
  target.username ||
  target.password ||
  target.pathname !== "/" ||
  target.search ||
  target.hash
)
  throw new Error(
    "BACKEND_URL must be the HTTPS origin of your Railway backend, without credentials or a path.",
  );
// Build Output API permits environment-dependent external rewrites without embedding a
// backend origin in the browser bundle. The browser always uses same-origin session cookies.
await rm(".vercel/output", { recursive: true, force: true });
await mkdir(".vercel/output/static", { recursive: true });
await cp("dist", ".vercel/output/static", { recursive: true });
await writeFile(
  ".vercel/output/config.json",
  JSON.stringify(
    {
      version: 3,
      routes: [
        {
          src: "/api/(.*)",
          dest: `${target.origin}/api/$1`,
          headers: { "Cache-Control": "private, no-store" },
        },
        {
          src: "/(login|logout)",
          dest: `${target.origin}/$1`,
          headers: { "Cache-Control": "private, no-store" },
        },
        {
          src: "/assets/(.*)",
          headers: { "Cache-Control": "public, max-age=31536000, immutable" },
          continue: true,
        },
        { handle: "filesystem" },
        {
          src: "/.*",
          dest: "/index.html",
          headers: { "Cache-Control": "no-cache" },
        },
      ],
    },
    null,
    2,
  ),
);
console.log("Vercel static output and same-origin backend routes are ready.");
