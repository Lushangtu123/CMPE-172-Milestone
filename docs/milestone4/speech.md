# Milestone 4 English walkthrough

目标6至7分钟。括号内是屏幕操作提示，不必照读。用自己的话解释代码，并以你实际运行的结果为准。真实 Gemini 问答和提醒需先配置 Key；不要在录屏中显示 .env。

## 0:00 to 0:45 Purpose and architecture

[打开项目树、AiModelConfiguration 和运行中的首页。]

This is TutorConnect, a tutoring appointment scheduler. For Milestone Four, I added a conversational scheduling assistant, an autonomous reminder agent, and policy questions answered with retrieval augmented generation.

Both agents use Spring AI and the Gemini Developer API. The existing controllers, service layer, JDBC repositories and MySQL database still enforce appointment rules. AI can request tools, but the server controls which customer those tools act for and when a booking can be committed.

Here, the API key comes from the environment. There is no project ID or location, so this uses the Developer API rather than Vertex AI. The application can also start with AI disabled.

## 0:45 to 2:00 Conversational tools and memory

[打开 AiChatService、AiBookingTools，指向 ChatClient、ToolCallingAdvisor 与各个 @Tool。]

The chat endpoint calls Spring AI ChatClient with our registered tools. The model can check availability, read the result, and then propose a booking in the same turn. It can also list my appointments, propose cancellation, and retrieve policy passages.

Each tool object is created with the authenticated Account. User identity is not an argument the model can supply. Availability comes from SlotService, and personal appointments come from AppointmentService using the current customer's ID.

The server keeps recent user and assistant messages in the HTTP session. That supports follow-up questions without accepting a conversation history supplied by the browser. There are limits on message length, tool calls and model rounds.

[在助手页面查询未来一周时段，再提出预约。]

The assistant has found a real session and proposed it. At this point, there is still no new appointment. The pending action is only a proposal.

## 2:00 to 3:10 Confirmation and transaction protection

[打开 AiConversation 和 AiChatService.confirm，再指向 BookingTransaction。]

The server creates a summary containing the exact tutoring service, provider and UTC time. It also creates a random confirmation token that expires after five minutes. The token belongs to this login session and is not passed to the model.

Clicking Confirm sends a CSRF-protected request. The server reads the stored action, checks the token and customer, then consumes it. The browser cannot replace the slot in that confirmation request. Typing yes into chat cannot bypass this step.

The confirmed action calls the existing transactional service. BookingTransaction locks the slot row, checks that it is still available and inserts the appointment. The unique active slot key is still present in the database. If another customer books first, confirmation receives a conflict. Cancellation also rechecks ownership in the transactional service.

[点击确认并展示预约；如需取消，先展示 AI 取消提案，再确认。]

## 3:10 to 4:15 Autonomous reminder agent

[打开 AiReminderService、ReminderTransaction、NotificationDispatcher，再展示 provider 按钮。]

The second agent runs through Spring Scheduled without a customer prompt. Every minute, it scans booked appointments starting within the next twenty-four hours. Gemini generates a short personalized reminder using the customer's name, subject and start time.

The model call happens outside a database transaction. After generation, a short transaction locks and rechecks the appointment. This matters because the customer might cancel while the model is responding.

The reminder is stored in the existing notification outbox, then sent after commit. Its event ID is stable for that appointment. Repeating the job does not create another reminder event. If notification delivery fails, recovery retries the stored event. If the appointment has been cancelled or started before recovery, the reminder is suppressed.

The provider button runs the same logic for that provider's appointments. The scheduled run covers all providers. Reminder generation and notification recovery have separate capacity in a two-thread scheduler.

## 4:15 to 5:05 RAG knowledge source

[打开知识文件和 AiKnowledgeService，再展示真实政策问答。]

This small knowledge file contains policies for cancellation, services, available hours, reminders and account access. Each section has a source ID.

On the first search, the application embeds those passages and adds them to SimpleVectorStore. A question retrieves up to three matching passages. The knowledge tool returns their text and source IDs so Gemini can ground its answer and cite the policy.

The assistant is instructed to admit when the source has no answer. Current availability still comes from the database tools. The policy file cannot tell us whether a particular session is free right now.

## 5:05 to 6:30 Tests and evidence

[打开 AiWorkflowTests 的并发测试，展示实际通过摘要。再打开 GeminiAdapterTests。]

This test uses two separate logged-in customer sessions. Both receive a proposal for the same slot. Readiness and start latches synchronize their confirmation requests, and futures collect the results. The assertions require one success, one conflict and exactly one active appointment in the database. This shows the agent's confirmed booking keeps the original transaction protection.

Other tests cover CSRF, copied and expired tokens, cancellation ownership, model failures, reminder deduplication and notification delivery. The adapter tests use the real Google SDK with a local HTTP server. They check signed tool calls and ensure thought summaries are not displayed as final answers.

[展示 mvn test 或 MySQL 测试的真实结果。]

The automated results are separate from live Gemini behavior. With my configured key, the browser demonstrations show the real model responses. My submission includes the PDF report, source code with the knowledge file and tests, the GitHub repository link, and this recorded walkthrough.
