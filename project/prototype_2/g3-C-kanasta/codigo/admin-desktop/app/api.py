"""Client for the API Gateway. It is the only file of the desktop app that uses the network."""
import os

import requests

DEFAULT_URL = os.getenv("KANASTA_API", "http://localhost:8000")

ORDER_FIELDS = (
    "id customerName email phone address notes total estimated status createdAt "
    "items { productId name price unit quantity subtotal }"
)


class ApiError(Exception):
    pass


class Gateway:
    def __init__(self, url=DEFAULT_URL):
        self.url = url.rstrip("/")
        self.token = None
        self.user = None

    # ---------- basic calls ----------

    def _headers(self):
        return {"Authorization": f"Bearer {self.token}"} if self.token else {}

    def _request(self, method, path, extra_headers=None, **kwargs):
        headers = {**self._headers(), **(extra_headers or {})}
        try:
            response = requests.request(method, self.url + path, headers=headers, timeout=15, **kwargs)
        except requests.RequestException:
            raise ApiError(f"No hay conexión con el servidor ({self.url})")
        try:
            data = response.json()
        except ValueError:
            data = {}
        if not response.ok:
            message = data.get("detail") or data.get("message") or "No fue posible completar la operación"
            if isinstance(message, list):
                message = "Revisa los datos del formulario"
            raise ApiError(message)
        return data

    def graphql(self, query, variables=None):
        data = self._request("POST", "/graphql", json={"query": query, "variables": variables or {}})
        if data.get("errors"):
            raise ApiError(data["errors"][0]["message"])
        return data["data"]

    # ---------- session (Accounts Service) ----------

    def login(self, email, password):
        session = self._request("POST", "/api/accounts/login", json={"email": email, "password": password})
        if session["user"]["role"] != "ADMIN":
            self.token = session["token"]
            self.logout()
            raise ApiError("Esta aplicación es solo para el administrador de la tienda")
        self.token = session["token"]
        self.user = session["user"]

    def logout(self):
        try:
            self._request("POST", "/api/accounts/logout")
        except ApiError:
            pass
        self.token = None
        self.user = None

    def me(self):
        return self._request("GET", "/api/accounts/me")

    def update_me(self, data):
        self.user = self._request("PUT", "/api/accounts/me", json=data)
        return self.user

    def change_password(self, current, new):
        return self._request("PUT", "/api/accounts/me/password", json={"currentPassword": current, "newPassword": new})

    def users(self):
        return self._request("GET", "/api/accounts/users")

    def set_user_active(self, user_id, active):
        return self._request("PUT", f"/api/accounts/users/{user_id}/active", json={"active": active})

    # ---------- orders (Orders Service, GraphQL) ----------

    def orders(self, status=None):
        query = f"query ($status: String) {{ allOrders(status: $status) {{ {ORDER_FIELDS} }} }}"
        return self.graphql(query, {"status": status})["allOrders"]

    def update_order_status(self, order_id, status):
        query = "mutation ($id: ID!, $status: String!) { updateOrderStatus(orderId: $id, status: $status) { id status } }"
        return self.graphql(query, {"id": order_id, "status": status})["updateOrderStatus"]

    # ---------- catalog (Catalog Service, REST) ----------

    def store(self):
        return self._request("GET", "/api/catalog/store")

    def save_store(self, data):
        return self._request("PUT", "/api/catalog/store", json=data)

    def categories(self):
        return self._request("GET", "/api/catalog/categories")

    def create_category(self, name, slug, position):
        return self._request("POST", "/api/catalog/categories", json={"name": name, "slug": slug, "position": position})

    def delete_category(self, category_id):
        return self._request("DELETE", f"/api/catalog/categories/{category_id}")

    def products(self, search=""):
        return self._request("GET", "/api/catalog/products", params={"search": search})

    def save_product(self, data, product_id=None):
        if product_id:
            return self._request("PUT", f"/api/catalog/products/{product_id}", json=data)
        return self._request("POST", "/api/catalog/products", json=data)

    def delete_product(self, product_id):
        return self._request("DELETE", f"/api/catalog/products/{product_id}")

    # ---------- images (Image Provider, HTTP) ----------

    def upload_image(self, file_path):
        name = os.path.basename(file_path)
        extension = name.rsplit(".", 1)[-1].lower()
        content_type = "image/jpeg" if extension in ("jpg", "jpeg") else f"image/{extension}"
        with open(file_path, "rb") as file:
            data = self._request("POST", "/images", params={"name": name}, data=file.read(),
                                 extra_headers={"Content-Type": content_type})
        return data["name"]

    def image(self, name):
        try:
            return requests.get(f"{self.url}/images/{name}", timeout=10).content
        except requests.RequestException:
            return b""

    # ---------- email (Notifications Service, REST) ----------

    def email_settings(self):
        return self._request("GET", "/api/notifications/settings")

    def save_email_settings(self, data):
        return self._request("PUT", "/api/notifications/settings", json=data)

    def send_test_email(self, to):
        return self._request("POST", "/api/notifications/test", json={"to": to})

    def emails(self):
        return self._request("GET", "/api/notifications/emails")
