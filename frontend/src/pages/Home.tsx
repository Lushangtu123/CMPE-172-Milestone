import { Link } from "react-router-dom";
import { useResource } from "../lib/useResource";
import type { SlotPage } from "../lib/types";
import { dateParts, ErrorNotice } from "../components/ui";

export function Home() {
  const info = useResource<{
    providerCount: number;
    serviceCount: number;
    openSlotCount: number;
  }>("/api/home");
  const slots = useResource<SlotPage>("/api/slots?size=1");
  const slot = slots.data?.items[0];
  const date = slot && dateParts(slot.startTime);
  return (
    <>
      <section className="home-hero">
        <div className="hero-copy">
          <p className="eyebrow">
            <span className="live-dot" /> A LITTLE SUPPORT. A BIG DIFFERENCE.
          </p>
          <h1>
            Your next
            <br />
            breakthrough
            <br />
            <em>starts here.</em>
          </h1>
          <p className="intro">
            A tricky equation. An unfinished essay. Whatever’s next, find
            someone who can help you move forward.
          </p>
          <div className="actions">
            <Link to="/sessions" className="button">
              Find your session <span>↗</span>
            </Link>
            <Link to="/assistant" className="text-link">
              Meet your AI assistant <span>✦</span>
            </Link>
          </div>
          <p className="hero-note">
            One-on-one tutoring. Time set aside for you.
          </p>
        </div>
        <div className="hero-board">
          <div className="board-heading">
            <span>MAKE ROOM FOR PROGRESS</span>
            <span>↗</span>
          </div>
          <div className="board-title">
            Good questions.
            <br />
            <em>Great company.</em>
          </div>
          <div className="board-orbit" aria-hidden="true">
            <span>+</span>
            <span>∑</span>
            <span>“a”</span>
          </div>
          <div className="spotlight">
            <p className="eyebrow">NEXT AVAILABLE SESSION</p>
            {slot && date ? (
              <>
                <div className="spotlight-date">
                  <b>{date.day}</b>
                  <span>
                    {date.month}
                    <br />
                    {date.time} UTC
                  </span>
                </div>
                <h3>{slot.serviceName}</h3>
                <p>
                  with {slot.providerName} · {slot.durationMinutes} min
                </p>
                <Link
                  to={`/book/${slot.slotId}`}
                  className="round-link"
                  aria-label={`View ${slot.serviceName}`}
                >
                  ↗
                </Link>
              </>
            ) : (
              <p>
                {slots.loading
                  ? "Finding your next opportunity…"
                  : "Explore the schedule for your next session."}
              </p>
            )}
          </div>
        </div>
      </section>
      <ErrorNotice message={info.error} retry={info.reload} />
      <section className="stats-strip" aria-label="Live tutoring availability">
        <div>
          <strong>{info.data?.providerCount ?? "—"}</strong>
          <span>Tutors in your corner</span>
        </div>
        <div>
          <strong>{info.data?.serviceCount ?? "—"}</strong>
          <span>Ways to move forward</span>
        </div>
        <div>
          <strong>{info.data?.openSlotCount ?? "—"}</strong>
          <span>Sessions ready to book</span>
        </div>
        <p>
          More understanding.
          <br />
          <em>Less going it alone.</em>
        </p>
      </section>
      <section className="home-bottom">
        <div>
          <p className="eyebrow">FROM “I’M STUCK” TO “I GET IT”</p>
          <h2>
            Small steps.
            <br />
            <em>Lasting confidence.</em>
          </h2>
        </div>
        <div className="learning-steps">
          <article>
            <span>01 / FIND YOUR FIT</span>
            <h3>Start with what you need.</h3>
            <p>
              Browse subjects, tutors, and available times to find the right
              session.
            </p>
          </article>
          <article>
            <span>02 / MAKE IT HAPPEN</span>
            <h3>A place on your calendar.</h3>
            <p>
              Review the details and confirm your booking. Your appointments
              stay in one place.
            </p>
          </article>
          <article>
            <span>03 / ASK AWAY</span>
            <h3>Help, before the session.</h3>
            <p>
              Ask the assistant to find availability or explain a policy. You
              approve every booking change.
            </p>
          </article>
        </div>
      </section>
    </>
  );
}
