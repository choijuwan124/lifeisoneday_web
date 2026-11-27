// End-to-end photo checks for a Spring Boot app connected to a disposable MySQL DB.
// Use a separate temporary upload folder and explicitly opt in:
//   PHOTO_TEST_ISOLATED=1 PHOTO_TEST_PORT=18084 node --test scripts/test-photos-http.mjs
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { before, test } from "node:test";

assert.equal(process.env.PHOTO_TEST_ISOLATED, "1", "Refusing to run unless PHOTO_TEST_ISOLATED=1 confirms a disposable database");
const port = Number(process.env.PHOTO_TEST_PORT);
assert(Number.isInteger(port) && port > 0 && port <= 65535, "Set PHOTO_TEST_PORT for the isolated app");
const base = `http://127.0.0.1:${port}`;
const tinyPng = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADUlEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC", "base64");

function client() {
  let cookie;
  const call = async (method, path, expected, data, { binary = false, json = false, details = false } = {}) => {
    const headers = { Accept: binary ? "image/png, image/jpeg, application/json" : "application/json" };
    if (cookie) headers.Cookie = cookie;
    if (json) headers["Content-Type"] = "application/json";
    const response = await fetch(base + path, {
      method, headers, body: data,
      signal: AbortSignal.timeout(20000),
    });
    const session = response.headers.getSetCookie().find(value => value.startsWith("JSESSIONID="));
    if (session) cookie = session.split(";")[0];
    const isJson = (response.headers.get("content-type") ?? "").includes("application/json");
    const body = isJson ? await response.json() : Buffer.from(await response.arrayBuffer());
    assert.equal(response.status, expected, `${method} ${path}: ${isJson ? body.message : response.statusText}`);
    if (isJson) assert.equal(body.success, expected < 400);
    const result = isJson ? body.data : body;
    return details ? { data: result, status: response.status, headers: response.headers } : result;
  };
  return { call, upload: (path, bytes, filename = "photo.png", type = "image/png", expected = 200) => {
    const form = new FormData();
    form.append("file", new Blob([bytes], { type }), filename);
    return call("PUT", path, expected, form);
  }, binary: (path, expected = 200) => call("GET", path, expected, undefined, { binary: true, details: true }) };
}

async function register() {
  const account = client();
  const username = `photo-${randomUUID().replaceAll("-", "").slice(0, 28)}`;
  const password = randomUUID();
  await account.call("POST", "/api/auth/signup", 201,
    JSON.stringify({ username, password, nickname: username.slice(0, 20) }), { json: true });
  await account.call("POST", "/api/auth/login", 200,
    JSON.stringify({ username, password }), { json: true });
  return { ...account, username, id: (await account.call("GET", "/api/auth/me", 200)).id };
}

let author;
let owner;
let outsider;
let group;
let diary;
let path;
before(async () => {
  author = await register();
  owner = await register();
  outsider = await register();
  ({ id: group } = await author.call("POST", "/api/groups", 201,
    JSON.stringify({ name: "Disposable photo HTTP group" }), { json: true }));
  await author.call("POST", `/api/groups/${group}/members`, 201,
    JSON.stringify({ username: owner.username }), { json: true });
  ({ id: diary } = await owner.call("POST", `/api/groups/${group}/diaries`, 201,
    JSON.stringify({ title: "Photo test diary", content: "Disposable" }), { json: true }));
  path = `/api/groups/${group}/diaries/${diary}/photo`;
});

test("checks access before revealing missing photo metadata or bytes", async () => {
  assert.equal(await author.call("GET", path, 200), null);
  await author.call("GET", `${path}/content`, 404);
  await client().call("GET", path, 401);
  await outsider.call("GET", path, 404);
  await outsider.call("GET", `${path}/content`, 404);
  await author.call("GET", `/api/groups/${group}/diaries/${diary + 9999}/photo`, 404);
  await author.call("GET", `/api/groups/${group + 9999}/diaries/${diary}/photo`, 404);
});

test("accepts a real PNG padded to exactly 5 MiB and returns matching private bytes", async () => {
  const exact = Buffer.concat([tinyPng, Buffer.alloc(5 * 1024 * 1024 - tinyPng.length)]);
  const saved = await owner.upload(path, exact, "misleading.gif", "application/octet-stream");
  assert.equal(saved.diaryId, diary);
  assert.equal(saved.contentType, "image/png");
  assert.equal(saved.sizeBytes, 5 * 1024 * 1024);
  assert.equal(saved.contentPath, `${path}/content`);
  assert.equal("storageKey" in saved, false);

  const response = await owner.binary(saved.contentPath);
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("content-type"), "image/png");
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.equal(response.headers.get("x-content-type-options"), "nosniff");
  assert.deepEqual(response.data, exact);
});

test("rejects empty, spoofed and over-limit files and protects authorship", async () => {
  const empty = new FormData(); empty.append("file", new Blob([]), "empty.png");
  await owner.call("PUT", path, 400, empty);
  await owner.upload(path, Buffer.from("not an image"), "fake.png", "image/png", 415);
  const tooLarge = Buffer.concat([tinyPng, Buffer.alloc(5 * 1024 * 1024 + 1 - tinyPng.length)]);
  await owner.upload(path, tooLarge, "large.png", "image/png", 413);

  // The diary author is owner above; author is group owner. A non-author group owner cannot change this photo.
  const friendDiary = await author.call("POST", `/api/groups/${group}/diaries`, 201,
    JSON.stringify({ title: "Author check", content: "Owner must not edit" }), { json: true });
  const friendPath = `/api/groups/${group}/diaries/${friendDiary.id}/photo`;
  await owner.upload(friendPath, tinyPng, "x.png", "image/png", 403);
  await outsider.upload(friendPath, tinyPng, "x.png", "image/png", 404);
  await owner.call("PUT", `/api/groups/${group + 1}/diaries/${diary}/photo`, 404,
    new FormData());
});

test("replaces and deletes metadata and binary consistently", async () => {
  const normal = await owner.upload(path, tinyPng);
  assert.equal(normal.contentType, "image/png");
  const bytes = await owner.binary(normal.contentPath);
  assert.deepEqual(bytes.data, tinyPng);
  const replaced = await owner.upload(path, tinyPng, "second.png");
  assert.equal(replaced.diaryId, diary);
  assert.equal((await author.call("GET", path, 200)).id, replaced.id);
  assert.equal(await owner.call("DELETE", path, 200), null);
  assert.equal(await owner.call("DELETE", path, 200), null);
  assert.equal(await owner.call("GET", path, 200), null);
  await owner.call("GET", `${path}/content`, 404);
});
