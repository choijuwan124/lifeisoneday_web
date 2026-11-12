(() => {
  "use strict";

  const createForm = document.getElementById("group-create-form");
  const list = document.getElementById("group-list-items");
  const detail = document.getElementById("group-detail-content");

  function unwrap(response) {
    if (response && typeof response === "object" && "success" in response) {
      if (!response.success) {
        throw new Error(response.message || "요청을 처리하지 못했습니다.");
      }
      return response.data;
    }
    return response;
  }

  async function apiGet(path) {
    if (typeof window.api?.get !== "function") {
      throw new Error("API를 준비하지 못했습니다. 페이지를 새로고침해 주세요.");
    }
    return unwrap(await window.api.get(path));
  }

  async function apiPost(path, body) {
    if (typeof window.api?.post !== "function") {
      throw new Error("API를 준비하지 못했습니다. 페이지를 새로고침해 주세요.");
    }
    return unwrap(await window.api.post(path, body));
  }

  function setMessage(element, message, state = "info") {
    if (!element) return;
    element.textContent = message;
    element.dataset.state = state;
    element.hidden = !message;
    element.setAttribute("role", state === "error" ? "alert" : "status");
    element.setAttribute("aria-live", state === "error" ? "assertive" : "polite");
  }

  function positiveId(value) {
    const id = Number(value);
    return Number.isSafeInteger(id) && id > 0 ? id : null;
  }

  function detailHref(id) {
    const url = new URL("group-detail.html", window.location.href);
    url.searchParams.set("groupId", String(id));
    return url.href;
  }

  async function loadList() {
    const loading = document.getElementById("group-list-loading");
    const empty = document.getElementById("group-list-empty");
    const error = document.getElementById("group-list-error");

    loading.hidden = false;
    empty.hidden = true;
    list.hidden = true;
    setMessage(error, "");

    try {
      const groups = await apiGet("/api/groups");
      if (!Array.isArray(groups)) {
        throw new Error("단체 일기장 목록 형식이 올바르지 않습니다.");
      }

      loading.hidden = true;
      if (groups.length === 0) {
        empty.hidden = false;
        return;
      }

      const fragment = document.createDocumentFragment();
      for (const group of groups) {
        const id = positiveId(group?.id);
        if (id === null) continue;

        const item = document.createElement("li");
        item.className = "group-list-item";

        const link = document.createElement("a");
        link.className = "group-list-link";
        link.href = detailHref(id);
        link.textContent = typeof group.name === "string" ? group.name : "이름 없는 일기장";
        item.append(link);

        if (typeof group.description === "string" && group.description.length > 0) {
          const description = document.createElement("p");
          description.className = "group-list-description";
          description.textContent = group.description;
          item.append(description);
        }
        fragment.append(item);
      }

      if (fragment.childNodes.length === 0) {
        throw new Error("표시할 수 있는 단체 일기장 정보가 없습니다.");
      }
      list.replaceChildren(fragment);
      list.hidden = false;
    } catch (requestError) {
      loading.hidden = true;
      setMessage(error, requestError.message || "목록을 불러오지 못했습니다.", "error");
    }
  }

  function initCreateForm() {
    const nameInput = document.getElementById("diary-name");
    const descriptionInput = document.getElementById("diary-description");
    const submitButton = document.getElementById("group-create-submit");
    const status = document.getElementById("group-create-status");
    let pending = false;

    for (const field of [nameInput, descriptionInput]) {
      field.addEventListener("input", () => field.setCustomValidity(""));
    }

    createForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (pending) return;

      nameInput.setCustomValidity(nameInput.value.trim() ? "" : "일기장 이름을 입력해 주세요.");
      if (!createForm.reportValidity()) return;

      pending = true;
      submitButton.disabled = true;
      createForm.setAttribute("aria-busy", "true");
      setMessage(status, "단체 일기장을 만들고 있습니다.");

      try {
        const group = await apiPost("/api/groups", {
          name: nameInput.value.trim(),
          description: descriptionInput.value.trim(),
        });
        const id = positiveId(group?.id);
        if (id === null) {
          throw new Error("생성 결과에서 일기장 번호를 확인하지 못했습니다.");
        }

        setMessage(status, "단체 일기장을 만들었습니다. 상세 화면으로 이동합니다.", "success");
        window.location.assign(detailHref(id));
      } catch (requestError) {
        setMessage(status, requestError.message || "단체 일기장을 만들지 못했습니다.", "error");
        pending = false;
        submitButton.disabled = false;
        createForm.removeAttribute("aria-busy");
      }
    });
  }

  function formatCreatedAt(value) {
    if (typeof value !== "string" || value.length === 0) return "정보 없음";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return new Intl.DateTimeFormat("ko-KR", {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(date);
  }

  async function loadDetail() {
    const loading = document.getElementById("group-detail-loading");
    const error = document.getElementById("group-detail-error");
    const params = new URLSearchParams(window.location.search);
    const groupId = positiveId(params.get("groupId"));

    if (groupId === null) {
      loading.hidden = true;
      setMessage(error, "일기장 번호가 없거나 올바르지 않습니다. 목록에서 다시 선택해 주세요.", "error");
      return;
    }

    try {
      const group = await apiGet(`/api/groups/${groupId}`);
      if (!group || typeof group !== "object" || positiveId(group.id) !== groupId) {
        throw new Error("단체 일기장 정보를 확인할 수 없습니다.");
      }

      document.getElementById("group-detail-name").textContent =
        typeof group.name === "string" ? group.name : "이름 없는 일기장";
      document.getElementById("group-detail-description").textContent =
        typeof group.description === "string" && group.description.length > 0
          ? group.description
          : "등록된 설명이 없습니다.";
      document.getElementById("group-detail-owner").textContent =
        group.ownerId == null ? "정보 없음" : String(group.ownerId);
      document.getElementById("group-detail-created-at").textContent = formatCreatedAt(group.createdAt);

      const memberLink = document.getElementById("group-members-link");
      const memberContext = document.getElementById("group-members-context");
      const memberUrl = new URL(memberLink.dataset.route, window.location.href);
      memberUrl.searchParams.set("groupId", String(groupId));
      memberLink.href = memberUrl.href;
      memberContext.textContent = String(groupId);
      memberLink.addEventListener("click", (event) => {
        if (memberLink.getAttribute("aria-disabled") === "true") event.preventDefault();
      });

      loading.hidden = true;
      detail.hidden = false;
    } catch (requestError) {
      loading.hidden = true;
      setMessage(error, requestError.message || "일기장 정보를 불러오지 못했습니다.", "error");
    }
  }

  if (createForm) initCreateForm();
  if (list) loadList();
  if (detail) loadDetail();
})();
