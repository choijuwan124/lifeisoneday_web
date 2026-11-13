import fs from "node:fs";
import vm from "node:vm";
import test from "node:test";
import assert from "node:assert/strict";

const frontend = new URL("../", import.meta.url);
const read = (file) => fs.readFileSync(new URL(file, frontend), "utf8");

function element() {
  return { value: "", textContent: "", dataset: {}, disabled: false, hidden: false, events: {},
    addEventListener(name, callback) { this.events[name] = callback; },
    setAttribute() {}, removeAttribute() {}, setCustomValidity() {},
    reportValidity() { return true; }, reset() {}, focus() {} };
}

function page(ids, script, response, {hostname = "localhost", failure = false, ok = true} = {}) {
  const elements = Object.fromEntries(ids.map(id => [id, element()]));
  const initialUrl = `http://${hostname}:5500/auth/login.html`;
  const alerts = [];
  const window = {location: {href: initialUrl, hostname}, alert: message => alerts.push(message)};
  const calls = [];
  const network = {failure, ok, response};
  const context = vm.createContext({window, document: {getElementById: id => elements[id]}, TypeError, Error,
    fetch: async (url, options) => {
      calls.push({url, options});
      if (network.failure) throw new TypeError("simulated offline request");
      return {ok: network.ok, headers: {get: () => "application/json"}, json: async () => network.response};
    }});
  for (const file of ["js/api.js", script]) {
    vm.runInContext(read(file), context, {filename: file});
  }
  return {elements, window, initialUrl, calls, network, alerts};
}

function loginPage(options) {
  const result = page(["login-form", "username", "password", "login-submit", "login-message"],
    "auth/login.js", {success: true, message: "로그인 완료", data: {nickname: "tester"}}, options);
  result.elements.username.value = " tester ";
  result.elements.password.value = "test-password";
  return result;
}
const submit = page => page.elements["login-form"].events.submit({preventDefault() {}});

for (const hostname of ["localhost", "127.0.0.1"]) {
  test(`successful login opens group list and retains host ${hostname}`, async () => {
    const p = loginPage({hostname});
    await submit(p);
    assert.equal(p.window.location.href, "../group/group-list.html");
    assert.equal(p.calls[0].url, `http://${hostname}:8080/api/auth/login`);
    assert.equal(p.calls[0].options.credentials, "include");
    assert.deepEqual(JSON.parse(p.calls[0].options.body), {username: "tester", password: "test-password"});
    assert.equal(p.elements.password.value, "");
  });
}

test("rejected login stays on page and allows retry", async () => {
  const p = loginPage({ok: false});
  p.network.response = {success: false, message: "잘못된 비밀번호", data: null};
  await submit(p);
  assert.equal(p.window.location.href, p.initialUrl);
  assert.equal(p.elements["login-message"].textContent, "잘못된 비밀번호");
  assert.equal(p.elements["login-submit"].disabled, false);
  assert.equal(p.elements.password.disabled, false);
});

test("offline login allows a later successful retry", async () => {
  const p = loginPage({failure: true});
  await submit(p);
  assert.equal(p.window.location.href, p.initialUrl);
  assert.equal(p.elements["login-message"].dataset.state, "error");
  p.network.failure = false;
  await submit(p);
  assert.equal(p.window.location.href, "../group/group-list.html");
});

test("malformed successful login data is not treated as authenticated", async () => {
  const p = loginPage();
  p.network.response = {success: true, data: null};
  await submit(p);
  assert.equal(p.window.location.href, p.initialUrl);
  assert.equal(p.elements["login-message"].dataset.state, "error");
});

test("signup still handles null-data success through the shared API", async () => {
  const p = page(["signup-form", "username", "password", "nickname", "signup-submit", "signup-message", "signup-success", "login-link"],
    "auth/auth.js", {success: true, message: "회원가입 완료", data: null});
  p.elements.username.value = "tester";
  p.elements.password.value = "test-password";
  p.elements.nickname.value = "tester";
  await p.elements["signup-form"].events.submit({preventDefault() {}});
  assert.equal(p.elements["signup-form"].hidden, true);
  assert.equal(p.elements["signup-success"].hidden, false);
});

test("failed logout stays on page, shows error, and retries successfully", async () => {
  const p = page(["logout-button", "logout-message"], "auth/logout.js", {success: true, data: null}, {failure: true});
  await p.elements["logout-button"].events.click();
  assert.equal(p.window.location.href, p.initialUrl);
  assert.equal(p.elements["logout-message"].hidden, false);
  assert.match(p.elements["logout-message"].textContent, /로그아웃하지 못했습니다/);
  assert.equal(p.elements["logout-button"].disabled, false);
  p.network.failure = false;
  await p.elements["logout-button"].events.click();
  assert.equal(p.window.location.href, "auth/login.html");
});

test("malformed logout success does not navigate", async () => {
  const p = page(["logout-button", "logout-message"], "auth/logout.js", {});
  await p.elements["logout-button"].events.click();
  assert.equal(p.window.location.href, p.initialUrl);
  assert.equal(p.elements["logout-button"].disabled, false);
});

test("logout failure is visible even without a message element", async () => {
  const p = page(["logout-button"], "auth/logout.js", null, {failure: true});
  await p.elements["logout-button"].events.click();
  assert.equal(p.alerts.length, 1);
  assert.equal(p.window.location.href, p.initialUrl);
});

test("home contains a usable entry point into group pages", () => {
  assert.match(read("index.html"), /<a\b[^>]*href="group\/group-list\.html"/);
  assert.match(read("index.html"), /id="logout-message"/);
});
