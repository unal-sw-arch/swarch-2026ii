import os
import sys
from logging.config import fileConfig

from alembic import context
from sqlalchemy import engine_from_config, pool, text

# Garantiza que `app` sea importable aunque Alembic se ejecute desde otra carpeta.
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import app.models  # noqa: E402,F401  (registra las tablas en Base.metadata)
from app.config import settings  # noqa: E402
from app.database import SCHEMA, Base  # noqa: E402

config = context.config
if config.config_file_name is not None:
    fileConfig(config.config_file_name)

# `%` debe escaparse porque configparser lo interpreta.
config.set_main_option("sqlalchemy.url", settings.database_url.replace("%", "%%"))
target_metadata = Base.metadata


def run_migrations_offline() -> None:
    context.configure(
        url=settings.database_url,
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        version_table_schema=SCHEMA,
    )
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    connectable = engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )
    with connectable.connect() as connection:
        if connection.dialect.name == "postgresql":
            # El esquema normalmente lo crea infrastructure/postgres/init.sql; esto lo hace idempotente.
            connection.execute(text(f'CREATE SCHEMA IF NOT EXISTS "{SCHEMA}"'))
            connection.commit()
        context.configure(
            connection=connection,
            target_metadata=target_metadata,
            version_table_schema=SCHEMA,
        )
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
