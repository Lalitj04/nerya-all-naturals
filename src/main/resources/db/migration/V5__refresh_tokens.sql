-- Opaque, server-side-revocable refresh tokens. Access tokens (JWTs) are now short-lived
-- (1h, see application.yml); clients renew them via POST /api/auth/refresh using the
-- refresh token issued alongside login/register, without re-entering credentials.

CREATE TABLE refresh_tokens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    token       VARCHAR(128) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    revoked     BIT(1) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT uk_refresh_tokens_token UNIQUE (token),
    KEY idx_refresh_tokens_user (user_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
