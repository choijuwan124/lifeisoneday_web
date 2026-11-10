(() => {
  "use strict";

  const form = document.getElementById("signup-form");
  if (!form) return;

  const username = document.getElementById("username");
  const password = document.getElementById("password");
  const nickname = document.getElementById("nickname");
  const submit = document.getElementById("signup-submit");
  const message = document.getElementById("signup-message");
  const success = document.getElementById("signup-success");
  const loginLink = document.getElementById("login-link");
  const fields = [username, password, nickname];
  let pending = false;
  let completed = false;

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
    showMessage("회원가입을 준비하지 못했습니다. 페이지를 새로고침해 주세요.", true);
    return;
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (pending || completed) return;

    const labels = ["아이디", "비밀번호", "닉네임"];
    const limits = [50, 128, 50];
    fields.forEach((field, index) => {
      field.setCustomValidity("");
      if (!field.value.trim()) {
        field.setCustomValidity(`${labels[index]}을(를) 입력해 주세요.`);
      } else if (field.value.length > limits[index]) {
        field.setCustomValidity(`${labels[index]}은(는) ${limits[index]}자 이하여야 합니다.`);
      }
    });
    if (!form.reportValidity()) return;

    const payload = {
      username: username.value.trim(),
      password: password.value,
      nickname: nickname.value.trim(),
    };
    pending = true;
    submit.disabled = true;
    fields.forEach((field) => { field.disabled = true; });
    submit.textContent = "가입 중…";
    form.setAttribute("aria-busy", "true");
    showMessage("회원가입을 처리하고 있습니다.");

    try {
      const result = await window.api.post("/api/auth/signup", payload);
      if (result?.success !== true) {
        throw new Error(result?.message || "회원가입 결과를 확인하지 못했습니다. 다시 시도해 주세요.");
      }
      completed = true;
      form.reset();
      form.hidden = true;
      success.hidden = false;
      showMessage(result.message || "회원가입이 완료되었습니다. 로그인해 주세요.");
      loginLink.focus();
    } catch (error) {
      const text = error instanceof TypeError
        ? "서버에 연결할 수 없습니다. 연결 상태를 확인한 뒤 다시 시도해 주세요."
        : error.message || "회원가입에 실패했습니다. 다시 시도해 주세요.";
      showMessage(text, true);
    } finally {
      pending = false;
      form.removeAttribute("aria-busy");
      if (!completed) {
        fields.forEach((field) => { field.disabled = false; });
        submit.disabled = false;
        submit.textContent = "회원가입";
      }
    }
  });

  submit.disabled = false;
})();
