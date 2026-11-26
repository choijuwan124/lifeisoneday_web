import fs from "node:fs";
import vm from "node:vm";
import test from "node:test";
import assert from "node:assert/strict";

const script = fs.readFileSync(new URL("../like/like.js", import.meta.url), "utf8");
const path = "/api/groups/4/diaries/7/likes";
const status = (likeCount, likedByMe) => ({ diaryId: 7, likeCount, likedByMe });

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((onResolve, onReject) => {
    resolve = onResolve;
    reject = onReject;
  });
  return { promise, resolve, reject };
}

function setup(get = () => status(2, false)) {
  class Element {
    ownerDocument = { createElement: () => new Element() };
    children = [];
    attributes = {};
    listeners = new Map();

    setAttribute(name, value) { this.attributes[name] = value; }
    addEventListener(name, handler) { this.listeners.set(name, handler); }
    removeEventListener(name, handler) {
      if (this.listeners.get(name) === handler) this.listeners.delete(name);
    }
    append(...children) {
      for (const child of children) {
        child.parent = this;
        this.children.push(child);
      }
    }
    remove() {
      this.parent.children = this.parent.children.filter(child => child !== this);
    }
    click() { return this.listeners.get("click")?.(); }
  }

  const root = new Element();
  const calls = [];
  const handlers = { get, put: () => status(5, true), delete: () => status(1, false) };
  const api = Object.fromEntries(Object.keys(handlers).map(method => [method, (...args) => {
    calls.push([method, ...args]);
    return handlers[method](...args);
  }]));
  const window = { api };
  vm.runInNewContext(script, { window });
  return { root, calls, handlers, mount: () => window.LikeUI.mount({ root, groupId: 4, diaryId: 7 }) };
}

test("renders server counts and selection for GET, PUT, DELETE, and refresh", async () => {
  const page = setup();
  const widget = await page.mount();
  const [button, count] = page.root.children[0].children;
  assert.equal(button.type, "button");
  assert.equal(button.attributes["aria-pressed"], "false");
  assert.equal(count.textContent, "좋아요 2개");
  await button.click();
  assert.equal(count.textContent, "좋아요 5개");
  assert.equal(button.attributes["aria-pressed"], "true");
  await button.click();
  assert.equal(count.textContent, "좋아요 1개");
  assert.equal(button.attributes["aria-pressed"], "false");
  page.handlers.get = () => status(3, true);
  await widget.refresh();
  assert.equal(count.textContent, "좋아요 3개");
  assert.equal(button.attributes["aria-pressed"], "true");
  assert.deepEqual(page.calls, [["get", path], ["put", path + "/me"],
    ["delete", path + "/me"], ["get", path]]);
});

test("blocks duplicate clicks, preserves status on failure, and lets a retry succeed", async () => {
  const page = setup();
  await page.mount();
  const [button, count, message] = page.root.children[0].children;
  const failure = deferred();
  page.handlers.put = () => failure.promise;
  const first = button.click();
  assert.equal(button.disabled, true);
  assert.equal(button.click(), undefined);
  failure.reject(new Error("네트워크 오류"));
  await first;
  assert.equal(count.textContent, "좋아요 2개");
  assert.equal(button.attributes["aria-pressed"], "false");
  assert.match(message.textContent, /네트워크 오류/);
  assert.equal(button.disabled, false);
  page.handlers.put = () => status(4, true);
  await button.click();
  assert.equal(message.textContent, "");
  assert.equal(count.textContent, "좋아요 4개");
  assert.equal(button.attributes["aria-pressed"], "true");
  assert.deepEqual(page.calls, [["get", path], ["put", path + "/me"], ["put", path + "/me"]]);
});

test("retries initial GET on an empty response", async () => {
  const page = setup(() => null);
  await page.mount();
  const [button, count, message] = page.root.children[0].children;
  assert.match(message.textContent, /상태를 확인할 수 없습니다/);
  assert.equal(count.textContent, "");
  page.handlers.get = () => status(1, true);
  await button.click();
  assert.equal(count.textContent, "좋아요 1개");
  assert.deepEqual(page.calls, [["get", path], ["get", path]]);
});

test("remount and destroy remove listeners and ignore late responses", async () => {
  const late = deferred();
  const page = setup(() => late.promise);
  const oldMount = page.mount();
  const [oldButton, oldCount] = page.root.children[0].children;
  await Promise.resolve();
  page.handlers.get = () => status(8, false);
  const widget = await page.mount();
  late.resolve(status(42, true));
  const oldWidget = await oldMount;
  oldWidget.destroy();
  assert.equal(oldButton.listeners.size, 0);
  assert.equal(oldCount.textContent, "");
  assert.equal(page.root.children.length, 1);
  const [button, count] = page.root.children[0].children;
  assert.equal(count.textContent, "좋아요 8개");
  widget.destroy();
  await widget.refresh();
  assert.equal(button.listeners.size, 0);
  assert.equal(page.root.children.length, 0);
  assert.deepEqual(page.calls, [["get", path], ["get", path]]);
});
