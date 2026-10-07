INSERT INTO users (user_id, name, email, password_hash, role) VALUES
    (1, 'Maya Patel', 'maya.patel@example.com', '$2a$10$1jmKx2weU4LzLmzev3qFN.tPuwTRF6QIMQwsG6Zm4PAl6LPi14kCW', 'CUSTOMER'),
    (2, 'Sam Rivera', 'sam.rivera@example.com', '$2a$10$1jmKx2weU4LzLmzev3qFN.tPuwTRF6QIMQwsG6Zm4PAl6LPi14kCW', 'CUSTOMER'),
    (3, 'Priya Shah', 'priya.shah@example.com', '$2a$10$1jmKx2weU4LzLmzev3qFN.tPuwTRF6QIMQwsG6Zm4PAl6LPi14kCW', 'PROVIDER'),
    (4, 'Jordan Lee', 'jordan.lee@example.com', '$2a$10$1jmKx2weU4LzLmzev3qFN.tPuwTRF6QIMQwsG6Zm4PAl6LPi14kCW', 'PROVIDER');

INSERT INTO providers (provider_id, user_id, bio) VALUES
    (1, 3, 'High-school math and physics tutor.'),
    (2, 4, 'College writing and essay coach.');

INSERT INTO services (service_id, provider_id, name, description, duration_minutes) VALUES
    (1, 1, 'Algebra Coaching', 'One-on-one algebra problem solving.', 60),
    (2, 1, 'Physics Problem Solving', 'Mechanics practice with worked examples.', 45),
    (3, 2, 'Essay Coaching', 'Outline, draft, and revision coaching.', 60);

INSERT INTO availability_slots (slot_id, service_id, start_time, end_time) VALUES
    (1, 1, TIMESTAMPADD(DAY, 1, CURRENT_TIMESTAMP), TIMESTAMPADD(MINUTE, 60, TIMESTAMPADD(DAY, 1, CURRENT_TIMESTAMP))),
    (2, 1, TIMESTAMPADD(DAY, 2, CURRENT_TIMESTAMP), TIMESTAMPADD(MINUTE, 60, TIMESTAMPADD(DAY, 2, CURRENT_TIMESTAMP))),
    (3, 2, TIMESTAMPADD(DAY, 3, CURRENT_TIMESTAMP), TIMESTAMPADD(MINUTE, 45, TIMESTAMPADD(DAY, 3, CURRENT_TIMESTAMP))),
    (4, 3, TIMESTAMPADD(DAY, 4, CURRENT_TIMESTAMP), TIMESTAMPADD(MINUTE, 60, TIMESTAMPADD(DAY, 4, CURRENT_TIMESTAMP))),
    (5, 3, TIMESTAMPADD(DAY, 5, CURRENT_TIMESTAMP), TIMESTAMPADD(MINUTE, 60, TIMESTAMPADD(DAY, 5, CURRENT_TIMESTAMP)));

INSERT INTO appointments (appointment_id, user_id, slot_id, status, created_at) VALUES
    (1, 1, 2, 'BOOKED', TIMESTAMP '2026-09-20 10:00:00');
