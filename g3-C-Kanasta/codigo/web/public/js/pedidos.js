function orderCard(order) {
  const items = order.items.map((i) => `<li>${i.quantity} × ${escapeHtml(i.name)} — ${formatPrice(i.price * i.quantity)}</li>`).join("");
  return `
    <article class="panel order">
      <div class="order-head">
        <strong>Pedido #${order.id.slice(-6).toUpperCase()}</strong>
        <span class="status ${order.status}">${STATUS_LABELS[order.status]}</span>
      </div>
      <p class="muted">${formatDate(order.createdAt)} · ${escapeHtml(order.address)}</p>
      <ul>${items}</ul>
      <p class="total">Total: <strong>${formatPrice(order.total)}</strong></p>
    </article>`;
}

document.getElementById("orders-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const box = document.getElementById("orders");
  try {
    const orders = await getOrdersByEmail(document.getElementById("orders-email").value);
    box.innerHTML = orders.length
      ? orders.map(orderCard).join("")
      : `<p class="muted">No encontramos pedidos con ese correo.</p>`;
  } catch (error) {
    box.innerHTML = `<p class="error-text">${error.message}</p>`;
  }
});

refreshCartBadge();
