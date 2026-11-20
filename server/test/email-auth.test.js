'use strict';

const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createApp } = require('../src/app');

function createFakeMailer() {
  const sent = [];
  return {
    enabled: true,
    ttlMinutes: 10,
    sent,
    async sendCode({ to, code, device }) {
      sent.push({ to, code, device });
      return { ok: true };
    },
    async verify() {
      return { ok: true };
    },
  };
}

let app;
let base;
let dataDir;
let mailer;

async function start(options = {}) {
  const dir = options.dataDir || fs.mkdtempSync(path.join(os.tmpdir(), 'ultrachat-email-'));
  const fake = options.mailer || createFakeMailer();
  const instance = createApp({
    dataDir: dir,
    apkPath: path.join(dir, 'missing.apk'),
    mailer: fake,
    ...options,
  });
  const address = await instance.listen(0, '127.0.0.1');
  return {
    app: instance,
    dir,
    mailer: fake,
    base: `http://127.0.0.1:${address.port}`,
  };
}

function post(url, payload) {
  return fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

async function requestCode(baseUrl, email, extra = {}) {
  const response = await post(`${baseUrl}/api/auth/request-code`, { email, ...extra });
  return { status: response.status, body: await response.json() };
}

async function verifyCode(baseUrl, email, code, extra = {}) {
  const response = await post(`${baseUrl}/api/auth/verify-code`, { email, code, ...extra });
  return { status: response.status, body: await response.json() };
}

before(async () => {
  const started = await start();
  app = started.app;
  base = started.base;
  dataDir = started.dir;
  mailer = started.mailer;
});

after(async () => {
  await app.close();
  fs.rmSync(dataDir, { recursive: true, force: true });
});

test('request-code: пустая и кривая почта отклоняются', async () => {
  for (const email of ['', 'not-an-email', 'a@b', 'a@@b.ru', 'a b@c.ru']) {
    const result = await requestCode(base, email);
    assert.equal(result.status, 400, email);
    assert.equal(result.body.error, 'invalid_email');
  }
});

test('request-code: письмо уходит, код из 6 цифр', async () => {
  const email = 'first@example.com';
  const result = await requestCode(base, email, { device: 'Windows · Chrome' });
  assert.equal(result.status, 200, JSON.stringify(result.body));
  assert.equal(result.body.ok, true);
  assert.equal(result.body.ttlMinutes, 10);
  assert.equal(result.body.devCode, undefined, 'код не должен уходить клиенту');

  const message = mailer.sent.find((item) => item.to === email);
  assert.ok(message, 'письмо отправлено');
  assert.match(message.code, /^\d{6}$/);
  assert.equal(message.device, 'Windows · Chrome');
});

test('verify-code: неверный код не пускает и считает попытки', async () => {
  const email = 'wrong@example.com';
  await requestCode(base, email);
  const sent = mailer.sent.find((item) => item.to === email);
  const wrong = String((Number(sent.code) + 1) % 1_000_000).padStart(6, '0');

  const result = await verifyCode(base, email, wrong);
  assert.equal(result.status, 400);
  assert.equal(result.body.error, 'invalid_code');
  assert.equal(result.body.attemptsLeft, 4);
});

test('verify-code: правильный код создаёт аккаунт и рабочий токен', async () => {
  const email = 'login@example.com';
  await requestCode(base, email, { device: 'Test device' });
  const sent = mailer.sent.find((item) => item.to === email);

  const result = await verifyCode(base, email, sent.code, { deviceId: 'email-device-1' });
  assert.equal(result.status, 200, JSON.stringify(result.body));
  assert.equal(result.body.ok, true);
  assert.equal(result.body.created, true);
  assert.equal(result.body.email, email);
  assert.ok(result.body.token);
  assert.ok(result.body.userId);
  assert.ok(result.body.nickname, 'ник подставлен из почты');

  const roster = await fetch(`${base}/api/roster?token=${encodeURIComponent(result.body.token)}`);
  assert.equal(roster.status, 200, 'токен принимается обычными запросами');
});

test('verify-code: повторный вход в тот же аккаунт, старый токен гаснет', async () => {
  // Отдельный экземпляр с нулевым кулдауном: два письма подряд без ожидания.
  const repeatApp = await start({ authCooldownMs: 0 });
  try {
    const email = 'repeat@example.com';
    await requestCode(repeatApp.base, email);
    const first = repeatApp.mailer.sent.find((item) => item.to === email);
    const login1 = await verifyCode(repeatApp.base, email, first.code);
    assert.equal(login1.status, 200, JSON.stringify(login1.body));

    await requestCode(repeatApp.base, email);
    const second = repeatApp.mailer.sent.filter((item) => item.to === email).pop();
    const login2 = await verifyCode(repeatApp.base, email, second.code);
    assert.equal(login2.status, 200, JSON.stringify(login2.body));
    assert.equal(login2.body.userId, login1.body.userId, 'тот же аккаунт');
    assert.notEqual(login2.body.token, login1.body.token);

    const oldRoster = await fetch(`${repeatApp.base}/api/roster?token=${encodeURIComponent(login1.body.token)}`);
    assert.equal(oldRoster.status, 401, 'старая сессия разлогинена');

    const newRoster = await fetch(`${repeatApp.base}/api/roster?token=${encodeURIComponent(login2.body.token)}`);
    assert.equal(newRoster.status, 200, 'новая сессия работает');
  } finally {
    await repeatApp.app.close();
    fs.rmSync(repeatApp.dir, { recursive: true, force: true });
  }
});

test('verify-code: после 5 неверных попыток код сгорает', async () => {
  const email = 'bruteforce@example.com';
  await requestCode(base, email);
  const sent = mailer.sent.find((item) => item.to === email);
  const wrong = String((Number(sent.code) + 7) % 1_000_000).padStart(6, '0');

  for (let i = 0; i < 5; i += 1) {
    const attempt = await verifyCode(base, email, wrong);
    assert.equal(attempt.status, 400);
  }
  const last = await verifyCode(base, email, sent.code);
  assert.equal(last.status, 400);
  assert.equal(last.body.error, 'code_not_requested', 'код удалён после лимита');
});

test('request-code: повторная отправка в течение минуты — 429', async () => {
  const email = 'flood@example.com';
  const first = await requestCode(base, email);
  assert.equal(first.status, 200);
  const second = await requestCode(base, email);
  assert.equal(second.status, 429);
  assert.equal(second.body.error, 'rate_limited');
  assert.ok(second.body.retryAfter > 0);
});

test('verify-code: просроченный код отклоняется', async () => {
  const shortApp = await start({ authTtlMs: 60 });
  try {
    const email = 'expired@example.com';
    await requestCode(shortApp.base, email);
    const sent = shortApp.mailer.sent.find((item) => item.to === email);
    await new Promise((resolve) => setTimeout(resolve, 120));
    const result = await verifyCode(shortApp.base, email, sent.code);
    assert.equal(result.status, 400);
    assert.equal(result.body.error, 'code_expired');
  } finally {
    await shortApp.app.close();
    fs.rmSync(shortApp.dir, { recursive: true, force: true });
  }
});

test('почта нормализуется: регистр и пробелы не важны', async () => {
  await requestCode(base, '  MiXeD@Example.COM  ');
  const sent = mailer.sent.find((item) => item.to === 'mixed@example.com');
  assert.ok(sent, 'письмо ушло на нижний регистр');

  const result = await verifyCode(base, 'MIXED@example.com', sent.code);
  assert.equal(result.status, 200, JSON.stringify(result.body));
  assert.equal(result.body.email, 'mixed@example.com');
});

test('без настроенного SMTP сервер честно отвечает 503', async () => {
  const noMail = await start({
    mailer: {
      enabled: false,
      ttlMinutes: 10,
      async sendCode() { throw new Error('should not be called'); },
      async verify() { return { ok: false }; },
    },
  });
  try {
    const result = await requestCode(noMail.base, 'nomail@example.com');
    assert.equal(result.status, 503);
    assert.equal(result.body.error, 'mail_not_configured');
  } finally {
    await noMail.app.close();
    fs.rmSync(noMail.dir, { recursive: true, force: true });
  }
});
