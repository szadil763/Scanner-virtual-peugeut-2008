// Fotografa o painel REAL do multimídia (android/app/src/multimidia/assets/painel/index.html) com dados de exemplo
// no mesmo formato que o celular envia (PainelLink). Serve a página num servidor local e responde /estado e /midia.
// Uso: node marketing/linkedin/painel-demo.js [pasta-de-saída]   (padrão: marketing/linkedin/telas)
const { chromium } = require("playwright");
const http = require("http");
const fs = require("fs");
const path = require("path");

const PAINEL = path.resolve(__dirname, "../../android/app/src/multimidia/assets/painel/index.html");
const out = path.resolve(process.argv[2] || path.join(__dirname, "telas"));
fs.mkdirSync(out, { recursive: true });

const agora = Date.now();
const dia = 86400000;
const hoje = Math.floor(agora / dia);

// Tanque: últimos 12 dias, cai rodando, sobe nos abastecimentos.
const historico = [];
let litros = 18;
for (let i = 0; i <= 96; i++) {
  const t = agora - (96 - i) * 3 * 3600000;
  if (i === 30 || i === 70) { litros = 50; historico.push([t, litros, "abastecimento"]); continue; }
  litros = Math.max(6, litros - 0.55);
  historico.push([t, Math.round(litros * 10) / 10, "rodando"]);
}
const consumoHist = Array.from({ length: 120 }, (_, i) => Math.round((11.5 + 3 * Math.sin(i / 9) + 1.4 * Math.sin(i / 3.1)) * 100) / 100);
const velHist = Array.from({ length: 240 }, (_, i) => Math.max(0, Math.round((48 + 30 * Math.sin(i / 20) + 8 * Math.sin(i / 5)) * 10) / 10));

const serie = (base, amp, n = 12) => Array.from({ length: n }, (_, i) => Math.round((base + amp * Math.sin(i / 2)) * 100) / 100);
const dados = {
  ok: true, instancia: "demo", usuario: "Adilson", celular: "Moto G54", carro: "Peugeot 2008 1.6 flex", carroAviso: false,
  rpm: 2150, speed: 62, coolant: 89, battery: 14.1, kmL: 12.4, lph: 5.0, parado: false,
  tripAvgKmL: 11.8, tripAvgL100: 8.5, tripAvgMovKmL: 12.6, tripKm: 38.4, tripMin: 52,
  carga: 34, pressaoKpa: 46, arAdmissao: 31, borboleta: 14, versao: 170,
  bateriaSituacoes: [
    { situacao: "Parado (marcha lenta)", n: 820, media: 13.62, min: 13.21, max: 14.02 },
    { situacao: "Rodando sem acelerar", n: 1310, media: 13.95, min: 13.4, max: 14.3 },
    { situacao: "Cruzeiro", n: 980, media: 14.08, min: 13.7, max: 14.35 },
    { situacao: "Acelerando", n: 260, media: 14.12, min: 13.8, max: 14.4 },
  ],
  trajetos: { total: { quantidade: 6, km: 412.3, litros: 34.6, custo: 213.81, mediaKmL: 11.9, mediaMovKmL: 12.7 },
    lista: [0, 1, 2].map((i) => ({ fim: agora - (i + 1) * 2 * dia, km: [74.1, 58.6, 92.3][i], litros: [6.1, 5.2, 7.4][i], kmL: [12.1, 11.3, 12.5][i], custo: [37.7, 32.1, 45.7][i] })) },
  tanque: { capacidade: 50, litros: 31.5, percentual: 63, autonomiaKm: 372, alerta: null, historico },
  midia: { musica: null, radio: null },
  saude: [
    { titulo: "Luz de injeção e falhas", status: "ALERTA", valor: "1 falha(s)", conselho: "A central registrou falha. Veja os códigos em Diagnóstico e leve a um mecânico com eles anotados.", unidade: "", serie: [], linhas: [], abastecimento: null, antes: null, depois: null, viagensDepois: 0 },
    { titulo: "Mistura (correção de combustível)", status: "ALERTA", valor: "+31,1% · lenta +30,6%", conselho: "Mistura pobre (a central está adicionando combustível). Verifique entrada falsa de ar, bicos e pressão da bomba.", unidade: "%", serie: serie(29, 3), linhas: [{ valor: 10, alerta: false }, { valor: 20, alerta: true }], abastecimento: null, antes: null, depois: null, viagensDepois: 0 },
    { titulo: "Marcha lenta", status: "ATENCAO", valor: "764 rpm ± 92", conselho: "Marcha lenta oscilando: verifique velas, bobinas e limpeza do corpo de borboleta.", unidade: "rpm", serie: serie(80, 15), linhas: [{ valor: 80, alerta: false }], abastecimento: null, antes: null, depois: null, viagensDepois: 0 },
    { titulo: "Sonda lambda (antes do catalisador)", status: "OK", valor: "faixa 0,92 V", conselho: "Oscilando bem (uma sonda boa vai de ~0,1 a ~0,9 V).", unidade: "V", serie: serie(0.9, 0.03), linhas: [], abastecimento: null, antes: null, depois: null, viagensDepois: 0 },
    { titulo: "Arrefecimento", status: "OK", valor: "89 °C · máx 96", conselho: "Aquece e se mantém na faixa normal.", unidade: "°C", serie: serie(94, 2), linhas: [{ valor: 105, alerta: true }], abastecimento: null, antes: null, depois: null, viagensDepois: 0 },
    { titulo: "Bateria e alternador", status: "OK", valor: "14,1 V agora · ligado 13,9", conselho: "Bateria e carga normais.", unidade: "V", serie: serie(13.9, 0.15), linhas: [{ valor: 13, alerta: true }], abastecimento: null, antes: null, depois: null, viagensDepois: 0 },
  ],
  postos: [
    { nome: "Posto Azul", endereco: "Av. Central, 1200", nota: "BOA", tanques: 3, visitas: 4, preco: 6.09, distanciaKm: 1.8, resumo: "Rendeu 4% melhor que o normal do carro · 3 tanque(s) avaliado(s)" },
    { nome: "Posto Bandeira Verde", endereco: "Rua das Flores, 80", nota: "NORMAL", tanques: 2, visitas: 2, preco: 5.99, distanciaKm: 3.4, resumo: "Rodou dentro do normal do carro · 2 tanque(s) avaliado(s)" },
    { nome: "Posto da Estrada", endereco: "Rodovia, km 42", nota: "SUSPEITA", tanques: 2, visitas: 2, preco: 5.79, distanciaKm: 12.6, resumo: "Gastou 8% mais combustível que o normal · 2 tanque(s) avaliado(s)" },
  ],
  manutencao: [
    { id: "oleo", nome: "Óleo do motor e filtro", porque: "Intervalo usado: 10000 km ou 12 meses, o que vier primeiro. Confirme no manual.", feito: true, estado: "SOON", kmRestante: 820, diasRestantes: 41, intervaloKm: 10000, intervaloMeses: 12 },
    { id: "filtro_ar", nome: "Filtro de ar", porque: "Filtro sujo aumenta o consumo.", feito: true, estado: "OK", kmRestante: 6400, diasRestantes: 210, intervaloKm: 10000, intervaloMeses: 12 },
    { id: "velas", nome: "Velas de ignição", porque: "Velas gastas causam falha e marcha lenta irregular.", feito: true, estado: "DUE", kmRestante: -350, diasRestantes: null, intervaloKm: 30000, intervaloMeses: null },
    { id: "correia", nome: "Correia dentada", porque: "Se romper, pode danificar o motor.", feito: true, estado: "OK", kmRestante: 21500, diasRestantes: null, intervaloKm: 60000, intervaloMeses: 48 },
    { id: "fluido_freio", nome: "Fluido de freio", porque: "Absorve umidade com o tempo.", feito: true, estado: "OK", kmRestante: null, diasRestantes: 300, intervaloKm: null, intervaloMeses: 24 },
    { id: "filtro_comb", nome: "Filtro de combustível", porque: "Entupido, deixa a mistura pobre.", feito: true, estado: "SOON", kmRestante: 1500, diasRestantes: null, intervaloKm: 20000, intervaloMeses: null },
  ],
  consumo: { historico: consumoHist, velocidades: velHist, custo: 23.39, litrosViagem: 3.84, velMedia: 44.3,
    abastecimentos: [
      { id: agora - 4 * dia, dia: hoje - 4, litros: 38.2, preco: 6.09, cheio: true, etanol: false, kmL: 12.3, posto: "Posto Azul", endereco: "Av. Central, 1200" },
      { id: agora - 16 * dia, dia: hoje - 16, litros: 41.5, preco: 5.99, cheio: true, etanol: false, kmL: 11.6, posto: "Posto Bandeira Verde", endereco: "Rua das Flores, 80" },
    ] },
  diagnostico: { estado: "CONNECTED", mensagem: "Conectado ao OBDBLE", protocolo: "ISO 15765-4 (CAN 11/500)", carro: "Peugeot 2008 1.6 flex", simulado: false, falhasLidas: true,
    falhas: [{ codigo: "P0171", descricao: "Mistura pobre (banco 1)", dica: "Entrada falsa de ar, bicos sujos ou bomba de combustível fraca." }],
    pendentes: [],
    registro: ["> ATZ", "ELM327 v1.5", "> 0100", "41 00 BE 3F A8 13", "> 010C", "41 0C 21 98", "> 010D", "41 0D 3E", "> 0105", "41 05 81", "> ATRV", "14.1V", "> 03", "43 01 01 71"] },
  falaId: 0, pendente: null, pendenteId: 0, aviso: "", mapa: null, posto: null, fala: "",
  ajustes: { sol: false, direcao: "ALWAYS_FULL", combustivel: "E30", calibracao: 0.4, preco: 6.09, eficiencia: 0.8, carro: "peugeot2008", capacidadeTanque: 50,
    conta: { email: "", carro: "Peugeot 2008 1.6 flex" }, carros: [{ id: "peugeot2008", nome: "Peugeot 2008 1.6 flex" }] },
};

const estado = () => JSON.stringify({ idadeMs: 300, dados, vozMultimidia: "", postoLocal: null, carroFixo: "peugeot2008", outraFonte: "", carrosLista: [{ id: "peugeot2008", nome: "Peugeot 2008 1.6 flex" }] });
const midia = JSON.stringify({ musica: { app: "Spotify", titulo: "Música do celular", artista: "Bluetooth", tocando: true, posMs: 74000, durMs: 211000, chave: "x", capa: false }, radio: null, radios: [], fontes: [] });

const server = http.createServer((req, res) => {
  const u = req.url.split("?")[0];
  if (u === "/" || u === "/painel") { res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" }); return res.end(fs.readFileSync(PAINEL)); }
  if (u === "/estado") { res.writeHead(200, { "Content-Type": "application/json" }); return res.end(estado()); }
  if (u === "/midia") { res.writeHead(200, { "Content-Type": "application/json" }); return res.end(midia); }
  if (req.method === "POST") { res.writeHead(204); return res.end(); }
  res.writeHead(404); res.end();
});

(async () => {
  await new Promise((r) => server.listen(0, "127.0.0.1", r));
  const port = server.address().port;
  const browser = await chromium.launch();
  // Altura real do WebView no multimídia (~650 px, ver CLAUDE.md).
  const page = await browser.newPage({ viewport: { width: +(process.env.W || 1280), height: +(process.env.H || 650) }, deviceScaleFactor: 2 });
  const erros = [];
  page.on("pageerror", (e) => erros.push(e.message));
  await page.goto(`http://127.0.0.1:${port}/`);
  await page.waitForTimeout(2500);
  for (const aba of ["painel", "consumo", "saude", "diag", "manutencao", "postos", "tocador"]) {
    await page.evaluate((a) => window.aba(a), aba);
    await page.waitForTimeout(1500);
    // Diagnóstico: rola até as falhas lidas da central.
    if (aba === "diag") await page.evaluate(() => { const f = [...document.querySelectorAll("#t-diag *")].find((e) => /P0171/.test(e.textContent) && e.children.length === 0); if (f) f.scrollIntoView({ block: "center" }); });
    await page.waitForTimeout(300);
    await page.screenshot({ path: path.join(out, `${aba}.png`) });
  }
  await browser.close();
  server.close();
  if (erros.length) console.log("Erros na página:", erros.slice(0, 5));
  console.log("Telas em", out);
})();
