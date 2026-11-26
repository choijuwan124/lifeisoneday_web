(() => {
  const widgets = new WeakMap();

  async function mount({ root, groupId, diaryId }) {
    widgets.get(root)?.destroy();

    const path = `/api/groups/${encodeURIComponent(groupId)}/diaries/${encodeURIComponent(diaryId)}/likes`;
    const document = root.ownerDocument;
    const widget = document.createElement("div");
    widget.className = "like-widget";
    const button = document.createElement("button");
    button.type = "button";
    const count = document.createElement("span");
    count.setAttribute("aria-live", "polite");
    const message = document.createElement("span");
    message.className = "like-widget-message";
    message.setAttribute("role", "status");
    widget.append(button, count, message);
    root.append(widget);

    let status = null;
    let pending = null;
    let destroyed = false;

    function render() {
      button.textContent = status ? "좋아요" : "좋아요 다시 불러오기";
      button.setAttribute("aria-pressed", String(status?.likedByMe ?? false));
      count.textContent = status ? `좋아요 ${status.likeCount}개` : "";
    }

    function run(request) {
      button.disabled = true;
      widget.setAttribute("aria-busy", "true");
      message.textContent = "";
      pending = Promise.resolve().then(async () => {
        try {
          if (destroyed) return;
          const result = await request();
          if (destroyed) return;
          if (!Number.isInteger(result?.likeCount) || result.likeCount < 0
              || typeof result.likedByMe !== "boolean") {
            throw new Error("좋아요 상태를 확인할 수 없습니다.");
          }
          status = result;
          render();
        } catch (error) {
          if (!destroyed) {
            message.textContent = error?.message || "좋아요 요청에 실패했습니다.";
          }
        } finally {
          pending = null;
          if (!destroyed) {
            button.disabled = false;
            widget.setAttribute("aria-busy", "false");
          }
        }
      });
      return pending;
    }

    async function refresh() {
      while (pending) await pending;
      if (!destroyed) await run(() => window.api.get(path));
    }

    function toggle() {
      if (destroyed || pending) return;
      if (!status) return refresh();
      return run(() => status.likedByMe
        ? window.api.delete(`${path}/me`)
        : window.api.put(`${path}/me`));
    }

    function destroy() {
      if (destroyed) return;
      destroyed = true;
      button.removeEventListener("click", toggle);
      widget.remove();
      widgets.delete(root);
    }

    const controls = { refresh, destroy };
    widgets.set(root, controls);
    button.addEventListener("click", toggle);
    render();
    await refresh();
    return controls;
  }

  window.LikeUI = { mount };
})();
