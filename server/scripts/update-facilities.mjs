// Refreshes data/facilities/ (pharmacies, general hospitals, convenience stores) from open public files.
// The app copies these into its assets at build time, so it can list nearby places with no connection.
//
// Usage: npm run update-facilities                 downloads both files (about 370 MB) and converts them
//        npm run update-facilities -- --from <dir> converts hira.zip and sbiz.zip already in <dir>
//
// Sources (data.go.kr file data, no key needed):
//   15051059 건강보험심사평가원 전국 병의원 및 약국 현황 (공공누리 1유형: 출처 표시)
//   15083033 소상공인시장진흥공단 상가(상권)정보
// Needs the `unzip` command.
import { spawn } from 'node:child_process';
import { createWriteStream, existsSync, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { fileURLToPath } from 'node:url';

const PORTAL = 'https://www.data.go.kr';
const FILES = {
  hira: { id: '15051059', org: '건강보험심사평가원', title: '전국 병의원 및 약국 현황' },
  sbiz: { id: '15083033', org: '소상공인시장진흥공단', title: '상가(상권)정보' },
};
const OUT = fileURLToPath(new URL('../data/facilities/', import.meta.url));

/** Finds the current file of a dataset and saves it. Returns the name the portal gives it (it ends with the date). */
async function download(file, target) {
  const page = await (await fetch(`${PORTAL}/data/${file.id}/fileData.do`)).text();
  const detail = page.match(/fn_fileDataDown\('\d+',\s*'(uddi:[0-9a-f-]+)'/)?.[1];
  if (!detail) throw new Error(`${file.id}: download link not found on the page`);
  const meta = await (
    await fetch(`${PORTAL}/tcs/dss/selectFileDataDownload.do?publicDataPk=${file.id}&publicDataDetailPk=${detail}`)
  ).json();
  if (meta.status !== true) throw new Error(`${file.id}: the portal refused the download`);
  const res = await fetch(`${PORTAL}/cmm/cmm/fileDownload.do?atchFileId=${meta.atchFileId}&fileDetailSn=${meta.fileDetailSn}`);
  if (!res.ok || !res.body) throw new Error(`${file.id}: HTTP ${res.status}`);
  await pipeline(Readable.fromWeb(res.body), createWriteStream(target));
  return String(meta.dataSetFileDetailInfo?.dataNm ?? '');
}

/** Streams the CSV rows of the zip entries matching [pattern]; quoted fields may hold commas and line breaks. */
async function* csvRows(zip, pattern, encoding) {
  const child = spawn('unzip', ['-p', zip, ...(pattern ? [pattern] : [])], { stdio: ['ignore', 'pipe', 'ignore'] });
  const decoder = new TextDecoder(encoding);
  let row = [];
  let field = '';
  let quoted = false;
  let quoteSeen = false;
  for await (const chunk of child.stdout) {
    const text = decoder.decode(chunk, { stream: true });
    for (let i = 0; i < text.length; i++) {
      const ch = text[i];
      if (quoted) {
        if (ch === '"') {
          if (text[i + 1] === '"') {
            field += '"';
            i++;
          } else if (i + 1 === text.length) {
            quoteSeen = true; // decided by the first character of the next chunk
            quoted = false;
          } else {
            quoted = false;
          }
        } else {
          field += ch;
        }
      } else if (quoteSeen && ch === '"') {
        field += '"';
        quoted = true;
        quoteSeen = false;
      } else {
        quoteSeen = false;
        if (ch === '"') quoted = true;
        else if (ch === ',') {
          row.push(field);
          field = '';
        } else if (ch === '\n') {
          row.push(field.replace(/\r$/, ''));
          field = '';
          yield row;
          row = [];
        } else {
          field += ch;
        }
      }
    }
  }
  if (field || row.length) {
    row.push(field);
    yield row;
  }
}

const clean = (s) => String(s ?? '').replace(/[\t\r\n]+/g, ' ').replace(/\s+/g, ' ').trim();

/** One output line, or null when the place has no usable position inside Korea. */
function line(name, lat, lon, address) {
  const la = Number(lat);
  const lo = Number(lon);
  if (!name || !(la > 32.5 && la < 39.5 && lo > 124 && lo < 132.5)) return null;
  return `${la.toFixed(5)}\t${lo.toFixed(5)}\t${clean(name)}\t${clean(address)}`;
}

function write(kind, lines, minimum) {
  const unique = [...new Set(lines)].sort();
  // A short conversion would silently drop whole regions; keep the old file instead.
  if (unique.length < minimum) throw new Error(`${kind}: only ${unique.length} rows`);
  writeFileSync(join(OUT, `${kind}.tsv`), unique.join('\n') + '\n');
  return unique.length;
}

/** "…_20260930" → "2026-09-30". */
const dated = (name) => name.match(/(\d{4})(\d{2})(\d{2})\s*$/)?.slice(1).join('-') ?? '';

const from = process.argv.includes('--from') ? process.argv[process.argv.indexOf('--from') + 1] : null;
const dir = from ?? mkdtempSync(join(tmpdir(), 'facilities-'));
const zips = { hira: join(dir, 'hira.zip'), sbiz: join(dir, 'sbiz.zip') };
const dates = { hira: process.env.HIRA_DATE ?? '', sbiz: process.env.SBIZ_DATE ?? '' };

try {
  for (const key of Object.keys(FILES)) {
    if (from) {
      if (!existsSync(zips[key])) throw new Error(`${zips[key]} is missing`);
    } else {
      console.log(`downloading ${FILES[key].title} …`);
      dates[key] = dated(await download(FILES[key], zips[key]));
    }
  }
  mkdirSync(OUT, { recursive: true });

  // 약국정보서비스: 요양기관명, …, 주소, …, 좌표(X) = longitude, 좌표(Y) = latitude.
  const pharmacies = [];
  let header = null;
  for await (const r of csvRows(zips.hira, '2.*', 'euc-kr')) {
    if (!header) {
      header = { name: r.indexOf('요양기관명'), addr: r.indexOf('주소'), x: r.indexOf('좌표(X)'), y: r.indexOf('좌표(Y)') };
      if (Object.values(header).some((i) => i < 0)) throw new Error('약국 파일의 열 이름이 바뀌었습니다');
      continue;
    }
    const l = line(r[header.name], r[header.y], r[header.x], String(r[header.addr] ?? '').split(',')[0]);
    if (l) pharmacies.push(l);
  }

  // 병원정보서비스: the same layout plus 종별코드명. Only general hospitals, which run emergency rooms.
  const hospitals = [];
  header = null;
  for await (const r of csvRows(zips.hira, '1.*', 'euc-kr')) {
    if (!header) {
      header = {
        name: r.indexOf('요양기관명'),
        kind: r.indexOf('종별코드명'),
        addr: r.indexOf('주소'),
        x: r.indexOf('좌표(X)'),
        y: r.indexOf('좌표(Y)'),
      };
      if (Object.values(header).some((i) => i < 0)) throw new Error('병원 파일의 열 이름이 바뀌었습니다');
      continue;
    }
    if (r[header.kind] !== '종합병원' && r[header.kind] !== '상급종합') continue;
    const l = line(r[header.name], r[header.y], r[header.x], String(r[header.addr] ?? '').split(',')[0]);
    if (l) hospitals.push(l);
  }

  // 상가(상권)정보: one CSV per 시·도 with the same header; only 상권업종소분류명 = 편의점.
  const stores = [];
  header = null;
  for await (const r of csvRows(zips.sbiz, null, 'utf-8')) {
    if (r.includes('상권업종소분류명')) {
      header = {
        name: r.indexOf('상호명'),
        branch: r.indexOf('지점명'),
        kind: r.indexOf('상권업종소분류명'),
        road: r.indexOf('도로명주소'),
        lot: r.indexOf('지번주소'),
        x: r.indexOf('경도'),
        y: r.indexOf('위도'),
      };
      if (Object.values(header).some((i) => i < 0)) throw new Error('상가 파일의 열 이름이 바뀌었습니다');
      continue;
    }
    if (!header || r[header.kind] !== '편의점') continue;
    const name = [r[header.name], r[header.branch]].map(clean).filter(Boolean).join(' ');
    const l = line(name, r[header.y], r[header.x], r[header.road] || r[header.lot]);
    if (l) stores.push(l);
  }

  const counts = {
    pharmacy: write('pharmacy', pharmacies, 15_000),
    hospital: write('hospital', hospitals, 200),
    store: write('store', stores, 30_000),
  };
  writeFileSync(
    join(OUT, 'sources.json'),
    JSON.stringify(
      {
        columns: ['lat', 'lon', 'name', 'address'],
        files: {
          pharmacy: { org: FILES.hira.org, title: FILES.hira.title, date: dates.hira, rows: counts.pharmacy },
          hospital: { org: FILES.hira.org, title: FILES.hira.title, date: dates.hira, rows: counts.hospital },
          store: { org: FILES.sbiz.org, title: FILES.sbiz.title, date: dates.sbiz, rows: counts.store },
        },
      },
      null,
      2,
    ) + '\n',
  );
  console.log(counts);
} catch (error) {
  console.error('update failed:', String(error?.message ?? error));
  process.exit(1);
}
