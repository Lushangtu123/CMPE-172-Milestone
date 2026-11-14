# TutorConnect cloud deployment

The React frontend is built for Vercel, the Spring Boot container for Railway, and the database for AWS RDS MySQL 8.4. Run one backend replica: authentication sessions, pending AI confirmations and the bounded model context are held in memory. Saved conversations, appointments and the outbox are persisted in MySQL. A backend restart signs users out and discards unconfirmed proposals.

## AWS RDS

Use a Single-AZ `db.t4g.micro` with 20 GiB storage for the portfolio demo. Disable storage autoscaling and optional paid add-ons. Keep encryption and automated backups enabled. Free-plan credit balances and expiry must be checked in the actual AWS account; this configuration consumes credits and is not a promise of indefinite free hosting.

Create the `tutorconnect` database and a dedicated security group. When the backend is outside AWS, a public RDS endpoint still needs a restrictive inbound rule for MySQL port 3306. Initially allow only the administrator's current IP (`/32`). Add the backend's specific outbound IP when known. Never allow `0.0.0.0/0` inbound. Railway static outbound IP availability depends on the plan; a changing trial egress address requires updating this rule, and may make Railway unsuitable for a stable free deployment.

Create an ignored `.env.rds-admin` file locally:

```dotenv
AWS_REGION=us-east-2
RDS_HOSTNAME=your-instance.us-east-2.rds.amazonaws.com
RDS_PORT=3306
RDS_DB_NAME=tutorconnect
RDS_USERNAME=tutoradmin
RDS_PASSWORD=your-master-password
```

Protect this file with `chmod 600 .env.rds-admin`. With Docker running, initialize a new database:

```sh
python3 scripts/bootstrap-rds.py --demo-data
```

This explicitly opts into inserting the public demo accounts into an empty database. Omit `--demo-data` to initialize the schema without demo users. The script verifies the AWS TLS certificate and hostname, creates the application user with database-scoped privileges and SSL required, and saves generated app credentials to ignored `.env.rds` with mode 600. It never drops tables or resets an existing user's password. The app needs CREATE/INDEX/REFERENCES for its idempotent schema initializers; it has no global database privileges or DROP privilege. Keep the master credentials out of Railway.

The Docker build downloads AWS's regional public CA bundle and builds `/app/rds-ca.p12`. The `rds` profile uses Connector/J `sslMode=VERIFY_IDENTITY` and UTC database sessions. For another supported US region set `AWS_REGION` when running the truststore script; a Docker build for a different region should use that region's bundle too.

## Railway backend

Connect this repository and select the `react-rds-upgrade` branch while evaluating the upgrade. The root `railway.json` selects the Dockerfile, `/health` readiness check and one replica. Configure the variables from `.env.rds` using Railway's private variable editor. No database password or Gemini key belongs in git or frontend variables.

Required variables:

- `SPRING_PROFILES_ACTIVE=rds`
- `RDS_HOSTNAME`, `RDS_PORT=3306`, `RDS_DB_NAME=tutorconnect`
- `RDS_USERNAME=tutorconnect`, generated `RDS_PASSWORD`
- generated `NOTIFICATION_SERVICE_TOKEN`
- `AI_ENABLED=false` until a valid Gemini key is configured

To enable AI, set `AI_ENABLED=true`, `GEMINI_API_KEY`, `GEMINI_CHAT_MODEL=gemini-3.1-flash-lite`, `GEMINI_EMBEDDING_MODEL=gemini-embedding-001` and `AI_REMINDERS_ENABLED=true`. The notification transport remains a local demo sink; it does not send real email. Railway supplies `PORT`, and the RDS profile uses secure cookies and forwarded HTTPS headers. Generate an HTTPS service domain and wait for `/health` to pass before connecting the frontend. Review Railway's remaining trial credits and enforce spending limits in the workspace settings.

## Vercel frontend

Import the repository as a new project. Set the root directory to `frontend`, select Node.js 24 and use the checked-in `vercel.json`. Keep the framework preset **Other** so the Build Output API configuration controls routing.

Set the build environment variable `BACKEND_URL` to the HTTPS origin of the healthy Railway service, for example `https://your-backend.up.railway.app`. It must not contain a path, credentials or query string. The build fails if this variable is absent or invalid. Run `npm run build:vercel` to produce `.vercel/output`.

The generated routes proxy `/api/*`, `/login` and `/logout` to Spring Boot. The browser sees same-origin requests, so session cookies work without cross-site cookies or permissive CORS. Session/API responses use `private, no-store`. Static assets are cached by their content hash, and other paths fall back to the SPA entry point. No RDS credentials or Gemini keys are bundled into JavaScript.

Before sharing a link, verify HTTPS login, customer/provider role checks, one booking and cancellation, saved chat history, and the explicit AI confirmation flow using a configured model. Test MySQL concurrency against the dedicated local `tutorconnect_test` database; never point the reset-based test profile at RDS's application database.
