# Milestone 3 运行与录屏

## 一次启动应用和 MySQL

先启动 Docker Desktop，在项目根目录执行：

```sh
docker compose up -d --build --wait
```

打开 http://localhost:8080 。客户账号 maya.patel@example.com，导师账号 priya.shah@example.com；演示密码 TutorDemo123!。同一 Docker Compose 项目沿用原 MySQL 数据卷，新通知表由应用启动时自动创建，不删除已有预约。

健康检查：

```sh
curl -s http://localhost:8080/health
```

正常返回 `{"status":"UP","database":"UP"}`，JSON 字段顺序可能不同。应用中的模拟通知接口通过 HTTP 接收通知，只记录数据，不发送真实邮件或短信。

## 测试

H2 全套测试：

```sh
mvn test
```

创建独立 MySQL 测试库：

```sh
docker compose exec -T -e MYSQL_PWD=root-demo-only db mysql -uroot -e "CREATE DATABASE IF NOT EXISTS tutorconnect_test; GRANT ALL PRIVILEGES ON tutorconnect_test.* TO 'tutorconnect'@'%';"
```

MySQL 全套测试：

```sh
sh scripts/test-mysql.sh
```

只运行最重要的通知服务停止测试：

```sh
sh scripts/test-mysql.sh '-Dtest=NotificationDownTests'
```

运行通知提交顺序、超时、故障与恢复测试：

```sh
sh scripts/test-mysql.sh '-Dtest=Milestone3IntegrationTests'
```

测试只重置 tutorconnect_test，不使用网页的 tutorconnect 数据库。测试无需手动启动网页服务器。NotificationDownTests 会指向没有服务监听的本地端口，断言预约仍返回 201，数据库预约有效且通知保留为 PENDING。

## 完整容器验证

针对新初始化的演示环境执行（它会创建并取消一个演示预约）：

```sh
python3 scripts/smoke.py --base-url http://localhost:8080
```

输出健康状态、预约 201、取消 200 和通知成功数量。该脚本使用真实登录、Session、CSRF 和真实 HTTP Gateway。

查看日志与持久化通知：

```sh
docker compose logs app --tail=100
docker compose exec -T -e MYSQL_PWD=root-demo-only db mysql -uroot --table tutorconnect -e "SELECT event_id,type,state,attempts,last_error FROM notification_outbox; SELECT event_id,type,received_at FROM notification_receipts;"
```

指标接口 `/api/provider/metrics` 需要导师登录。返回预约成功/失败、取消数、成功通知数、待发送/永久失败队列数及平均预约耗时。Micrometer 计数和耗时随 JVM 重启归零；MySQL 中的待发送记录和接收记录持久保留。

## 建议录屏顺序

1. 展示分布式架构图与 NotificationRequest DTO。
2. 展示 BookingTransaction 中的 outbox 入库，再展示 AppointmentService 中事务提交后的发送调用。
3. 展示 Gateway 的超时、最多三次尝试，以及接收端 eventId 唯一约束和载荷校验。
4. 运行 NotificationDownTests，解释 201 和数据库 PENDING 断言。
5. 展示 deferredNotificationRecoversUsingSameEventAfterServiceReturns 测试，以及恢复队列按 next_attempt_at 排序避免饥饿。
6. 展示 /health、INFO/WARN/ERROR 日志和导师指标；解释 Dockerfile、Compose 与 CI。
7. 视频至少五分钟，讲代码而非只演示网页。英文讲稿另见 speech.md。

## 配置

- NOTIFICATION_URL：模拟通知服务目标，默认本机应用的 /internal/notifications。
- NOTIFICATION_SERVICE_TOKEN：服务间共享凭据；本地演示有默认值。
- APP_PORT：Compose 网页端口，默认 8080。
- 默认连接超时 200ms、完整 HTTP 响应期限 300ms、最多 3 次尝试、间隔 25ms。
- PENDING 通知约 15 秒后进入下一次恢复，每批最多 20 条。
- HTTP 408/429/5xx、网络中断和超时可重试；其他非成功响应转 FAILED，修复原因后人工重新排队。
- 默认每条事件独立投递，跨事件的通知到达顺序不作保证；模拟接口保证相同事件只记录一次。
