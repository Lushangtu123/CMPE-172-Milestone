# TutorConnect

The TutorConnect is a project to connect students who need to be tutored and tutors who can tutor in specific subject. In this phase (Milestone 1) we will create a layered Spring Boot framework skeleton. In this phase we will create five core data tables, load sample data and provide two read endpoints. The next phase will be adding reservation, login and other features.

This project does not use the ORM framework. The processing workflow for each endpoint is as follows:

```text
Controller → Service → Repository → JdbcTemplate → Database → DTO
```

## Endpoint

`GET /`

```text
HomeController → HomeService → HomeRepository → JdbcTemplate → Database → HomeDto
```

Return the application name, scenario description, number of mentors/services/free time slots, and the mentor name loaded from the database.

`GET /slots`

```text
SlotController → SlotService → SlotRepository → JdbcTemplate → Database → SlotDto
```

Return detailed information for each available slot (slot), including the mentor, service, time slot and status (`OPEN` or `BOOKED`). When there is an appointment record referencing that time slot, the time slot status is `BOOKED` (booked).

## Project structure

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

The `DispatcherServlet` in Spring MVC acts as the current front controller. `HomeController` and `SlotController` are responsible only for receiving HTTP requests and delegating the processing logic to the service layer.

## Database

The `schema.sql` script created five tables. Each table contains a surrogate primary key (surrogate primary key). Foreign keys are used to represent the relationship of belonging and reservation:

| Name of the table | Relationship |
| --- | --- |
| `users` | Student and mentor accounts. Email field is unique. `role` field is `CUSTOMER` or `PROVIDER`. |
| `providers` | Each user can correspond to at most one mentor's information (`user_id` is unique). |
| `services` | Each mentor provides multiple services. `duration_minutes > 0`. |
| `availability_slots` | Each service includes multiple available time slots. `end_time > start_time`. A service cannot be used in the same time slot. |
| Reservation | Students reserve a time slot (slot), which is identified by a unique `slot_id`. |

Mechanism to prevent duplicate reservations:

```sql
CONSTRAINT uq_appointment_slot UNIQUE (slot_id)
```

Once a reservation is associated with a time slot, the database will refuse second reservation requests for the same time slot.

The `seed.sql` script will pre-load two students, two tutors, three services, five time slots, and one confirmed appointment record. The `application.properties` configuration will use the H2 database in Spring Boot's memory and execute the relevant script when the application starts.

## Run

Requires JDK 17 or later and Maven.

```bash
mvn spring-boot:run
```

Then:

```bash
curl http://localhost:8080/
curl http://localhost:8080/slots
```

The test targeting pre-loaded data covers the two endpoints above:

```bash
mvn test
```
