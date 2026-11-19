// Gera as lâminas do carrossel do LinkedIn (PNG 1080x1350) e o PDF para postar como documento.
// Antes: W=900 H=1000 node marketing/linkedin/painel-demo.js marketing/linkedin/telas
//        W=1280 H=650 node marketing/linkedin/painel-demo.js marketing/linkedin/telas/largo
// Uso: node marketing/linkedin/render.js [pasta-de-saída]   (padrão: marketing/linkedin/saida)
const { chromium } = require("playwright");
const path = require("path");
const fs = require("fs");

(async () => {
  const out = path.resolve(process.argv[2] || path.join(__dirname, "saida"));
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1080, height: 1350 } });
  await page.goto("file://" + path.join(__dirname, "carrossel.html"));
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(500);
  const slides = await page.$$(".slide");
  for (let i = 0; i < slides.length; i++) {
    await slides[i].screenshot({ path: path.join(out, `lamina-${i + 1}.png`) });
  }
  await page.pdf({ path: path.join(out, "carrossel.pdf"), width: "1080px", height: "1350px", printBackground: true });
  await browser.close();
  console.log(`${slides.length} lâminas em ${out}`);
})();
