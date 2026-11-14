const fs = require('fs');
const path = require('path');
const sharp = require('sharp');
const pngToIco = require('png-to-ico');

async function main() {
  const root = path.resolve(__dirname, '..');
  const svg = path.join(root, 'branding', 'help-icon.svg');
  const out = path.join(__dirname, 'build');
  fs.mkdirSync(out, { recursive: true });
  const png = path.join(out, 'icon.png');
  const ico = path.join(out, 'icon.ico');
  await sharp(svg).resize(256, 256).png().toFile(png);
  fs.writeFileSync(ico, await pngToIco(png));
  console.log('HELP icon generated:', ico);
}
main().catch(error => { console.error(error); process.exit(1); });
