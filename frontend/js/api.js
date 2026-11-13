// Keep the loopback hostname consistent so session cookies work with Live Server too.
const API_HOST = window.location.hostname === "127.0.0.1" ? "127.0.0.1" : "localhost";
const API_BASE_URL = `http://${API_HOST}:8080`;

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    credentials: "include",
    ...options,
    headers: {
      Accept: "application/json",
      ...options.headers,
    },
  });

  const contentType = response.headers.get("content-type") ?? "";
  const body = contentType.includes("application/json") ? await response.json() : null;

  if (!response.ok) {
    throw new Error(body?.message ?? "요청 처리 중 오류가 발생했습니다.");
  }

  return body?.data ?? body;
}

function jsonRequest(method, path, data) {
  return request(path, {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

window.api = {
  get: (path) => request(path),
  post: (path, data) => jsonRequest("POST", path, data),
  put: (path, data) => jsonRequest("PUT", path, data),
  patch: (path, data) => jsonRequest("PATCH", path, data),
  delete: (path) => request(path, { method: "DELETE" }),
};
