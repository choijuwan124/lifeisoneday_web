// Private diary photo viewer/editor. Load after api.js; diary pages own their HTML and mount into a root.
(() => {
  const mounts = new WeakMap();

  function dataOrNull(value) {
    if (value?.success === true && value.data === null) return null;
    return value?.data ?? value;
  }

  function isPhoto(value) {
    return value !== null && typeof value === "object" && Number.isInteger(value.id)
      && Number.isInteger(value.diaryId) && typeof value.contentPath === "string";
  }

  function mountRoot(root, label, build) {
    if (!root || typeof root.append !== "function") throw new TypeError("PhotoUI에는 DOM 요소 root가 필요합니다.");
    mounts.get(root)?.destroy();
    const doc = root.ownerDocument;
    const view = doc.defaultView ?? globalThis;
    const listeners = [];
    const make = (tag, className, text) => {
      const element = doc.createElement(tag);
      if (className) element.className = className;
      if (text !== undefined) element.textContent = text;
      return element;
    };
    const section = make("section", "photo-widget");
    section.setAttribute("aria-label", label);
    const preview = make("div", "photo-widget-preview");
    const message = make("p", "photo-widget-message");
    message.setAttribute("role", "status");
    const retry = make("button", "photo-widget-retry", "사진 다시 불러오기");
    retry.type = "button";
    root.append(section);
    let destroyed = false;
    let revision = 0;
    const urls = new Set();
    const listen = (element, type, handler) => {
      element.addEventListener(type, handler);
      listeners.push([element, type, handler]);
    };
    const createUrl = (blob) => {
      const url = view.URL.createObjectURL(blob);
      urls.add(url);
      return url;
    };
    const revoke = (url) => {
      if (!url || !urls.delete(url)) return;
      view.URL.revokeObjectURL(url);
    };
    const image = (url, alt = "일기 사진") => {
      const img = make("img", "photo-widget-image");
      img.src = url;
      img.alt = alt;
      return img;
    };
    const destroy = () => {
      if (destroyed) return;
      destroyed = true;
      revision += 1;
      for (const [element, type, handler] of listeners) element.removeEventListener(type, handler);
      listeners.length = 0;
      for (const url of [...urls]) revoke(url);
      section.remove();
      if (mounts.get(root)?.destroy === destroy) mounts.delete(root);
    };
    const base = { root, doc, view, section, preview, message, retry, make, listen, createUrl, revoke, image,
      get destroyed() { return destroyed; }, nextRevision: () => ++revision,
      isCurrent: token => !destroyed && token === revision, destroy };
    mounts.set(root, base);
    build(base);
    return base;
  }

  function apiPath(groupId, diaryId) {
    return `/api/groups/${encodeURIComponent(groupId)}/diaries/${encodeURIComponent(diaryId)}/photo`;
  }

  async function mountViewer({ root, groupId, diaryId }) {
    const ui = mountRoot(root, "일기 사진", () => {});
    const path = apiPath(groupId, diaryId);
    let currentUrl = null;
    const load = async () => {
      const token = ui.nextRevision();
      ui.message.textContent = "사진을 불러오는 중입니다.";
      ui.retry.hidden = true;
      try {
        const photo = dataOrNull(await window.api.get(path));
        if (!ui.isCurrent(token)) return;
        if (photo === null) {
          if (currentUrl) ui.revoke(currentUrl);
          currentUrl = null;
          ui.preview.replaceChildren(ui.make("p", "photo-widget-empty", "등록된 사진이 없습니다."));
          ui.message.textContent = "";
          return;
        }
        if (!isPhoto(photo)) throw new Error("사진 정보를 확인하지 못했습니다.");
        const blob = await window.api.getBlob(photo.contentPath);
        if (!ui.isCurrent(token)) return;
        const nextUrl = ui.createUrl(blob);
        ui.preview.replaceChildren(ui.image(nextUrl));
        if (currentUrl) ui.revoke(currentUrl);
        currentUrl = nextUrl;
        ui.message.textContent = "";
      } catch (error) {
        if (!ui.isCurrent(token)) return;
        ui.message.textContent = error?.message ?? "사진을 불러오지 못했습니다.";
        ui.retry.hidden = false;
      }
    };
    ui.section.append(ui.preview, ui.message, ui.retry);
    ui.listen(ui.retry, "click", () => { void load(); });
    await load();
    return { refresh: load, destroy: ui.destroy };
  }

  async function mountEditor({ root, groupId, diaryId = null }) {
    const ui = mountRoot(root, "사진 첨부", () => {});
    const pathFor = id => apiPath(groupId, id);
    const input = ui.make("input", "photo-widget-input");
    input.type = "file";
    input.accept = "image/jpeg,image/png,.jpg,.jpeg,.png";
    input.setAttribute("aria-label", "JPG 또는 PNG 사진 선택");
    const actions = ui.make("div", "photo-widget-actions");
    const changeButton = ui.make("label", "photo-widget-change", "사진 선택");
    changeButton.append(input);
    const stateButton = ui.make("button", "photo-widget-state", "");
    stateButton.type = "button";
    actions.append(changeButton, stateButton);
    ui.section.append(ui.preview, actions, ui.message, ui.retry);

    let currentPhoto = null;
    let currentUrl = null;
    let selectedFile = null;
    let selectedUrl = null;
    let deleting = false;

    const resetSelection = () => {
      selectedFile = null;
      input.value = "";
      if (selectedUrl) ui.revoke(selectedUrl);
      selectedUrl = null;
      deleting = false;
      render();
    };
    const render = () => {
      if (selectedUrl) ui.preview.replaceChildren(ui.image(selectedUrl, "선택한 사진 미리보기"));
      else if (currentUrl && !deleting) ui.preview.replaceChildren(ui.image(currentUrl));
      else ui.preview.replaceChildren(ui.make("p", "photo-widget-empty", "등록된 사진이 없습니다."));
      stateButton.hidden = !selectedFile && !currentPhoto;
      stateButton.textContent = selectedFile ? "선택 취소" : deleting ? "삭제 취소" : "사진 삭제";
      ui.message.textContent = "";
    };
    ui.listen(input, "change", () => {
      const file = input.files?.[0];
      if (!file) { resetSelection(); return; }
      if (selectedUrl) ui.revoke(selectedUrl);
      selectedFile = file;
      selectedUrl = ui.createUrl(file);
      deleting = false;
      render();
    });
    ui.listen(input, "cancel", resetSelection);
    ui.listen(stateButton, "click", () => {
      if (selectedFile) resetSelection();
      else {
        deleting = !deleting;
        render();
      }
    });
    ui.listen(ui.retry, "click", () => { void loadExisting(); });

    const loadExisting = async () => {
      if (diaryId == null) { render(); return; }
      const token = ui.nextRevision();
      ui.message.textContent = "기존 사진을 불러오는 중입니다.";
      ui.retry.hidden = true;
      try {
        const photo = dataOrNull(await window.api.get(pathFor(diaryId)));
        if (!ui.isCurrent(token)) return;
        let blob = null;
        if (photo !== null) {
          if (!isPhoto(photo)) throw new Error("사진 정보를 확인하지 못했습니다.");
          blob = await window.api.getBlob(photo.contentPath);
          if (!ui.isCurrent(token)) return;
        }
        if (currentUrl) ui.revoke(currentUrl);
        currentPhoto = photo;
        currentUrl = blob ? ui.createUrl(blob) : null;
        render();
      } catch (error) {
        if (!ui.isCurrent(token)) return;
        ui.message.textContent = error?.message ?? "사진을 불러오지 못했습니다.";
        ui.retry.hidden = false;
      }
    };

    if (diaryId != null) await loadExisting();
    else render();
    return {
      async save(savedDiaryId) {
        if (ui.destroyed) throw new Error("사진 편집기가 해제되었습니다.");
        const id = savedDiaryId ?? diaryId;
        if (!Number.isInteger(Number(id)) || Number(id) <= 0) throw new TypeError("일기를 먼저 저장해 주세요.");
        if (!selectedFile && !deleting) return currentPhoto;
        if (selectedFile) {
          const form = new ui.view.FormData();
          form.append("file", selectedFile);
          const result = dataOrNull(await window.api.upload(pathFor(id), form));
          if (!isPhoto(result)) throw new Error("사진 저장 결과를 확인하지 못했습니다.");
          if (currentUrl) ui.revoke(currentUrl);
          currentPhoto = result;
          currentUrl = selectedUrl;
          selectedUrl = null;
          selectedFile = null;
          input.value = "";
          deleting = false;
          diaryId = Number(id);
          render();
          return currentPhoto;
        }
        await window.api.delete(pathFor(id)); // data:null is a successful ApiResponse in api.js.
        if (currentUrl) ui.revoke(currentUrl);
        if (selectedUrl) ui.revoke(selectedUrl);
        currentUrl = null;
        selectedUrl = null;
        selectedFile = null;
        currentPhoto = null;
        deleting = false;
        input.value = "";
        diaryId = Number(id);
        render();
        return null;
      },
      isDirty: () => selectedFile !== null || deleting,
      destroy: ui.destroy,
    };
  }

  window.PhotoUI = { mountViewer, mountEditor };
})();
