(() => {
  "use strict";

  const form = document.getElementById("login-form");
  if (!form) return;

  const username = document.getElementById("username");
  const password = document.getElementById("password");
  const submit = document.getElementById("login-submit");
  const message = document.getElementById("login-message");
  const fields = [username, password];
  let pending = false;

  function showMessage(text, isError = false) {
    message.textContent = text;
    message.dataset.state = isError ? "error" : "success";
    message.setAttribute("role", isError ? "alert" : "status");
    message.setAttribute("aria-live", isError ? "assertive" : "polite");
  }

  for (const field of fields) {
    field.addEventListener("input", () => field.setCustomValidity(""));
  }

  if (typeof window.api?.post !== "function") {
    showMessage("로그인을 준비하지 못했습니다. 페이지를 새로고침해 주세요.", true);
    return;
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (pending) return;

    username.setCustomValidity("");
    password.setCustomValidity("");
    if (!username.value.trim()) username.setCustomValidity("아이디를 입력해 주세요.");
    if (!password.value) password.setCustomValidity("비밀번호를 입력해 주세요.");
    if (!form.reportValidity()) return;

    pending = true;
    submit.disabled = true;
    fields.forEach((field) => { field.disabled = true; });
    submit.textContent = "로그인 중…";
    form.setAttribute("aria-busy", "true");
    showMessage("로그인 정보를 확인하고 있습니다.");

    try {
      const result = await window.api.post("/api/auth/login", {
        username: username.value.trim(),
        password: password.value,
      });
      if (result?.success !== true) {
        throw new Error(result?.message || "로그인 결과를 확인하지 못했습니다. 다시 시도해 주세요.");
      }
      showMessage(result.message || "로그인되었습니다. 이동합니다.");
      window.location.href = "../index.html";
    } catch (error) {
      const text = error instanceof TypeError
        ? "서버에 연결할 수 없습니다. 연결 상태를 확인한 뒤 다시 시도해 주세요."
        : error.message || "로그인에 실패했습니다. 다시 시도해 주세요.";
      showMessage(text, true);
      fields.forEach((field) => { field.disabled = false; });
      submit.disabled = false;
      submit.textContent = "로그인";
    } finally {
      pending = false;
      form.removeAttribute("aria-busy");
    }
  });

  submit.disabled = false;
})();
