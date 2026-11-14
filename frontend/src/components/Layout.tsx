import { useEffect, useState } from "react";
import {
  Link,
  NavLink,
  Outlet,
  useLocation,
  useNavigate,
} from "react-router-dom";
import { useAuth } from "../lib/auth";
import { ErrorNotice } from "./ui";

export function Layout() {
  const { account, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    window.scrollTo(0, 0);
    setError("");
  }, [location.pathname]);
  async function signOut() {
    setBusy(true);
    try {
      await logout();
      navigate("/sign-in");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>
      <header className="site-header">
        <Link className="brand" to="/" aria-label="TutorConnect home">
          <span className="brand-mark">t.</span>TutorConnect
          <span className="brand-dot">®</span>
        </Link>
        <nav aria-label="Main navigation">
          <NavLink to="/sessions">Find a session</NavLink>
          {account?.role === "CUSTOMER" && (
            <>
              <NavLink to="/appointments">My appointments</NavLink>
              <NavLink to="/assistant">
                AI assistant <span className="tiny-star">✦</span>
              </NavLink>
            </>
          )}
          {account?.role === "PROVIDER" && (
            <NavLink to="/provider">Tutor workspace</NavLink>
          )}
        </nav>
        <div className="account-menu">
          {account ? (
            <>
              <span className="account-name">{account.name.split(" ")[0]}</span>
              <button
                className="secondary small"
                onClick={signOut}
                disabled={busy}
              >
                {busy ? "Signing out…" : "Sign out"}
              </button>
            </>
          ) : (
            <Link className="button small" to="/sign-in">
              Sign in <span>↗</span>
            </Link>
          )}
        </div>
      </header>
      <main
        id="main-content"
        tabIndex={-1}
        className={
          location.pathname === "/assistant" ? "main assistant-main" : "main"
        }
      >
        <ErrorNotice message={error} />
        <Outlet />
      </main>
      {location.pathname !== "/assistant" && (
        <footer className="site-footer">
          <span>LEARN WITH PURPOSE.</span>
          <span>TutorConnect · All appointment times are UTC</span>
        </footer>
      )}
    </>
  );
}
