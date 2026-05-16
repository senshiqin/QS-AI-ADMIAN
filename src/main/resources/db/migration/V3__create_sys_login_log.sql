CREATE TABLE IF NOT EXISTS sys_login_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '登录日志ID',
  username VARCHAR(64) NOT NULL COMMENT '用户名',
  user_id BIGINT DEFAULT NULL COMMENT '用户ID',
  client_ip VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',
  user_agent VARCHAR(500) DEFAULT NULL COMMENT 'User-Agent',
  success TINYINT NOT NULL COMMENT '是否成功:1成功,0失败',
  failure_reason VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
  trace_id VARCHAR(64) DEFAULT NULL COMMENT '链路追踪ID',
  login_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (id),
  KEY idx_login_username_time (username, login_time),
  KEY idx_login_user_time (user_id, login_time),
  KEY idx_login_success_time (success, login_time),
  KEY idx_login_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统登录日志表';
