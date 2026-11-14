import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { api, ApiError } from "../lib/api";
import { useAuth } from "../lib/auth";
import { useResource } from "../lib/useResource";
import type { Conversation, HistoryPage, Transcript, Turn } from "../lib/types";
import { dateTime, Empty, ErrorNotice, Loading } from "../components/ui";

function Messages({ messages }: { messages: Turn[] }) {
  return (
    <>
      {messages.map((message, index) => (
        <article key={index} className={`message ${message.role}`}>
          <span className="message-avatar" aria-hidden="true">
            {message.role === "assistant" ? "✦" : "Y"}
          </span>
          <div>
            <span className="message-author">
              {message.role === "assistant" ? "TutorConnect assistant" : "You"}
            </span>
            <div className="message-text">
              {message.text
                .split(/(\*\*[^*]+\*\*)/g)
                .map((part, i) =>
                  part.startsWith("**") && part.endsWith("**") ? (
                    <strong key={i}>{part.slice(2, -2)}</strong>
                  ) : (
                    part
                  ),
                )}
            </div>
          </div>
        </article>
      ))}
    </>
  );
}
function Archive({ id, onBack }: { id: number; onBack: () => void }) {
  const transcript = useResource<Transcript>(
    `/api/customer/ai/conversations/${id}`,
  );
  return (
    <>
      <div className="archive-banner">
        <span>Saved conversation · Read only</span>
        <button className="text-button" onClick={onBack}>
          Back to current chat ↗
        </button>
      </div>
      <div className="chat-scroll">
        <ErrorNotice message={transcript.error} retry={transcript.reload} />
        {transcript.loading ? (
          <Loading />
        ) : (
          transcript.data && (
            <>
              <div className="archive-title">
                <p className="eyebrow">
                  {dateTime(transcript.data.thread.updatedAt)} UTC
                </p>
                <h2>{transcript.data.thread.title}</h2>
              </div>
              <Messages messages={transcript.data.messages} />
            </>
          )
        )}
      </div>
    </>
  );
}
const suggestions = [
  {
    title: "Find my next session",
    text: "See what’s on your calendar",
    prompt: "Find my next session",
  },
  {
    title: "Find a tutoring session",
    text: "Make time for your next question",
    prompt: "What tutoring sessions are available?",
  },
  {
    title: "Understand a policy",
    text: "Get the details before you book",
    prompt: "What is the cancellation policy?",
  },
];
export function Assistant() {
  const { account } = useAuth();
  const draftKey = `tutorconnect-draft-${account!.id}`;
  const [draft, setDraft] = useState(() => {
    try {
      return sessionStorage.getItem(draftKey) || "";
    } catch {
      return "";
    }
  });
  const [current, setCurrent] = useState<Conversation>();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [sending, setSending] = useState("");
  const [uncertain, setUncertain] = useState(false);
  const [selected, setSelected] = useState<number | null>(null);
  const [page, setPage] = useState(0);
  const [sidebar, setSidebar] = useState(false);
  const [clock, setClock] = useState(Date.now());
  const endRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const history = useResource<HistoryPage>(
    `/api/customer/ai/conversations?page=${page}`,
  );
  async function loadCurrent() {
    setLoading(true);
    setError("");
    try {
      setCurrent(
        await api<Conversation>("/api/customer/ai/history", { timeout: 90000 }),
      );
      setUncertain(false);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    loadCurrent();
  }, []);
  useEffect(() => {
    try {
      if (draft) sessionStorage.setItem(draftKey, draft);
      else sessionStorage.removeItem(draftKey);
    } catch {}
  }, [draft, draftKey]);
  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
  }, [current?.messages.length, sending]);
  useEffect(() => {
    if (!current?.pending) return;
    const timer = setInterval(() => setClock(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [current?.pending]);
  async function action(
    kind: "chat" | "confirm" | "discard" | "reset",
    text = "",
  ) {
    if (busy || loading || uncertain || !current) return;
    if (kind === "chat" && !text.trim()) return;
    setBusy(true);
    setError("");
    setSending(kind === "chat" ? text.trim() : "");
    try {
      const data: Record<string, string | number> =
        kind === "chat"
          ? { message: text.trim() }
          : kind === "confirm"
            ? { token: current.pending!.token }
            : {};
      setCurrent(
        await api<Conversation>(`/api/customer/ai/${kind}`, {
          data,
          timeout: 90000,
        }),
      );
      if (kind === "chat") setDraft("");
      if (kind === "reset") setSelected(null);
      history.reload();
    } catch (e) {
      setError((e as Error).message);
      if (e instanceof ApiError && e.status === 0) setUncertain(true);
      // A proposal may have been consumed on a conflict; never replay its token.
      else if (kind === "confirm" || kind === "chat") {
        try {
          setCurrent(
            await api<Conversation>("/api/customer/ai/history", {
              timeout: 90000,
            }),
          );
        } catch {
          setUncertain(true);
        }
      }
    } finally {
      setBusy(false);
      setSending("");
      inputRef.current?.focus();
    }
  }
  function submit(e: FormEvent) {
    e.preventDefault();
    action("chat", draft);
  }
  const pending = current?.pending;
  const expired = pending
    ? new Date(pending.expiresAt).getTime() <= clock
    : false;
  const disabled = busy || loading || uncertain || !current;
  return (
    <section className="assistant-shell">
      <aside className={`chat-sidebar ${sidebar ? "is-open" : ""}`}>
        <div className="sidebar-heading">
          <span className="eyebrow">YOUR LEARNING COMPANION</span>
          <h1>
            A little
            <br />
            <em>clarity.</em>
          </h1>
          <button
            className="full"
            disabled={disabled}
            onClick={() => {
              action("reset");
              setSidebar(false);
            }}
          >
            New conversation <span>＋</span>
          </button>
        </div>
        <div className="history-heading">
          <h2>Saved conversations</h2>
          <button
            className="icon-button sidebar-close"
            aria-label="Close conversation list"
            onClick={() => setSidebar(false)}
          >
            ×
          </button>
        </div>
        <ErrorNotice message={history.error} retry={history.reload} />
        <div className="history-list">
          {history.loading ? (
            <Loading />
          ) : history.data?.items.length ? (
            history.data.items.map((thread) => (
              <button
                className={
                  selected === thread.id
                    ? "history-item active"
                    : "history-item"
                }
                key={thread.id}
                disabled={busy}
                onClick={() => {
                  setSelected(thread.id);
                  setSidebar(false);
                }}
              >
                <span>{thread.title}</span>
                <small>{dateTime(thread.updatedAt)} UTC</small>
              </button>
            ))
          ) : (
            <p className="muted">
              Your conversations will appear here after your first reply.
            </p>
          )}
        </div>
        <div className="history-pagination">
          <button
            className="text-button"
            disabled={page === 0 || history.loading}
            onClick={() => setPage((v) => v - 1)}
          >
            ← Newer
          </button>
          <button
            className="text-button"
            disabled={!history.data?.hasNext || history.loading}
            onClick={() => setPage((v) => v + 1)}
          >
            Older →
          </button>
        </div>
        <div className="sidebar-note">
          <span>✦</span>
          <p>
            Find a time. Ask a question.
            <br />
            Take your next step.
          </p>
        </div>
      </aside>
      <section className="chat-panel">
        <header className="chat-heading">
          <div>
            <span className="assistant-icon">✦</span>
            <div>
              <h2>TutorConnect assistant</h2>
              <p>Your next step, a little clearer.</p>
            </div>
          </div>
          <div className="actions">
            <button
              className="secondary small mobile-history"
              onClick={() => setSidebar((v) => !v)}
            >
              History
            </button>
            <Link className="text-link" to="/appointments">
              My appointments ↗
            </Link>
          </div>
        </header>
        {selected !== null ? (
          <Archive id={selected} onBack={() => setSelected(null)} />
        ) : (
          <>
            <div className="chat-scroll" aria-busy={busy}>
              <ErrorNotice message={error} />
              {(uncertain || (!current && !loading)) && (
                <button
                  className="secondary"
                  disabled={loading || busy}
                  onClick={loadCurrent}
                >
                  Refresh conversation
                </button>
              )}
              {loading && <Loading />}
              {current && !current.messages.length && !busy && (
                <div className="chat-welcome">
                  <span className="welcome-star">✦</span>
                  <p className="eyebrow">A GOOD PLACE TO START</p>
                  <h2>
                    What’s on
                    <br />
                    <em>your mind?</em>
                  </h2>
                  <p>
                    I can find sessions, look up your appointments,
                    <br />
                    and help you understand tutoring policies.
                  </p>
                  <div className="suggestions">
                    {suggestions.map((s) => (
                      <button
                        className="suggestion"
                        key={s.title}
                        disabled={disabled}
                        onClick={() => {
                          setDraft(s.prompt);
                          action("chat", s.prompt);
                        }}
                      >
                        <span>↗</span>
                        <strong>{s.title}</strong>
                        <small>{s.text}</small>
                      </button>
                    ))}
                  </div>
                </div>
              )}
              <div aria-live="polite" aria-relevant="additions">
                <Messages messages={current?.messages ?? []} />
                {sending && (
                  <Messages messages={[{ role: "user", text: sending }]} />
                )}
              </div>
              {busy && (
                <div role="status" className="thinking">
                  <span className="spinner" />
                  {sending
                    ? "Finding a little clarity…"
                    : "Updating your conversation…"}
                </div>
              )}
              {pending && (
                <section className="proposal">
                  <div className="proposal-top">
                    <span>YOUR CONFIRMATION IS NEEDED</span>
                    <span>
                      {expired ? "Expired" : "Review before confirming"}
                    </span>
                  </div>
                  <h3>
                    {pending.action === "CANCEL"
                      ? "Cancel this session?"
                      : "Book this session?"}
                  </h3>
                  <p>{pending.summary}</p>
                  {pending.details && (
                    <dl>
                      <div>
                        <dt>Subject</dt>
                        <dd>{pending.details.service}</dd>
                      </div>
                      <div>
                        <dt>Tutor</dt>
                        <dd>{pending.details.tutor}</dd>
                      </div>
                      <div>
                        <dt>Time</dt>
                        <dd>{pending.details.time}</dd>
                      </div>
                    </dl>
                  )}
                  <div className="actions">
                    <button
                      disabled={disabled || expired}
                      onClick={() => action("confirm")}
                    >
                      {expired
                        ? "Ask again to continue"
                        : pending.action === "CANCEL"
                          ? "Confirm cancellation"
                          : "Confirm booking"}{" "}
                      <span>↗</span>
                    </button>
                    <button
                      className="secondary"
                      disabled={disabled}
                      onClick={() => action("discard")}
                    >
                      Dismiss
                    </button>
                  </div>
                </section>
              )}
              <div ref={endRef} />
            </div>
            <form className="chat-composer" onSubmit={submit}>
              <div className="composer-input">
                <label className="sr-only" htmlFor="chat-message">
                  Message TutorConnect assistant
                </label>
                <textarea
                  id="chat-message"
                  ref={inputRef}
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  placeholder="Ask about a session, your schedule, or a policy…"
                  rows={2}
                  maxLength={2000}
                  disabled={disabled}
                  onKeyDown={(e) => {
                    if (
                      e.key === "Enter" &&
                      !e.shiftKey &&
                      !e.nativeEvent.isComposing
                    ) {
                      e.preventDefault();
                      action("chat", draft);
                    }
                  }}
                />
                <button
                  aria-label="Send message"
                  disabled={disabled || !draft.trim()}
                  type="submit"
                >
                  ↗
                </button>
              </div>
              <div className="composer-caption">
                <span>Bookings and cancellations need your confirmation.</span>
                <span>{draft.length}/2000</span>
              </div>
            </form>
          </>
        )}
      </section>
    </section>
  );
}
