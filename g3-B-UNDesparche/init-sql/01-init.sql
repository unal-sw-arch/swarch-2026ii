SELECT 'CREATE DATABASE undesparche_events'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'undesparche_events')\gexec

SELECT 'CREATE DATABASE undesparche_inventory'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'undesparche_inventory')\gexec
