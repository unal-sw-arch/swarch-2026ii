-- Accounts database: users and their open sessions

CREATE TABLE users (
    id            SERIAL PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    phone         VARCHAR(40) NOT NULL DEFAULT '',
    address       VARCHAR(200) NOT NULL DEFAULT '',
    role          VARCHAR(10) NOT NULL DEFAULT 'CUSTOMER' CHECK (role IN ('CUSTOMER', 'ADMIN')),
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE sessions (
    token      VARCHAR(64) PRIMARY KEY,
    user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- The demo accounts are created by the service on its first start (DataSeeder.java),
-- because the passwords must be stored as BCrypt hashes.
