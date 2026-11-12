CREATE TABLE IF NOT EXISTS ai_chat_threads (
 thread_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 title VARCHAR(160) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_chat_thread_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
 INDEX idx_chat_thread_owner (user_id, updated_at, thread_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ai_chat_messages (
 message_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 thread_id BIGINT NOT NULL,
 role VARCHAR(12) NOT NULL,
 body TEXT NOT NULL,
 CONSTRAINT ck_chat_message_role CHECK (role IN ('user', 'assistant')),
 CONSTRAINT fk_chat_message_thread FOREIGN KEY (thread_id) REFERENCES ai_chat_threads(thread_id) ON DELETE CASCADE,
 INDEX idx_chat_message_thread (thread_id, message_id)
) ENGINE=InnoDB;
