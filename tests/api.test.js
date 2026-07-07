const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');

const { createApp } = require('../src/app');

function createTestServer() {
  const storageDir = fs.mkdtempSync(path.join(os.tmpdir(), 'week-planner-storage-'));
  const uploadDir = path.join(storageDir, 'uploads');
  const app = createApp({
    storageDir,
    uploadDir,
    accessSecret: `access-${crypto.randomUUID()}`,
    refreshSecret: `refresh-${crypto.randomUUID()}`,
  });

  const server = app.listen(0);
  const address = server.address();

  return {
    storageDir,
    uploadDir,
    baseUrl: `http://127.0.0.1:${address.port}`,
    close: async () => {
      await new Promise((resolve, reject) => server.close((error) => (error ? reject(error) : resolve())));
      fs.rmSync(storageDir, { recursive: true, force: true });
    },
  };
}

async function jsonRequest(baseUrl, pathname, { token, headers, ...options } = {}) {
  const response = await fetch(`${baseUrl}${pathname}`, {
    ...options,
    headers: {
      ...(headers || {}),
      ...(token ? { authorization: 'Bearer ' + token } : {}),
    },
  });

  const contentType = response.headers.get('content-type') || '';
  const payload = contentType.includes('application/json') ? await response.json() : await response.text();
  return { response, payload };
}

test('register, login and fetch current user', async () => {
  const server = createTestServer();

  try {
    const register = await jsonRequest(server.baseUrl, '/api/auth/register', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ name: 'Test User', email: 'user@example.com', password: 'secret123' }),
    });

    assert.equal(register.response.status, 201);
    assert.equal(register.payload.user.email, 'user@example.com');
    assert.ok(register.payload.tokens.accessToken);
    assert.ok(register.payload.tokens.refreshToken);

    const login = await jsonRequest(server.baseUrl, '/api/auth/login', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ email: 'user@example.com', password: 'secret123' }),
    });

    assert.equal(login.response.status, 200);

    const me = await jsonRequest(server.baseUrl, '/api/auth/me', {
      method: 'GET',
      token: login.payload.tokens.accessToken,
    });

    assert.equal(me.response.status, 200);
    assert.equal(me.payload.name, 'Test User');
  } finally {
    await server.close();
  }
});

test('upload images and manage weekly plan slots', async () => {
  const server = createTestServer();

  try {
    const register = await jsonRequest(server.baseUrl, '/api/auth/register', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ name: 'Planner User', email: 'planner@example.com', password: 'secret123' }),
    });
    const token = register.payload.tokens.accessToken;

    const form = new FormData();
    form.append('name', 'School');
    form.append('tags[]', 'weekday');
    form.append('tags[]', 'routine');
    form.append('image', new File([Buffer.from('fake-image-data')], 'school.png', { type: 'image/png' }));

    const upload = await jsonRequest(server.baseUrl, '/api/images', {
      method: 'POST',
      token,
      body: form,
    });

    assert.equal(upload.response.status, 201);
    assert.deepEqual(upload.payload.tags, ['weekday', 'routine']);

    const images = await jsonRequest(server.baseUrl, '/api/images?tags=weekday', {
      method: 'GET',
      token,
    });

    assert.equal(images.response.status, 200);
    assert.equal(images.payload.length, 1);

    const slot = await jsonRequest(server.baseUrl, '/api/plans/slot', {
      method: 'PUT',
      token,
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        weekStart: '2026-07-06',
        dayIndex: 1,
        slotIndex: 0,
        imageId: upload.payload.id,
      }),
    });

    assert.equal(slot.response.status, 201);
    assert.equal(slot.payload.imageId, upload.payload.id);

    const plans = await jsonRequest(server.baseUrl, '/api/plans?weekStart=2026-07-06', {
      method: 'GET',
      token,
    });

    assert.equal(plans.response.status, 200);
    assert.equal(plans.payload.slots.length, 1);
    assert.equal(plans.payload.slots[0].image.id, upload.payload.id);

    const deleted = await jsonRequest(server.baseUrl, `/api/plans/slot/${slot.payload.id}`, {
      method: 'DELETE',
      token,
    });

    assert.equal(deleted.response.status, 200);

    const removedImage = await jsonRequest(server.baseUrl, `/api/images/${upload.payload.id}`, {
      method: 'DELETE',
      token,
    });

    assert.equal(removedImage.response.status, 200);

    const remainingImages = await jsonRequest(server.baseUrl, '/api/images', {
      method: 'GET',
      token,
    });

    assert.equal(remainingImages.response.status, 200);
    assert.equal(remainingImages.payload.length, 0);
  } finally {
    await server.close();
  }
});
