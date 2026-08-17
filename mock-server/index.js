/**
 * Zero-dependency mock API for reference-data validation.
 *
 * Endpoints:
 *   GET /v1/field/{fieldKey}
 *       -> 200 { fieldKey, label, dataType, dataSourceUniqueName }
 *       -> 404 { code: "FIELD_NOT_FOUND" }
 *   GET /v1/data_source/dictionary/{uniqueName}/validation?value={value}
 *       -> 200 { code: "PASS" } | { code: "FAIL" }
 *       -> 404 { code: "DICTIONARY_NOT_FOUND" }
 *
 * Fault injection (drives the upstream.* scenarios in the validation fixtures):
 *   value=__TIMEOUT__      hangs until the caller times out
 *   value=__UNAVAILABLE__  responds 503
 */

import { createServer } from 'node:http';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const DATA = JSON.parse(readFileSync(join(HERE, 'data.json'), 'utf8'));
const PORT = Number(process.env.PORT ?? 8081);

const FIELD_PATH = /^\/v1\/field\/([^/]+)$/;
const DICTIONARY_PATH = /^\/v1\/data_source\/dictionary\/([^/]+)\/validation$/;

/** Writes a JSON body so every response shares one content-type and encoding. */
function sendJson(response, status, body) {
  const payload = JSON.stringify(body);
  response.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(payload)
  });
  response.end(payload);
}

/** Resolves a field's metadata; the data source name is one of its properties. */
function handleField(response, fieldKey) {
  const field = DATA.fields[fieldKey];
  if (!field) {
    sendJson(response, 404, { code: 'FIELD_NOT_FOUND', fieldKey });
    return;
  }
  sendJson(response, 200, field);
}

/**
 * Answers whether a value exists in a dictionary. Reserved sentinel values simulate upstream
 * faults so the caller's timeout and unavailable paths stay testable without extra tooling.
 */
function handleDictionaryValidation(response, uniqueName, value) {
  if (value === '__TIMEOUT__') return; // never respond

  if (value === '__UNAVAILABLE__') {
    sendJson(response, 503, { code: 'SERVICE_UNAVAILABLE' });
    return;
  }

  const entries = DATA.dictionaries[uniqueName];
  if (!entries) {
    sendJson(response, 404, { code: 'DICTIONARY_NOT_FOUND', uniqueName });
    return;
  }
  sendJson(response, 200, { code: entries.includes(value) ? 'PASS' : 'FAIL' });
}

const server = createServer((request, response) => {
  const url = new URL(request.url, `http://${request.headers.host ?? 'localhost'}`);

  if (request.method !== 'GET') {
    sendJson(response, 405, { code: 'METHOD_NOT_ALLOWED' });
    return;
  }

  const fieldMatch = FIELD_PATH.exec(url.pathname);
  if (fieldMatch) {
    handleField(response, decodeURIComponent(fieldMatch[1]));
    return;
  }

  const dictionaryMatch = DICTIONARY_PATH.exec(url.pathname);
  if (dictionaryMatch) {
    handleDictionaryValidation(
      response,
      decodeURIComponent(dictionaryMatch[1]),
      url.searchParams.get('value') ?? ''
    );
    return;
  }

  sendJson(response, 404, { code: 'NOT_FOUND' });
});

server.listen(PORT, () => {
  process.stdout.write(`mock api listening on http://localhost:${PORT}\n`);
});
