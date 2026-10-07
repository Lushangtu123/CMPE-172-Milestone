# Milestone 2 implementation design

Approved scope: extend the existing JDBC layered skeleton into a session-authenticated tutoring booking application. Keep Java 17 and Spring Boot; add Spring Security, Thymeleaf and the MySQL JDBC driver. MySQL is the default persistent database; the demo/test profile uses H2 for fast local feedback.

Authentication uses email as the username, BCrypt password hashes, JDBC-loaded CUSTOMER/PROVIDER roles, server-side sessions and CSRF-protected mutations. A custom principal carries the authenticated database user ID; request bodies never choose the acting user. API authentication failures return 401 and role violations 403.

Booking locks the existing availability slot by primary key with SELECT FOR UPDATE inside a READ_COMMITTED @Transactional service method. All booking, cancellation and slot removal operations use the same slot-first lock order. A generated active_slot_id equals slot_id for BOOKED/COMPLETED appointments and NULL for CANCELLED appointments. UNIQUE(active_slot_id) is the database-level slot backstop, preserving cancellation history while allowing future slots to be booked again. Bound retries wrap fresh transactions only for transient lock failures; ordinary conflicts return 409 without retry.

Customers browse future available slots with provider/service/date filters, SQL LIMIT/OFFSET and stable ordering; book, view upcoming/history and cancel their own future bookings. Providers manage slots only for their own services and view their own appointments. Past BOOKED records transition to COMPLETED during appointment-list reads. Booked slots cannot be removed; removal soft-deactivates slots so appointment history remains referentially intact.

The UI uses server-rendered HTML with no external assets: home, login, filtered slots, booking form, confirmation, customer appointments and provider dashboard. JSON endpoints live under /api. Global errors produce safe messages and 400/403/404/409/503 statuses. The public home and slots API are retained under /api/home and /api/slots.

Validation: service unit tests, HTTP/session/CSRF/RBAC/ownership integration tests, cancellation and rebooking, rollback behavior and simultaneous two-customer attempts with exactly one success and one conflict, verified with MySQL. Deliver source, report and code walkthrough script. A real student-recorded video and final personal filename remain user deliverables.
