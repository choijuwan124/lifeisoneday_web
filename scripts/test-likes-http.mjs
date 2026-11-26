// Run explicitly against a local Spring Boot instance connected to a disposable MySQL database.
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { before, test } from "node:test";

const port = Number(process.env.LIKES_TEST_PORT);
assert(Number.isInteger(port) && port > 0 && port <= 65535, "Set LIKES_TEST_PORT for the isolated app");
const base = `http://127.0.0.1:${port}`;

function client() {
  let cookie;
  return async (method, path, expected, data) => {
    const headers = { Accept: "application/json" };
    if (cookie) headers.Cookie = cookie;
    if (data !== undefined) headers["Content-Type"] = "application/json";
    const response = await fetch(base + path, {
      method, headers, body: JSON.stringify(data), signal: AbortSignal.timeout(10000),
    });
    const session = response.headers.getSetCookie().find(value => value.startsWith("JSESSIONID="));
    if (session) cookie = session.split(";")[0];
    const body = await response.json();
    assert.equal(response.status, expected, `${method} ${path}: ${body.message}`);
    assert.equal(body.success, expected < 400);
    return body.data;
  };
}

async function register() {
  const call = client();
  const username = `like-${randomUUID()}`;
  const password = randomUUID();
  await call("POST", "/api/auth/signup", 201, { username, password, nickname: username });
  await call("POST", "/api/auth/login", 200, { username, password });
  return { call, username };
}

async function diary(call, group) {
  const entry = await call("POST", `/api/groups/${group}/diaries`, 201, {
    title: "Like HTTP test",
    content: "Disposable test diary",
  });
  return { id: entry.id, path: `/api/groups/${group}/diaries/${entry.id}/likes` };
}

let owner;
let friend;
let group;
let otherGroup;
before(async () => {
  ({ call: owner } = await register());
  friend = await register();
  ({ id: group } = await owner("POST", "/api/groups", 201, { name: "Likes test group" }));
  ({ id: otherGroup } = await owner("POST", "/api/groups", 201, { name: "Other test group" }));
});

test("repeated and concurrent PUTs leave one MySQL like per user and diary", async () => {
  const { id, path } = await diary(owner, group);
  const liked = { diaryId: id, likeCount: 1, likedByMe: true };
  assert.deepEqual(await owner("PUT", path + "/me", 200), liked);
  assert.deepEqual(await owner("PUT", path + "/me", 200), liked);
  await owner("DELETE", path + "/me", 200);
  const writes = await Promise.all(Array.from({ length: 8 }, () => owner("PUT", path + "/me", 200)));
  for (const result of writes) assert.deepEqual(result, liked);
  assert.deepEqual(await owner("GET", path, 200), liked);
});

test("likes stay scoped to each diary and user, including repeated DELETE", async () => {
  const first = await diary(owner, group);
  const second = await diary(owner, group);
  await owner("POST", `/api/groups/${group}/members`, 201, { username: friend.username });
  await owner("PUT", first.path + "/me", 200);
  await friend.call("PUT", first.path + "/me", 200);
  await owner("PUT", second.path + "/me", 200);
  assert.deepEqual(await owner("GET", first.path, 200), { diaryId: first.id, likeCount: 2, likedByMe: true });
  assert.deepEqual(await friend.call("GET", second.path, 200), { diaryId: second.id, likeCount: 1, likedByMe: false });
  const remaining = { diaryId: first.id, likeCount: 1, likedByMe: false };
  assert.deepEqual(await owner("DELETE", first.path + "/me", 200), remaining);
  assert.deepEqual(await owner("DELETE", first.path + "/me", 200), remaining);
  assert.deepEqual(await friend.call("GET", first.path, 200), { ...remaining, likedByMe: true });
});

test("rejects invalid access and preserves a former member's existing like", async () => {
  const { id, path } = await diary(owner, otherGroup);
  await client()("GET", path, 401);
  await friend.call("GET", path, 404);
  await owner("GET", `/api/groups/${group}/diaries/${id}/likes`, 404);
  await owner("GET", `/api/groups/${group}/diaries/9223372036854775807/likes`, 404);
  await owner("GET", `/api/groups/${group}/diaries/9223372036854775808/likes`, 400);
  await owner("POST", `/api/groups/${otherGroup}/members`, 201, { username: friend.username });
  await friend.call("PUT", path + "/me", 200);
  await friend.call("DELETE", `/api/groups/${otherGroup}/members/me`, 200);
  await friend.call("GET", path, 404);
  await friend.call("DELETE", path + "/me", 404);
  assert.deepEqual(await owner("GET", path, 200), { diaryId: id, likeCount: 1, likedByMe: false });
});
