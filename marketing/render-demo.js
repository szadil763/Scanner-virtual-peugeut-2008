// Grava marketing/demo.html em vídeo: chama render(t) quadro a quadro e junta com o ffmpeg.
// Uso: node marketing/render-demo.js <pasta-dos-quadros> <saida.mp4> [fps]
//      node marketing/render-demo.js --shots <pasta> 3 13 25 ...   (só fotos nesses segundos)
const { chromium } = require("playwright");
const { execFileSync } = require("child_process");
const path = require("path");
const fs = require("fs");

(async () => {
  const args = process.argv.slice(2);
  const shots = args[0] === "--shots";
  const dir = shots ? args[1] : args[0];
  fs.mkdirSync(dir, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
  await page.goto("file://" + path.resolve(__dirname, "demo.html") + "?frames");
  await page.evaluate(() => document.fonts.ready);
  if (shots) {
    for (const s of args.slice(2)) {
      await page.evaluate(t => window.render(t), +s);
      await page.screenshot({ path: path.join(dir, `t${s}.png`) });
    }
  } else {
    const out = args[1];
    const fps = +(args[2] || 30);
    const total = await page.evaluate(() => window.TOTAL);
    const n = Math.round(total * fps);
    for (let i = 0; i < n; i++) {
      await page.evaluate(t => window.render(t), i / fps);
      await page.screenshot({ path: path.join(dir, String(i).padStart(5, "0") + ".jpg"), type: "jpeg", quality: 92 });
    }
    execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-framerate", String(fps), "-i", path.join(dir, "%05d.jpg"),
      "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "20", "-preset", "medium", "-movflags", "+faststart", out]);
  }
  await browser.close();
})();
