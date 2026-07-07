const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const express = require('express');
const cors = require('cors');
const multer = require('multer');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { OAuth2Client } = require('google-auth-library');

const DEFAULT_DATA = { users: [], images: [], plans: [] };
const ACCESS_TOKEN_TTL = '1h';
const REFRESH_TOKEN_TTL = '30d';
const GOOGLE_CLIENT = new OAuth2Client();

function createApp(overrides = {}) {
  const config = resolveConfig(overrides);
  const store = createStore(config.storageDir);
  ensureDirectory(config.uploadDir);
  const apiRateLimiter = createRateLimiter({ windowMs: 60 * 1000, maxRequests: 120 });
  const authRateLimiter = createRateLimiter({ windowMs: 15 * 60 * 1000, maxRequests: 20 });

  const upload = multer({
    storage: multer.diskStorage({
      destination: (_req, _file, callback) => callback(null, config.uploadDir),
      filename: (_req, file, callback) => {
        const extension = path.extname(file.originalname) || '.bin';
        callback(null, `${crypto.randomUUID()}${extension.toLowerCase()}`);
      },
    }),
    limits: {
      fileSize: 5 * 1024 * 1024,
      files: 1,
      fields: 20,
    },
    fileFilter: (_req, file, callback) => {
      callback(null, file.mimetype.startsWith('image/'));
    },
  });

  const app = express();
  app.use(cors());
  app.use(express.json({ limit: '1mb' }));
  app.use('/api', apiRateLimiter);
  app.use('/uploads', express.static(config.uploadDir));

  app.get('/api/health', (_req, res) => {
    res.json({ ok: true });
  });

  app.post('/api/auth/register', authRateLimiter, async (req, res, next) => {
    try {
      const { name, email, password } = req.body || {};
      if (!name || !email || !password) {
        return res.status(400).json({ error: 'name, email and password are required' });
      }
      const normalizedEmail = normalizeEmail(email);
      const data = store.read();
      if (data.users.some((user) => user.email === normalizedEmail)) {
        return res.status(409).json({ error: 'Email is already registered' });
      }

      const user = {
        id: crypto.randomUUID(),
        name: String(name).trim(),
        email: normalizedEmail,
        passwordHash: await bcrypt.hash(String(password), 10),
        refreshTokens: [],
        googleSub: null,
        createdAt: new Date().toISOString(),
      };

      data.users.push(user);
      const tokens = issueTokens(user, config);
      user.refreshTokens.push(tokens.refreshToken);
      store.write(data);

      res.status(201).json({ user: toPublicUser(user), tokens });
    } catch (error) {
      next(error);
    }
  });

  app.post('/api/auth/login', authRateLimiter, async (req, res, next) => {
    try {
      const { email, password } = req.body || {};
      if (!email || !password) {
        return res.status(400).json({ error: 'email and password are required' });
      }

      const data = store.read();
      const user = data.users.find((candidate) => candidate.email === normalizeEmail(email));
      if (!user?.passwordHash || !(await bcrypt.compare(String(password), user.passwordHash))) {
        return res.status(401).json({ error: 'Invalid email or password' });
      }

      const tokens = issueTokens(user, config);
      user.refreshTokens = [...new Set([...(user.refreshTokens || []), tokens.refreshToken])];
      store.write(data);

      res.json({ user: toPublicUser(user), tokens });
    } catch (error) {
      next(error);
    }
  });

  app.post('/api/auth/google', authRateLimiter, async (req, res, next) => {
    try {
      const { credential } = req.body || {};
      if (!credential) {
        return res.status(400).json({ error: 'credential is required' });
      }
      if (!config.googleClientId) {
        return res.status(503).json({ error: 'Google OAuth is not configured' });
      }

      const ticket = await GOOGLE_CLIENT.verifyIdToken({
        idToken: credential,
        audience: config.googleClientId,
      });
      const payload = ticket.getPayload();
      if (!payload?.email || !payload.sub) {
        return res.status(401).json({ error: 'Invalid Google credential' });
      }

      const normalizedEmail = normalizeEmail(payload.email);
      const data = store.read();
      let user = data.users.find(
        (candidate) => candidate.googleSub === payload.sub || candidate.email === normalizedEmail,
      );

      if (!user) {
        user = {
          id: crypto.randomUUID(),
          name: payload.name || normalizedEmail.split('@')[0],
          email: normalizedEmail,
          passwordHash: null,
          refreshTokens: [],
          googleSub: payload.sub,
          createdAt: new Date().toISOString(),
        };
        data.users.push(user);
      } else {
        user.googleSub = payload.sub;
      }

      const tokens = issueTokens(user, config);
      user.refreshTokens = [...new Set([...(user.refreshTokens || []), tokens.refreshToken])];
      store.write(data);

      res.json({ user: toPublicUser(user), tokens });
    } catch (_error) {
      return res.status(401).json({ error: 'Invalid Google credential' });
    }
  });

  app.post('/api/auth/logout', requireAuth(store, config), (req, res) => {
    const data = store.read();
    const user = data.users.find((candidate) => candidate.id === req.user.id);
    if (user) {
      user.refreshTokens = [];
      store.write(data);
    }
    res.status(200).json({ success: true });
  });

  app.get('/api/auth/me', requireAuth(store, config), (req, res) => {
    res.json(toPublicUser(req.user));
  });

  app.get('/api/images', requireAuth(store, config), (req, res) => {
    const requestedTags = String(req.query.tags || '')
      .split(',')
      .map((tag) => tag.trim())
      .filter(Boolean);

    const images = store
      .read()
      .images.filter((image) => image.userId === req.user.id)
      .filter((image) => requestedTags.every((tag) => image.tags.includes(tag)))
      .map((image) => toPublicImage(req, image));

    res.json(images);
  });

  app.post('/api/images', requireAuth(store, config), upload.single('image'), (req, res, next) => {
    try {
      if (!req.file) {
        return res.status(400).json({ error: 'image is required' });
      }
      const name = String(req.body?.name || '').trim();
      if (!name) {
        void safeUnlink(req.file.path);
        return res.status(400).json({ error: 'name is required' });
      }

      const image = {
        id: crypto.randomUUID(),
        userId: req.user.id,
        name,
        tags: normalizeTags(extractTags(req.body)),
        filename: req.file.filename,
        mimeType: req.file.mimetype,
        createdAt: new Date().toISOString(),
      };

      const data = store.read();
      data.images.push(image);
      store.write(data);
      res.status(201).json(toPublicImage(req, image));
    } catch (error) {
      next(error);
    }
  });

  app.patch('/api/images/:id', requireAuth(store, config), (req, res) => {
    const data = store.read();
    const image = data.images.find((candidate) => candidate.id === req.params.id && candidate.userId === req.user.id);
    if (!image) {
      return res.status(404).json({ error: 'Image not found' });
    }

    if (req.body?.name !== undefined) {
      const name = String(req.body.name).trim();
      if (!name) {
        return res.status(400).json({ error: 'name cannot be empty' });
      }
      image.name = name;
    }

    if (req.body?.tags !== undefined) {
      image.tags = normalizeTags(req.body.tags);
    }

    store.write(data);
    res.json(toPublicImage(req, image));
  });

  app.delete('/api/images/:id', requireAuth(store, config), async (req, res, next) => {
    try {
      const data = store.read();
      const index = data.images.findIndex((candidate) => candidate.id === req.params.id && candidate.userId === req.user.id);
      if (index === -1) {
        return res.status(404).json({ error: 'Image not found' });
      }

      const [removedImage] = data.images.splice(index, 1);
      data.plans = data.plans.filter((slot) => slot.imageId !== removedImage.id || slot.userId !== req.user.id);
      store.write(data);
      await safeUnlink(resolveUploadPath(config.uploadDir, removedImage.filename));

      res.status(200).json({ success: true });
    } catch (error) {
      next(error);
    }
  });

  app.get('/api/plans', requireAuth(store, config), (req, res) => {
    const weekStart = validateWeekStart(req.query.weekStart || getCurrentWeekStart());
    if (!weekStart) {
      return res.status(400).json({ error: 'weekStart must be YYYY-MM-DD' });
    }

    const data = store.read();
    const slots = data.plans
      .filter((slot) => slot.userId === req.user.id && slot.weekStart === weekStart)
      .sort((left, right) => left.dayIndex - right.dayIndex || left.slotIndex - right.slotIndex)
      .map((slot) => toPublicSlot(req, slot, data.images));

    res.json({ weekStart, slots });
  });

  app.put('/api/plans/slot', requireAuth(store, config), (req, res) => {
    const weekStart = validateWeekStart(req.body?.weekStart);
    const dayIndex = Number(req.body?.dayIndex);
    const slotIndex = Number(req.body?.slotIndex);
    const imageId = String(req.body?.imageId || '');

    if (!weekStart || !Number.isInteger(dayIndex) || dayIndex < 0 || dayIndex > 6 || !Number.isInteger(slotIndex) || slotIndex < 0 || !imageId) {
      return res.status(400).json({ error: 'weekStart, dayIndex, slotIndex and imageId are required' });
    }

    const data = store.read();
    const image = data.images.find((candidate) => candidate.id === imageId && candidate.userId === req.user.id);
    if (!image) {
      return res.status(404).json({ error: 'Image not found' });
    }

    const existing = data.plans.find(
      (slot) =>
        slot.userId === req.user.id &&
        slot.weekStart === weekStart &&
        slot.dayIndex === dayIndex &&
        slot.slotIndex === slotIndex,
    );

    if (existing) {
      existing.imageId = imageId;
      existing.updatedAt = new Date().toISOString();
      store.write(data);
      return res.json(toPublicSlot(req, existing, data.images));
    }

    const slot = {
      id: crypto.randomUUID(),
      userId: req.user.id,
      weekStart,
      dayIndex,
      slotIndex,
      imageId,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    data.plans.push(slot);
    store.write(data);
    res.status(201).json(toPublicSlot(req, slot, data.images));
  });

  app.delete('/api/plans/slot/:id', requireAuth(store, config), (req, res) => {
    const data = store.read();
    const index = data.plans.findIndex((candidate) => candidate.id === req.params.id && candidate.userId === req.user.id);
    if (index === -1) {
      return res.status(404).json({ error: 'Plan slot not found' });
    }

    data.plans.splice(index, 1);
    store.write(data);
    res.status(200).json({ success: true });
  });

  app.use((error, _req, res, _next) => {
    if (error instanceof multer.MulterError) {
      return res.status(400).json({ error: error.message });
    }
    if (error && process.env.NODE_ENV !== 'production') {
      console.error(error);
    }
    if (error) {
      return res.status(500).json({ error: 'Internal server error' });
    }
    return res.status(500).json({ error: 'Internal server error' });
  });

  return app;
}

function createStore(storageDir) {
  ensureDirectory(storageDir);
  const dataFile = path.join(storageDir, 'data.json');
  if (!fs.existsSync(dataFile)) {
    fs.writeFileSync(dataFile, JSON.stringify(DEFAULT_DATA, null, 2));
  }

  return {
    read() {
      return JSON.parse(fs.readFileSync(dataFile, 'utf8'));
    },
    write(data) {
      const tempFile = `${dataFile}.tmp`;
      fs.writeFileSync(tempFile, JSON.stringify(data, null, 2));
      fs.renameSync(tempFile, dataFile);
    },
  };
}

function requireAuth(store, config) {
  return (req, res, next) => {
    try {
      const header = req.get('authorization');
      if (!header || !header.startsWith('Bearer ')) {
        return res.status(401).json({ error: 'Authorization header is required' });
      }

      const token = header.slice('Bearer '.length);
      const payload = jwt.verify(token, config.accessSecret);
      const user = store.read().users.find((candidate) => candidate.id === payload.sub);
      if (!user) {
        return res.status(401).json({ error: 'Invalid token' });
      }

      req.user = user;
      next();
    } catch (_error) {
      res.status(401).json({ error: 'Invalid token' });
    }
  };
}

function issueTokens(user, config) {
  const basePayload = { sub: user.id, email: user.email };
  return {
    accessToken: jwt.sign(basePayload, config.accessSecret, { expiresIn: ACCESS_TOKEN_TTL }),
    refreshToken: jwt.sign(basePayload, config.refreshSecret, { expiresIn: REFRESH_TOKEN_TTL }),
  };
}

function toPublicUser(user) {
  return {
    id: user.id,
    name: user.name,
    email: user.email,
    createdAt: user.createdAt,
  };
}

function toPublicImage(req, image) {
  return {
    id: image.id,
    name: image.name,
    tags: image.tags,
    url: absoluteUrl(req, `/uploads/${image.filename}`),
    createdAt: image.createdAt,
  };
}

function toPublicSlot(req, slot, images) {
  const image = images.find((candidate) => candidate.id === slot.imageId && candidate.userId === slot.userId) || null;
  return {
    id: slot.id,
    weekStart: slot.weekStart,
    dayIndex: slot.dayIndex,
    slotIndex: slot.slotIndex,
    imageId: slot.imageId,
    image: image ? toPublicImage(req, image) : null,
    createdAt: slot.createdAt,
    updatedAt: slot.updatedAt,
  };
}

function extractTags(body) {
  if (!body) {
    return [];
  }
  if (body.tags !== undefined) {
    return body.tags;
  }
  if (body['tags[]'] !== undefined) {
    return body['tags[]'];
  }
  return [];
}

function normalizeTags(value) {
  const list = Array.isArray(value)
    ? value
    : typeof value === 'string'
      ? value.split(',')
      : [];

  return [...new Set(list.map((tag) => String(tag).trim()).filter(Boolean))];
}

function normalizeEmail(email) {
  return String(email).trim().toLowerCase();
}

function validateWeekStart(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(value || ''))) {
    return null;
  }
  const date = new Date(`${value}T00:00:00.000Z`);
  if (Number.isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== value) {
    return null;
  }
  return value;
}

function getCurrentWeekStart() {
  const current = new Date();
  const utcDay = current.getUTCDay();
  const offset = utcDay === 0 ? -6 : 1 - utcDay;
  current.setUTCDate(current.getUTCDate() + offset);
  return current.toISOString().slice(0, 10);
}

function resolveConfig(overrides) {
  const storageDir = path.resolve(overrides.storageDir || process.env.STORAGE_DIR || path.join(process.cwd(), 'storage'));
  const uploadDir = path.resolve(overrides.uploadDir || path.join(storageDir, 'uploads'));
  const isProduction = (overrides.nodeEnv || process.env.NODE_ENV) === 'production';

  return {
    storageDir,
    uploadDir,
    accessSecret: overrides.accessSecret || process.env.JWT_ACCESS_SECRET || requiredSecretFallback('JWT_ACCESS_SECRET', isProduction),
    refreshSecret:
      overrides.refreshSecret || process.env.JWT_REFRESH_SECRET || requiredSecretFallback('JWT_REFRESH_SECRET', isProduction),
    googleClientId: overrides.googleClientId || process.env.GOOGLE_CLIENT_ID || '',
  };
}

function requiredSecretFallback(name, isProduction) {
  if (isProduction) {
    throw new Error(`${name} must be configured in production`);
  }
  return `local-development-${name.toLowerCase()}`;
}

function ensureDirectory(directoryPath) {
  fs.mkdirSync(directoryPath, { recursive: true });
}

function createRateLimiter({ windowMs, maxRequests }) {
  const hits = new Map();

  return (req, res, next) => {
    const now = Date.now();
    const key = String(req.headers['x-forwarded-for'] || req.ip || 'unknown');
    const current = hits.get(key);

    if (!current || current.resetAt <= now) {
      hits.set(key, { count: 1, resetAt: now + windowMs });
      return next();
    }

    if (current.count >= maxRequests) {
      return res.status(429).json({ error: 'Too many requests' });
    }

    current.count += 1;
    next();
  };
}

function resolveUploadPath(uploadDir, filename) {
  const safeName = path.basename(String(filename || ''));
  const resolvedPath = path.resolve(uploadDir, safeName);
  const uploadRoot = `${path.resolve(uploadDir)}${path.sep}`;

  if (!safeName || safeName !== filename || !resolvedPath.startsWith(uploadRoot)) {
    throw new Error('Invalid upload path');
  }

  return resolvedPath;
}

async function safeUnlink(filePath) {
  try {
    await fsp.unlink(filePath);
  } catch (error) {
    if (error.code !== 'ENOENT') {
      throw error;
    }
  }
}

function absoluteUrl(req, pathname) {
  return `${req.protocol}://${req.get('host')}${pathname}`;
}

module.exports = { createApp };
