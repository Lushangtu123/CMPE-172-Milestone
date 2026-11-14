import { createRoot } from "react-dom/client";
import { BrowserRouter, Link, Route, Routes } from "react-router-dom";
import { AuthProvider, RequireRole } from "./lib/auth";
import { Layout } from "./components/Layout";
import { Home } from "./pages/Home";
import { SignIn } from "./pages/SignIn";
import { Sessions } from "./pages/Sessions";
import { Booking, Confirmation } from "./pages/Booking";
import { Appointments } from "./pages/Appointments";
import { ProviderWorkspace } from "./pages/Provider";
import { Assistant } from "./pages/Assistant";
import "./styles.css";

createRoot(document.getElementById("root")!).render(
  <BrowserRouter>
    <AuthProvider>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<Home />} />
          <Route path="sign-in" element={<SignIn />} />
          <Route path="sessions" element={<Sessions />} />
          <Route
            path="book/:id"
            element={
              <RequireRole role="CUSTOMER">
                <Booking />
              </RequireRole>
            }
          />
          <Route
            path="appointments"
            element={
              <RequireRole role="CUSTOMER">
                <Appointments />
              </RequireRole>
            }
          />
          <Route
            path="appointments/:id"
            element={
              <RequireRole role="CUSTOMER">
                <Confirmation />
              </RequireRole>
            }
          />
          <Route
            path="assistant"
            element={
              <RequireRole role="CUSTOMER">
                <Assistant />
              </RequireRole>
            }
          />
          <Route
            path="provider"
            element={
              <RequireRole role="PROVIDER">
                <ProviderWorkspace />
              </RequireRole>
            }
          />
          <Route
            path="*"
            element={
              <section className="empty">
                <p className="eyebrow">404 · A LITTLE OFF COURSE</p>
                <h1>
                  Let’s get you
                  <br />
                  <em>back on track.</em>
                </h1>
                <Link className="button" to="/sessions">
                  Find a session ↗
                </Link>
              </section>
            }
          />
        </Route>
      </Routes>
    </AuthProvider>
  </BrowserRouter>,
);
