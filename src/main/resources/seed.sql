INSERT INTO users (user_id, name, email, password_hash, role) VALUES
    (1, 'Maya Patel', 'maya.patel@example.com', 'not-used-in-milestone-1', 'CUSTOMER'),
    (2, 'Sam Rivera', 'sam.rivera@example.com', 'not-used-in-milestone-1', 'CUSTOMER'),
    (3, 'Priya Shah', 'priya.shah@example.com', 'not-used-in-milestone-1', 'PROVIDER'),
    (4, 'Jordan Lee', 'jordan.lee@example.com', 'not-used-in-milestone-1', 'PROVIDER');

INSERT INTO providers (provider_id, user_id, bio) VALUES
    (1, 3, 'High-school math and physics tutor.'),
    (2, 4, 'College writing and essay coach.');

INSERT INTO services (service_id, provider_id, name, description, duration_minutes) VALUES
    (1, 1, 'Algebra Coaching', 'One-on-one algebra problem solving.', 60),
    (2, 1, 'Physics Problem Solving', 'Mechanics practice with worked examples.', 45),
    (3, 2, 'Essay Coaching', 'Outline, draft, and revision coaching.', 60);

INSERT INTO availability_slots (slot_id, service_id, start_time, end_time) VALUES
    (1, 1, TIMESTAMP '2026-10-05 15:00:00', TIMESTAMP '2026-10-05 16:00:00'),
    (2, 1, TIMESTAMP '2026-10-06 15:00:00', TIMESTAMP '2026-10-06 16:00:00'),
    (3, 2, TIMESTAMP '2026-10-07 17:00:00', TIMESTAMP '2026-10-07 17:45:00'),
    (4, 3, TIMESTAMP '2026-10-08 14:00:00', TIMESTAMP '2026-10-08 15:00:00'),
    (5, 3, TIMESTAMP '2026-10-09 14:00:00', TIMESTAMP '2026-10-09 15:00:00');

INSERT INTO appointments (appointment_id, user_id, slot_id, status, created_at) VALUES
    (1, 1, 2, 'BOOKED', TIMESTAMP '2026-09-20 10:00:00');

ALTER TABLE users ALTER COLUMN user_id RESTART WITH 5;
ALTER TABLE providers ALTER COLUMN provider_id RESTART WITH 3;
ALTER TABLE services ALTER COLUMN service_id RESTART WITH 4;
ALTER TABLE availability_slots ALTER COLUMN slot_id RESTART WITH 6;
ALTER TABLE appointments ALTER COLUMN appointment_id RESTART WITH 2;
