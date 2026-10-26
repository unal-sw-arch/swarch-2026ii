let selectedCategory = "";

function productCard(product) {
  const soldOut = product.stock === 0;
  return `
    <article class="card">
      <a href="producto.html?id=${product.id}">
        <img src="${imageUrl(product.image)}" alt="${escapeHtml(product.name)}">
      </a>
      <div class="card-body">
        <span class="tag">${escapeHtml(product.category_name)}</span>
        <h3><a href="producto.html?id=${product.id}">${escapeHtml(product.name)}</a></h3>
        <p class="price">${formatPrice(product.price)}</p>
        <button data-id="${product.id}" ${soldOut ? "disabled" : ""}>${soldOut ? "Agotado" : "Agregar al carrito"}</button>
      </div>
    </article>`;
}

async function loadCategories() {
  const categories = await getCategories();
  const box = document.getElementById("categories");
  box.innerHTML =
    `<button class="chip active" data-slug="">Todas</button>` +
    categories.map((c) => `<button class="chip" data-slug="${c.slug}">${escapeHtml(c.name)}</button>`).join("");

  box.addEventListener("click", (event) => {
    if (!event.target.matches(".chip")) return;
    box.querySelectorAll(".chip").forEach((chip) => chip.classList.remove("active"));
    event.target.classList.add("active");
    selectedCategory = event.target.dataset.slug;
    loadProducts();
  });
}

async function loadProducts() {
  const search = document.getElementById("search-input").value.trim();
  const list = document.getElementById("products");
  try {
    const products = await getProducts(search, selectedCategory);
    document.getElementById("results-info").textContent = `${products.length} productos encontrados`;
    list.innerHTML = products.length
      ? products.map(productCard).join("")
      : `<p class="muted">No encontramos productos con esa búsqueda.</p>`;
  } catch (error) {
    list.innerHTML = `<p class="error-text">${error.message}</p>`;
  }
}

document.getElementById("search-form").addEventListener("submit", (event) => {
  event.preventDefault();
  loadProducts();
});

document.getElementById("products").addEventListener("click", async (event) => {
  if (!event.target.matches("button[data-id]")) return;
  try {
    const cart = await addToCart(Number(event.target.dataset.id), 1);
    refreshCartBadge(cart);
    showMessage("Producto agregado al carrito");
  } catch (error) {
    showMessage(error.message, "error");
  }
});

loadCategories();
loadProducts();
refreshCartBadge();
