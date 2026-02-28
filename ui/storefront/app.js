const productsEl = document.getElementById("products");
const orderForm = document.getElementById("orderForm");
const orderResultEl = document.getElementById("orderResult");
const ordersResultEl = document.getElementById("ordersResult");
const refreshOrdersEl = document.getElementById("refreshOrders");

async function fetchProducts() {
    const response = await fetch("/api/products");
    const products = await response.json();

    productsEl.innerHTML = "";
    products.forEach((product) => {
        const card = document.createElement("div");
        card.className = "card";
        card.innerHTML = `
            <h3>${product.name}</h3>
            <div class="sku">${product.skuCode}</div>
            <p>${product.description ?? ""}</p>
            <strong>$${product.price}</strong>
        `;
        card.addEventListener("click", () => {
            document.getElementById("skuCode").value = product.skuCode;
        });
        productsEl.appendChild(card);
    });
}

async function fetchOrders() {
    const response = await fetch("/api/orders");
    const orders = await response.json();
    ordersResultEl.textContent = JSON.stringify(orders, null, 2);
}

orderForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const payload = {
        skuCode: document.getElementById("skuCode").value.trim(),
        quantity: Number(document.getElementById("quantity").value),
        customerEmail: document.getElementById("customerEmail").value.trim()
    };

    const response = await fetch("/api/orders", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
    });

    const result = await response.json();
    orderResultEl.textContent = JSON.stringify(result, null, 2);

    if (response.ok) {
        await fetchOrders();
    }
});

refreshOrdersEl.addEventListener("click", fetchOrders);

fetchProducts();
fetchOrders();
