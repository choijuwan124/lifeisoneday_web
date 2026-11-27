import fs from "node:fs";
import vm from "node:vm";
import test from "node:test";
import assert from "node:assert/strict";

// DOM and API doubles: these checks run in Node, not in a real browser.
const script = fs.readFileSync(new URL("../comment/comment.js", import.meta.url), "utf8");
const path = "/api/groups/4/diaries/7/comments";
const T0 = "2026-11-27T10:00:00";

const comment = (id, content, mine = false, extra = {}) => ({
  id, diaryId: 7, authorId: mine ? 9 : 10, authorNickname: mine ? "나" : "친구", content,
  createdAt: T0, updatedAt: T0, canEdit: mine, canDelete: mine, ...extra,
});

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((onResolve, onReject) => { resolve = onResolve; reject = onReject; });
  return { promise, resolve, reject };
}

const flush = () => new Promise(resolve => setImmediate(resolve));

function setup({ list = [comment(1, "첫 댓글")], confirm = () => true } = {}) {
  let listenerCount = 0;
  const view = { confirm: (...args) => confirm(...args) };

  class Element {
    constructor(tag) {
      this.tagName = tag.toUpperCase();
      this.children = [];
      this.attributes = {};
      this.listeners = new Map();
      this.dataset = {};
      this.hidden = false;
      this.disabled = false;
      this.value = "";
      this.className = "";
      this._text = "";
    }
    get ownerDocument() { return doc; }
    get textContent() { return this._text + this.children.map(child => child.textContent).join(""); }
    set textContent(value) { this.children = []; this._text = String(value); }
    setAttribute(name, value) { this.attributes[name] = String(value); }
    addEventListener(type, handler) {
      if (!this.listeners.has(type)) this.listeners.set(type, new Set());
      this.listeners.get(type).add(handler);
      listenerCount += 1;
    }
    removeEventListener(type, handler) {
      if (this.listeners.get(type)?.delete(handler)) listenerCount -= 1;
    }
    append(...children) {
      for (const child of children) {
        child.remove?.();
        child.parent = this;
        this.children.push(child);
      }
    }
    replaceChildren(...children) {
      for (const child of this.children) child.parent = null;
      this.children = [];
      this.append(...children);
    }
    remove() {
      if (!this.parent) return;
      this.parent.children = this.parent.children.filter(child => child !== this);
      this.parent = null;
    }
    dispatch(type) {
      const event = { type, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } };
      const results = [...(this.listeners.get(type) ?? [])].map(handler => handler(event));
      return Promise.all(results);
    }
    click() { return this.disabled ? Promise.resolve() : this.dispatch("click"); }
    set innerHTML(_) { throw new Error("innerHTML must not be used"); }
  }

  const doc = { createElement: tag => new Element(tag), defaultView: view };
  const root = new Element("div");
  const calls = [];
  const handlers = {
    get: () => structuredClone(list),
    post: (_, body) => comment(50, body.content, true),
    put: (url, body) => comment(Number(url.split("/").pop()), body.content, true, { updatedAt: "2026-11-27T10:30:00" }),
    // api.js returns the whole ApiResponse when data is null.
    delete: () => ({ success: true, message: "댓글을 삭제했습니다.", data: null }),
  };
  const api = Object.fromEntries(Object.keys(handlers).map(method => [method, (...args) => {
    // Bodies are built inside the vm realm; copy them so strict deep-equal compares plain values.
    calls.push(JSON.parse(JSON.stringify([method, ...args])));
    return handlers[method](...args);
  }]));
  const window = { api };
  vm.runInNewContext(script, { window, globalThis: {} });

  const all = (node = root, found = []) => {
    for (const child of node.children) { found.push(child); all(child, found); }
    return found;
  };
  const find = (className, node = root) => all(node).filter(element => element.className === className);
  const one = (className, node) => find(className, node)[0];
  const items = () => find("comment-widget-item");
  const texts = () => find("comment-widget-content").map(element => element.textContent);
  const submit = () => one("comment-widget-form").dispatch("submit");
  return {
    root, calls, handlers, find, one, items, texts, submit,
    listeners: () => listenerCount,
    mount: () => window.CommentUI.mount({ root, groupId: 4, diaryId: 7 }),
  };
}

test("renders comments as text with author, time and permission-based buttons", async () => {
  const page = setup({ list: [
    comment(1, "<img src=x onerror=alert(1)>", false, { authorNickname: "<b>친구</b>" }),
    comment(2, "내 댓글", true, { updatedAt: "2026-11-27T10:05:00" }),
  ] });
  await page.mount();
  assert.doesNotMatch(script, /innerHTML|insertAdjacentHTML|outerHTML/);
  const [theirs, mine] = page.items();
  assert.equal(page.one("comment-widget-author", theirs).textContent, "<b>친구</b>");
  assert.equal(page.one("comment-widget-content", theirs).textContent, "<img src=x onerror=alert(1)>");
  assert.equal(page.one("comment-widget-content", theirs).children.length, 0);
  assert.equal(page.one("comment-widget-time", theirs).textContent, "2026-11-27 10:00");
  assert.equal(page.find("comment-widget-edit", theirs).length, 0);
  assert.equal(page.find("comment-widget-delete", theirs).length, 0);
  assert.equal(page.find("comment-widget-edit", mine).length, 1);
  assert.equal(page.find("comment-widget-delete", mine).length, 1);
  assert.equal(page.one("comment-widget-edited", mine).textContent, "수정됨 2026-11-27 10:05");
  assert.equal(page.one("comment-widget-empty").hidden, true);
  assert.deepEqual(page.calls, [["get", path]]);
});

test("shows the empty state for an empty list", async () => {
  const page = setup({ list: [] });
  await page.mount();
  assert.equal(page.items().length, 0);
  assert.equal(page.one("comment-widget-empty").hidden, false);
  assert.equal(page.one("comment-widget-form").hidden, false);
});

test("creates once per submit, clears input only after success and appends the server comment", async () => {
  const page = setup();
  await page.mount();
  const input = page.one("comment-widget-input");
  const button = page.one("comment-widget-submit");
  const pending = deferred();
  page.handlers.post = () => pending.promise;
  input.value = "  새 댓글  ";
  const first = page.submit();
  await page.submit();
  assert.equal(button.disabled, true);
  assert.equal(input.value, "  새 댓글  ");
  pending.resolve(comment(50, "새 댓글", true));
  await first;
  assert.equal(input.value, "");
  assert.equal(button.disabled, false);
  assert.deepEqual(page.texts(), ["첫 댓글", "새 댓글"]);
  assert.deepEqual(page.calls, [["get", path], ["post", path, { content: "새 댓글" }]]);
});

test("does not send blank or too long input", async () => {
  const page = setup();
  await page.mount();
  const input = page.one("comment-widget-input");
  input.value = "   ";
  await page.submit();
  assert.match(page.one("comment-widget-message").textContent, /입력해 주세요/);
  input.value = "가".repeat(1001);
  await page.submit();
  assert.match(page.one("comment-widget-message").textContent, /1000자 이하/);
  input.value = "가".repeat(1000);
  await page.submit();
  assert.deepEqual(page.calls.map(call => call[0]), ["get", "post"]);
});

test("keeps the input and re-enables the button after a failed create, then retries", async () => {
  const page = setup();
  await page.mount();
  const input = page.one("comment-widget-input");
  page.handlers.post = async () => { throw new Error("서버 오류가 발생했습니다."); };
  input.value = "보존될 댓글";
  await page.submit();
  assert.equal(input.value, "보존될 댓글");
  assert.equal(input.disabled, false);
  assert.equal(page.one("comment-widget-submit").disabled, false);
  assert.match(page.one("comment-widget-message").textContent, /서버 오류/);
  assert.deepEqual(page.texts(), ["첫 댓글"]);
  page.handlers.post = (_, body) => comment(51, body.content, true);
  await page.submit();
  assert.equal(input.value, "");
  assert.equal(page.one("comment-widget-message").textContent, "");
  assert.deepEqual(page.texts(), ["첫 댓글", "보존될 댓글"]);
  assert.deepEqual(page.calls.filter(call => call[0] === "post").map(call => call[2]),
    [{ content: "보존될 댓글" }, { content: "보존될 댓글" }]);
});

test("edit shows the server's new content and updated time; cancel restores the original", async () => {
  const page = setup({ list: [comment(5, "원래 댓글", true)] });
  await page.mount();
  await page.one("comment-widget-edit").click();
  const box = page.one("comment-widget-edit-input");
  assert.equal(box.value, "원래 댓글");
  box.value = "취소할 내용";
  await box.dispatch("input");
  await page.one("comment-widget-cancel").click();
  assert.deepEqual(page.texts(), ["원래 댓글"]);

  await page.one("comment-widget-edit").click();
  const again = page.one("comment-widget-edit-input");
  assert.equal(again.value, "원래 댓글");
  again.value = " 고친 댓글 ";
  await again.dispatch("input");
  page.handlers.put = () => comment(5, "서버가 정리한 댓글", true, { updatedAt: "2026-11-27T10:30:00" });
  await page.one("comment-widget-save").click();
  assert.deepEqual(page.texts(), ["서버가 정리한 댓글"]);
  assert.equal(page.one("comment-widget-edited").textContent, "수정됨 2026-11-27 10:30");
  assert.deepEqual(page.calls.at(-1), ["put", `${path}/5`, { content: "고친 댓글" }]);
});

test("failed edit keeps the draft and lets the user retry", async () => {
  const page = setup({ list: [comment(5, "원래 댓글", true)] });
  await page.mount();
  await page.one("comment-widget-edit").click();
  const box = page.one("comment-widget-edit-input");
  box.value = "새 내용";
  await box.dispatch("input");
  page.handlers.put = async () => { throw new Error("작성자만 댓글을 수정하거나 삭제할 수 있습니다."); };
  await page.one("comment-widget-save").click();
  assert.equal(page.one("comment-widget-edit-input").value, "새 내용");
  assert.equal(page.one("comment-widget-save").disabled, false);
  assert.match(page.one("comment-widget-item-message").textContent, /작성자만/);
  page.handlers.put = (_, body) => comment(5, body.content, true, { updatedAt: "2026-11-27T10:31:00" });
  await page.one("comment-widget-save").click();
  assert.deepEqual(page.texts(), ["새 내용"]);
});

test("delete asks first, treats the data:null response as success and shows the empty state", async () => {
  let answer = false;
  const page = setup({ list: [comment(5, "지울 댓글", true)], confirm: () => answer });
  await page.mount();
  await page.one("comment-widget-delete").click();
  assert.deepEqual(page.calls.map(call => call[0]), ["get"]);
  answer = true;
  await page.one("comment-widget-delete").click();
  assert.deepEqual(page.calls.at(-1), ["delete", `${path}/5`]);
  assert.equal(page.items().length, 0);
  assert.equal(page.one("comment-widget-empty").hidden, false);
  assert.equal(page.one("comment-widget-message").textContent, "");
});

test("failed delete keeps the comment and the delete button usable", async () => {
  const page = setup({ list: [comment(5, "남을 댓글", true)] });
  await page.mount();
  page.handlers.delete = async () => { throw new Error("네트워크 오류"); };
  await page.one("comment-widget-delete").click();
  assert.deepEqual(page.texts(), ["남을 댓글"]);
  assert.equal(page.one("comment-widget-delete").disabled, false);
  assert.match(page.one("comment-widget-message").textContent, /네트워크 오류/);
});

test("a failed initial load offers a retry button", async () => {
  const page = setup();
  page.handlers.get = async () => { throw new Error("로그인이 필요합니다."); };
  await page.mount();
  assert.match(page.one("comment-widget-message").textContent, /로그인이 필요합니다/);
  assert.equal(page.one("comment-widget-retry").hidden, false);
  assert.equal(page.one("comment-widget-form").hidden, true);
  page.handlers.get = () => [comment(1, "다시 불러온 댓글")];
  await page.one("comment-widget-retry").click();
  assert.deepEqual(page.texts(), ["다시 불러온 댓글"]);
  assert.equal(page.one("comment-widget-retry").hidden, true);
});

test("remount and destroy remove listeners and ignore late responses", async () => {
  const late = deferred();
  const page = setup();
  page.handlers.get = () => late.promise;
  const oldMount = page.mount();
  await flush();
  page.handlers.get = () => [comment(2, "새 위젯 댓글")];
  const widget = await page.mount();
  late.resolve([comment(99, "늦게 온 응답")]);
  const oldWidget = await oldMount;
  oldWidget.destroy();
  assert.equal(page.root.children.length, 1);
  assert.deepEqual(page.texts(), ["새 위젯 댓글"]);

  const slowPost = deferred();
  page.handlers.post = () => slowPost.promise;
  page.one("comment-widget-input").value = "destroy 직전 댓글";
  const posting = page.submit();
  widget.destroy();
  slowPost.resolve(comment(60, "destroy 직전 댓글", true));
  await posting;
  await widget.refresh();
  assert.equal(page.root.children.length, 0);
  assert.equal(page.listeners(), 0);
  assert.deepEqual(page.calls.map(call => call[0]), ["get", "get", "post"]);
});

test("refresh overlapping POST merges the saved ID once and preserves chronological order", async () => {
  const page = setup({ list: [] });
  const widget = await page.mount();
  const saved = comment(50, "새 댓글", true);
  const later = comment(51, "나중 댓글");
  const post = deferred();
  page.handlers.post = () => post.promise;
  page.one("comment-widget-input").value = "새 댓글";
  const sending = page.submit();
  // GET sees the committed row while its original POST response is still in flight.
  page.handlers.get = () => [saved, later];
  await widget.refresh();
  post.resolve(saved);
  await sending;
  assert.deepEqual(page.texts(), ["새 댓글", "나중 댓글"]);
  assert.deepEqual(page.items().map(item => item.dataset.commentId), ["50", "51"]);
});

test("a late create response stays before a newer comment returned by refresh", async () => {
  const page = setup({ list: [] });
  const widget = await page.mount();
  const post = deferred();
  page.handlers.post = () => post.promise;
  page.one("comment-widget-input").value = "먼저 작성";
  const sending = page.submit();
  page.handlers.get = () => [comment(51, "나중 작성")];
  await widget.refresh();
  post.resolve(comment(50, "먼저 작성", true));
  await sending;
  assert.deepEqual(page.texts(), ["먼저 작성", "나중 작성"]);
});
