function renderCart(cart) {
  const box = document.getElementById("cart-items");
  document.getElementById("cart-total").textContent = formatPrice(cart.total);
  document.getElementById("checkout-button").disabled = cart.items.length === 0;
  refreshCartBadge(cart);

  if (cart.items.length === 0) {
    box.innerHTML = `<p class="muted">Tu carrito está vacío. <a href="index.html">Ver productos</a></p>`;
    return;
  }

  box.innerHTML = cart.items
    .map(
      (item) => `
      <div class="cart-item">
        <img src="${imageUrl(item.image)}" alt="${escapeHtml(item.name)}">
        <div class="grow">
          <h3>${escapeHtml(item.name)}</h3>
          <p class="muted">${formatPrice(item.price)} c/u</p>
        </div>
        <input type="number" min="1" value="${item.quantity}" data-id="${item.productId}" class="qty">
        <strong>${formatPrice(item.price * item.quantity)}</strong>
        <button class="link-button" data-remove="${item.productId}">Quitar</button>
      </div>`
    )
    .join("");
}

async function loadCart() {
  try {
    renderCart(await getCart());
  } catch (error) {
    document.getElementById("cart-items").innerHTML = `<p class="error-text">${error.message}</p>`;
  }
}

document.getElementById("cart-items").addEventListener("change", async (event) => {
  if (!event.target.matches(".qty")) return;
  try {
    renderCart(await updateCartItem(Number(event.target.dataset.id), Number(event.target.value)));
  } catch (error) {
    showMessage(error.message, "error");
    loadCart();
  }
});

document.getElementById("cart-items").addEventListener("click", async (event) => {
  if (!event.target.dataset.remove) return;
  renderCart(await removeFromCart(Number(event.target.dataset.remove)));
  showMessage("Producto retirado del carrito");
});

document.getElementById("checkout-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const order = await placeOrder(
      document.getElementById("customer-name").value,
      document.getElementById("customer-email").value,
      document.getElementById("customer-address").value
    );
    document.querySelector(".cart-layout").classList.add("hidden");
    const done = document.getElementById("order-done");
    done.classList.remove("hidden");
    done.innerHTML = `
      <h2>¡Gracias por tu compra, ${escapeHtml(order.customerName)}!</h2>
      <p>Tu pedido <strong>#${order.id.slice(-6).toUpperCase()}</strong> por <strong>${formatPrice(order.total)}</strong> fue recibido.</p>
      <p>Lo enviaremos a: ${escapeHtml(order.address)}</p>
      <p><a href="pedidos.html">Ver mis pedidos</a> · <a href="index.html">Seguir comprando</a></p>`;
    refreshCartBadge();
  } catch (error) {
    showMessage(error.message, "error");
  }
});

loadCart();
