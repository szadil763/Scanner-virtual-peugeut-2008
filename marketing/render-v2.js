// Grava marketing/v2.html em MP4. Uso: node marketing/render-v2.js h|v <pasta-dos-quadros> <saida.mp4> [fps]
//   ou: node marketing/render-v2.js h|v --shots <pasta> 3 13 25 ...
const { chromium } = require("playwright");
const { execFileSync } = require("child_process");
const path = require("path");
const fs = require("fs");

(async () => {
  const [fmt, ...args] = process.argv.slice(2);
  const shots = args[0] === "--shots";
  const dir = shots ? args[1] : args[0];
  fs.mkdirSync(dir, { recursive: true });
  const [w, h] = fmt === "v" ? [720, 1280] : [1280, 720];
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: w, height: h } });
  const errs = [];
  page.on("pageerror", e => errs.push(e.message));
  await page.goto("file://" + path.resolve(__dirname, "v2.html") + "?frames&" + fmt);
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(500);
  if (shots) {
    for (const s of args.slice(2)) {
      await page.evaluate(t => window.render(t), +s);
      await page.screenshot({ path: path.join(dir, `${fmt}${s}.png`) });
    }
  } else {
    const out = args[1], fps = +(args[2] || 30);
    const total = await page.evaluate(() => window.TOTAL);
    for (let i = 0; i < Math.round(total * fps); i++) {
      await page.evaluate(t => window.render(t), i / fps);
      await page.screenshot({ path: path.join(dir, String(i).padStart(5, "0") + ".jpg"), type: "jpeg", quality: 92 });
    }
    execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-framerate", String(fps), "-i", path.join(dir, "%05d.jpg"),
      "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "20", "-preset", "medium", "-movflags", "+faststart", out]);
  }
  if (errs.length) console.log("erros:", errs);
  await browser.close();
})();
