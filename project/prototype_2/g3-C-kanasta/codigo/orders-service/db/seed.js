// Sample orders loaded the first time MongoDB starts.
// userId 2 and 3 are the demo customers created by the Accounts Service.
db = db.getSiblingDB("orders");

db.orders.insertMany([
  {
    userId: 2,
    customerName: "Ana Gómez",
    email: "ana@correo.com",
    phone: "310 555 0202",
    address: "Calle 45 # 12-30, Bogotá",
    notes: "Timbre del apartamento 302",
    items: [
      { productId: 1, name: "Banano", price: 3200, unit: "kg", quantity: 1.5, subtotal: 4800 },
      { productId: 24, name: "Huevos x 30", price: 18000, unit: "unidad", quantity: 1, subtotal: 18000 },
      { productId: 23, name: "Leche fresca a granel", price: 3800, unit: "litro", quantity: 2, subtotal: 7600 },
    ],
    total: 30400,
    estimated: true,
    status: "DELIVERED",
    createdAt: new Date("2026-10-28T14:20:00Z"),
  },
  {
    userId: 2,
    customerName: "Ana Gómez",
    email: "ana@correo.com",
    phone: "310 555 0202",
    address: "Calle 45 # 12-30, Bogotá",
    notes: "",
    items: [
      { productId: 33, name: "Café molido 500 g", price: 16500, unit: "unidad", quantity: 1, subtotal: 16500 },
      { productId: 35, name: "Pan tajado", price: 6500, unit: "unidad", quantity: 2, subtotal: 13000 },
    ],
    total: 29500,
    estimated: false,
    status: "SHIPPED",
    createdAt: new Date("2026-11-05T12:05:00Z"),
  },
  {
    userId: 3,
    customerName: "Carlos Ruiz",
    email: "carlos@correo.com",
    phone: "320 555 0303",
    address: "Carrera 15 # 47-10, Bogotá",
    notes: "Dejar en portería",
    items: [
      { productId: 19, name: "Carne de res molida", price: 26000, unit: "kg", quantity: 0.5, subtotal: 13000 },
      { productId: 10, name: "Tomate", price: 4800, unit: "kg", quantity: 1, subtotal: 4800 },
      { productId: 12, name: "Cebolla cabezona", price: 3400, unit: "kg", quantity: 0.75, subtotal: 2550 },
      { productId: 27, name: "Arroz a granel", price: 4200, unit: "kg", quantity: 2, subtotal: 8400 },
    ],
    total: 28750,
    estimated: true,
    status: "PENDING",
    createdAt: new Date("2026-11-07T15:40:00Z"),
  },
]);
