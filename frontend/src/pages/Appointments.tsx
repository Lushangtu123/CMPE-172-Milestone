import { useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";
import { useResource } from "../lib/useResource";
import type { Appointment } from "../lib/types";
import {
  Badge,
  ConfirmDialog,
  dateParts,
  dateTime,
  Empty,
  ErrorNotice,
  Loading,
} from "../components/ui";

export function AppointmentRows({
  items,
  provider = false,
  onCancel,
}: {
  items: Appointment[];
  provider?: boolean;
  onCancel?: (item: Appointment) => void;
}) {
  return (
    <div className="appointment-list">
      {items.map((item) => {
        const date = dateParts(item.startTime);
        return (
          <article className="appointment-row" key={item.appointmentId}>
            <div className="date-tile">
              <span>{date.month}</span>
              <b>{date.day}</b>
            </div>
            <div className="appointment-info">
              <span className="row-reference">
                APPOINTMENT #{item.appointmentId}
              </span>
              <h3>{item.serviceName}</h3>
              <p>
                {provider ? item.customerName : item.providerName}{" "}
                <span>·</span> {dateTime(item.startTime)} UTC
              </p>
            </div>
            <Badge>{item.status}</Badge>
            {onCancel &&
              item.status === "BOOKED" &&
              new Date(item.startTime + "Z").getTime() > Date.now() && (
                <button
                  className="text-button danger-text"
                  onClick={() => onCancel(item)}
                >
                  Cancel
                </button>
              )}
          </article>
        );
      })}
    </div>
  );
}
export function Appointments() {
  const resource = useResource<Appointment[]>("/api/customer/appointments");
  const [tab, setTab] = useState("upcoming");
  const [cancel, setCancel] = useState<Appointment | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const upcoming = resource.data?.filter((a) => a.status === "BOOKED") ?? [];
  const history = resource.data?.filter((a) => a.status !== "BOOKED") ?? [];
  async function confirmCancel() {
    if (!cancel) return;
    setBusy(true);
    setError("");
    try {
      await api(`/api/customer/appointments/${cancel.appointmentId}/cancel`, {
        data: {},
      });
      setCancel(null);
      resource.reload();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <section className="page-heading">
        <div>
          <p className="eyebrow">MAKE TIME FOR YOURSELF</p>
          <h1>
            Your learning.
            <br />
            <em>On the calendar.</em>
          </h1>
        </div>
        <Link className="button" to="/sessions">
          Book a session <span>↗</span>
        </Link>
      </section>
      <div className="tab-bar" role="group" aria-label="Appointment filter">
        <button
          className={tab === "upcoming" ? "selected" : ""}
          aria-pressed={tab === "upcoming"}
          onClick={() => setTab("upcoming")}
        >
          Upcoming <span>{upcoming.length}</span>
        </button>
        <button
          className={tab === "history" ? "selected" : ""}
          aria-pressed={tab === "history"}
          onClick={() => setTab("history")}
        >
          Past & cancelled <span>{history.length}</span>
        </button>
      </div>
      <ErrorNotice message={resource.error} retry={resource.reload} />
      {!cancel && <ErrorNotice message={error} />}
      {resource.loading ? (
        <Loading />
      ) : (tab === "upcoming" ? upcoming : history).length ? (
        <AppointmentRows
          items={tab === "upcoming" ? upcoming : history}
          onCancel={(a) => {
            setError("");
            setCancel(a);
          }}
        />
      ) : (
        !resource.error && (
          <Empty
            title={
              tab === "upcoming"
                ? "A little room for something new."
                : "Your story starts here."
            }
          >
            <p>
              {tab === "upcoming"
                ? "Find your next session and put a little progress on the calendar."
                : "Your completed and cancelled sessions will appear here."}
            </p>
            <Link className="button secondary" to="/sessions">
              Explore sessions ↗
            </Link>
          </Empty>
        )
      )}
      {cancel && (
        <ConfirmDialog
          title="Cancel this session?"
          busy={busy}
          onConfirm={confirmCancel}
          onClose={() => setCancel(null)}
          label="Cancel session"
        >
          <p>
            {cancel.serviceName} with {cancel.providerName}
            <br />
            {dateTime(cancel.startTime)} UTC
          </p>
          <p className="muted">
            Your appointment stays in your history. The time becomes available
            for someone else.
          </p>
          <ErrorNotice message={error} />
        </ConfirmDialog>
      )}
    </>
  );
}
