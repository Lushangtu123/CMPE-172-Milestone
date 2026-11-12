# Milestone 4 运行与录屏指南

本次新增客户 AI 助手、确认后预约与取消、政策 RAG 问答，以及定时 AI 提醒。现有 JDBC 事务、角色权限和通知 outbox 继续使用。

## 1 配置 Gemini

使用 Google AI Studio 的 Gemini Developer API Key。不要在聊天、报告、GitHub 或视频中展示 Key。

在项目根目录执行：

```bash
cp .env.example .env
```

用编辑器打开本机 `.env`，填入自己的 `GEMINI_API_KEY`。保留 `AI_ENABLED=true`。`.env` 已加入 Git 和 Docker 构建忽略规则，Compose 只在运行时传入配置。不要设置 project-id 或 location，也不需要 Google Cloud service account。

项目示例默认使用 `gemini-3.8-flash`，本机实际验证通过的是 `gemini-3.1-flash-lite`，文字向量模型使用 `gemini-embedding-001`。可在本机 `.env` 设置 `GEMINI_CHAT_MODEL=gemini-3.1-flash-lite`。模型可用性以账户权限和配额为准。

## 2 运行

```bash
docker compose up -d --build --wait
curl -s http://localhost:8080/health
```

打开 http://localhost:8080 。演示密码为 `TutorDemo123!`：

- 客户：`maya.patel@example.com`、`sam.rivera@example.com`。
- Provider：`priya.shah@example.com`、`jordan.lee@example.com`。

客户登录后点击 AI assistant。Provider 登录后，dashboard 的 Run reminder agent 按钮会手动运行自己名下的提醒扫描。

没有 Key 时不要复制空 Key 且 AI_ENABLED=true 的配置；保持 AI_ENABLED=false 即可运行原预约功能。AI 请求会返回503提示配置，不会生成假回答。

宿主机 H2 演示也可以运行。环境变量必须由启动 Maven 的终端提供，Maven 不会自动读取 `.env`：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

要在这个模式使用 AI，请通过本机环境设置 AI_ENABLED 和 GEMINI_API_KEY。避免把 Key 直接写入可录到的终端命令。

## 3 助手演示顺序

1. 问：`Find the earliest algebra session in the next seven days.` 检查返回的真实时段。
2. 继续：`Please propose booking that session.` 此时数据库不会新增预约。
3. 查看页面中的课程、tutor、UTC 时间摘要，点击 Confirm booking 后才提交。取消预约对应 Confirm cancellation。
4. 问：`List my appointments.` 再提出取消，查看摘要并确认。
5. 问：`What is the cancellation policy? Cite the retrieved policy ID.` 展示政策来源，例如 policy-cancellation。
6. 用第二位客户抢先预约同一时段，再确认旧提案，可展示409冲突；让助手重新查询可选时段。

每个会话仅有一个待确认动作，新问题会使旧提案失效。提案5分钟过期。输入 yes 不会绕过确认按钮。模型上下文保留最近20条消息，成功对话会另外保存到数据库。点击 Chat history 可以查看自己的历史记录，New chat 和重新登录后仍可查看。待确认动作与登录会话绑定。

## 4 定时与手动提醒

默认每60秒扫描未来24小时内的 BOOKED 预约，每次最多20条。先调用 Gemini 生成个性化文字，再进入短事务重新检查预约并保存 APPOINTMENT_REMINDER outbox 事件；提交后才发 HTTP 通知。无 Key 不执行自动生成。

Provider 手动触发只扫描自己的预约。全局定时任务扫描全部 provider。`.env` 中设置 AI_REMINDERS_ENABLED=false 可关闭自动扫描，手动按钮仍可用于演示。

为便于录屏，可由 Provider 创建2小时后的空闲时段，由客户预约后再运行按钮。默认 seed 中部分时段超过24小时，不会被提醒任务选中。重复运行不会重复创建同一预约的提醒事件。通知 mock 只存储消息，不发送真实邮件。

生成失败不创建假 AI 提醒，下一轮仍可尝试。通知 HTTP 失败后事件保留 PENDING，由既有恢复任务重试。已取消或已开始的待发送提醒会被抑制，记录 FAILED/NOT_UPCOMING；这类记录无需重发。

## 5 测试

H2 和本地模型测试不需要 Key：

```bash
mvn test
mvn '-Dtest=AiWorkflowTests#twoAgentCustomersExactlyOneWinsAfterConfirmation' test
mvn '-Dtest=GeminiAdapterTests,AiReminderDeliveryTests' test
```

MySQL 测试先启动数据库，并创建专用测试库：

```bash
docker compose up -d --wait db
docker compose exec -T -e MYSQL_PWD=root-demo-only db mysql -uroot -e "CREATE DATABASE IF NOT EXISTS tutorconnect_test; GRANT ALL PRIVILEGES ON tutorconnect_test.* TO 'tutorconnect'@'%';"
sh scripts/test-mysql.sh
sh scripts/test-mysql.sh '-Dtest=AiWorkflowTests#twoAgentCustomersExactlyOneWinsAfterConfirmation'
```

测试重置 tutorconnect_test，禁止把 TEST_DB_URL 指向网页使用的 tutorconnect。模型测试用确定的响应替代外部服务，实际 Spring AI 工具循环、HTTP、Service 与数据库继续运行。GeminiAdapterTests 使用真实 Google SDK/Spring AI 适配器和本地 HTTP 服务验证响应格式，不代表已调用 Google 在线服务。

真实 Gemini 运行后可验证 RAG：

```bash
python3 scripts/ai-live-smoke.py
```

仅在演示数据库中，显式允许创建并取消预约后，才运行：

```bash
python3 scripts/ai-live-smoke.py --confirm-writes
```

该脚本先读取真实 AI 提案，再通过确认接口提交，并用原服务接口取消预约以清理。AI 取消提案与确认仍建议在页面录制。没有 Key 时脚本不会伪造通过结果。

## 发送消息长时间无响应

先查看 `docker compose logs --tail=100 app`。API Key 有效也不保证每个模型在当前账户下都能及时响应。本机验证中 gemini-3.8-flash 生成请求超时，同一 Key 的 gemini-3.1-flash-lite 正常返回。遇到这种情况，可在本机 .env 设置 GEMINI_CHAT_MODEL=gemini-3.1-flash-lite，然后执行 `docker compose up -d --no-deps --wait app` 重新创建应用容器。模型选择仍须以账户实际可用性为准。

页面发送时显示 Thinking 和等待提示；单次聊天等待45秒后会停止等待并恢复按钮。这个浏览器超时不会终止服务器上已经执行中的模型请求，稍后重试即可。确认写入请求若超时，请先查看 My appointments，避免把未收到响应误当成未提交。

## 6 录屏与提交

录制至少5分钟的本人屏幕及讲解，重点解释 AiModelConfiguration、AiChatService、AiBookingTools、确认事务路径、AiReminderService、ReminderTransaction 和 AiKnowledgeService，并展示相应测试。完整 Maven 测试可提前运行，录制通过摘要。

提交 ZIP、GitHub 仓库链接、本人视频链接。ZIP 必须包含 PDF 报告、源码、知识文件和测试。录屏稿为建议提纲，请理解代码后用自己的话说明。关于录制和学术规范，以课程 General Rules 为准。
