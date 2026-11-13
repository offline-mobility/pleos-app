import { parseRelayLink, relaySummary } from './relay.js';

const $ = (id) => document.getElementById(id);
const report = parseRelayLink(location.search);

function reporterId() {
  try {
    const saved = localStorage.getItem('reporter');
    if (saved) return saved;
  } catch { /* private mode: use a one-off id */ }
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = [...b].map((x) => x.toString(16).padStart(2, '0')).join('');
  const id = `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
  try { localStorage.setItem('reporter', id); } catch { /* ignore */ }
  return id;
}

function setState(text, cls) {
  $('state').textContent = text;
  $('state').className = cls || '';
}

async function send() {
  $('send').disabled = true;
  setState('전달하는 중…');
  try {
    const res = await fetch('/api/reports', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ version: 1, reporter: reporterId(), reports: [report] }),
      cache: 'no-store',
    });
    if (res.status === 429) throw new Error('busy');
    if (!res.ok) throw new Error('rejected');
    const result = await res.json();
    if (result.accepted !== 1) {
      setState('너무 오래된 요청이라 전달할 수 없어요', 'wait');
      return;
    }
    setState('구조 서버에 전달했어요. 고맙습니다.', 'ok');
    $('send').hidden = true;
  } catch {
    setState('아직 전달하지 못했어요. 인터넷이 되는 곳에서 다시 눌러 주세요.', 'wait');
    $('send').disabled = false;
  }
}

if (!report) {
  $('lead').textContent = '주소가 올바르지 않아요. 차량 화면의 QR을 다시 찍어 주세요.';
} else {
  $('title').textContent = relaySummary(report);
  $('lead').textContent = '통신이 끊긴 곳의 차량에서 보낸 구조 요청입니다. 아래 버튼을 누르면 구조 서버로 전달됩니다.';
  $('where').textContent = report.lat === undefined ? '위치 정보 없음' : `위치 ${report.lat.toFixed(5)}, ${report.lon.toFixed(5)}`;
  $('send').hidden = false;
  $('call').hidden = false;
  $('note').hidden = false;
  $('send').addEventListener('click', send);
}
