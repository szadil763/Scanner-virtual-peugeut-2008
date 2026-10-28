#!/usr/bin/env node
// Confere as fórmulas OBD e de consumo usadas no app Android e no protótipo.
// Os mesmos números aparecem nos testes Kotlin (android/app/src/test).
// Uso: node tools/verify-formulas.js

const assert = require('assert');
let ok = 0;
const near = (a, b, tol, nome) => {
  assert.ok(Math.abs(a - b) <= tol, `${nome}: esperado ${b}, obtido ${a}`);
  ok++;
  console.log(`ok  ${nome} = ${a}`);
};

// ---------- PIDs ----------
const PID = {
  0x04: (d) => (d[0] * 100) / 255,
  0x05: (d) => d[0] - 40,
  0x0b: (d) => d[0],
  0x0c: (d) => (256 * d[0] + d[1]) / 4,
  0x0d: (d) => d[0],
  0x0f: (d) => d[0] - 40,
  0x10: (d) => (256 * d[0] + d[1]) / 100,
  0x11: (d) => (d[0] * 100) / 255,
  0x2f: (d) => (d[0] * 100) / 255,
  0x42: (d) => (256 * d[0] + d[1]) / 1000,
  0x5e: (d) => (256 * d[0] + d[1]) / 20,
};

// Um bloco por central que respondeu (no 2008 automático, motor e câmbio respondem juntos).
// Com `size` (bytes de dados), também separa respostas grudadas na mesma linha.
function payloads(raw, prefix, size) {
  const linhas = raw.split(/[\r\n]+/)
    .map((l) => l.toUpperCase().replace(/[^0-9A-Z.?]/g, '').replace(/^SEARCHING\.\.\./, ''))
    .filter((l) => l);
  const bytesDe = (hex) => {
    if (hex.length % 2) return null;
    const b = hex.match(/../g)?.map((h) => parseInt(h, 16)) ?? [];
    return b.some(Number.isNaN) ? null : b;
  };
  const out = [];
  for (const l of linhas) {
    if (size === undefined) {
      if (!l.startsWith(prefix)) continue;
      const b = bytesDe(l.slice(prefix.length));
      if (b) out.push(b);
      continue;
    }
    const frame = prefix.length + 2 * size;
    for (let pos = 0; l.startsWith(prefix, pos) && pos + frame <= l.length; pos += frame) {
      const b = bytesDe(l.slice(pos + prefix.length, pos + frame));
      if (b) out.push(b);
    }
  }
  return out;
}
const payload = (raw, prefix) => payloads(raw, prefix)[0] ?? null;
const BYTES = { 0x04: 1, 0x05: 1, 0x0b: 1, 0x0c: 2, 0x0d: 1, 0x0f: 1, 0x10: 2, 0x11: 1, 0x2f: 1, 0x42: 2, 0x5e: 2 };
const hex2 = (n) => n.toString(16).toUpperCase().padStart(2, '0');
const parse = (pid, raw) => {
  const b = payloads(raw, '41' + hex2(pid), BYTES[pid])[0];
  return b ? PID[pid](b) : null;
};

near(parse(0x0c, '41 0C 1A F8 \r\r>'), 1726, 1e-9, 'RPM 41 0C 1A F8');
near(parse(0x0d, '41 0D 32'), 50, 1e-9, 'Velocidade 41 0D 32');
near(parse(0x05, 'SEARCHING...\r41 05 7B\r\r>'), 83, 1e-9, 'Temp. motor com SEARCHING');
near(parse(0x10, '41 10 01 F4'), 5.0, 1e-9, 'MAF 41 10 01 F4');
near(parse(0x42, '41 42 38 A4'), 14.5, 1e-9, 'Bateria 41 42 38 A4');
near(parse(0x2f, '41 2F 80'), 50.196078, 1e-5, 'Nível combustível 41 2F 80');
near(parse(0x5e, '41 5E 00 C8'), 10.0, 1e-9, 'Taxa combustível 41 5E 00 C8');
near(parse(0x0c, '410C1AF8'), 1726, 1e-9, 'RPM sem espaços');
assert.strictEqual(parse(0x0c, 'NO DATA\r\r>'), null); ok++; console.log('ok  NO DATA -> null');
assert.strictEqual(parse(0x0c, '?\r\r>'), null); ok++; console.log('ok  "?" -> null');

// ---------- PIDs suportados ----------
function suportados(base, raw) {
  const out = new Set();
  for (const b of payloads(raw, '41' + hex2(base), 4)) {
    const mask = ((b[0] << 24) | (b[1] << 16) | (b[2] << 8) | b[3]) >>> 0;
    for (let i = 0; i < 32; i++) if ((mask >>> (31 - i)) & 1) out.add(base + 1 + i);
  }
  return [...out].sort((a, b) => a - b);
}
const s = suportados(0x00, '41 00 BE 3F A8 13');
assert.deepStrictEqual(s.slice(0, 7), [0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0b]); ok++;
console.log('ok  PIDs suportados 41 00 BE 3F A8 13 ->', s.map(hex2).join(' '));
assert.ok(s.includes(0x20)); ok++; console.log('ok  indica faixa 0x20 disponível');
// Resposta real do 2008 automático: o câmbio responde primeiro e quase não declara nada.
const s2 = suportados(0x00, '41 00 80 00 00 00 \r41 00 BE 3E B8 11 \r\r>');
assert.ok([0x01, 0x04, 0x05, 0x0b, 0x0c, 0x0d, 0x0f, 0x11, 0x20].every((p) => s2.includes(p))); ok++;
console.log('ok  junta as centrais (câmbio + motor) ->', s2.map(hex2).join(' '));
near(parse(0x0c, '41 0C 1A\r41 0C 1A F8\r\r>'), 1726, 1e-9, 'RPM pula linha incompleta de outra central');
// As duas respostas grudadas na mesma linha, como podem chegar pelo BLE.
const s3 = suportados(0x00, '41 00 80 00 00 00 41 00 BE 3E B8 11 \r\r>');
assert.ok([0x01, 0x04, 0x05, 0x0b, 0x0c, 0x0d, 0x0f, 0x11, 0x20].every((p) => s3.includes(p))); ok++;
console.log('ok  separa centrais grudadas na mesma linha ->', s3.map(hex2).join(' '));
near(parse(0x0c, '41 0C 0B DA 41 0C 0B DA\r\r>'), 758.5, 1e-9, 'RPM com duas centrais na mesma linha');

// ---------- Falhas (modo 03, CAN) ----------
function dtcs(raw) {
  const b = payload(raw, '43');
  if (!b || b.length < 1) return [];
  const n = b[0];
  const out = [];
  for (let i = 0; i < n; i++) {
    const a = b[1 + 2 * i], c = b[2 + 2 * i];
    if (a === undefined || c === undefined) break;
    if (a === 0 && c === 0) continue;
    out.push('PCBU'[(a >> 6) & 3] + ((a >> 4) & 3) + (a & 0xf).toString(16).toUpperCase() + hex2(c));
  }
  return out;
}
assert.deepStrictEqual(dtcs('43 02 01 33 02 00 00 00'), ['P0133', 'P0200']); ok++;
console.log('ok  DTC 43 02 01 33 02 00 -> P0133, P0200');
assert.deepStrictEqual(dtcs('43 00 00 00 00 00 00 00'), []); ok++;
console.log('ok  sem falhas -> lista vazia');
assert.deepStrictEqual(dtcs('43 01 C1 00 00 00 00 00'), ['U0100']); ok++;
console.log('ok  DTC C1 00 -> U0100');

// ---------- Consumo ----------
const AFR_GASOLINA = 14.7, AFR_ETANOL = 9.0, DENS_GASOLINA = 745, DENS_ETANOL = 789;
const blend = (f) => ({
  afr: AFR_GASOLINA * (1 - f) + AFR_ETANOL * f,
  dens: DENS_GASOLINA * (1 - f) + DENS_ETANOL * f,
});
const b30 = blend(0.3);
near(b30.afr, 12.99, 1e-9, 'AFR da mistura E30');
near(b30.dens, 758.2, 1e-9, 'Densidade E30 (g/L)');

const lphDeMaf = (maf, bl) => (maf * 3600) / (bl.afr * bl.dens);
near(lphDeMaf(5, b30), 1.8277, 1e-3, 'L/h com MAF 5 g/s (E30)');

// Vazão de ar por densidade-velocidade
const R = 287.05;
function mafDeMap({ mapKpa, rpm, iatC, cilindradaL, ve }) {
  const rho = (mapKpa * 1000) / (R * (iatC + 273.15));
  const volPorS = (rpm / 120) * (cilindradaL / 1000);
  return rho * ve * volPorS * 1000; // g/s
}
near(mafDeMap({ mapKpa: 40, rpm: 800, iatC: 29.85, cilindradaL: 1.598, ve: 0.8 }), 3.92, 0.02, 'MAF estimado por MAP (g/s)');

const kmPorL = (v, lph) => (lph > 0.05 && v >= 3 ? Math.min(v / lph, 50) : null);
near(kmPorL(60, 5), 12, 1e-9, 'km/l instantâneo 60 km/h a 5 L/h');
assert.strictEqual(kmPorL(0, 1), null); ok++; console.log('ok  parado -> km/l nulo');
near(kmPorL(60, 0.06), 50, 1e-9, 'km/l limitado a 50');

// Etanol puro
const b95 = blend(0.95);
near(b95.afr, 9.285, 1e-9, 'AFR etanol (f=0,95)');

// Calibração tanque cheio a tanque cheio
const fator = (litrosReais, litrosEstimados) => Math.min(1.5, Math.max(0.5, litrosReais / litrosEstimados));
near(fator(38, 34.5), 1.1014, 1e-3, 'Fator de calibração 38 L / 34,5 L');
near(fator(100, 10), 1.5, 1e-9, 'Fator limitado em 1,5');

// Troca de óleo
const kmRestantes = (intervalo, kmAtual, kmTroca) => intervalo - (kmAtual - kmTroca);
near(kmRestantes(10000, 14300, 8000), 3700, 1e-9, 'Óleo: km restantes');

console.log(`\n${ok} verificações passaram.`);
