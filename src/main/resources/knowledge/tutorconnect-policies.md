## policy-cancellation
Customers can cancel their own future BOOKED appointments. Past, COMPLETED, or already CANCELLED appointments cannot be cancelled. Cancellation keeps appointment history and releases the slot for another customer. An assistant cancellation requires review of the server summary and an explicit Confirm action. This demo defines no refund or payment policy.

## policy-hours
TutorConnect displays all appointment times in UTC. Available tutoring hours are the future slots that providers publish. Check live availability for exact dates and times; this FAQ does not promise fixed daily opening hours.

## policy-services
The demo offers Algebra Coaching for 60 minutes, Physics Problem Solving for 45 minutes, and Essay Coaching for 60 minutes. Check live availability for current providers, services and dates. A slot must match its service and duration.

## policy-booking
Only a logged-in customer can book tutoring sessions. A session must be in the future and still available when the transaction commits. Two customers cannot reserve the same active slot. The assistant first proposes a booking; the customer reviews the summary and clicks Confirm. If another customer takes the slot, choose an alternative.

## policy-reminders
The autonomous reminder job scans BOOKED appointments beginning within the next 24 hours and asks Gemini to generate a short study reminder. It sends each reminder through the notification service. The demo notification service stores messages; it does not send actual email or SMS. Notifications can be delayed during service outages without undoing an appointment.

## policy-access
Customers can see and cancel only their own appointments. Providers manage their own services and availability. Chat messages cannot change account roles. When this knowledge source lacks an answer, the assistant should say it does not know and suggest asking the provider.
