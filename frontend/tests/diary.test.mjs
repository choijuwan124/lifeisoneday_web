import fs from "node:fs";
import vm from "node:vm";
import test from "node:test";
import assert from "node:assert/strict";

const script = fs.readFileSync(new URL("../diary/diary.js", import.meta.url), "utf8");
function setup(entry, {failure = null, confirm = true} = {}) {
  const ids = ["diary-error", "diary-loading", "diary-detail", "diary-detail-title", "diary-author",
    "diary-created", "diary-updated", "diary-detail-content", "diary-list-link", "diary-owner-actions",
    "diary-edit-link", "diary-delete"];
  const elements = Object.fromEntries(ids.map(id => [id, {
    id, hidden: ["diary-owner-actions", "diary-detail", "diary-loading"].includes(id), textContent: "", href: "", disabled: false, handlers: {},
    addEventListener(name, handler) { this.handlers[name] = handler; },
  }]));
  const calls = [];
  const window = {
    location: {href: "http://localhost/diary/diary-detail.html?groupId=4&diaryId=7", search: "?groupId=4&diaryId=7", assign(url) { this.href = url; }},
    confirm: () => confirm,
    api: {
      async get(path) { calls.push(["get", path]); if (failure) throw new Error(failure); return entry; },
      async delete(path) { calls.push(["delete", path]); return {success: true, data: null}; },
    },
  };
  const context = vm.createContext({window, document: {getElementById: id => elements[id] ?? null},
    URL, URLSearchParams, Intl, Date, Number, Error});
  vm.runInContext(script, context);
  return {elements, window, calls};
}
const tick = () => new Promise(resolve => setImmediate(resolve));

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((done, fail) => { resolve = done; reject = fail; });
  return {promise, resolve, reject};
}

function runPage(page, {search: requestedSearch, entry = null, api = {}, ui = {}} = {}) {
  const detailIds = ["diary-loading", "diary-detail", "diary-detail-title", "diary-author", "diary-created", "diary-updated",
    "diary-detail-content", "diary-list-link", "diary-owner-actions", "diary-edit-link", "diary-delete",
    "diary-like-root", "diary-comment-root", "diary-photo-root"];
  const createIds = ["diary-create-form", "diary-title", "diary-content", "diary-submit", "diary-cancel",
    "diary-photo-save-status", "diary-photo-retry-area", "diary-photo-retry", "diary-photo-editor-root"];
  const editIds = ["diary-loading", "diary-edit-form", "diary-title", "diary-content", "diary-submit", "diary-cancel",
    "diary-no-permission", "diary-photo-save-status", "diary-photo-retry-area", "diary-photo-retry", "diary-photo-editor-root"];
  const ids = ["diary-error", ...(page === "detail" ? detailIds : page === "create" ? createIds : editIds)];
  const initiallyHidden = new Set(page === "detail"
    ? ["diary-detail", "diary-owner-actions"]
    : page === "create" ? ["diary-photo-retry-area"]
      : ["diary-loading", "diary-edit-form", "diary-no-permission", "diary-photo-retry-area"]);
  const elements = Object.fromEntries(ids.map(id => [id, {
    id, hidden: initiallyHidden.has(id), textContent: "", value: id === "diary-title" ? "title" : id === "diary-content" ? "body" : "",
    href: "", disabled: false, handlers: {}, attributes: {}, children: [],
    addEventListener(name, handler) { this.handlers[name] = handler; },
    setAttribute(name, value) { this.attributes[name] = value; },
    removeAttribute(name) { delete this.attributes[name]; },
    replaceChildren(...children) { this.children = children; },
  }]));
  const calls = [];
  const eventHandlers = new Map();
  const search = requestedSearch ?? (page === "create" ? "?groupId=4" : "?groupId=4&diaryId=7");
  const file = page === "detail" ? "diary-detail.html" : page === "create" ? "diary-create.html" : "diary-edit.html";
  const window = {
    location: {href: `http://localhost/diary/${file}${search}`, search, assigned: null, assign(url) { this.assigned = url; }},
    confirm: () => true,
    addEventListener(name, handler) {
      const handlers = eventHandlers.get(name) ?? [];
      handlers.push(handler);
      eventHandlers.set(name, handlers);
    },
    emit(name, event = {}) { for (const handler of eventHandlers.get(name) ?? []) handler(event); },
    api: {
      async get(path) { calls.push(["get", path]); return api.get ? api.get(path) : entry; },
      async post(path, body) { calls.push(["post", path, body]); return api.post ? api.post(path, body) : {id: 8}; },
      async put(path, body) { calls.push(["put", path, body]); return api.put ? api.put(path, body) : entry; },
      async delete(path) { calls.push(["delete", path]); return api.delete ? api.delete(path) : {success: true, data: null}; },
    },
    ...ui,
  };
  const document = {
    getElementById: id => elements[id] ?? null,
    createElement: tag => ({tag, className: "", textContent: "", attributes: {}, setAttribute(name, value) { this.attributes[name] = value; }}),
  };
  vm.runInContext(script, vm.createContext({window, document, URL, URLSearchParams, Intl, Date, Number, Error}));
  return {elements, window, calls, eventHandlers};
}

const editableEntry = {id: 7, groupId: 4, authorId: 9, authorNickname: "owner", title: "title", content: "body",
  createdAt: "2026-01-01T00:00:00", updatedAt: "2026-01-02T00:00:00", canEdit: true, canDelete: true};

test("diary detail renders untrusted text as text and preserves both ids", async () => {
  const page = setup({id: 7, groupId: 4, authorId: 9, authorNickname: "<img src=x>", title: "<script>title</script>",
    content: "<b>content</b>", createdAt: "2026-01-01T00:00:00", updatedAt: "2026-01-02T00:00:00", canEdit: false, canDelete: false});
  await tick();
  assert.equal(page.elements["diary-detail-title"].textContent, "<script>title</script>");
  assert.equal(page.elements["diary-detail-content"].textContent, "<b>content</b>");
  assert.equal(page.elements["diary-author"].textContent, "<img src=x>");
  assert.equal(page.elements["diary-owner-actions"].hidden, true);
  assert.deepEqual(page.calls, [["get", "/api/groups/4/diaries/7"]]);
});

test("only server-authorized users see controls and delete cancellation keeps the diary", async () => {
  const page = setup({id: 7, groupId: 4, authorId: 9, authorNickname: "owner", title: "title", content: "content",
    createdAt: "2026-01-01T00:00:00", updatedAt: "2026-01-02T00:00:00", canEdit: true, canDelete: true}, {confirm: false});
  await tick();
  assert.equal(page.elements["diary-owner-actions"].hidden, false);
  assert.match(page.elements["diary-edit-link"].href, /groupId=4.*diaryId=7/);
  await page.elements["diary-delete"].handlers.click();
  assert.deepEqual(page.calls, [["get", "/api/groups/4/diaries/7"]]);
});

test("API failures stay on the detail page and display an error", async () => {
  const page = setup(null, {failure: "접근 권한이 없습니다."});
  await tick();
  assert.equal(page.elements["diary-error"].textContent, "접근 권한이 없습니다.");
  assert.equal(page.elements["diary-detail"].hidden, true);
});

test("detail mounts like, comment, and photo with DOM roots and isolates widget failure", async () => {
  const mounted = [];
  const page = runPage("detail", {entry: editableEntry, ui: {
    LikeUI: {async mount(args) { mounted.push(["like", args]); throw new Error("like unavailable"); }},
    CommentUI: {async mount(args) { mounted.push(["comment", args]); return {destroy() {}}; }},
    PhotoUI: {async mountViewer(args) { mounted.push(["photo", args]); return {destroy() {}}; }},
  }});
  await tick();
  assert.deepEqual(mounted.map(([name]) => name), ["like", "comment", "photo"]);
  for (const [, args] of mounted) {
    assert.equal(args.groupId, 4);
    assert.equal(args.diaryId, 7);
    assert.equal(typeof args.root, "object");
    assert.notEqual(typeof args.root, "string");
  }
  assert.equal(page.elements["diary-detail"].hidden, false);
  assert.equal(page.elements["diary-like-root"].children[0].textContent, "like unavailable");
});

test("invalid group ID prevents detail lookup and widget mounting", async () => {
  let mountCalls = 0;
  const page = runPage("detail", {search: "?groupId=0&diaryId=7", entry: editableEntry, ui: {
    LikeUI: {async mount() { mountCalls += 1; }},
  }});
  await tick();
  assert.deepEqual(page.calls, []);
  assert.equal(mountCalls, 0);
  assert.match(page.elements["diary-error"].textContent, /번호가 올바르지 않습니다/);
});

test("diary response for a different group is rejected before widgets mount", async () => {
  let mountCalls = 0;
  const page = runPage("detail", {entry: {...editableEntry, groupId: 5}, ui: {
    LikeUI: {async mount() { mountCalls += 1; }},
  }});
  await tick();
  assert.equal(page.elements["diary-detail"].hidden, true);
  assert.equal(mountCalls, 0);
  assert.match(page.elements["diary-error"].textContent, /일기 정보를 확인할 수 없습니다/);
});

test("detail destroys all mounted widgets when the page is hidden", async () => {
  let destroyed = 0;
  const controls = () => ({destroy() { destroyed += 1; }});
  const page = runPage("detail", {entry: editableEntry, ui: {
    LikeUI: {async mount() { return controls(); }},
    CommentUI: {async mount() { return controls(); }},
    PhotoUI: {async mountViewer() { return controls(); }},
  }});
  await tick();
  page.window.emit("pagehide");
  assert.equal(destroyed, 3);
});

test("diary POST failure never starts a photo save and preserves entered text", async () => {
  let photoSaves = 0;
  const page = runPage("create", {api: {async post() { throw new Error("diary failed"); }}, ui: {
    PhotoUI: {async mountEditor() { return {isDirty: () => true, async save() { photoSaves += 1; }}; }},
  }});
  await tick();
  await page.elements["diary-create-form"].handlers.submit({preventDefault() {}});
  assert.equal(page.calls.filter(([method]) => method === "post").length, 1);
  assert.equal(photoSaves, 0);
  assert.equal(page.elements["diary-title"].value, "title");
  assert.equal(page.elements["diary-content"].value, "body");
  assert.equal(page.elements["diary-submit"].disabled, false);
});

test("creation photo retry reuses its diary ID without repeating POST", async () => {
  const photoIds = [];
  let attempts = 0;
  let editorRoot = null;
  const page = runPage("create", {ui: {
    PhotoUI: {async mountEditor({root, groupId, diaryId}) {
      editorRoot = root;
      assert.equal(groupId, 4);
      assert.equal(diaryId, null);
      return {isDirty: () => true, async save(id) { photoIds.push(id); if (++attempts === 1) throw new Error("photo failed"); }};
    }},
  }});
  await tick();
  assert.equal(editorRoot, page.elements["diary-photo-editor-root"]);
  await page.elements["diary-create-form"].handlers.submit({preventDefault() {}});
  assert.equal(page.calls.filter(([method]) => method === "post").length, 1);
  assert.deepEqual(photoIds, [8]);
  assert.equal(page.elements["diary-photo-retry-area"].hidden, false);
  assert.equal(page.elements["diary-title"].disabled, true);
  assert.equal(page.window.location.assigned, null);
  await page.elements["diary-photo-retry"].handlers.click();
  assert.equal(page.calls.filter(([method]) => method === "post").length, 1);
  assert.deepEqual(photoIds, [8, 8]);
  assert.match(page.window.location.assigned, /groupId=4.*diaryId=8/);
});

test("duplicate create submits are blocked and navigation waits for photo save", async () => {
  const postResult = deferred();
  const photoResult = deferred();
  const page = runPage("create", {api: {post: () => postResult.promise}, ui: {
    PhotoUI: {async mountEditor() { return {isDirty: () => true, save: () => photoResult.promise}; }},
  }});
  await tick();
  const submit = page.elements["diary-create-form"].handlers.submit;
  const first = submit({preventDefault() {}});
  await submit({preventDefault() {}});
  assert.equal(page.calls.filter(([method]) => method === "post").length, 1);
  postResult.resolve({id: 8});
  await tick();
  assert.equal(page.window.location.assigned, null);
  photoResult.resolve(null);
  await first;
  assert.match(page.window.location.assigned, /diaryId=8/);
});

test("edit photo failure retries only the photo after one diary PUT", async () => {
  let photoAttempts = 0;
  const page = runPage("edit", {entry: editableEntry, ui: {
    PhotoUI: {async mountEditor({groupId, diaryId}) {
      assert.equal(groupId, 4);
      assert.equal(diaryId, 7);
      return {isDirty: () => true, async save(id) { assert.equal(id, 7); if (++photoAttempts === 1) throw new Error("photo failed"); }};
    }},
  }});
  await tick();
  await page.elements["diary-edit-form"].handlers.submit({preventDefault() {}});
  assert.equal(page.calls.filter(([method]) => method === "put").length, 1);
  assert.equal(page.elements["diary-photo-retry-area"].hidden, false);
  assert.equal(page.window.location.assigned, null);
  await page.elements["diary-photo-retry"].handlers.click();
  assert.equal(page.calls.filter(([method]) => method === "put").length, 1);
  assert.equal(photoAttempts, 2);
  assert.match(page.window.location.assigned, /diaryId=7/);
});

test("unauthorized edit never mounts the photo editor", async () => {
  let mountCalls = 0;
  const page = runPage("edit", {entry: {...editableEntry, canEdit: false}, ui: {
    PhotoUI: {async mountEditor() { mountCalls += 1; }},
  }});
  await tick();
  assert.equal(page.elements["diary-edit-form"].hidden, true);
  assert.equal(page.elements["diary-no-permission"].hidden, false);
  assert.equal(mountCalls, 0);
});

test("diary HTML has required roots and loads UI scripts after shared scripts", () => {
  const detail = fs.readFileSync(new URL("../diary/diary-detail.html", import.meta.url), "utf8");
  const create = fs.readFileSync(new URL("../diary/diary-create.html", import.meta.url), "utf8");
  const edit = fs.readFileSync(new URL("../diary/diary-edit.html", import.meta.url), "utf8");
  for (const id of ["diary-photo-root", "diary-like-root", "diary-comment-root"]) assert.match(detail, new RegExp(`id="${id}"`));
  for (const page of [create, edit]) assert.match(page, /id="diary-photo-editor-root"/);
  for (const html of [detail, create, edit]) {
    const scripts = [...html.matchAll(/<script src="([^"]+)" defer>/g)].map(match => match[1]);
    assert.ok(scripts.indexOf("../js/api.js") < scripts.indexOf("../js/app.js"));
    assert.ok(scripts.indexOf("../js/app.js") < scripts.indexOf("../auth/logout.js"));
    assert.ok(scripts.indexOf("../auth/logout.js") < scripts.indexOf("diary.js"));
  }
});
