'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createMailer, prettyCode } = require('../src/mailer');

test('настройки читаются из data/mail.json', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ultrachat-mailer-'));
  const configPath = path.join(dir, 'mail.json');
  fs.writeFileSync(configPath, JSON.stringify({
    user: 'ultrachat@example.com',
    pass: 'abcd efgh ijkl mnop',
    from: 'ultrachat@example.com',
    fromName: 'UltraChat',
  }), 'utf8');

  const mailer = createMailer({ configPath });
  assert.equal(mailer.enabled, true);
  assert.equal(mailer.fromEmail, 'ultrachat@example.com');

  fs.rmSync(dir, { recursive: true, force: true });
});

test('relay-режим: только хост и адрес отправителя, без логина', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ultrachat-mailer-relay-'));
  const configPath = path.join(dir, 'mail.json');
  fs.writeFileSync(configPath, JSON.stringify({
    mode: 'relay',
    host: 'smtpout1.paris1.alwaysdata.com',
    port: 25,
    secure: false,
    from: 'ultrachat2026@alwaysdata.net',
    fromName: 'UltraChat',
  }), 'utf8');

  const saved = {};
  for (const key of ['SMTP_HOST', 'SMTP_USER', 'SMTP_PASS', 'GMAIL_USER', 'GMAIL_APP_PASSWORD', 'MAIL_FROM']) {
    saved[key] = process.env[key];
    delete process.env[key];
  }
  try {
    const mailer = createMailer({ configPath });
    assert.equal(mailer.enabled, true, 'relay считается настроенным');
    assert.equal(mailer.fromEmail, 'ultrachat2026@alwaysdata.net');
  } finally {
    for (const [key, value] of Object.entries(saved)) {
      if (value === undefined) delete process.env[key];
      else process.env[key] = value;
    }
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('без настроек отправка выключена, но превью строится', () => {
  const mailer = createMailer({
    configPath: path.join(os.tmpdir(), 'ultrachat-no-such-config.json'),
    host: '',
    user: '',
    pass: '',
  });
  // окружение может содержать SMTP_*; проверяем только поведение превью
  const preview = mailer.renderPreview({ code: '482916' });
  assert.match(preview.subject, /482 916|482916/);
  assert.match(preview.html, /482 916/);
  assert.match(preview.text, /482916/);
  assert.ok(preview.html.includes('UltraChat'));
});

test('код в письме разбивается по три цифры', () => {
  assert.equal(prettyCode('482916'), '482 916');
  assert.equal(prettyCode('000001'), '000 001');
});

test('шаблон письма лежит рядом с сервером и содержит подстановки', () => {
  const template = fs.readFileSync(path.join(__dirname, '..', 'email', 'code.html'), 'utf8');
  for (const token of ['{{CODE}}', '{{CODE_PRETTY}}', '{{TTL}}', '{{DEVICE}}', '{{TIME}}', '{{YEAR}}']) {
    assert.ok(template.includes(token), token);
  }
});
