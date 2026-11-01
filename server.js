const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const multer = require('multer');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const app = express();
const PORT = Number(process.env.PORT || 3000);
const ADMIN_KEY = process.env.HELP_ADMIN_KEY || '';
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, 'data');
const UPLOAD_DIR = process.env.UPLOAD_DIR || path.join(DATA_DIR, 'uploads');
const CATALOG_FILE = path.join(DATA_DIR, 'catalog.json');

fs.mkdirSync(UPLOAD_DIR, { recursive: true });
if (!fs.existsSync(CATALOG_FILE)) fs.writeFileSync(CATALOG_FILE, JSON.stringify({ categories: [], apps: [] }, null, 2));

app.use(helmet({ contentSecurityPolicy: false, crossOriginResourcePolicy: { policy: 'cross-origin' } }));
app.use(cors());
app.use(express.json({ limit: '1mb' }));
app.use(express.static(path.join(__dirname, 'public')));

function readCatalog() {
  try {
    const data = JSON.parse(fs.readFileSync(CATALOG_FILE, 'utf8'));
    return { categories: Array.isArray(data.categories) ? data.categories : [], apps: Array.isArray(data.apps) ? data.apps : [] };
  } catch {
    throw new Error('Не удалось прочитать каталог. Проверьте data/catalog.json');
  }
}
function saveCatalog(data) {
  const tmp = CATALOG_FILE + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(data, null, 2));
  fs.renameSync(tmp, CATALOG_FILE);
}
function adminOnly(req, res, next) {
  if (!ADMIN_KEY) return res.status(503).json({ error: 'На сервере не задан HELP_ADMIN_KEY' });
  const supplied = req.get('x-admin-key') || req.body?.adminKey || '';
  if (supplied !== ADMIN_KEY) return res.status(401).json({ error: 'Неверный ключ администратора' });
  next();
}
const storage = multer.diskStorage({
  destination: (_req, _file, cb) => cb(null, UPLOAD_DIR),
  filename: (_req, file, cb) => cb(null, crypto.randomUUID() + '.apk')
});
const upload = multer({
  storage,
  limits: { fileSize: 1024 * 1024 * 1024 },
  fileFilter: (_req, file, cb) => {
    if (!file.originalname.toLowerCase().endsWith('.apk')) return cb(new Error('Разрешены только APK-файлы'));
    cb(null, true);
  }
});

app.get('/api/health', (_req, res) => res.json({ ok: true, service: 'HELP', version: '1.0.0' }));
app.get('/api/categories', (_req, res) => {
  const catalog = readCatalog();
  res.json(catalog.categories.map(c => ({ id: c.id, name: c.name, appCount: catalog.apps.filter(a => a.categoryId === c.id).length })));
});
app.get('/api/apps', (req, res) => {
  const catalog = readCatalog();
  const list = catalog.apps.filter(a => !req.query.categoryId || a.categoryId === req.query.categoryId)
    .map(({ id, name, categoryId, version, description, fileName, size, createdAt }) => ({
      id, name, categoryId, version, description, fileName, size, createdAt,
      downloadUrl: '/api/apps/' + encodeURIComponent(id) + '/download'
    }));
  res.json(list);
});
app.get('/api/apps/:id/download', (req, res) => {
  const item = readCatalog().apps.find(a => a.id === req.params.id);
  if (!item) return res.status(404).json({ error: 'Приложение не найдено' });
  const file = path.join(UPLOAD_DIR, path.basename(item.diskName));
  if (!fs.existsSync(file)) return res.status(404).json({ error: 'Файл APK отсутствует на сервере' });
  res.download(file, item.fileName);
});

app.post('/api/admin/categories', adminOnly, (req, res) => {
  const name = String(req.body.name || '').trim();
  if (!name || name.length > 50) return res.status(400).json({ error: 'Название категории: от 1 до 50 символов' });
  const catalog = readCatalog();
  if (catalog.categories.some(c => c.name.toLowerCase() === name.toLowerCase())) return res.status(409).json({ error: 'Такая категория уже есть' });
  const category = { id: crypto.randomUUID(), name };
  catalog.categories.push(category);
  saveCatalog(catalog);
  res.status(201).json(category);
});

app.post('/api/admin/apps', adminOnly, upload.single('apk'), (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'Выберите APK-файл' });
  const catalog = readCatalog();
  const category = catalog.categories.find(c => c.id === req.body.categoryId);
  if (!category) {
    fs.unlinkSync(req.file.path);
    return res.status(400).json({ error: 'Выберите существующую категорию' });
  }
  const name = String(req.body.name || path.basename(req.file.originalname, '.apk')).trim();
  const item = {
    id: crypto.randomUUID(), name, categoryId: category.id,
    version: String(req.body.version || '').trim(),
    description: String(req.body.description || '').trim(),
    fileName: path.basename(req.file.originalname), diskName: req.file.filename,
    size: req.file.size, createdAt: new Date().toISOString()
  };
  catalog.apps.push(item);
  saveCatalog(catalog);
  const { diskName, ...publicItem } = item;
  res.status(201).json(publicItem);
});

app.delete('/api/admin/apps/:id', adminOnly, (req, res) => {
  const catalog = readCatalog();
  const index = catalog.apps.findIndex(a => a.id === req.params.id);
  if (index < 0) return res.status(404).json({ error: 'Приложение не найдено' });
  const [item] = catalog.apps.splice(index, 1);
  const file = path.join(UPLOAD_DIR, path.basename(item.diskName));
  if (fs.existsSync(file)) fs.unlinkSync(file);
  saveCatalog(catalog);
  res.json({ ok: true });
});

app.use((err, _req, res, _next) => {
  const status = err instanceof multer.MulterError ? 400 : 400;
  res.status(status).json({ error: err.message || 'Ошибка запроса' });
});

app.listen(PORT, '0.0.0.0', () => console.log('HELP server listening on ' + PORT));
