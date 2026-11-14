import { useState, type FormEvent } from "react";
import { api } from "../lib/api";
import { useResource } from "../lib/useResource";
import type { Appointment, Service, Slot } from "../lib/types";
import {
  Badge,
  ConfirmDialog,
  dateTime,
  Empty,
  ErrorNotice,
  Loading,
} from "../components/ui";
import { AppointmentRows } from "./Appointments";

export function ProviderWorkspace() {
  const services = useResource<Service[]>("/api/provider/services");
  const slots = useResource<Slot[]>("/api/provider/slots");
  const appointments = useResource<Appointment[]>("/api/provider/appointments");
  const [serviceId, setServiceId] = useState("");
  const [start, setStart] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [remove, setRemove] = useState<Slot | null>(null);
  const [reminding, setReminding] = useState(false);
  const [reminderMessage, setReminderMessage] = useState("");
  const [reminderError, setReminderError] = useState("");
  const service = services.data?.find((s) => String(s.serviceId) === serviceId);
  const end =
    start && service
      ? new Date(
          new Date(start + "Z").getTime() + service.durationMinutes * 60000,
        )
          .toISOString()
          .slice(0, 16)
      : "";
  async function create(e: FormEvent) {
    e.preventDefault();
    if (!end) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await api("/api/provider/slots", {
        data: { serviceId, startTime: start, endTime: end },
      });
      setStart("");
      setNotice("Your availability is published.");
      slots.reload();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function removeSlot() {
    if (!remove) return;
    setBusy(true);
    setError("");
    try {
      await api(`/api/provider/slots/${remove.slotId}/remove-upcoming`, {
        data: {},
      });
      setRemove(null);
      slots.reload();
      setNotice("Availability removed.");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function remind() {
    setReminding(true);
    setReminderMessage("");
    setReminderError("");
    try {
      const result = await api<{
        scanned: number;
        enqueued: number;
        failed: number;
      }>("/api/provider/ai/reminders/run", { data: {}, timeout: 90000 });
      setReminderMessage(
        `Checked ${result.scanned} upcoming appointments. Queued ${result.enqueued} reminders. ${result.failed ? `${result.failed} could not be prepared; try again later.` : ""}`,
      );
    } catch (e) {
      setReminderError((e as Error).message);
    } finally {
      setReminding(false);
    }
  }
  return (
    <>
      <section className="page-heading">
        <div>
          <p className="eyebrow">YOUR TUTOR WORKSPACE</p>
          <h1>
            Make space
            <br />
            <em>for learning.</em>
          </h1>
        </div>
        <p className="intro">
          Set your availability.
          <br />
          We’ll keep your schedule together.
        </p>
      </section>
      <div className="provider-layout">
        <section className="panel availability-form">
          <p className="eyebrow">OPEN UP YOUR CALENDAR</p>
          <h2>Add a session.</h2>
          <ErrorNotice message={services.error} retry={services.reload} />
          <form onSubmit={create}>
            <label htmlFor="tutor-service">Your subject</label>
            <select
              id="tutor-service"
              value={serviceId}
              onChange={(e) => setServiceId(e.target.value)}
              required
            >
              <option value="">Choose a subject</option>
              {services.data?.map((s) => (
                <option key={s.serviceId} value={s.serviceId}>
                  {s.name} · {s.durationMinutes} min
                </option>
              ))}
            </select>
            <label htmlFor="slot-start">Start date & time · UTC</label>
            <input
              id="slot-start"
              type="datetime-local"
              value={start}
              onChange={(e) => setStart(e.target.value)}
              min={new Date().toISOString().slice(0, 16)}
              required
            />
            <div className="end-preview">
              <span>Ends at</span>
              <strong>
                {end
                  ? dateTime(end) + " UTC"
                  : "Choose a subject and start time"}
              </strong>
            </div>
            <p className="muted">
              The end time follows your subject’s duration. Times cannot overlap
              another session.
            </p>
            <button className="full" disabled={busy || !end}>
              {busy ? "Saving…" : "Publish availability ↗"}
            </button>
          </form>
        </section>
        <section>
          <div className="section-caption">
            <h2>Your availability</h2>
            <span>UTC</span>
          </div>
          <ErrorNotice message={slots.error} retry={slots.reload} />
          {!remove && <ErrorNotice message={error} />}{" "}
          {notice && (
            <p className="notice" role="status">
              {notice}
            </p>
          )}
          {slots.loading ? (
            <Loading />
          ) : slots.data?.length ? (
            <div className="availability-list">
              {slots.data.map((s) => (
                <article key={s.slotId}>
                  <div>
                    <h3>{s.serviceName}</h3>
                    <p>{dateTime(s.startTime)} UTC</p>
                  </div>
                  <Badge>{s.status}</Badge>
                  {s.status === "OPEN" &&
                    new Date(s.startTime + "Z").getTime() > Date.now() && (
                      <button
                        className="text-button danger-text"
                        disabled={busy}
                        onClick={() => {
                          setRemove(s);
                          setError("");
                        }}
                      >
                        Remove
                      </button>
                    )}
                </article>
              ))}
            </div>
          ) : (
            <Empty title="Ready when you are.">
              <p>Add your first session to make it available to customers.</p>
            </Empty>
          )}
        </section>
      </div>
      <section className="reminder-panel panel">
        <div>
          <p className="eyebrow">A LITTLE ENCOURAGEMENT</p>
          <h2>Keep the momentum.</h2>
          <p className="muted">
            Prepare study reminders for your booked sessions starting in the
            next 24 hours. This portfolio demo records notifications without
            sending real email.
          </p>
        </div>
        <button className="secondary" disabled={reminding} onClick={remind}>
          {reminding ? "Preparing reminders…" : "Prepare reminders ✦"}
        </button>
        <ErrorNotice message={reminderError} />
        {reminderMessage && (
          <p role="status" className="notice">
            {reminderMessage}
          </p>
        )}
      </section>
      <section className="provider-appointments">
        <div className="section-caption">
          <h2>Customer appointments</h2>
          <span>{appointments.data?.length ?? "—"} TOTAL</span>
        </div>
        <ErrorNotice message={appointments.error} retry={appointments.reload} />
        {appointments.loading ? (
          <Loading />
        ) : appointments.data?.length ? (
          <AppointmentRows items={appointments.data} provider />
        ) : (
          <Empty title="Good company is on its way.">
            <p>Bookings for your sessions will appear here.</p>
          </Empty>
        )}
      </section>
      {remove && (
        <ConfirmDialog
          title="Remove this availability?"
          busy={busy}
          onConfirm={removeSlot}
          onClose={() => setRemove(null)}
          label="Remove availability"
        >
          <p>
            {remove.serviceName}
            <br />
            {dateTime(remove.startTime)} UTC
          </p>
          <p className="muted">
            This time will no longer appear in available sessions.
          </p>
          <ErrorNotice message={error} />
        </ConfirmDialog>
      )}
    </>
  );
}
