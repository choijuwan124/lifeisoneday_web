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
