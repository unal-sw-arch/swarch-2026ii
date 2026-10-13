const productId = Number(new URLSearchParams(window.location.search).get("id"));

async function loadProduct() {
  const box = document.getElementById("product");
  try {
    const product = await getProduct(productId);
    const soldOut = product.stock === 0;
    document.title = `${product.name} | Kanasta`;
    box.innerHTML = `
      <img src="${imageUrl(product.image)}" alt="${escapeHtml(product.name)}">
      <div>
        <span class="tag">${escapeHtml(product.category_name)}</span>
        <h1>${escapeHtml(product.name)}</h1>
        <p class="price big">${formatPrice(product.price)}</p>
        <p>${escapeHtml(product.description)}</p>
        <p class="muted">${soldOut ? "Producto agotado" : `${product.stock} unidades disponibles`}</p>
        <div class="buy">
          <input id="quantity" type="number" min="1" max="${product.stock}" value="1" ${soldOut ? "disabled" : ""}>
          <button id="add-button" ${soldOut ? "disabled" : ""}>Agregar al carrito</button>
        </div>
      </div>`;

    document.getElementById("add-button").addEventListener("click", async () => {
      try {
        const quantity = Number(document.getElementById("quantity").value);
        const cart = await addToCart(product.id, quantity);
        refreshCartBadge(cart);
        showMessage("Producto agregado al carrito");
      } catch (error) {
        showMessage(error.message, "error");
      }
    });
  } catch (error) {
    box.innerHTML = `<p class="error-text">No encontramos este producto.</p>`;
  }
}

loadProduct();
refreshCartBadge();
