(() => {
  "use strict";
  const root = new URL("../", document.currentScript.src);
  const header = document.querySelector(".site-header");
  if (!header) return;
  const protectedPage = /\/(group|member|diary)\//.test(window.location.pathname);
  const loginUrl = new URL("auth/login.html", root).href;
  const nav = header.querySelector("nav") || document.createElement("nav");
  nav.setAttribute("aria-label", "주요 메뉴");
  header.append(nav);

  function link(label, route) {
    const element = document.createElement("a");
    element.textContent = label;
    element.href = new URL(route, root).href;
    if (element.href === window.location.href.split("?")[0]) element.setAttribute("aria-current", "page");
    return element;
  }
  const groups = link("내 일기장", "group/group-list.html");
  const login = link("로그인", "auth/login.html");
  const signup = link("회원가입", "auth/signup.html");
  const nickname = document.createElement("span");
  nickname.className = "nav-user";
  nickname.hidden = true;
  const logout = document.getElementById("logout-button") || document.createElement("button");
  logout.id = "logout-button";
  logout.type = "button";
  logout.textContent = "로그아웃";
  logout.dataset.loginUrl = loginUrl;
  logout.hidden = true;
  nav.replaceChildren(groups, nickname, login, signup, logout);

  let message = document.getElementById("logout-message");
  if (!message) {
    message = document.createElement("p");
    message.id = "logout-message";
    message.setAttribute("role", "alert");
    message.hidden = true;
    header.after(message);
  }
  message.classList.add("site-message");
  window.addEventListener("auth:required", () => {
    if (protectedPage) window.location.replace(loginUrl);
  });
  // Recheck the session when returning to a cached page after logout in another tab.
  window.addEventListener("pageshow", event => {
    if (event.persisted) window.location.reload();
  });

  window.sessionReady = window.api.get("/api/auth/me").then(user => {
    if (!user || !Number.isSafeInteger(user.id) || typeof user.nickname !== "string") {
      throw new Error("로그인 상태를 확인하지 못했습니다. 새로고침해 주세요.");
    }
    nickname.textContent = `${user.nickname}님`;
    nickname.hidden = false;
    logout.hidden = false;
    login.hidden = true;
    signup.hidden = true;
    return user;
  }).catch(error => {
    if (error.status === 401) {
      if (protectedPage) window.location.replace(loginUrl);
    } else {
      message.textContent = "로그인 상태를 확인하지 못했습니다. 연결을 확인하고 새로고침해 주세요.";
      message.hidden = false;
    }
    return null;
  });
})();
