// Run explicitly against a local Spring Boot instance connected to a disposable MySQL database.
// It creates its own random test accounts, groups and diaries; never point it at your development data.
//   COMMENTS_TEST_PORT=18083 node --test scripts/test-comments-http.mjs
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { before, test } from "node:test";

const port = Number(process.env.COMMENTS_TEST_PORT);
assert(Number.isInteger(port) && port > 0 && port <= 65535, "Set COMMENTS_TEST_PORT for the isolated app");
const base = `http://127.0.0.1:${port}`;

function client() {
  let cookie;
  const call = async (method, path, expected, data) => {
    const headers = { Accept: "application/json" };
    if (cookie) headers.Cookie = cookie;
    if (data !== undefined) headers["Content-Type"] = "application/json";
    const response = await fetch(base + path, {
      method, headers, body: data === undefined ? undefined : JSON.stringify(data), signal: AbortSignal.timeout(10000),
    });
    const session = response.headers.getSetCookie().find(value => value.startsWith("JSESSIONID="));
    if (session) cookie = session.split(";")[0];
    const body = await response.json();
    assert.equal(response.status, expected, `${method} ${path}: ${body.message}`);
    assert.equal(body.success, expected < 400);
    return body.data;
  };
  return call;
}

async function register() {
  const call = client();
  const username = `cmt-${randomUUID().slice(0, 30)}`;
  const password = randomUUID();
  await call("POST", "/api/auth/signup", 201, { username, password, nickname: username.slice(0, 20) });
  await call("POST", "/api/auth/login", 200, { username, password });
  const { id, nickname } = await call("GET", "/api/auth/me", 200);
  return { call, username, id, nickname };
}

async function diary(call, group) {
  const entry = await call("POST", `/api/groups/${group}/diaries`, 201, {
    title: "Comment HTTP test",
    content: "Disposable test diary",
  });
  return { id: entry.id, path: `/api/groups/${group}/diaries/${entry.id}/comments` };
}

let owner;
let friend;
let outsider;
let group;
let otherGroup;
before(async () => {
  owner = await register();
  friend = await register();
  outsider = await register();
  ({ id: group } = await owner.call("POST", "/api/groups", 201, { name: "Comments test group" }));
  ({ id: otherGroup } = await owner.call("POST", "/api/groups", 201, { name: "Other comments group" }));
  await owner.call("POST", `/api/groups/${group}/members`, 201, { username: friend.username });
});

test("create, list, update and delete with the session user as author", async () => {
  const { id, path } = await diary(owner.call, group);
  assert.deepEqual(await owner.call("GET", path, 200), []);

  const created = await friend.call("POST", path, 201, { content: "  첫 댓글  ", authorId: owner.id, userId: owner.id });
  assert.equal(created.diaryId, id);
  assert.equal(created.authorId, friend.id);
  assert.equal(created.authorNickname, friend.nickname);
  assert.equal(created.content, "첫 댓글");
  assert.equal(created.canEdit, true);
  assert.equal(created.canDelete, true);
  assert.match(created.createdAt, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?$/);

  const seenByOwner = await owner.call("GET", path, 200);
  assert.equal(seenByOwner.length, 1);
  assert.equal(seenByOwner[0].canEdit, false);
  assert.equal(seenByOwner[0].canDelete, false);

  await new Promise(resolve => setTimeout(resolve, 20));
  const updated = await friend.call("PUT", `${path}/${created.id}`, 200, { content: "고친 댓글" });
  assert.equal(updated.content, "고친 댓글");
  assert.equal(updated.createdAt, created.createdAt);
  assert(updated.updatedAt > created.updatedAt, "updatedAt moves forward");

  assert.equal(await friend.call("DELETE", `${path}/${created.id}`, 200), null);
  assert.deepEqual(await owner.call("GET", path, 200), []);
  await friend.call("DELETE", `${path}/${created.id}`, 404);
});

test("lists oldest first, breaking equal timestamps by ID", async () => {
  const { path } = await diary(owner.call, group);
  const posted = [];
  for (const text of ["하나", "둘", "셋"]) posted.push(await owner.call("POST", path, 201, { content: text }));
  const parallel = await Promise.all(["넷", "다섯", "여섯"].map(text => friend.call("POST", path, 201, { content: text })));
  const listed = await owner.call("GET", path, 200);
  const expected = [...posted, ...parallel].sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id - b.id);
  assert.deepEqual(listed.map(entry => entry.id), expected.map(entry => entry.id));
  assert.deepEqual(listed.slice(0, 3).map(entry => entry.content), ["하나", "둘", "셋"]);
});

test("validates content: blank, null, non-string, 1000 and 1001 characters", async () => {
  const { path } = await diary(owner.call, group);
  for (const body of [{}, { content: null }, { content: "" }, { content: "   \n\t " }, { content: 7 },
    { content: "a".repeat(1001) }, { content: " " + "가".repeat(1001) + " " }]) {
    await owner.call("POST", path, 400, body);
  }
  const thousand = await owner.call("POST", path, 201, { content: "가".repeat(1000) });
  assert.equal([...thousand.content].length, 1000);
  const padded = await owner.call("POST", path, 201, { content: "  " + "b".repeat(1000) + "  " });
  assert.equal(padded.content.length, 1000);
  await owner.call("PUT", `${path}/${thousand.id}`, 400, { content: "  " });
  await owner.call("PUT", `${path}/${thousand.id}`, 400, { content: "c".repeat(1001) });
  const listed = await owner.call("GET", path, 200);
  assert.equal(listed.length, 2);
  assert.equal(listed[0].content, "가".repeat(1000));
});

test("blocks anonymous users, outsiders and invalid IDs", async () => {
  const { id, path } = await diary(owner.call, group);
  const own = await owner.call("POST", path, 201, { content: "멤버 댓글" });
  await client()("GET", path, 401);
  await client()("POST", path, 401, { content: "x" });
  await client()("DELETE", `${path}/${own.id}`, 401);
  await outsider.call("GET", path, 404);
  await outsider.call("POST", path, 404, { content: "침입" });
  await outsider.call("PUT", `${path}/${own.id}`, 404, { content: "침입" });
  await outsider.call("DELETE", `${path}/${own.id}`, 404);
  await owner.call("GET", `/api/groups/${group}/diaries/abc/comments`, 400);
  await owner.call("GET", `/api/groups/${group}/diaries/9223372036854775808/comments`, 400);
  await owner.call("GET", `/api/groups/${group}/diaries/0/comments`, 400);
  await owner.call("PUT", `${path}/0`, 400, { content: "x" });
  await owner.call("GET", `/api/groups/${group}/diaries/9223372036854775807/comments`, 404);
  // Diary exists but in another group: the group/diary pair is checked.
  await owner.call("GET", `/api/groups/${otherGroup}/diaries/${id}/comments`, 404);
  assert.deepEqual((await owner.call("GET", path, 200)).map(entry => entry.content), ["멤버 댓글"]);
});

test("only the author edits or deletes; the owner cannot change a friend's comment", async () => {
  const { path } = await diary(owner.call, group);
  const theirs = await friend.call("POST", path, 201, { content: "친구 댓글" });
  const mine = await owner.call("POST", path, 201, { content: "방장 댓글" });
  await owner.call("PUT", `${path}/${theirs.id}`, 403, { content: "방장이 고침" });
  await owner.call("DELETE", `${path}/${theirs.id}`, 403);
  await friend.call("PUT", `${path}/${mine.id}`, 403, { content: "친구가 고침" });
  await friend.call("DELETE", `${path}/${mine.id}`, 403);
  const listed = await friend.call("GET", path, 200);
  assert.deepEqual(listed.map(entry => [entry.content, entry.canEdit]), [["친구 댓글", true], ["방장 댓글", false]]);
});

test("comment and diary IDs must match; other diaries' comments stay untouched", async () => {
  const first = await diary(owner.call, group);
  const second = await diary(owner.call, group);
  const onFirst = await owner.call("POST", first.path, 201, { content: "첫 일기 댓글" });
  const onSecond = await owner.call("POST", second.path, 201, { content: "둘째 일기 댓글" });
  await owner.call("PUT", `${second.path}/${onFirst.id}`, 404, { content: "다른 일기 경로" });
  await owner.call("DELETE", `${second.path}/${onFirst.id}`, 404);
  await owner.call("DELETE", `${first.path}/${onSecond.id}`, 404);
  assert.deepEqual((await owner.call("GET", first.path, 200)).map(entry => entry.content), ["첫 일기 댓글"]);
  assert.deepEqual((await owner.call("GET", second.path, 200)).map(entry => entry.content), ["둘째 일기 댓글"]);
});

test("a member who leaves loses access but their comments stay for others", async () => {
  const leaver = await register();
  const { path } = await diary(owner.call, otherGroup);
  await owner.call("POST", `/api/groups/${otherGroup}/members`, 201, { username: leaver.username });
  const written = await leaver.call("POST", path, 201, { content: "나가기 전 댓글" });
  await leaver.call("DELETE", `/api/groups/${otherGroup}/members/me`, 200);
  await leaver.call("GET", path, 404);
  await leaver.call("PUT", `${path}/${written.id}`, 404, { content: "나간 뒤 수정" });
  await leaver.call("DELETE", `${path}/${written.id}`, 404);
  const remaining = await owner.call("GET", path, 200);
  assert.deepEqual(remaining.map(entry => [entry.content, entry.authorId, entry.canDelete]),
    [["나가기 전 댓글", leaver.id, false]]);
});
