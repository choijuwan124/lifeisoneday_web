import fs from "node:fs";
import vm from "node:vm";
import test from "node:test";
import assert from "node:assert/strict";

const script = fs.readFileSync(new URL("../photo/photo.js", import.meta.url), "utf8");
const path = "/api/groups/4/diaries/7/photo";
const photo = { id: 2, diaryId: 7, contentType: "image/png", sizeBytes: 12, contentPath: `${path}/content` };
const flush = () => new Promise(resolve => setImmediate(resolve));

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

function setup({ metadata = { success: true, data: null }, blob = new Blob(["photo"]), handlers = {} } = {}) {
  let urlNumber = 0;
  let listenerCount = 0;
  const created = [];
  const revoked = [];
  class Element {
    constructor(tag) {
      this.tagName = tag.toUpperCase(); this.children = []; this.listeners = new Map(); this.attributes = {};
      this.parent = null; this.hidden = false; this.disabled = false; this.value = ""; this.files = [];
      this.textContent = ""; this.dataset = {};
    }
    setAttribute(name, value) { this.attributes[name] = String(value); }
    append(...children) { for (const child of children) { child.remove?.(); child.parent = this; this.children.push(child); } }
    replaceChildren(...children) { for (const child of this.children) child.parent = null; this.children = []; this.append(...children); }
    remove() { if (this.parent) { this.parent.children = this.parent.children.filter(child => child !== this); this.parent = null; } }
    addEventListener(type, handler) { if (!this.listeners.has(type)) this.listeners.set(type, new Set()); this.listeners.get(type).add(handler); listenerCount++; }
    removeEventListener(type, handler) { if (this.listeners.get(type)?.delete(handler)) listenerCount--; }
    dispatch(type) { return Promise.all([...(this.listeners.get(type) ?? [])].map(fn => fn({ type, preventDefault() {} }))); }
    click() { return this.disabled ? Promise.resolve() : this.dispatch("click"); }
    get innerHTML() { throw new Error("innerHTML must not be used"); }
    set innerHTML(_) { throw new Error("innerHTML must not be used"); }
  }
  const view = {
    FormData,
    URL: {
      createObjectURL(value) { const url = `blob:${++urlNumber}`; created.push([url, value]); return url; },
      revokeObjectURL(url) { revoked.push(url); },
    },
  };
  const doc = { createElement: tag => new Element(tag), defaultView: view };
  const root = new Element("div"); root.ownerDocument = doc;
  const calls = [];
  const api = {
    get: async (...args) => { calls.push(["get", ...args]); return handlers.get ? handlers.get(...args) : metadata; },
    getBlob: async (...args) => { calls.push(["getBlob", ...args]); return handlers.getBlob ? handlers.getBlob(...args) : blob; },
    upload: async (...args) => { calls.push(["upload", args[0], args[1]]); return handlers.upload ? handlers.upload(...args) : photo; },
    delete: async (...args) => { calls.push(["delete", ...args]); return handlers.delete ? handlers.delete(...args) : { success: true, data: null }; },
  };
  const window = { api };
  vm.runInNewContext(script, { window, globalThis: {} });
  const all = (node = root, result = []) => { for (const child of node.children) { result.push(child); all(child, result); } return result; };
  const find = (className) => all().find(item => item.className === className);
  return { root, window, calls, created, revoked, find, all, get listenerCount() { return listenerCount; } };
}

test("new editor selection is local until save and cancel restores no-photo state", async () => {
  const env = setup();
  const editor = await env.window.PhotoUI.mountEditor({ root: env.root, groupId: 4, diaryId: null });
  const input = env.find("photo-widget-input");
  input.files = [new Blob(["selected"] )];
  await input.dispatch("change");
  assert.equal(editor.isDirty(), true);
  assert.equal(env.find("photo-widget-image").src, "blob:1");
  await input.dispatch("cancel");
  assert.equal(editor.isDirty(), false);
  await editor.save(7);
  assert.deepEqual(env.calls.map(call => call[0]), []);
  assert.deepEqual(env.revoked, ["blob:1"]);
  await editor.save(7);
  assert.deepEqual(env.calls.map(call => call[0]), []);
  editor.destroy();
});

test("canceling a replacement preserves the server photo without revoking its visible URL", async () => {
  const env = setup({ metadata: photo });
  const editor = await env.window.PhotoUI.mountEditor({ root: env.root, groupId: 4, diaryId: 7 });
  const input = env.find("photo-widget-input");
  input.files = [new Blob(["replacement"])];
  await input.dispatch("change");
  assert.deepEqual(env.revoked, []);
  await input.dispatch("cancel");
  assert.equal(env.find("photo-widget-image").src, "blob:1");
  assert.deepEqual(env.revoked, ["blob:2"]);
  await editor.save(7);
  assert.deepEqual(env.calls.map(call => call[0]), ["get", "getBlob"]);
  editor.destroy();
  assert.deepEqual(env.revoked, ["blob:2", "blob:1"]);
});

test("existing photo delete is explicit and null-data response succeeds", async () => {
  const env = setup({ metadata: photo });
  const editor = await env.window.PhotoUI.mountEditor({ root: env.root, groupId: 4, diaryId: 7 });
  assert.equal(env.find("photo-widget-image").src, "blob:1");
  const action = env.find("photo-widget-state");
  await action.click();
  assert.equal(editor.isDirty(), true);
  assert.equal(env.find("photo-widget-image"), undefined);
  await editor.save(7);
  assert.deepEqual(env.calls.map(call => call[0]), ["get", "getBlob", "delete"]);
  assert.equal(editor.isDirty(), false);
  assert.deepEqual(env.revoked, ["blob:1"]);
  editor.destroy();
});

test("failed upload keeps the selected file for retry with the same diary ID", async () => {
  let uploads = 0;
  const env = setup({ handlers: { upload: async () => { uploads++; if (uploads === 1) throw new Error("offline"); return photo; } } });
  const editor = await env.window.PhotoUI.mountEditor({ root: env.root, groupId: 4, diaryId: null });
  const input = env.find("photo-widget-input"); input.files = [new Blob(["new image"])]; await input.dispatch("change");
  await assert.rejects(editor.save(7), /offline/);
  assert.equal(editor.isDirty(), true);
  assert.equal(env.find("photo-widget-image").src, "blob:1");
  const saved = await editor.save(7);
  assert.equal(saved.id, 2);
  assert.equal(editor.isDirty(), false);
  assert.deepEqual(env.calls.filter(call => call[0] === "upload").map(call => call[1]), [path, path]);
  assert.equal(env.calls.find(call => call[0] === "upload")[2].get("file").name, "blob");
  editor.destroy();
  assert.deepEqual(env.revoked, ["blob:1"]);
});

test("viewer refresh revokes replaced URL and destroy ignores a late response", async () => {
  let count = 0;
  const env = setup({ handlers: { get: async () => ({ success: true, data: photo }), getBlob: async () => new Blob([String(++count)]) } });
  const viewer = await env.window.PhotoUI.mountViewer({ root: env.root, groupId: 4, diaryId: 7 });
  assert.equal(env.find("photo-widget-image").src, "blob:1");
  await viewer.refresh();
  assert.deepEqual(env.revoked, ["blob:1"]);
  assert.equal(env.find("photo-widget-image").src, "blob:2");
  viewer.destroy();
  assert.deepEqual(env.revoked, ["blob:1", "blob:2"]);

  const pending = deferred();
  const lateEnv = setup({ handlers: { get: () => pending.promise } });
  const lateViewerPromise = lateEnv.window.PhotoUI.mountViewer({ root: lateEnv.root, groupId: 4, diaryId: 7 });
  await flush();
  const widget = lateEnv.root.children[0];
  const replacement = await lateEnv.window.PhotoUI.mountEditor({ root: lateEnv.root, groupId: 4, diaryId: null });
  pending.resolve(photo);
  await lateViewerPromise;
  await flush();
  assert.equal(lateEnv.root.children.length, 1);
  assert.equal(widget.parent, null);
  assert.equal(lateEnv.created.length, 0);
  replacement.destroy();
});

test("remount destroys previous listeners and object URLs", async () => {
  const env = setup({ metadata: photo });
  const first = await env.window.PhotoUI.mountEditor({ root: env.root, groupId: 4, diaryId: 7 });
  assert.ok(env.listenerCount > 0);
  const second = await env.window.PhotoUI.mountViewer({ root: env.root, groupId: 4, diaryId: 7 });
  assert.equal(env.root.children.length, 1);
  assert.ok(env.listenerCount < 3);
  first.destroy();
  second.destroy();
  assert.equal(env.root.children.length, 0);
});
