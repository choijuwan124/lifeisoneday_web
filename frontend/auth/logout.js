(() => {
  "use strict";

  const logoutButton = document.getElementById("logout-button");
  if (!logoutButton) return;

  logoutButton.addEventListener("click", async () => {
    if (typeof window.api?.post !== "function") return;

    logoutButton.disabled = true;
    const originalText = logoutButton.textContent;
    logoutButton.textContent = "로그아웃 중…";

    try {
      await window.api.post("/api/auth/logout");
    } catch (error) {
      console.error("로그아웃 요청에 실패했습니다.", error);
    } finally {
      window.location.href = "auth/login.html";
      logoutButton.textContent = originalText;
    }
  });
})();
