import {randomBytes} from 'node:crypto';
import {mkdir, readFile, writeFile} from 'node:fs/promises';
import path from 'node:path';

const api = new URL(process.env.DEMO_API_URL || 'http://localhost:8080');
if (!['localhost', '127.0.0.1'].includes(api.hostname)) throw new Error('발표 데이터는 로컬 서버에만 생성합니다.');
const directory = path.resolve(process.env.DEMO_DATA_DIR || '.local');
const credentialsFile = path.join(directory, 'demo-credentials.json');
await mkdir(directory, {recursive: true, mode: 0o700});
let credentials;
try {
  credentials = JSON.parse(await readFile(credentialsFile, 'utf8'));
} catch (error) {
  if (error.code !== 'ENOENT') throw error;
  const suffix = randomBytes(4).toString('hex');
  credentials = {
    owner: {username: `demo_owner_${suffix}`, password: randomBytes(18).toString('base64url'), nickname: '주완(발표용)'},
    friend: {username: `demo_friend_${suffix}`, password: randomBytes(18).toString('base64url'), nickname: '친구(발표용)'},
  };
  await writeFile(credentialsFile, JSON.stringify(credentials, null, 2) + '\n', {flag: 'wx', mode: 0o600});
}

async function client(account) {
  let cookie = '';
  async function call(method, route, data) {
    const response = await fetch(new URL(route, api), {
      method, headers: {'Content-Type': 'application/json', ...(cookie ? {Cookie: cookie} : {})},
      ...(data === undefined ? {} : {body: JSON.stringify(data)}),
    });
    const session = response.headers.get('set-cookie')?.match(/JSESSIONID=[^;]+/);
    if (session) cookie = session[0];
    const body = await response.json();
    if (!response.ok || body.success !== true) {
      const error = new Error(body.message || '요청 실패'); error.status = response.status; throw error;
    }
    return body.data;
  }
  try { await call('POST', '/api/auth/signup', account); }
  catch (error) { if (error.status !== 400 || error.message !== '이미 사용 중인 아이디입니다.') throw error; }
  await call('POST', '/api/auth/login', {username: account.username, password: account.password});
  return {call, user: await call('GET', '/api/auth/me')};
}

try {
  const owner = await client(credentials.owner);
  const friend = await client(credentials.friend);
  const name = '우리의 첫 공유 일기장 · 발표용';
  const groups = await owner.call('GET', '/api/groups');
  const group = groups.find(item => item.name === name && item.ownerId === owner.user.id)
    || await owner.call('POST', '/api/groups', {name, description:'친구와 함께한 하루를 기록하는 시연용 일기장입니다.'});
  const membersRoute = `/api/groups/${group.id}/members`;
  const members = await owner.call('GET', membersRoute);
  if (!members.some(member => member.userId === friend.user.id)) {
    await owner.call('POST', membersRoute, {username: credentials.friend.username});
  }
  const diaryRoute = `/api/groups/${group.id}/diaries`;
  const diaries = await owner.call('GET', diaryRoute);
  for (const [writer, title, content] of [
    [owner, '함께 만드는 첫 기록', '혼자 만들던 프로젝트를 친구들과 함께 완성했다.\n각자 한 파일씩 작업하고 커밋을 남겨 하나의 서비스를 만들었다.'],
    [friend, '시험이 끝난 뒤의 하루', '시험이 끝나고 친구들과 다시 모였다.\n오늘의 작은 이야기도 우리 일기장에 오래 남았으면 좋겠다.'],
  ]) {
    if (!diaries.some(diary => diary.authorId === writer.user.id && diary.title === title)) {
      await writer.call('POST', diaryRoute, {title, content});
    }
  }
  await owner.call('POST', '/api/auth/logout');
  await friend.call('POST', '/api/auth/logout');
  console.log(`발표 데이터 준비 완료. 일기장 ID: ${group.id}`);
  console.log(`로그인 정보 파일: ${credentialsFile}`);
} catch (error) {
  console.error('발표 데이터 준비 실패:', error.cause?.code || error.message);
  process.exitCode = 1;
}
