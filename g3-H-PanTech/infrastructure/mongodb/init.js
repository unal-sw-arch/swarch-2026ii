// =============================================================================
// Panadería Express — Prototipo 2
// Inicialización de MongoDB.
//
// El contenedor ejecuta este archivo una sola vez, al crear el volumen.
// Si se borra el volumen (docker compose down -v), se vuelve a ejecutar.
//
// Colecciones:
//   menu              catálogo denormalizado (Order Service)
//   order_history     read model de pedidos (Order/Worker)
//   reservation_read  read model de reservas (Order/Worker)
//   report_snapshots  agregados precalculados (Reporting)
// =============================================================================

const dbName = process.env.MONGO_INITDB_DATABASE || "panaderia";
const target = db.getSiblingDB(dbName);

// --- Menú -------------------------------------------------------------------
target.createCollection("menu", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["product_id", "name", "price", "category"],
      properties: {
        product_id: { bsonType: "string" },
        name: { bsonType: "string" },
        description: { bsonType: "string" },
        price: { bsonType: ["int", "long", "double", "decimal"] },
        category: { enum: ["vitrina", "preparado"] },
        prep_minutes: { bsonType: ["int", "long", "double", "decimal"] },
        available: { bsonType: "bool" },
      },
    },
  },
});

target.menu.createIndex({ product_id: 1 }, { unique: true });
target.menu.createIndex({ category: 1, available: 1 });
target.menu.createIndex({ name: "text", description: "text" });

// --- Historial de pedidos ---------------------------------------------------
target.createCollection("order_history");
target.order_history.createIndex({ order_id: 1 }, { unique: true });
target.order_history.createIndex({ customer_id: 1, created_at: -1 });
target.order_history.createIndex({ status: 1, created_at: -1 });
target.order_history.createIndex({ created_at: -1 });

// --- Read model de reservas ------------------------------------------------
target.createCollection("reservation_read");
target.reservation_read.createIndex({ reservation_id: 1 }, { unique: true });
target.reservation_read.createIndex({ customer_id: 1, date: -1 });
target.reservation_read.createIndex({ status: 1, date: 1 });

// --- Snapshots de reportes -------------------------------------------------
target.createCollection("report_snapshots");
target.report_snapshots.createIndex({ report_type: 1, period: 1 }, { unique: true });
target.report_snapshots.createIndex({ generated_at: -1 });

print("[mongo-init] Colecciones e índices de Panadería Express creados.");
