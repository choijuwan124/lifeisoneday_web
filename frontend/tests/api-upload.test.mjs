import fs from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';

const source = fs.readFileSync(new URL('../js/api.js', import.meta.url), 'utf8');
function setup({host = 'localhost', status = 200, data = {id: 9}, binary = false} = {}) {
  const requests = [], events = [];
  const window = {location: {hostname: host}, dispatchEvent: event => events.push(event.type)};
  const context = vm.createContext({window, FormData, TypeError, Error, CustomEvent,
    fetch: async (url, options) => {
      requests.push(new Request(url, options));
      // A photo endpoint can declare image/png as its response media type.
      // Simulate content negotiation instead of always accepting JSON-only requests.
      const accept = requests.at(-1).headers.get('Accept') ?? '*/*';
      if (binary && !accept.split(',').some(value => ['image/png', 'image/*', '*/*'].includes(value.trim()))) {
        return Response.json({success: false, message: 'Not Acceptable', data: null}, {status: 406});
      }
      return binary
        ? new Response(new Uint8Array([137, 80, 78, 71]), {status, headers:{'Content-Type':'image/png'}})
        : Response.json({success: status < 400, message: 'upload result', data}, {status});
    }});
  vm.runInContext(source, context);
  return {api: window.api, requests, events};
}
function photo() {
  const body = new FormData();
  body.append('file', new Blob(['test-image-bytes'], {type:'image/png'}), 'photo.png');
  return body;
}

for (const host of ['localhost', '127.0.0.1']) {
  test(`upload uses a generated multipart boundary on ${host}`, async () => {
    const {api, requests} = setup({host});
    const result = await api.upload('/api/groups/1/diaries/2/photo', photo());
    assert.equal(result.id, 9);
    assert.match(requests[0].headers.get('Content-Type'), /^multipart\/form-data; boundary=/);
    assert.equal(requests[0].method, 'PUT');
    assert.equal(requests[0].url, `http://${host}:8080/api/groups/1/diaries/2/photo`);
    const parsed = await requests[0].formData();
    assert.equal(parsed.get('file').name, 'photo.png');
    assert.equal(await parsed.get('file').text(), 'test-image-bytes');
  });
}

test('upload retains session cookies and disables response caching', async () => {
  const {api, requests} = setup();
  await api.upload('/api/groups/1/diaries/2/photo', photo());
  assert.equal(requests[0].credentials, 'include');
  assert.equal(requests[0].cache, 'no-store');
});

test('upload rejects a plain object before making a request', () => {
  const {api, requests} = setup();
  assert.throws(() => api.upload('/photo', {file:'photo.png'}), /FormData/);
  assert.equal(requests.length, 0);
});

test('upload preserves the HTTP error status and does not report success', async () => {
  const {api} = setup({status:413, data:null});
  await assert.rejects(api.upload('/photo', photo()), error => error.status === 413);
});

test('private image fetching keeps cookies and returns binary bytes', async () => {
  const {api, requests} = setup({binary:true});
  const blob = await api.getBlob('/api/groups/1/diaries/2/photo/content');
  assert.deepEqual([...new Uint8Array(await blob.arrayBuffer())], [137,80,78,71]);
  assert.equal(requests[0].credentials, 'include');
  assert.equal(requests[0].cache, 'no-store');
});

test('expired image access reports 401 through the shared authentication event', async () => {
  const {api, events} = setup({status:401, data:null});
  await assert.rejects(api.getBlob('/photo/content'), error => error.status === 401);
  assert.deepEqual(events, ['auth:required']);
});
