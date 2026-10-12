INSERT INTO resource_versions(resource, version, updated_at)
VALUES ('halls', 0, CURRENT_TIMESTAMP), ('employees', 0, CURRENT_TIMESTAMP)
ON CONFLICT(resource) DO NOTHING;

CREATE TRIGGER realtime_halls_insert AFTER INSERT ON hall BEGIN
  INSERT INTO resource_versions VALUES ('halls', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER realtime_halls_update AFTER UPDATE ON hall BEGIN
  INSERT INTO resource_versions VALUES ('halls', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER realtime_halls_delete AFTER DELETE ON hall BEGIN
  INSERT INTO resource_versions VALUES ('halls', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER realtime_employees_insert AFTER INSERT ON users
WHEN NEW.role != 'admin' BEGIN
  INSERT INTO resource_versions VALUES ('employees', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER realtime_employees_update AFTER UPDATE ON users
WHEN NEW.role != 'admin' BEGIN
  INSERT INTO resource_versions VALUES ('employees', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER realtime_employees_delete AFTER DELETE ON users
WHEN OLD.role != 'admin' BEGIN
  INSERT INTO resource_versions VALUES ('employees', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
