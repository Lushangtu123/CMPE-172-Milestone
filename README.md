# TutorConnect

TutorConnect connects students who want coaching with tutors who offer subject-specific sessions. Milestone 1 is a layered Spring Boot skeleton. It creates the five core tables, loads sample data, and exposes two read endpoints. Later milestones add booking, login, and the remaining features.

There is no ORM. Each endpoint follows:

```text
Controller → Service → Repository → JdbcTemplate → Database → DTO
```

## Endpoints

`GET /`

```text
HomeController → HomeService → HomeRepository → JdbcTemplate → Database → HomeDto
```

Returns application name, scenario, provider/service/open-slot counts, and provider names loaded from the database.

`GET /slots`

```text
SlotController → SlotService → SlotRepository → JdbcTemplate → Database → SlotDto
```

Returns each availability slot with its tutor, service, time window, and `OPEN` or `BOOKED` status. A slot is `BOOKED` when an appointment row references it.

## Project layout

```text
├── pom.xml
├── README.md
└── src/
    └── main/
        ├── java/com/example/tutorconnect/
        │   ├── TutorConnectApplication.java
        │   ├── controller/
        │   │   ├── HomeController.java
        │   │   └── SlotController.java
        │   ├── service/
        │   │   ├── HomeService.java
        │   │   └── SlotService.java
        │   ├── repository/
        │   │   ├── HomeRepository.java
        │   │   └── SlotRepository.java
        │   └── dto/
        │       ├── HomeDto.java
        │       └── SlotDto.java
        └── resources/
            ├── application.properties
            ├── schema.sql
            └── seed.sql
```

Spring MVC `DispatcherServlet` is the Front Controller. `HomeController` and `SlotController` only accept the HTTP request and delegate to the service layer.

## Database

`schema.sql` creates five tables. Each has a surrogate primary key. Foreign keys express ownership and reservation:

| Table | Relationship |
| --- | --- |
| `users` | Student and tutor accounts. `email` is unique. `role` is `CUSTOMER` or `PROVIDER`. |
| `providers` | At most one tutor profile per user (`user_id` unique). |
| `services` | Many services per provider. `duration_minutes > 0`. |
| `availability_slots` | Many slots per service. `end_time > start_time`. The same service cannot repeat an identical window. |
| `appointments` | A student books a slot. `slot_id` is unique. |

Double-booking guard:

```sql
CONSTRAINT uq_appointment_slot UNIQUE (slot_id)
```

After one appointment references a slot, the database rejects a second appointment for that same slot.

`seed.sql` loads two students, two tutors, three services, five slots, and one booked appointment. `application.properties` points Spring Boot at an in-memory H2 database and runs both scripts on startup.

## Run

Requires JDK 17 or newer and Maven.

```bash
mvn spring-boot:run
```

Then:

```bash
curl http://localhost:8080/
curl http://localhost:8080/slots
```

Tests cover both endpoints against the seeded database:

```bash
mvn test
```
