const productsEl = document.getElementById("products");
const orderForm = document.getElementById("orderForm");
const orderResultEl = document.getElementById("orderResult");
const inventoryTableBodyEl = document.getElementById("inventoryTableBody");
const ordersTableBodyEl = document.getElementById("ordersTableBody");
const notificationsTableBodyEl = document.getElementById("notificationsTableBody");
const workflowMessageEl = document.getElementById("workflowMessage");
const submitOrderEl = document.getElementById("submitOrder");

const refreshInventoryEl = document.getElementById("refreshInventory");
const refreshOrdersEl = document.getElementById("refreshOrders");
const refreshNotificationsEl = document.getElementById("refreshNotifications");

const stepElements = {
    order: document.getElementById("orderStep"),
    inventory: document.getElementById("inventoryStep"),
    notification: document.getElementById("notificationStep")
};

let productsCache = [];
let inventoryMap = new Map();

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function formatMoney(value) {
    const num = Number(value ?? 0);
    return Number.isFinite(num) ? num.toFixed(2) : "0.00";
}

function formatTime(value) {
    if (!value) return "-";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "-";
    return date.toISOString().replace("T", " ").replace(".000Z", "");
}

function setStep(step, state, label) {
    const el = stepElements[step];
    if (!el) return;
    el.className = `badge ${state}`;
    el.textContent = label;
}

function resetWorkflow() {
    setStep("order", "idle", "WAITING");
    setStep("inventory", "idle", "WAITING");
    setStep("notification", "idle", "WAITING");
    workflowMessageEl.textContent = "Place an order to validate the Saga flow.";
}

async function requestJson(url, options = {}) {
    const response = await fetch(url, options);
    let payload = null;
    try {
        payload = await response.json();
    } catch (_ignored) {
        payload = null;
    }

    if (!response.ok) {
        const message = payload?.message || payload?.error || `Request failed (${response.status})`;
        throw new Error(message);
    }

    return payload;
}

function renderProducts() {
    productsEl.innerHTML = "";
    productsCache.forEach((product) => {
        const stock = inventoryMap.get(product.skuCode);
        const stockText = stock ? `${stock.quantity} in stock` : "Stock unavailable";
        const card = document.createElement("div");
        card.className = "card";
        card.innerHTML = `
            <h3>${escapeHtml(product.name)}</h3>
            <div class="sku">${escapeHtml(product.skuCode)}</div>
            <p>${escapeHtml(product.description ?? "")}</p>
            <div class="card-meta">
                <strong>$${formatMoney(product.price)}</strong>
                <span class="stock-pill">${escapeHtml(stockText)}</span>
            </div>
        `;
        card.addEventListener("click", () => {
            document.getElementById("skuCode").value = product.skuCode;
        });
        productsEl.appendChild(card);
    });
}

function renderInventoryTable(items) {
    inventoryTableBodyEl.innerHTML = "";
    if (!items.length) {
        inventoryTableBodyEl.innerHTML = '<tr><td colspan="3">No inventory rows found.</td></tr>';
        return;
    }

    items.forEach((item) => {
        const availability = item.available ? "YES" : "NO";
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${escapeHtml(item.skuCode)}</td>
            <td><span class="flag ${item.available ? "ok" : "bad"}">${availability}</span></td>
            <td>${escapeHtml(item.quantity)}</td>
        `;
        inventoryTableBodyEl.appendChild(row);
    });
}

function renderOrdersTable(items) {
    ordersTableBodyEl.innerHTML = "";
    if (!items.length) {
        ordersTableBodyEl.innerHTML = '<tr><td colspan="8">No orders found.</td></tr>';
        return;
    }

    items
        .slice()
        .sort((a, b) => new Date(b.updatedAt ?? b.createdAt) - new Date(a.updatedAt ?? a.createdAt))
        .forEach((order) => {
            const statusClass = order.status === "CONFIRMED" ? "ok" : (order.status === "CANCELLED" ? "bad" : "");
            const row = document.createElement("tr");
            row.innerHTML = `
                <td>${escapeHtml(order.orderNumber)}</td>
                <td><span class="flag ${statusClass}">${escapeHtml(order.status ?? "PENDING")}</span></td>
                <td>${escapeHtml(order.skuCode)}</td>
                <td>${escapeHtml(order.quantity)}</td>
                <td>$${formatMoney(order.totalPrice)}</td>
                <td>${escapeHtml(order.failureReason ?? "-")}</td>
                <td>${escapeHtml(formatTime(order.createdAt))}</td>
                <td>${escapeHtml(formatTime(order.updatedAt))}</td>
            `;
            ordersTableBodyEl.appendChild(row);
        });
}

function renderNotificationsTable(items) {
    notificationsTableBodyEl.innerHTML = "";
    if (!items.length) {
        notificationsTableBodyEl.innerHTML = '<tr><td colspan="6">No notification events yet.</td></tr>';
        return;
    }

    items.forEach((event) => {
        const statusClass = event.status === "CONFIRMED" ? "ok" : (event.status === "CANCELLED" ? "bad" : "");
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${escapeHtml(event.orderNumber)}</td>
            <td><span class="flag ${statusClass}">${escapeHtml(event.status)}</span></td>
            <td>${escapeHtml(event.skuCode)}</td>
            <td>${escapeHtml(event.quantity)}</td>
            <td>${escapeHtml(event.message ?? "-")}</td>
            <td>${escapeHtml(formatTime(event.processedAt))}</td>
        `;
        notificationsTableBodyEl.appendChild(row);
    });
}

async function fetchProducts() {
    const products = await requestJson("/api/products");
    productsCache = products;
    renderProducts();
}

async function fetchInventory() {
    const inventory = await requestJson("/api/inventory");
    inventoryMap = new Map(inventory.map((item) => [item.skuCode, item]));
    renderInventoryTable(inventory);
    renderProducts();
    return inventory;
}

async function fetchOrders() {
    const orders = await requestJson("/api/orders");
    renderOrdersTable(orders);
    return orders;
}

async function fetchNotifications(limit = 20) {
    const events = await requestJson(`/api/notifications?limit=${limit}`);
    renderNotificationsTable(events);
    return events;
}

function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitForOrderFinal(orderNumber, maxRetries = 20, delayMs = 1000) {
    for (let attempt = 0; attempt < maxRetries; attempt += 1) {
        const order = await requestJson(`/api/orders/${encodeURIComponent(orderNumber)}`);
        if (order.status === "CONFIRMED" || order.status === "CANCELLED") {
            return order;
        }
        await sleep(delayMs);
    }
    return null;
}

async function waitForNotification(orderNumber, maxRetries = 20, delayMs = 1000) {
    for (let attempt = 0; attempt < maxRetries; attempt += 1) {
        try {
            const event = await requestJson(`/api/notifications/${encodeURIComponent(orderNumber)}`);
            await fetchNotifications(20);
            return event;
        } catch (_ignored) {
            await sleep(delayMs);
        }
    }
    return null;
}

async function placeOrder(payload) {
    return requestJson("/api/orders", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
    });
}

orderForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const payload = {
        skuCode: document.getElementById("skuCode").value.trim(),
        quantity: Number(document.getElementById("quantity").value),
        customerEmail: document.getElementById("customerEmail").value.trim()
    };

    submitOrderEl.disabled = true;
    submitOrderEl.textContent = "Placing...";
    orderResultEl.textContent = "Submitting order...";
    workflowMessageEl.textContent = "Order accepted. Waiting for Saga completion...";
    setStep("order", "pending", "PENDING");
    setStep("inventory", "pending", "WAITING_EVENT");
    setStep("notification", "pending", "WAITING_EVENT");

    try {
        const createdOrder = await placeOrder(payload);
        orderResultEl.textContent = JSON.stringify(createdOrder, null, 2);

        const finalOrder = await waitForOrderFinal(createdOrder.orderNumber);
        await Promise.all([fetchOrders(), fetchInventory()]);

        if (!finalOrder) {
            setStep("order", "warn", "TIMEOUT");
            setStep("inventory", "warn", "UNKNOWN");
            workflowMessageEl.textContent = "Order created but Saga did not finish in time.";
        } else if (finalOrder.status === "CONFIRMED") {
            setStep("order", "ok", "CONFIRMED");
            const updatedInventory = inventoryMap.get(finalOrder.skuCode);
            setStep("inventory", "ok", updatedInventory ? `RESERVED (${updatedInventory.quantity} LEFT)` : "RESERVED");
            workflowMessageEl.textContent = `Order ${finalOrder.orderNumber} confirmed after inventory reservation.`;
        } else {
            setStep("order", "bad", "CANCELLED");
            setStep("inventory", "bad", "RESERVATION_FAILED");
            workflowMessageEl.textContent = `Order ${finalOrder.orderNumber} cancelled: ${finalOrder.failureReason ?? "Inventory check failed"}`;
        }

        const notificationEvent = await waitForNotification(createdOrder.orderNumber);
        if (notificationEvent) {
            setStep("notification", "ok", notificationEvent.status ?? "PROCESSED");
        } else {
            setStep("notification", "warn", "TIMEOUT");
        }
    } catch (err) {
        const message = err instanceof Error ? err.message : "Order failed";
        orderResultEl.textContent = JSON.stringify({ error: message }, null, 2);
        setStep("order", "bad", "FAILED");
        setStep("inventory", "idle", "WAITING");
        setStep("notification", "idle", "WAITING");
        workflowMessageEl.textContent = `Order failed: ${message}`;
    } finally {
        submitOrderEl.disabled = false;
        submitOrderEl.textContent = "Submit Order";
    }
});

refreshInventoryEl.addEventListener("click", () => {
    fetchInventory().catch((err) => {
        workflowMessageEl.textContent = `Inventory refresh failed: ${err.message}`;
    });
});

refreshOrdersEl.addEventListener("click", () => {
    fetchOrders().catch((err) => {
        workflowMessageEl.textContent = `Order refresh failed: ${err.message}`;
    });
});

refreshNotificationsEl.addEventListener("click", () => {
    fetchNotifications(20).catch((err) => {
        workflowMessageEl.textContent = `Notification refresh failed: ${err.message}`;
    });
});

async function bootstrap() {
    resetWorkflow();
    try {
        await Promise.all([fetchProducts(), fetchInventory(), fetchOrders(), fetchNotifications(20)]);
    } catch (err) {
        workflowMessageEl.textContent = `Initial load failed: ${err.message}`;
    }
}

bootstrap();
setInterval(() => {
    fetchInventory().catch(() => {});
    fetchOrders().catch(() => {});
    fetchNotifications(20).catch(() => {});
}, 10000);
