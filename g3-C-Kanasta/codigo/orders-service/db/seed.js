// Sample orders loaded the first time MongoDB starts
db = db.getSiblingDB("orders");

db.orders.insertMany([
  {
    customerName: "Ana Gómez",
    email: "ana@correo.com",
    address: "Calle 45 # 12-30, Bogotá",
    items: [
      { productId: 5, name: "Mouse inalámbrico", price: 69000, quantity: 1 },
      { productId: 19, name: "Cuaderno argollado", price: 12000, quantity: 3 },
    ],
    total: 105000,
    status: "DELIVERED",
    createdAt: new Date("2026-09-02T15:20:00Z"),
  },
  {
    customerName: "Ana Gómez",
    email: "ana@correo.com",
    address: "Calle 45 # 12-30, Bogotá",
    items: [{ productId: 7, name: "Cafetera de goteo", price: 159000, quantity: 1 }],
    total: 159000,
    status: "SHIPPED",
    createdAt: new Date("2026-09-20T10:05:00Z"),
  },
  {
    customerName: "Carlos Ruiz",
    email: "carlos@correo.com",
    address: "Carrera 7 # 80-15, Medellín",
    items: [
      { productId: 15, name: "Balón de fútbol", price: 89000, quantity: 2 },
      { productId: 14, name: "Gorra deportiva", price: 59000, quantity: 1 },
    ],
    total: 237000,
    status: "PENDING",
    createdAt: new Date("2026-10-05T18:40:00Z"),
  },
]);
