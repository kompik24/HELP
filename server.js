const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const multer = require('multer');
const crypto = require('crypto');
const path = require('path');
const { createClient } = require('@supabase/supabase-js');

const app = express();
const PORT = Number(process.env.PORT || 3000);
const ADMIN_KEY = process.env.HELP_ADMIN_KEY || '';
const SUPABASE_URL = process.env.SUPABASE_URL || '';
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY || '';
const BUCKET = process.env.SUPABASE_BUCKET || 'help-apks';
const supabase = SUPABASE_URL && SUPABASE_SERVICE_ROLE_KEY
  ? createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, { auth: { persistSession: false } })
  : null;

app.use(helmet({ contentSecurityPolicy: false, crossOriginResourcePolicy: { policy: 'cross-origin' } }));
app.use(cors());
app.use(express.json({ limit: '1mb' }));
app.use(express.static(path.join(__dirname, 'public')));

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 50 * 1024 * 1024 },
  fileFilter: (_req, file, cb) => {
    if (!file.originalname.toLowerCase().endsWith('.apk')) return cb(new Error('Разрешены только APK-файлы'));
    cb(null, true);
  }
});

function requireStorage(_req, res, next) {
  if (!supabase) return res.status(503).json({ error: 'Не настроено постоянное хранилище Supabase: задайте SUPABASE_URL и SUPABASE_SERVICE_ROLE_KEY' });
  next();
}
function adminOnly(req, res, next) {
  if (!ADMIN_KEY) return res.status(503).json({ error: 'На сервере не задан HELP_ADMIN_KEY' });
  const supplied = req.get('x-admin-key') || req.body?.adminKey || '';
  if (supplied !== ADMIN_KEY) return res.status(401).json({ error: 'Неверный ключ администратора' });
  next();
}
function fail(res, error) {
  console.error(error);
  return res.status(500).json({ error: error.message || 'Ошибка базы данных Supabase' });
}
async function getCatalog() {
  const [cats, apps] = await Promise.all([
    supabase.from('categories').select('id,name').order('name'),
    supabase.from('apps').select('id,name,category_id,version,description,file_name,storage_path,size,created_at').order('created_at', { ascending: false })
  ]);
  if (cats.error) throw cats.error;
  if (apps.error) throw apps.error;
  return { categories: cats.data || [], apps: apps.data || [] };
}

app.get('/api/health', (_req, res) => res.json({
  ok: true, service: 'HELP', version: '1.1.0',
  persistentStorageConfigured: Boolean(supabase)
}));

app.get('/api/categories', requireStorage, async (_req, res) => {
  try {
    const { categories, apps } = await getCatalog();
    res.json(categories.map(c => ({ id: c.id, name: c.name, appCount: apps.filter(a => a.category_id === c.id).length })));
  } catch (e) { fail(res, e); }
});

app.get('/api/apps', requireStorage, async (req, res) => {
  try {
    const { apps } = await getCatalog();
    res.json(apps.filter(a => !req.query.categoryId || a.category_id === req.query.categoryId)
      .map(a => ({
        id: a.id, name: a.name, categoryId: a.category_id, version: a.version,
        description: a.description, fileName: a.file_name, size: Number(a.size),
        createdAt: a.created_at, downloadUrl: '/api/apps/' + encodeURIComponent(a.id) + '/download'
      })));
  } catch (e) { fail(res, e); }
});

app.get('/api/apps/:id/download', requireStorage, async (req, res) => {
  try {
    const { data: item, error } = await supabase.from('apps').select('*').eq('id', req.params.id).maybeSingle();
    if (error) throw error;
    if (!item) return res.status(404).json({ error: 'Приложение не найдено' });
    const { data, error: downloadError } = await supabase.storage.from(BUCKET).download(item.storage_path);
    if (downloadError) throw downloadError;
    res.setHeader('Content-Type', 'application/vnd.android.package-archive');
    res.setHeader('Content-Disposition', 'attachment; filename="' + encodeURIComponent(item.file_name) + '"');
    res.setHeader('Content-Length', String(Number(item.size)));
    data.stream().pipe(res);
  } catch (e) { fail(res, e); }
});

app.post('/api/admin/categories', requireStorage, adminOnly, async (req, res) => {
  try {
    const name = String(req.body.name || '').trim();
    if (!name || name.length > 50) return res.status(400).json({ error: 'Название категории: от 1 до 50 символов' });
    const { data: existing, error: lookupError } = await supabase.from('categories').select('id').ilike('name', name).limit(1);
    if (lookupError) throw lookupError;
    if (existing.length) return res.status(409).json({ error: 'Такая категория уже есть' });
    const category = { id: crypto.randomUUID(), name };
    const { error } = await supabase.from('categories').insert(category);
    if (error) throw error;
    res.status(201).json(category);
  } catch (e) { fail(res, e); }
});

app.post('/api/admin/apps', requireStorage, adminOnly, upload.single('apk'), async (req, res) => {
  let uploadedPath = null;
  try {
    if (!req.file) return res.status(400).json({ error: 'Выберите APK-файл' });
    const { data: category, error: categoryError } = await supabase.from('categories').select('id').eq('id', req.body.categoryId).maybeSingle();
    if (categoryError) throw categoryError;
    if (!category) return res.status(400).json({ error: 'Выберите существующую категорию' });
    const id = crypto.randomUUID();
    uploadedPath = id + '.apk';
    const { error: uploadError } = await supabase.storage.from(BUCKET).upload(uploadedPath, req.file.buffer, {
      contentType: 'application/vnd.android.package-archive', upsert: false
    });
    if (uploadError) throw uploadError;
    const item = {
      id, name: String(req.body.name || path.basename(req.file.originalname, '.apk')).trim(),
      category_id: category.id, version: String(req.body.version || '').trim(),
      description: String(req.body.description || '').trim(),
      file_name: path.basename(req.file.originalname), storage_path: uploadedPath,
      size: req.file.size, created_at: new Date().toISOString()
    };
    const { error: insertError } = await supabase.from('apps').insert(item);
    if (insertError) throw insertError;
    res.status(201).json({
      id: item.id, name: item.name, categoryId: item.category_id, version: item.version,
      description: item.description, fileName: item.file_name, size: item.size, createdAt: item.created_at
    });
  } catch (e) {
    if (uploadedPath) await supabase.storage.from(BUCKET).remove([uploadedPath]);
    fail(res, e);
  }
});

app.delete('/api/admin/apps/:id', requireStorage, adminOnly, async (req, res) => {
  try {
    const { data: item, error: lookupError } = await supabase.from('apps').select('storage_path').eq('id', req.params.id).maybeSingle();
    if (lookupError) throw lookupError;
    if (!item) return res.status(404).json({ error: 'Приложение не найдено' });
    const { error: deleteError } = await supabase.from('apps').delete().eq('id', req.params.id);
    if (deleteError) throw deleteError;
    const { error: storageError } = await supabase.storage.from(BUCKET).remove([item.storage_path]);
    if (storageError) console.error('Не удалось удалить APK из Storage:', storageError);
    res.json({ ok: true });
  } catch (e) { fail(res, e); }
});

app.use((err, _req, res, _next) => {
  res.status(400).json({ error: err.message || 'Ошибка запроса' });
});

app.listen(PORT, '0.0.0.0', () => console.log('HELP server listening on ' + PORT));
