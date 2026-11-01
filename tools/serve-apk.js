// Tiny LAN download server for the PhoneKB APKs (used to install the
// companion app on the tablet, which has no adb).
// Usage: node tools/serve-apk.js   then open http://<PC-IP>:8000 on the tablet
const http = require("http");
const fs = require("fs");
const path = require("path");
const BUILD = path.join(__dirname, "..", "build");
const PORT = 8000;

const files = {
  "/PhoneKB.apk": "PhoneKB.apk",
  "/PhoneKB-Companion.apk": "PhoneKB-Companion.apk",
};

http.createServer((req, res) => {
  const url = (req.url || "/").split("?")[0];
  console.log(new Date().toISOString(), req.socket.remoteAddress, url);
  if (files[url]) {
    const p = path.join(BUILD, files[url]);
    if (!fs.existsSync(p)) { res.writeHead(404); res.end("not built yet"); return; }
    const stat = fs.statSync(p);
    res.writeHead(200, {
      "Content-Type": "application/vnd.android.package-archive",
      "Content-Disposition": 'attachment; filename="' + files[url] + '"',
      "Content-Length": stat.size,
    });
    fs.createReadStream(p).pipe(res);
    return;
  }
  res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
  res.end('<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">' +
    '<body style="font-family:sans-serif;background:#0f1115;color:#e9ecf2;padding:24px">' +
    '<h2>PhoneKB 下载</h2>' +
    '<p><a style="color:#4c8dff;font-size:18px" href="/PhoneKB-Companion.apk">下载 PhoneKB 伴侣（平板用）</a></p>' +
    '<p><a style="color:#4c8dff;font-size:18px" href="/PhoneKB.apk">下载 PhoneKB（手机用）</a></p>' +
    '<p style="color:#98a1b3">手机和平板需连同一个 WiFi。安装时系统可能提示「未知来源」，允许即可。</p>' +
    '</body>');
}).listen(PORT, "0.0.0.0", () => console.log("PhoneKB apk server on http://0.0.0.0:" + PORT));