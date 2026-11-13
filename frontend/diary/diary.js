(() => {
  "use strict";
  const byId = (id) => document.getElementById(id);
  const positiveId = (value) => {
    if (!/^[1-9]\d*$/.test(String(value ?? ""))) return null;
    const number = Number(value);
    return Number.isSafeInteger(number) ? number : null;
  };
  const params = new URLSearchParams(window.location.search);
  const groupId = positiveId(params.get("groupId"));
  const diaryId = positiveId(params.get("diaryId"));
  const error = byId("diary-error");
  function showError(message) {
    if (!error) return;
    error.textContent = message;
    error.hidden = false;
  }
  function groupUrl(file) {
    const url = new URL(file, window.location.href);
    if (groupId) url.searchParams.set("groupId", String(groupId));
    return url.href;
  }
  function diaryUrl(file, id = diaryId) {
    const url = new URL(file, window.location.href);
    if (groupId) url.searchParams.set("groupId", String(groupId));
    if (id) url.searchParams.set("diaryId", String(id));
    return url.href;
  }
  function formatDate(value) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "정보 없음" : new Intl.DateTimeFormat("ko-KR", {dateStyle: "medium", timeStyle: "short"}).format(date);
  }
  async function api(method, path, body) {
    const fn = window.api?.[method];
    if (typeof fn !== "function") throw new Error("API를 준비하지 못했습니다. 페이지를 새로고침해 주세요.");
    const response = await fn(path, ...(body === undefined ? [] : [body]));
    if (response && typeof response === "object" && "success" in response && !response.success) {
      throw new Error(response.message || "요청을 처리하지 못했습니다.");
    }
    return response && typeof response === "object" && "success" in response ? response.data : response;
  }
  function requireIds(needsDiary) {
    if (!groupId || (needsDiary && !diaryId)) throw new Error("주소의 일기장 또는 일기 번호가 올바르지 않습니다.");
  }
  async function loadList() {
    const loading = byId("diary-loading");
    const list = byId("diary-list");
    const empty = byId("diary-empty");
    try {
      requireIds(false);
      byId("diary-create-link").href = groupUrl("diary-create.html");
      byId("diary-group-link").href = groupUrl("../group/group-detail.html");
      const entries = await api("get", `/api/groups/${groupId}/diaries`);
      if (!Array.isArray(entries)) throw new Error("일기 목록 형식이 올바르지 않습니다.");
      loading.hidden = true;
      if (entries.length === 0) { empty.hidden = false; return; }
      const fragment = document.createDocumentFragment();
      for (const entry of entries) {
        const id = positiveId(entry?.id);
        if (!id) continue;
        const item = document.createElement("li");
        const link = document.createElement("a");
        link.href = diaryUrl("diary-detail.html", id);
        link.textContent = typeof entry.title === "string" ? entry.title : "제목 없는 일기";
        const meta = document.createElement("p");
        meta.textContent = `${entry.authorNickname || "알 수 없는 작성자"} · ${formatDate(entry.createdAt)}`;
        item.append(link, meta);
        fragment.append(item);
      }
      if (!fragment.childNodes.length) { empty.hidden = false; return; }
      list.replaceChildren(fragment);
      list.hidden = false;
    } catch (e) { loading.hidden = true; showError(e.message || "일기 목록을 불러오지 못했습니다."); }
  }
  function initCreate() {
    const form = byId("diary-create-form");
    const title = byId("diary-title");
    const content = byId("diary-content");
    const submit = byId("diary-submit");
    const cancel = byId("diary-cancel");
    if (!groupId) { form.hidden = true; showError("일기장 번호가 올바르지 않습니다."); return; }
    cancel.href = groupUrl("diary-list.html");
    let pending = false;
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (pending) return;
      if (!title.value.trim() || !content.value.trim()) { showError("제목과 내용을 입력해 주세요."); return; }
      if (title.value.trim().length > 200 || content.value.length > 10000) { showError("제목은 200자, 내용은 10,000자 이내로 작성해 주세요."); return; }
      pending = true; submit.disabled = true; form.setAttribute("aria-busy", "true"); error.hidden = true;
      try {
        const created = await api("post", `/api/groups/${groupId}/diaries`, {title: title.value.trim(), content: content.value});
        const id = positiveId(created?.id);
        if (!id) throw new Error("생성된 일기 번호를 확인하지 못했습니다.");
        window.location.assign(diaryUrl("diary-detail.html", id));
      } catch (e) { showError(e.message || "일기를 저장하지 못했습니다."); pending = false; submit.disabled = false; form.removeAttribute("aria-busy"); }
    });
  }
  async function loadDetail() {
    const loading = byId("diary-loading");
    try {
      requireIds(true);
      const entry = await api("get", `/api/groups/${groupId}/diaries/${diaryId}`);
      if (!entry || positiveId(entry.id) !== diaryId || positiveId(entry.groupId) !== groupId) throw new Error("일기 정보를 확인할 수 없습니다.");
      byId("diary-detail-title").textContent = entry.title;
      byId("diary-author").textContent = entry.authorNickname || "알 수 없는 작성자";
      byId("diary-created").textContent = formatDate(entry.createdAt);
      byId("diary-updated").textContent = formatDate(entry.updatedAt);
      byId("diary-detail-content").textContent = entry.content;
      byId("diary-list-link").href = groupUrl("diary-list.html");
      if (entry.canEdit === true && entry.canDelete === true) {
        byId("diary-owner-actions").hidden = false;
        byId("diary-edit-link").href = diaryUrl("diary-edit.html");
        byId("diary-delete").addEventListener("click", async () => {
          if (!window.confirm("이 일기를 삭제할까요?")) return;
          const button = byId("diary-delete");
          button.disabled = true; error.hidden = true;
          try {
            await api("delete", `/api/groups/${groupId}/diaries/${diaryId}`);
            window.location.assign(groupUrl("diary-list.html"));
          } catch (e) { showError(e.message || "일기를 삭제하지 못했습니다."); button.disabled = false; }
        });
      }
      loading.hidden = true;
      byId("diary-detail").hidden = false;
    } catch (e) { loading.hidden = true; showError(e.message || "일기를 불러오지 못했습니다."); }
  }
  async function initEdit() {
    const loading = byId("diary-loading");
    const form = byId("diary-edit-form");
    const title = byId("diary-title");
    const content = byId("diary-content");
    const submit = byId("diary-submit");
    try {
      requireIds(true);
      byId("diary-cancel").href = diaryUrl("diary-detail.html");
      const entry = await api("get", `/api/groups/${groupId}/diaries/${diaryId}`);
      if (!entry || positiveId(entry.id) !== diaryId || positiveId(entry.groupId) !== groupId) throw new Error("일기 정보를 확인할 수 없습니다.");
      loading.hidden = true;
      if (entry.canEdit !== true) { byId("diary-no-permission").hidden = false; return; }
      title.value = entry.title;
      content.value = entry.content;
      form.hidden = false;
      let pending = false;
      form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (pending) return;
        if (!title.value.trim() || !content.value.trim()) { showError("제목과 내용을 입력해 주세요."); return; }
        if (title.value.trim().length > 200 || content.value.length > 10000) { showError("제목은 200자, 내용은 10,000자 이내로 작성해 주세요."); return; }
        pending = true; submit.disabled = true; form.setAttribute("aria-busy", "true"); error.hidden = true;
        try {
          await api("put", `/api/groups/${groupId}/diaries/${diaryId}`, {title: title.value.trim(), content: content.value});
          window.location.assign(diaryUrl("diary-detail.html"));
        } catch (e) { showError(e.message || "일기를 수정하지 못했습니다."); pending = false; submit.disabled = false; form.removeAttribute("aria-busy"); }
      });
    } catch (e) { loading.hidden = true; showError(e.message || "일기를 불러오지 못했습니다."); }
  }
  if (byId("diary-list")) loadList();
  if (byId("diary-create-form")) initCreate();
  if (byId("diary-detail")) loadDetail();
  if (byId("diary-edit-form")) initEdit();
})();


