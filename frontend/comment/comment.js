// Comment widget for one diary. 민재 connects it with:
//   const widget = await CommentUI.mount({ root: document.getElementById("diary-comment-root"), groupId, diaryId });
// It returns { refresh(), destroy() }. Requires js/api.js (window.api) to be loaded first.
(() => {
  const MAX_LENGTH = 1000;
  const widgets = new WeakMap();

  // Same rule as the server: trim both ends, 1~1000 characters counted per code point.
  function validate(text) {
    const content = String(text ?? "").trim();
    if (!content) return { error: "댓글 내용을 입력해 주세요." };
    if ([...content].length > MAX_LENGTH) return { error: `댓글은 ${MAX_LENGTH}자 이하로 입력해 주세요.` };
    return { content };
  }

  function isComment(value) {
    return value !== null && typeof value === "object" && Number.isInteger(value.id)
      && typeof value.content === "string";
  }

  function formatTime(value) {
    return typeof value === "string" && value.length >= 16 ? value.slice(0, 16).replace("T", " ") : "";
  }

  async function mount({ root, groupId, diaryId }) {
    if (!root || typeof root.append !== "function") throw new TypeError("CommentUI.mount에는 DOM 요소 root가 필요합니다.");
    widgets.get(root)?.destroy();

    const path = `/api/groups/${encodeURIComponent(groupId)}/diaries/${encodeURIComponent(diaryId)}/comments`;
    const doc = root.ownerDocument;
    const view = doc.defaultView ?? globalThis;
    const make = (tag, className, text) => {
      const element = doc.createElement(tag);
      if (className) element.className = className;
      if (text !== undefined) element.textContent = text;
      return element;
    };

    const widget = make("section", "comment-widget");
    widget.setAttribute("aria-label", "댓글");
    const title = make("h2", "comment-widget-title", "댓글");
    const list = make("ul", "comment-widget-list");
    const empty = make("p", "comment-widget-empty", "아직 댓글이 없습니다.");
    const message = make("p", "comment-widget-message");
    message.setAttribute("role", "status");
    const retry = make("button", "comment-widget-retry", "댓글 다시 불러오기");
    retry.type = "button";
    const form = make("form", "comment-widget-form");
    const input = make("textarea", "comment-widget-input");
    input.setAttribute("aria-label", "댓글 입력");
    input.setAttribute("maxlength", String(MAX_LENGTH));
    input.setAttribute("rows", "3");
    input.setAttribute("placeholder", "댓글을 입력하세요");
    const submit = make("button", "comment-widget-submit", "댓글 작성");
    submit.type = "submit";
    form.append(input, submit);
    widget.append(title, list, empty, message, retry, form);
    root.append(widget);

    let comments = null; // null until the first successful load
    let destroyed = false;
    let creating = false;
    let version = 0; // bumped by every successful change so an older list response is not applied
    let loading = null;
    const editing = new Map(); // commentId -> { draft, pending, error }
    const busy = new Set(); // commentIds with a delete in flight
    let itemListeners = [];
    const listeners = [];

    function on(element, type, handler, bucket = listeners) {
      element.addEventListener(type, handler);
      bucket.push([element, type, handler]);
    }

    function off(bucket) {
      for (const [element, type, handler] of bucket) element.removeEventListener(type, handler);
      bucket.length = 0;
    }

    function setMessage(text) {
      message.textContent = text ?? "";
    }

    function renderItem(comment) {
      const item = make("li", "comment-widget-item");
      item.dataset.commentId = String(comment.id);
      const meta = make("p", "comment-widget-meta");
      const author = make("span", "comment-widget-author", comment.authorNickname ?? "");
      const time = make("span", "comment-widget-time", formatTime(comment.createdAt));
      meta.append(author, time);
      if (comment.updatedAt && comment.updatedAt !== comment.createdAt) {
        meta.append(make("span", "comment-widget-edited", `수정됨 ${formatTime(comment.updatedAt)}`));
      }
      item.append(meta);

      const edit = editing.get(comment.id);
      if (edit) {
        const box = make("textarea", "comment-widget-edit-input");
        box.setAttribute("aria-label", "댓글 수정");
        box.setAttribute("maxlength", String(MAX_LENGTH));
        box.value = edit.draft;
        box.disabled = edit.pending;
        const save = make("button", "comment-widget-save", "저장");
        save.type = "button";
        save.disabled = edit.pending;
        const cancel = make("button", "comment-widget-cancel", "취소");
        cancel.type = "button";
        cancel.disabled = edit.pending;
        const error = make("p", "comment-widget-item-message", edit.error ?? "");
        error.setAttribute("role", "status");
        on(box, "input", () => { edit.draft = box.value; }, itemListeners);
        on(save, "click", () => saveEdit(comment.id), itemListeners);
        on(cancel, "click", () => cancelEdit(comment.id), itemListeners);
        const actions = make("div", "comment-widget-actions");
        actions.append(save, cancel);
        item.append(box, actions, error);
        item.setAttribute("aria-busy", String(edit.pending));
        return item;
      }

      item.append(make("p", "comment-widget-content", comment.content));
      const actions = make("div", "comment-widget-actions");
      if (comment.canEdit) {
        const editButton = make("button", "comment-widget-edit", "수정");
        editButton.type = "button";
        editButton.disabled = busy.has(comment.id);
        on(editButton, "click", () => startEdit(comment.id), itemListeners);
        actions.append(editButton);
      }
      if (comment.canDelete) {
        const remove = make("button", "comment-widget-delete", "삭제");
        remove.type = "button";
        remove.disabled = busy.has(comment.id);
        on(remove, "click", () => removeComment(comment.id), itemListeners);
        actions.append(remove);
      }
      if (actions.children.length) item.append(actions);
      item.setAttribute("aria-busy", String(busy.has(comment.id)));
      return item;
    }

    function render() {
      if (destroyed) return;
      off(itemListeners);
      list.replaceChildren(...(comments ?? []).map(renderItem));
      empty.hidden = !(comments && comments.length === 0);
      retry.hidden = comments !== null || loading !== null;
      form.hidden = comments === null;
      input.disabled = creating;
      submit.disabled = creating;
      widget.setAttribute("aria-busy", String(creating || loading !== null));
    }

    async function refresh() {
      if (destroyed) return;
      if (loading) return loading;
      const started = version;
      setMessage("");
      loading = (async () => {
        try {
          const result = await window.api.get(path);
          if (destroyed) return;
          if (!Array.isArray(result) || !result.every(isComment)) throw new Error("댓글 목록을 확인할 수 없습니다.");
          if (version !== started) return "stale";
          comments = result;
          for (const id of [...editing.keys()]) if (!comments.some(comment => comment.id === id)) editing.delete(id);
        } catch (error) {
          if (!destroyed) setMessage(error?.message || "댓글을 불러오지 못했습니다.");
        } finally {
          loading = null;
          render();
        }
      })();
      render();
      const outcome = await loading;
      if (outcome === "stale" && !destroyed) await refresh();
    }

    async function create(event) {
      event?.preventDefault?.();
      if (destroyed || creating || comments === null) return;
      const checked = validate(input.value);
      if (checked.error) { setMessage(checked.error); return; }
      creating = true;
      setMessage("");
      input.value = ""; // [실습] 요청 성공 전에 입력창을 비움
      render();
      try {
        const created = await window.api.post(path, { content: checked.content });
        if (destroyed) return;
        if (!isComment(created)) throw new Error("작성한 댓글을 확인할 수 없습니다. 목록을 다시 불러와 주세요.");
        comments = [...comments, created];
        version += 1;
        input.value = ""; // only after the server confirmed the new comment
      } catch (error) {
        if (!destroyed) setMessage(error?.message || "댓글을 작성하지 못했습니다.");
      } finally {
        creating = false;
        render();
      }
    }

    function startEdit(id) {
      const comment = comments?.find(entry => entry.id === id);
      if (destroyed || !comment || editing.has(id) || busy.has(id)) return;
      editing.set(id, { draft: comment.content, pending: false, error: "" });
      render();
    }

    function cancelEdit(id) {
      const edit = editing.get(id);
      if (destroyed || !edit || edit.pending) return;
      editing.delete(id);
      render();
    }

    async function saveEdit(id) {
      const edit = editing.get(id);
      if (destroyed || !edit || edit.pending) return;
      const checked = validate(edit.draft);
      if (checked.error) { edit.error = checked.error; render(); return; }
      edit.pending = true;
      edit.error = "";
      render();
      try {
        const updated = await window.api.put(`${path}/${encodeURIComponent(id)}`, { content: checked.content });
        if (destroyed) return;
        if (!isComment(updated) || updated.id !== id) throw new Error("수정한 댓글을 확인할 수 없습니다.");
        comments = comments.map(comment => (comment.id === id ? updated : comment));
        version += 1;
        editing.delete(id);
      } catch (error) {
        if (!destroyed) {
          edit.pending = false;
          edit.error = error?.message || "댓글을 수정하지 못했습니다.";
        }
      } finally {
        if (!destroyed && editing.get(id) === edit) edit.pending = false;
        render();
      }
    }

    async function removeComment(id) {
      if (destroyed || busy.has(id)) return;
      const confirmDelete = typeof view.confirm === "function" ? view.confirm.bind(view) : () => true;
      if (!confirmDelete("댓글을 삭제할까요?")) return;
      busy.add(id);
      setMessage("");
      render();
      try {
        // A successful delete has data:null, so api.js returns the whole ApiResponse. It is not a Comment.
        await window.api.delete(`${path}/${encodeURIComponent(id)}`);
        if (destroyed) return;
        comments = comments.filter(comment => comment.id !== id);
        version += 1;
        editing.delete(id);
      } catch (error) {
        if (!destroyed) setMessage(error?.message || "댓글을 삭제하지 못했습니다.");
      } finally {
        busy.delete(id);
        render();
      }
    }

    function destroy() {
      if (destroyed) return;
      destroyed = true;
      off(itemListeners);
      off(listeners);
      widget.remove();
      if (widgets.get(root) === controls) widgets.delete(root);
    }

    const controls = { refresh, destroy };
    widgets.set(root, controls);
    on(form, "submit", create);
    on(retry, "click", () => refresh());
    render();
    await refresh();
    return controls;
  }

  window.CommentUI = { mount };
})();
