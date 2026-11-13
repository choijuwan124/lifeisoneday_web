(() => {
  "use strict";

  const logoutButton = document.getElementById("logout-button");
  if (!logoutButton) return;
  const message = document.getElementById("logout-message");
  let pending = false;

  logoutButton.addEventListener("click", async () => {
    if (pending) return;
    pending = true;

    logoutButton.disabled = true;
    const originalText = logoutButton.textContent;
    logoutButton.textContent = "로그아웃 중…";
    if (message) message.hidden = true;

    try {
      if (typeof window.api?.post !== "function") {
        throw new Error("로그아웃을 준비하지 못했습니다. 페이지를 새로고침해 주세요.");
      }
      const result = await window.api.post("/api/auth/logout");
      if (result?.success !== true) {
        throw new Error("로그아웃 결과를 확인하지 못했습니다. 다시 시도해 주세요.");
      }
      window.location.href = logoutButton.dataset.loginUrl || "auth/login.html";
    } catch (error) {
      const text = error instanceof TypeError
        ? "서버에 연결할 수 없어 로그아웃하지 못했습니다. 연결을 확인하고 다시 시도해 주세요."
        : error.message || "로그아웃하지 못했습니다. 다시 시도해 주세요.";
      if (message) {
        message.textContent = text;
        message.hidden = false;
      } else {
        window.alert(text);
      }
    } finally {
      pending = false;
      logoutButton.disabled = false;
      logoutButton.textContent = originalText;
    }
  });
})();
