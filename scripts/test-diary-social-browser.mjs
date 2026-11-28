// Native diary-page checks with API doubles; this is not an app/DB integration test.
// Node 22+: node --test scripts/test-diary-social-browser.mjs
// Set CHROME_BIN when Chrome/Chromium is installed outside the usual locations.
import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { existsSync } from 'node:fs';
import { createServer } from 'node:http';
import { writeFile, mkdir } from 'node:fs/promises';
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

async function startBrowser() {
  const candidates = process.env.CHROME_BIN ? [process.env.CHROME_BIN] : [
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser',
    join(process.env.PROGRAMFILES ?? 'C:/Program Files', 'Google/Chrome/Application/chrome.exe'),
    join(process.env.LOCALAPPDATA ?? 'C:/Users/Default/AppData/Local', 'Google/Chrome/Application/chrome.exe'),
  ];
  const binary = candidates.find(existsSync);
  assert.ok(binary, 'Install Chrome/Chromium or set CHROME_BIN to its executable');
  profile = await mkdtemp(join(tmpdir(), 'liod-ui-review-browser-'));
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
}

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

// Real submitted HTML, scripts and CSS; only API responses and their timing are controlled.
const frontend = new URL('../frontend/', import.meta.url);
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADUlEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC','base64');
let artifacts;
let server, origin, scenario = '', requests = [], held = [], entry, liked = false, comments = [], hasPhoto = true, uploadAttempts = 0;
const originalEntry = {id:7,groupId:4,authorId:9,authorNickname:'review owner',title:'Review title',content:'Review body',
  createdAt:'2026-11-28T01:00:00',updatedAt:'2026-11-28T01:00:00',canEdit:true,canDelete:true};
function json(res, data, status=200) {res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify({success:status<400,message:status<400?'OK':'Review upload failure',data}));}
function photo(id) {return {id:2,diaryId:id,contentType:'image/png',sizeBytes:png.length,contentPath:`/api/groups/4/diaries/${id}/photo/content`};}
function release() {for(const finish of held.splice(0))finish();}
function reset(mode='') {release();scenario=mode;requests=[];entry={...originalEntry};liked=false;comments=[];hasPhoto=true;uploadAttempts=0;}
async function until(expression) {
  for(let i=0;i<100;i++) {if(await evaluate(expression))return;await delay(50);}
  console.log(JSON.stringify({condition:expression,page:await evaluate('({url:location.href,body:document.body.innerText,transport:window.__reviewTransport})'),requests}));
  throw new Error('Condition timed out: '+expression);
}
async function navigate(page) {
  await call('Page.navigate',{url:origin+'/diary/'+page},sessionId);
  await until(`document.readyState === 'complete' && location.pathname === ${JSON.stringify('/diary/'+page.split('?')[0])}`);
}
async function screenshot(name) {
  const result=await call('Page.captureScreenshot',{format:'png',captureBeyondViewport:true},sessionId);
  if (process.env.DIARY_BROWSER_ARTIFACTS) {
    await mkdir(process.env.DIARY_BROWSER_ARTIFACTS,{recursive:true});
    await writeFile(join(process.env.DIARY_BROWSER_ARTIFACTS,name),Buffer.from(result.data,'base64'));
  }
}

before(async()=>{
  await startBrowser();
  artifacts=await mkdtemp(join(tmpdir(),'liod-diary-fixtures-'));
  server=createServer(async(req,res)=>{
    try {
      const url=new URL(req.url,'http://review.local');
      if(url.pathname.startsWith('/api/')){
        let raw='';for await(const chunk of req)raw+=chunk;
        const record={method:req.method,path:url.pathname};requests.push(record);
        if(raw && !req.headers['content-type']?.startsWith('multipart'))record.body=JSON.parse(raw);
        const body=record.body;
        if(url.pathname==='/api/auth/me')return json(res,{id:9,username:'review',nickname:'review'});
        const path=url.pathname.replace(/^\/api\/groups\/4\/diaries/,'');
        if(path===''&&req.method==='POST'){entry={...entry,...body,id:8};hasPhoto=false;return json(res,entry,201);}
        if(path===''&&req.method==='GET')return json(res,[entry]);
        if(/^\/(7|8)$/.test(path)){
          if(req.method==='PUT')Object.assign(entry,body);
          return json(res,entry);
        }
        if(path.endsWith('/likes')) {
          const finish=()=>json(res,{diaryId:entry.id,likeCount:liked?1:0,likedByMe:liked});
          if(scenario==='slow-like'){held.push(finish);return;}
          return finish();
        }
        if(path.endsWith('/likes/me')){liked=req.method==='PUT';return json(res,{diaryId:entry.id,likeCount:liked?1:0,likedByMe:liked});}
        if(path.endsWith('/comments')){
          if(req.method==='POST'){const comment={id:10,diaryId:entry.id,authorId:9,authorNickname:'review',content:body.content,createdAt:entry.createdAt,updatedAt:entry.updatedAt,canEdit:true,canDelete:true};comments.push(comment);return json(res,comment,201);}
          return json(res,comments);
        }
        if(path.endsWith('/comments/10')){
          if(req.method==='DELETE'){comments=[];return json(res,null);}
          Object.assign(comments[0],body);return json(res,comments[0]);
        }
        if(path.endsWith('/photo/content')){res.writeHead(200,{'Content-Type':'image/png'});res.end(png);return;}
        if(path.endsWith('/photo')){
          if(req.method==='GET'){
            const finish=()=>json(res,hasPhoto?photo(entry.id):null);
            if(scenario==='slow-photo'){held.push(finish);return;}
            return finish();
          }
          if(req.method==='DELETE'){hasPhoto=false;return json(res,null);}
          uploadAttempts++;
          if(scenario==='upload-fails-once'&&uploadAttempts===1)return json(res,null,500);
          hasPhoto=true;return json(res,photo(entry.id));
        }
        return json(res,null,404);
      }
      requests.push({method:req.method,path:url.pathname,query:url.search});
      const target=new URL('.'+url.pathname,frontend);
      if(!target.href.startsWith(frontend.href))return json(res,null,404);
      const bytes=await readFile(target);
      const type=url.pathname.endsWith('.js')?'text/javascript':url.pathname.endsWith('.css')?'text/css':'text/html';
      res.writeHead(200,{'Content-Type':type+'; charset=utf-8'});res.end(bytes);
    } catch(error){if(!res.headersSent)res.writeHead(500);res.end(String(error));}
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  origin=`http://127.0.0.1:${server.address().port}`;
  // Keep the real api.js and all UI code. Redirect its backend transport to the isolated API double.
  await call('Page.enable',{},sessionId);
  await call('Page.addScriptToEvaluateOnNewDocument',{source:`(() => {
    window.__reviewTransport=true;
    const originalFetch=window.fetch.bind(window);
    window.fetch=(input,options)=>{
      const url=new URL(typeof input==='string'?input:input.url,location.href);
      if(url.pathname.startsWith('/api/'))return originalFetch(location.origin+url.pathname+url.search,options);
      return originalFetch(input,options);
    };
    window.confirm=()=>true;
  })();`},sessionId);
  await writeFile(join(artifacts,'photo.png'),png);
});
after(async()=>{
  release();server?.closeAllConnections();
  if(server)await new Promise(resolve=>server.close(resolve));
  if(artifacts)await rm(artifacts,{recursive:true,force:true});
});

test('native detail page connects photos, likes and comment CRUD at mobile and desktop widths', async()=>{
  reset();
  await navigate('diary-detail.html?groupId=4&diaryId=7');
  await until(`document.querySelector('.photo-widget-image')?.complete && !!document.querySelector('.comment-widget-input')`);
  await evaluate(`document.querySelector('.like-widget button').click()`);
  await until(`document.querySelector('.like-widget button')?.getAttribute('aria-pressed') === 'true'`);
  await evaluate(`document.querySelector('.comment-widget-input').value='review comment'; document.querySelector('.comment-widget-form').requestSubmit()`);
  await until(`document.querySelector('.comment-widget-item')?.textContent.includes('review comment')`);
  await evaluate(`document.querySelector('.comment-widget-edit').click();document.querySelector('.comment-widget-edit-input').value='';document.querySelector('.comment-widget-edit-input').focus()`);
  await call('Input.insertText',{text:'edited comment'},sessionId);
  await evaluate(`document.querySelector('.comment-widget-save').click()`);
  await until(`document.querySelector('.comment-widget-item')?.textContent.includes('edited comment')`);
  await evaluate(`document.querySelector('.comment-widget-delete').click()`);
  await until(`document.querySelectorAll('.comment-widget-item').length === 0`);
  for(const width of [375,1280]) {
    await call('Emulation.setDeviceMetricsOverride',{width,height:900,deviceScaleFactor:1,mobile:false},sessionId);
    assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'),true);
    if(width===375)await screenshot('detail-mobile.png');
  }
});

test('native create page retries only its photo after an upload failure', async()=>{
  reset('upload-fails-once');
  await navigate('diary-create.html?groupId=4');
  await until(`!!document.querySelector('.photo-widget-input')`);
  const {root}=await call('DOM.getDocument',{},sessionId);
  const {nodeId}=await call('DOM.querySelector',{nodeId:root.nodeId,selector:'.photo-widget-input'},sessionId);
  await call('DOM.setFileInputFiles',{nodeId,files:[join(artifacts,'photo.png')]},sessionId);
  await evaluate(`document.querySelector('#diary-title').value='Photo retry review';document.querySelector('#diary-content').value='Disposable content';document.querySelector('#diary-create-form').requestSubmit()`);
  await until(`!document.querySelector('#diary-photo-retry-area')?.hidden`);
  assert.equal(requests.filter(r=>r.method==='POST'&&r.path==='/api/groups/4/diaries').length,1);
  assert.equal(await evaluate(`document.querySelector('#diary-title').disabled`),true);
  await evaluate(`document.querySelector('#diary-photo-retry').click()`);
  await until(`location.pathname.endsWith('diary-detail.html') && location.search.includes('diaryId=8') && !!document.querySelector('.photo-widget-image')`);
  assert.equal(requests.filter(r=>r.method==='POST'&&r.path==='/api/groups/4/diaries').length,1);
  assert.equal(uploadAttempts,2);
});

test('editing while photo metadata is pending must not use native GET submission', async()=>{
  reset('slow-photo');
  await navigate('diary-edit.html?groupId=4&diaryId=7');
  await until(`!!document.querySelector('.photo-widget-input') && !document.querySelector('#diary-edit-form').hidden`);
  const ready=await evaluate(`({saveEnabled:!document.querySelector('#diary-submit').disabled,photoLoading:document.querySelector('.photo-widget-message').textContent})`);
  await evaluate(`document.querySelector('#diary-title').value='DRAFT_TITLE';document.querySelector('#diary-content').value='DRAFT_BODY';document.querySelector('#diary-submit').click()`);
  await delay(300);
  const result=await evaluate(`({url:location.href,error:document.querySelector('#diary-error')?.textContent})`);
  const puts=requests.filter(r=>r.method==='PUT'&&r.path==='/api/groups/4/diaries/7').length;
  release();
  console.log(JSON.stringify({case:'early edit save',...ready,...result,diaryPutCount:puts}));
  assert.equal(new URL(result.url).searchParams.has('content'),false,'private diary content must not be submitted in the page URL');
  assert.equal(new URL(result.url).searchParams.get('groupId'),'4');
  assert.equal(ready.saveEnabled,false,'save must stay disabled until the editor is ready');
  await until(`!document.querySelector('#diary-submit').disabled`);
  await evaluate(`document.querySelector('#diary-submit').click()`);
  await until(`location.pathname.endsWith('diary-detail.html') && location.search.includes('diaryId=7')`);
  assert.equal(requests.filter(r=>r.method==='PUT'&&r.path==='/api/groups/4/diaries/7').length,1);
  assert.equal(entry.title,'DRAFT_TITLE');
  assert.equal(entry.content,'DRAFT_BODY');
});

test('slow likes must not block comment and photo mounting', async()=>{
  reset('slow-like');
  await navigate('diary-detail.html?groupId=4&diaryId=7');
  await until(`!!document.querySelector('.like-widget') && !document.querySelector('#diary-detail').hidden`);
  await delay(300);
  const result=await evaluate(`({likes:!!document.querySelector('.like-widget'),comments:!!document.querySelector('.comment-widget'),photos:!!document.querySelector('.photo-widget')})`);
  console.log(JSON.stringify({case:'pending likes',...result,apiRequests:requests.filter(r=>r.path.startsWith('/api/')).map(r=>r.path)}));
  release();
  await until(`!!document.querySelector('.photo-widget')`);
  assert.equal(result.comments,true,'comment UI must mount independently of the likes response');
  assert.equal(result.photos,true,'photo UI must mount independently of the likes response');
});

