import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api } from "../lib/api";
import { useResource } from "../lib/useResource";
import type { Appointment, Slot } from "../lib/types";
import { Badge, dateTime, ErrorNotice, Loading } from "../components/ui";

export function Booking() {
  const { id } = useParams();
  const resource = useResource<Slot>(`/api/slots/${id}`);
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function book() {
    if (!resource.data) return;
    setBusy(true);
    setError("");
    try {
      const result = await api<{ appointmentId: number }>(
        "/api/customer/appointments",
        {
          data: {
            slotId: resource.data.slotId,
            serviceId: resource.data.serviceId,
          },
        },
      );
      navigate(`/appointments/${result.appointmentId}`, { replace: true });
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Link className="back-link" to="/sessions">
        ← Back to sessions
      </Link>
      <section className="detail-layout">
        <div>
          <p className="eyebrow">A LITTLE TIME. A LOT OF POSSIBILITY.</p>
          <h1>
            Make it
            <br />
            <em>your session.</em>
          </h1>
          <p className="intro">
            Check the details, bring your questions,
            <br />
            and take the next step.
          </p>
        </div>
        <div className="panel detail-panel">
          <ErrorNotice message={resource.error} retry={resource.reload} />
          {resource.loading && <Loading />}
          {resource.data && (
            <>
              <p className="eyebrow">YOUR SESSION DETAILS</p>
              <h2>{resource.data.serviceName}</h2>
              <dl className="details-list">
                <div>
                  <dt>Tutor</dt>
                  <dd>{resource.data.providerName}</dd>
                </div>
                <div>
                  <dt>Starts</dt>
                  <dd>{dateTime(resource.data.startTime)} UTC</dd>
                </div>
                <div>
                  <dt>Ends</dt>
                  <dd>{dateTime(resource.data.endTime)} UTC</dd>
                </div>
                <div>
                  <dt>Duration</dt>
                  <dd>{resource.data.durationMinutes} minutes</dd>
                </div>
              </dl>
              <ErrorNotice message={error} />
              <p className="muted">
                Your session is reserved when the confirmation appears.
              </p>
              <button
                className="full"
                disabled={
                  busy ||
                  resource.data.status !== "OPEN" ||
                  new Date(resource.data.startTime + "Z").getTime() <=
                    Date.now()
                }
                onClick={book}
              >
                {busy
                  ? "Reserving your session…"
                  : resource.data.status === "OPEN"
                    ? "Confirm booking ↗"
                    : "Session unavailable"}
              </button>
              <Link to="/sessions" className="cancel-link">
                Choose another session
              </Link>
            </>
          )}
        </div>
      </section>
    </>
  );
}
export function Confirmation() {
  const { id } = useParams();
  const resource = useResource<Appointment>(`/api/customer/appointments/${id}`);
  return (
    <div className="confirmation panel">
      <ErrorNotice message={resource.error} retry={resource.reload} />
      {resource.loading && <Loading />}
      {resource.data && (
        <>
          <span className="success-seal" aria-hidden="true">
            ✓
          </span>
          <p className="eyebrow">APPOINTMENT #{resource.data.appointmentId}</p>
          <h1>
            {resource.data.status === "BOOKED" ? (
              <>
                You’re
                <br />
                <em>all booked.</em>
              </>
            ) : (
              <>
                Your session
                <br />
                <em>details.</em>
              </>
            )}
          </h1>
          <Badge>{resource.data.status}</Badge>
          <h2>{resource.data.serviceName}</h2>
          <p>with {resource.data.providerName}</p>
          <p className="confirmation-time">
            {dateTime(resource.data.startTime)} UTC
          </p>
          <div className="actions">
            <Link className="button" to="/appointments">
              My appointments ↗
            </Link>
            <Link className="secondary button" to="/assistant">
              Ask the assistant ✦
            </Link>
          </div>
        </>
      )}
    </div>
  );
}
