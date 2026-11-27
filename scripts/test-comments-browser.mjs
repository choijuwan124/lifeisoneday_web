// Native Chromium typing checks with API doubles; this is not an app/DB integration test.
// Node 22+: node --test scripts/test-comments-browser.mjs
// Set CHROME_BIN when Chrome/Chromium is installed outside the usual locations.
import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { existsSync } from 'node:fs';
import { readFile, mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn } from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';

let chrome, profile, socket, sessionId;
let sequence = 0;
const pending = new Map();
function call(method, params = {}, session) {
  const id = ++sequence;
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => { pending.delete(id); reject(new Error(`Timed out: ${method}`)); }, 10000);
    pending.set(id, {resolve, reject, timer});
    socket.send(JSON.stringify({id, method, params, ...(session ? {sessionId:session} : {})}));
  });
}
async function evaluate(expression) {
  const answer = await call('Runtime.evaluate', {expression, awaitPromise:true, returnByValue:true}, sessionId);
  if (answer.exceptionDetails) throw new Error(JSON.stringify(answer.exceptionDetails));
  return answer.result.value;
}

before(async () => {
  const candidates = process.env.CHROME_BIN ? [process.env.CHROME_BIN] : [
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser',
    join(process.env.PROGRAMFILES ?? 'C:/Program Files', 'Google/Chrome/Application/chrome.exe'),
    join(process.env.LOCALAPPDATA ?? 'C:/Users/Default/AppData/Local', 'Google/Chrome/Application/chrome.exe'),
  ];
  const binary = candidates.find(existsSync);
  assert.ok(binary, 'Install Chrome/Chromium or set CHROME_BIN to its executable');
  profile = await mkdtemp(join(tmpdir(), 'liod-comments-browser-'));
  chrome = spawn(binary, ['--headless=new', '--remote-debugging-port=0', `--user-data-dir=${profile}`,
    '--no-first-run', '--no-default-browser-check', '--disable-background-networking', 'about:blank'], {stdio:'ignore'});
  let launchError;
  chrome.once('error', error => { launchError = error; });
  let active;
  for (let attempt = 0; attempt < 100; attempt++) {
    if (launchError) throw launchError;
    if (chrome.exitCode !== null) throw new Error(`Chrome exited with ${chrome.exitCode}`);
    try { active = (await readFile(join(profile, 'DevToolsActivePort'), 'utf8')).trim().split('\n'); break; }
    catch (error) { if (error.code !== 'ENOENT') throw error; await delay(100); }
  }
  assert.ok(active, 'Chrome did not expose a debugging endpoint');
  socket = new WebSocket(`ws://127.0.0.1:${active[0]}${active[1]}`);
  await new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, {once:true});
    socket.addEventListener('error', reject, {once:true});
  });
  socket.addEventListener('message', ({data}) => {
    const message = JSON.parse(data);
    const entry = pending.get(message.id);
    if (!entry) return;
    pending.delete(message.id);
    clearTimeout(entry.timer);
    message.error ? entry.reject(new Error(JSON.stringify(message.error))) : entry.resolve(message.result);
  });
  const {targetInfos} = await call('Target.getTargets');
  ({sessionId} = await call('Target.attachToTarget', {
    targetId:targetInfos.find(target => target.type === 'page').targetId, flatten:true,
  }));
}, {timeout:30000});

after(async () => {
  if (socket?.readyState === WebSocket.OPEN) await call('Browser.close').catch(() => {});
  socket?.close();
  for (const entry of pending.values()) { clearTimeout(entry.timer); entry.reject(new Error('Browser closed')); }
  pending.clear();
  if (chrome) {
    for (let attempt = 0; attempt < 30 && chrome.exitCode === null; attempt++) await delay(100);
    if (chrome.exitCode === null) { chrome.kill(); await delay(300); }
  }
  if (profile) await rm(profile, {recursive:true, force:true, maxRetries:10, retryDelay:100});
});

async function mount(existing = []) {
  const source = await readFile(new URL('../frontend/comment/comment.js', import.meta.url), 'utf8');
  await evaluate(`(async () => {
    window.widget?.destroy();
    document.body.innerHTML = '<div id="root"></div>';
    window.calls = [];
    const result = (id, content) => ({id, diaryId:1, authorId:1, authorNickname:'나', content,
      createdAt:'2026-11-27T00:00:00', updatedAt:'2026-11-27T00:00:00', canEdit:true, canDelete:true});
    window.api = {
      get:async()=>${JSON.stringify(existing)},
      post:async(path, body)=>{calls.push({method:'POST',body});return result(10,body.content);},
      put:async(path, body)=>{calls.push({method:'PUT',body});return result(1,body.content);}
    };
    ${source}
    window.widget = await CommentUI.mount({root:document.getElementById('root'),groupId:1,diaryId:1});
  })()`);
}
async function type(selector, text) {
  await evaluate(`document.querySelector(${JSON.stringify(selector)}).focus()`);
  await call('Input.insertText', {text}, sessionId);
}
async function activate(expression) {
  await evaluate(`(async()=>{${expression};await new Promise(resolve=>setTimeout(resolve,0));})()`);
}

test('native create input accepts and submits 1000 emoji without truncation', async () => {
  await mount();
  await type('.comment-widget-input', '😀'.repeat(1000));
  assert.equal(await evaluate(`[...document.querySelector('.comment-widget-input').value].length`), 1000);
  await activate(`document.querySelector('.comment-widget-form').requestSubmit()`);
  assert.deepEqual(await evaluate(`calls.map(call=>({method:call.method,count:[...call.body.content].length}))`),
    [{method:'POST',count:1000}]);
});

test('native edit input accepts and saves 1000 emoji without truncation', async () => {
  await mount([{id:1,content:'원래 댓글',authorNickname:'나',createdAt:'2026-11-27T00:00:00',
    updatedAt:'2026-11-27T00:00:00',canEdit:true,canDelete:true}]);
  await evaluate(`document.querySelector('.comment-widget-edit').click();document.querySelector('.comment-widget-edit-input').value=''`);
  await type('.comment-widget-edit-input', '😀'.repeat(1000));
  assert.equal(await evaluate(`[...document.querySelector('.comment-widget-edit-input').value].length`), 1000);
  await activate(`document.querySelector('.comment-widget-save').click()`);
  assert.deepEqual(await evaluate(`calls.map(call=>({method:call.method,count:[...call.body.content].length}))`),
    [{method:'PUT',count:1000}]);
});

test('native input preserves 1001 emoji and reports validation instead of silently truncating', async () => {
  await mount();
  await type('.comment-widget-input', '😀'.repeat(1001));
  await activate(`document.querySelector('.comment-widget-form').requestSubmit()`);
  assert.equal(await evaluate(`calls.length`), 0);
  assert.equal(await evaluate(`[...document.querySelector('.comment-widget-input').value].length`), 1001);
  assert.match(await evaluate(`document.querySelector('.comment-widget-message').textContent`), /1000자 이하/);
});
