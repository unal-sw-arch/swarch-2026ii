-- Cloud clients authenticate over a stable HTTPS origin. Local discovery,
-- pairing certificates and device-bound sessions are not part of this model.

DROP TABLE employee_sessions;
DROP TABLE paired_devices;
DROP TABLE device_pairing_requests;
DROP TABLE admin_sessions;

CREATE TABLE cloud_sessions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  revoked_at TEXT,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX cloud_sessions_user_id_idx ON cloud_sessions(user_id);
CREATE INDEX cloud_sessions_expires_at_idx ON cloud_sessions(expires_at);
