import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { Navigate, useLocation } from "react-router-dom";
import { api, getSession } from "./api";
import type { Account } from "./types";

interface Auth {
  account: Account | null;
  refresh: () => Promise<Account | null>;
  logout: () => Promise<void>;
}
const Context = createContext<Auth | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [account, setAccount] = useState<Account | null>(null);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState("");
  async function refresh() {
    const session = await getSession();
    setAccount(session.account);
    return session.account;
  }
  function initialize() {
    setError("");
    refresh()
      .then(() => setReady(true))
      .catch((e) => setError(e.message));
  }
  useEffect(() => {
    initialize();
    const expired = () => {
      setAccount(null);
      getSession().catch(() => {});
    };
    window.addEventListener("session-expired", expired);
    return () => window.removeEventListener("session-expired", expired);
  }, []);
  async function logout() {
    await api("/logout", { data: {} });
    setAccount(null);
    await refresh();
  }
  if (!ready)
    return (
      <div className="boot-screen">
        <span className="brand-mark">t.</span>
        <h1>TutorConnect</h1>
        {error ? (
          <>
            <p role="alert">{error}</p>
            <button onClick={initialize}>Try again</button>
          </>
        ) : (
          <p role="status">Getting things ready…</p>
        )}
      </div>
    );
  return <Context value={{ account, refresh, logout }}>{children}</Context>;
}
export function useAuth() {
  const value = useContext(Context);
  if (!value) throw new Error("AuthProvider missing");
  return value;
}
export function RequireRole({
  role,
  children,
}: {
  role: Account["role"];
  children: ReactNode;
}) {
  const { account } = useAuth();
  const location = useLocation();
  if (!account)
    return (
      <Navigate
        to="/sign-in"
        replace
        state={{ from: location.pathname + location.search }}
      />
    );
  if (account.role !== role)
    return (
      <section className="empty panel">
        <h1>This page belongs to another role.</h1>
        <p>Use the navigation above to return to your workspace.</p>
      </section>
    );
  return children;
}
