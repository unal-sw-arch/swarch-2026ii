function statusSelect(order) {
  const options = Object.entries(STATUS_LABELS)
    .map(([value, label]) => `<option value="${value}" ${value === order.status ? "selected" : ""}>${label}</option>`)
    .join("");
  return `<select data-id="${order.id}">${options}</select>`;
}

async function loadOrders() {
  const box = document.getElementById("admin-orders");
  try {
    const orders = await getAllOrders();
    if (orders.length === 0) {
      box.innerHTML = `<p class="muted">Todavía no hay pedidos.</p>`;
      return;
    }
    const rows = orders
      .map(
        (order) => `
        <tr>
          <td>#${order.id.slice(-6).toUpperCase()}</td>
          <td>${formatDate(order.createdAt)}</td>
          <td>${escapeHtml(order.customerName)}<br><span class="muted small">${escapeHtml(order.email)}</span></td>
          <td>${order.items.reduce((sum, i) => sum + i.quantity, 0)}</td>
          <td>${formatPrice(order.total)}</td>
          <td>${statusSelect(order)}</td>
        </tr>`
      )
      .join("");
    box.innerHTML = `
      <table class="table">
        <thead><tr><th>Pedido</th><th>Fecha</th><th>Cliente</th><th>Productos</th><th>Total</th><th>Estado</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>`;
  } catch (error) {
    box.innerHTML = `<p class="error-text">${error.message}</p>`;
  }
}

document.getElementById("admin-orders").addEventListener("change", async (event) => {
  if (!event.target.matches("select")) return;
  try {
    await updateOrderStatus(event.target.dataset.id, event.target.value);
    showMessage("Estado actualizado");
  } catch (error) {
    showMessage(error.message, "error");
  }
});

loadOrders();
refreshCartBadge();
