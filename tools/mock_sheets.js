// Giả lập Google Sheets + Apps Script Web App để test đồng bộ của HUY FAN (chạy đúng mã apps_script.txt).
// Dùng: node tools/mock_sheets.js  →  adb reverse tcp:8787 tcp:8787  →  link trong app: http://localhost:8787/exec
const http = require('http');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const scriptPath = path.join(__dirname, '..', 'app', 'src', 'main', 'res', 'raw', 'apps_script.txt');
const PORT = 8787;

// ---------- Bảng tính trong bộ nhớ ----------
function makeSheet(name) {
  const rows = []; // rows[0] = dòng 1
  const sh = {
    name,
    rows,
    getName: () => name,
    getLastRow: () => rows.length,
    getRange(r, c, nr = 1, nc = 1) {
      const range = {
        setValues(vals) {
          for (let i = 0; i < nr; i++) {
            while (rows.length < r + i) rows.push([]);
            for (let j = 0; j < nc; j++) rows[r - 1 + i][c - 1 + j] = vals[i][j];
          }
          return range;
        },
        getValues() {
          const out = [];
          for (let i = 0; i < nr; i++) {
            const row = rows[r - 1 + i] || [];
            out.push(Array.from({ length: nc }, (_, j) => row[c - 1 + j] ?? ''));
          }
          return out;
        },
      };
      ['setNumberFormat', 'setFontWeight', 'setBackground', 'setFontColor'].forEach((m) => (range[m] = () => range));
      return range;
    },
    deleteRow(r) { rows.splice(r - 1, 1); },
  };
  ['setFrozenRows', 'hideColumns', 'setColumnWidth', 'setColumnWidths'].forEach((m) => (sh[m] = () => sh));
  return sh;
}

const book = { sheets: [makeSheet('Trang tính1')] };
const ss = {
  getSheetByName: (n) => book.sheets.find((s) => s.name === n) || null,
  getSheets: () => book.sheets.slice(),
  insertSheet(n, idx) { const s = makeSheet(n); book.sheets.splice(idx ?? book.sheets.length, 0, s); return s; },
  deleteSheet(s) { book.sheets = book.sheets.filter((x) => x !== s); },
};

const ctx = {
  SpreadsheetApp: { getActiveSpreadsheet: () => ss },
  LockService: { getScriptLock: () => ({ waitLock() {}, releaseLock() {} }) },
  ContentService: {
    MimeType: { JSON: 'application/json' },
    createTextOutput: (s) => ({ content: s, setMimeType() { return this; } }),
  },
  console,
};
vm.createContext(ctx);
let code = fs.readFileSync(scriptPath, 'utf8');
if (process.env.SECRET) code = code.replace("const SECRET = '';", `const SECRET = '${process.env.SECRET}';`);
vm.runInContext(code, ctx);

function dump() {
  return book.sheets.map((s) => `--- ${s.name} (${Math.max(0, s.rows.length - 1)} dòng)\n` +
    s.rows.map((r) => r.map((v) => String(v ?? '').slice(0, 22)).join(' | ')).join('\n')).join('\n');
}

// Apps Script thật: POST /exec chạy doPost rồi chuyển hướng 302 sang link "echo" để đọc kết quả
const echoes = new Map();
let echoId = 0;

http.createServer((req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  let body = '';
  req.on('data', (c) => (body += c));
  req.on('end', () => {
    const t = new Date().toISOString().slice(11, 19);
    if (url.pathname === '/dump') { res.end(dump()); return; }
    if (url.pathname === '/echo') {
      const out = echoes.get(url.searchParams.get('id')) || '{"ok":false,"error":"echo hết hạn"}';
      res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(out); return;
    }
    if (url.pathname !== '/exec') { res.writeHead(404); res.end('<html>Not found</html>'); return; }
    if (req.method === 'GET') {
      const out = ctx.doGet().content;
      console.log(`[${t}] GET  → ${out}`);
      res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(out); return;
    }
    const out = ctx.doPost({ postData: { contents: body } }).content;
    let summary = '';
    try {
      const d = JSON.parse(body);
      summary = `items=${(d.items || []).length} grammar=${(d.grammar || []).length} deletes=${(d.deletes || []).length} token=${d.token ? '***' : '(trống)'}`;
    } catch (e) { summary = 'body không phải JSON'; }
    console.log(`[${t}] POST ${summary} → ${out}`);
    const id = String(++echoId);
    echoes.set(id, out);
    res.writeHead(302, { Location: `/echo?id=${id}` }); res.end();
  });
}).listen(PORT, () => console.log(`Mock Sheets chạy ở http://localhost:${PORT}/exec (xem dữ liệu: /dump)`));
