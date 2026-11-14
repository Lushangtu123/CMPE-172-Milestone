import { Link, useSearchParams } from "react-router-dom";
import type { FormEvent } from "react";
import { useResource } from "../lib/useResource";
import { useAuth } from "../lib/auth";
import type { Catalog, SlotPage } from "../lib/types";
import { dateParts, Empty, ErrorNotice, Loading } from "../components/ui";

export function Sessions() {
  const [params, setParams] = useSearchParams();
  const { account } = useAuth();
  const catalog = useResource<Catalog>("/api/catalog");
  const slots = useResource<SlotPage>("/api/slots?" + params.toString());
  function filter(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    const next = new URLSearchParams();
    for (const [key, value] of data) if (value) next.set(key, String(value));
    setParams(next);
  }
  function page(value: number) {
    const next = new URLSearchParams(params);
    next.set("page", String(value));
    setParams(next);
  }
  return (
    <>
      <section className="page-heading">
        <div>
          <p className="eyebrow">A TIME TO FOCUS</p>
          <h1>
            Find your
            <br />
            <em>next session.</em>
          </h1>
        </div>
        <p className="intro">
          Bring your questions.
          <br />
          We’ll make room for the answers.
        </p>
      </section>
      <form key={params.toString()} className="filters panel" onSubmit={filter}>
        <div>
          <label htmlFor="provider">Tutor</label>
          <select
            id="provider"
            name="providerId"
            defaultValue={params.get("providerId") ?? ""}
          >
            <option value="">All tutors</option>
            {catalog.data?.providers.map((p) => (
              <option key={p.providerId} value={p.providerId}>
                {p.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="service">Subject</label>
          <select
            id="service"
            name="serviceId"
            defaultValue={params.get("serviceId") ?? ""}
          >
            <option value="">All subjects</option>
            {catalog.data?.services.map((s) => (
              <option key={s.serviceId} value={s.serviceId}>
                {s.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="date">Date · UTC</label>
          <input
            id="date"
            type="date"
            name="date"
            defaultValue={params.get("date") ?? ""}
          />
        </div>
        <button type="submit">
          Find sessions <span>↗</span>
        </button>
        {params.size > 0 && (
          <button
            type="button"
            className="text-button"
            onClick={() => setParams({})}
          >
            Clear
          </button>
        )}
      </form>
      <ErrorNotice message={catalog.error} retry={catalog.reload} />
      <ErrorNotice message={slots.error} retry={slots.reload} />
      <div className="section-caption">
        <h2>
          Available sessions{" "}
          <span className="count">{slots.data?.total ?? "—"}</span>
        </h2>
        <span>ALL TIMES IN UTC</span>
      </div>
      {slots.loading ? (
        <Loading />
      ) : slots.data?.items.length ? (
        <div className="session-grid">
          {slots.data.items.map((slot) => {
            const date = dateParts(slot.startTime);
            return (
              <article className="session-card" key={slot.slotId}>
                <div className="session-top">
                  <span className="subject-label">
                    {slot.durationMinutes} MIN · ONE-ON-ONE
                  </span>
                  <span className="open-dot">Available</span>
                </div>
                <h3>{slot.serviceName}</h3>
                <p className="muted">{slot.description}</p>
                <div className="tutor-line">
                  <span className="avatar">
                    {slot.providerName
                      .split(" ")
                      .map((n) => n[0])
                      .join("")}
                  </span>
                  <div>
                    <strong>{slot.providerName}</strong>
                    <span>Your tutor</span>
                  </div>
                </div>
                <div className="session-bottom">
                  <div className="date-tile">
                    <span>{date.month}</span>
                    <b>{date.day}</b>
                  </div>
                  <div className="slot-time">
                    <strong>{date.weekday}</strong>
                    <span>{date.time} UTC</span>
                  </div>
                  {account?.role !== "PROVIDER" && (
                    <Link
                      className="button round"
                      to={`/book/${slot.slotId}`}
                      aria-label={`Book ${slot.serviceName} on ${date.month} ${date.day}`}
                    >
                      ↗
                    </Link>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      ) : (
        !slots.error && (
          <Empty title="Let’s try another time.">
            <p>
              No sessions match these filters. Try another tutor, subject, or
              date.
            </p>
            <button className="secondary" onClick={() => setParams({})}>
              See all sessions
            </button>
          </Empty>
        )
      )}
      {slots.data && slots.data.total > slots.data.size && (
        <nav className="pagination" aria-label="Session pages">
          <button
            className="secondary"
            disabled={slots.data.page === 0 || slots.loading}
            onClick={() => page(slots.data!.page - 1)}
          >
            ← Previous
          </button>
          <span>
            Page {slots.data.page + 1} of{" "}
            {Math.ceil(slots.data.total / slots.data.size)}
          </span>
          <button
            className="secondary"
            disabled={
              (slots.data.page + 1) * slots.data.size >= slots.data.total ||
              slots.loading
            }
            onClick={() => page(slots.data!.page + 1)}
          >
            Next →
          </button>
        </nav>
      )}
    </>
  );
}
