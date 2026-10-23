// v4.csv (start,end,CC) -> geoip4.bin: "GEO4", u16 countryCount, countryCount*2 bytes ASCII codes,
// u32 rangeCount, then rangeCount * (u32 start, u8 countryIndex). Adjacent same-country ranges merged;
// gaps become index 255 (unknown).
const fs = require('fs');
const ip = s => s.split('.').reduce((a, b) => a * 256 + Number(b), 0);
const rows = fs.readFileSync(process.argv[2], 'utf8').trim().split('\n').map(l => l.split(','));
const codes = []; const idx = {};
const out = [];
let lastEnd = -1;
for (const [a, b, cc] of rows) {
  const s = ip(a), e = ip(b);
  if (s > lastEnd + 1) out.push([lastEnd + 1, 255]);
  if (!(cc in idx)) { idx[cc] = codes.length; codes.push(cc); }
  const ci = idx[cc];
  if (out.length && out[out.length - 1][1] === ci) { /* merge */ } else out.push([s, ci]);
  lastEnd = e;
}
const buf = Buffer.alloc(4 + 2 + codes.length * 2 + 4 + out.length * 5);
let o = buf.write('GEO4', 0);
buf.writeUInt16BE(codes.length, o); o += 2;
for (const c of codes) { o += buf.write(c.padEnd(2).slice(0, 2), o, 'ascii'); }
buf.writeUInt32BE(out.length, o); o += 4;
for (const [s, ci] of out) { buf.writeUInt32BE(s, o); o += 4; buf.writeUInt8(ci, o); o += 1; }
fs.writeFileSync(process.argv[3], buf);
console.log('countries', codes.length, 'ranges', out.length, 'bytes', buf.length);
