// Índices de MongoDB propios del Order Service (idempotente).
// Order Service también los aplica al arrancar (app/seed.py); este script sirve para correrlos a mano:
//   mongosh "mongodb://localhost:27017/panaderia" migrations/mongo/001_indexes.js
db = db.getSiblingDB("panaderia");

// Catálogo (fuente de verdad del menú). Dueño: Order Service. Mismos índices que infrastructure/mongodb/init.js.
db.menu.createIndex({ product_id: 1 }, { unique: true });
db.menu.createIndex({ category: 1, available: 1 });

// Read models que mantiene el Worker y consulta Reporting.
db.order_history.createIndex({ order_id: 1 }, { unique: true });
db.order_history.createIndex({ customer_id: 1, paid_at: -1 });
db.order_history.createIndex({ paid_at: -1 });
db.reservation_read.createIndex({ reservation_id: 1 }, { unique: true });
db.reservation_read.createIndex({ customer_id: 1, date: -1 });
