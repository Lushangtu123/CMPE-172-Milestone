import { useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { api, getSession } from "../lib/api";
import { useAuth } from "../lib/auth";
import { ErrorNotice } from "../components/ui";

export function SignIn() {
  const { account, refresh } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  if (account)
    return (
      <Navigate
        to={account.role === "PROVIDER" ? "/provider" : "/sessions"}
        replace
      />
    );
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    setBusy(true);
    setError("");
    try {
      await getSession();
      await api("/login", {
        data: {
          username: String(form.get("username")),
          password: String(form.get("password")),
        },
      });
      const user = await refresh();
      const from = location.state?.from;
      navigate(
        typeof from === "string" &&
          from.startsWith("/") &&
          !from.startsWith("//")
          ? from
          : user?.role === "PROVIDER"
            ? "/provider"
            : "/sessions",
        { replace: true },
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="sign-in-layout">
      <div>
        <p className="eyebrow">WELCOME BACK</p>
        <h1>
          Let’s make
          <br />
          <em>some progress.</em>
        </h1>
        <p className="intro">
          A little structure. The right support.
          <br />
          Your next session is waiting.
        </p>
        <div className="login-art" aria-hidden="true">
          t<span>ogether.</span>
          <small>BETTER QUESTIONS. BRIGHTER POSSIBILITIES.</small>
        </div>
      </div>
      <section className="panel login-panel">
        <span className="eyebrow">YOUR LEARNING, IN ONE PLACE</span>
        <h2>Sign in.</h2>
        <ErrorNotice message={error} />
        <form onSubmit={submit}>
          <label htmlFor="username">Email address</label>
          <input
            id="username"
            name="username"
            type="email"
            autoComplete="username"
            placeholder="you@example.com"
            required
          />
          <label htmlFor="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
          />
          <button className="full" disabled={busy}>
            {busy ? "Signing in…" : "Sign in"} <span>↗</span>
          </button>
        </form>
        <details className="demo">
          <summary>Try a demo account</summary>
          <p>
            Customer: maya.patel@example.com
            <br />
            Customer: sam.rivera@example.com
            <br />
            Tutor: priya.shah@example.com
            <br />
            Tutor: jordan.lee@example.com
          </p>
          <p>
            Password: <code>TutorDemo123!</code>
          </p>
        </details>
      </section>
    </section>
  );
}
