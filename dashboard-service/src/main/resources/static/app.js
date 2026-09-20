'use strict';

const byId = id => document.getElementById(id);
const pageSize = 12;
let offset = 0;
let refreshing = false;
let historySelection = null;
let historyOffset = 0;
let historyLoading = false;

function node(tag, value, className) {
  const element = document.createElement(tag);
  if (value !== undefined) element.textContent = value;
  if (className) element.className = className;
  return element;
}

function badge(severity) {
  const safeSeverity = ['info', 'warning', 'critical'].includes(severity) ? severity : 'info';
  return node('span', safeSeverity.toUpperCase(), `badge ${safeSeverity}`);
}

function timestamp(value) {
  return new Date(value).toLocaleString();
}

async function request(path, parameters) {
  const response = await fetch(`${path}?${new URLSearchParams(parameters)}`, {headers: {'Accept': 'application/json'}});
  if (!response.ok) throw new Error(`Dashboard request failed (HTTP ${response.status}).`);
  return response.json();
}

function renderPackages(page) {
  byId('packages').replaceChildren();
  if (!page.items.length) {
    byId('packages').append(node('p', page.total ? 'No packages on this page. Select Previous.' : 'Waiting for scanner events. Start a producer to see observations here.', 'empty'));
  }
  for (const pkg of page.items) {
    const card = node('article', undefined, 'package');
    const heading = node('div', undefined, 'package-title');
    const name = node('button', pkg.packageName, 'package-name');
    name.type = 'button';
    name.addEventListener('click', () => openHistory(pkg));
    heading.append(name, node('span', pkg.ecosystem, 'ecosystem'));
    card.append(heading);
    for (const event of pkg.latestFindings) {
      const finding = node('div', undefined, 'finding');
      const meta = node('div', undefined, 'finding-meta');
      meta.append(node('span', event.source), badge(event.severity));
      finding.append(meta, node('p', event.summary));
      finding.title = timestamp(event.timestamp);
      card.append(finding);
    }
    byId('packages').append(card);
  }
  byId('package-count').textContent = `${page.total} packages observed`;
  byId('page-status').textContent = page.total ? `${offset + 1}–${Math.min(offset + pageSize, page.total)} of ${page.total}` : '0 packages';
  byId('previous').disabled = offset === 0;
  byId('next').disabled = offset + pageSize >= page.total;
}

function renderRecent(page) {
  byId('recent').replaceChildren();
  for (const event of page.items) {
    const row = node('tr');
    const severity = node('td');
    severity.append(badge(event.severity));
    row.append(node('td', timestamp(event.timestamp)), node('td', `${event.packageName} (${event.ecosystem})`),
      node('td', event.source), severity, node('td', event.summary));
    byId('recent').append(row);
  }
  if (!page.items.length) {
    const row = node('tr');
    const empty = node('td', 'No events received yet.');
    empty.colSpan = 5;
    row.append(empty);
    byId('recent').append(row);
  }
}

async function refresh() {
  if (refreshing) return;
  refreshing = true;
  byId('refresh').disabled = true;
  const ecosystem = byId('ecosystem').value;
  const selectedOffset = offset;
  try {
    const [packages, events] = await Promise.all([
      request('/packages', {ecosystem, limit: pageSize, offset: selectedOffset}),
      request('/events/recent', {ecosystem, limit: 30})
    ]);
    // A filter may have changed while the requests were in flight.
    if (ecosystem === byId('ecosystem').value && selectedOffset === offset) {
      renderPackages(packages);
      renderRecent(events);
      byId('refresh-status').textContent = `Updated ${new Date().toLocaleTimeString()}`;
      byId('error').hidden = true;
    }
  } catch (error) {
    byId('error').textContent = `${error.message} Retrying automatically in 10 seconds.`;
    byId('error').hidden = false;
    byId('refresh-status').textContent = 'Connection interrupted';
  } finally {
    refreshing = false;
    byId('refresh').disabled = false;
    if (ecosystem !== byId('ecosystem').value || selectedOffset !== offset) refresh();
  }
}

async function openHistory(pkg) {
  historySelection = pkg;
  historyOffset = 0;
  byId('history-title').textContent = `${pkg.packageName} · ${pkg.ecosystem}`;
  byId('history').replaceChildren();
  byId('history-count').textContent = 'Loading history…';
  byId('more-history').hidden = true;
  if (!byId('history-dialog').open) byId('history-dialog').showModal();
  await loadHistory();
}

async function loadHistory() {
  if (historyLoading || !historySelection) return;
  historyLoading = true;
  const selection = historySelection;
  byId('more-history').disabled = true;
  try {
    const page = await request('/packages/events', {name: selection.packageName, ecosystem: selection.ecosystem, limit: 50, offset: historyOffset});
    if (selection !== historySelection) return;
    for (const event of page.items) {
      const entry = node('article', undefined, 'history-event');
      const meta = node('div', undefined, 'finding-meta');
      meta.append(node('span', `${event.source} · ${timestamp(event.timestamp)}`), badge(event.severity));
      const details = node('details');
      details.append(node('summary', 'Event details'), node('pre', JSON.stringify(event.detail, null, 2)));
      entry.append(meta, node('p', event.summary), details);
      byId('history').append(entry);
    }
    historyOffset += page.items.length;
    byId('history-count').textContent = `Showing ${historyOffset} of ${page.total} events`;
    byId('more-history').hidden = historyOffset >= page.total;
  } catch (error) {
    byId('history-count').textContent = error.message;
    byId('more-history').hidden = false;
  } finally {
    historyLoading = false;
    byId('more-history').disabled = false;
    if (selection !== historySelection && historySelection) loadHistory();
  }
}

byId('refresh').addEventListener('click', refresh);
byId('ecosystem').addEventListener('change', () => {offset = 0; refresh();});
byId('previous').addEventListener('click', () => {offset = Math.max(0, offset - pageSize); refresh();});
byId('next').addEventListener('click', () => {offset += pageSize; refresh();});
byId('close-history').addEventListener('click', () => byId('history-dialog').close());
byId('history-dialog').addEventListener('close', () => {historySelection = null;});
byId('more-history').addEventListener('click', loadHistory);
refresh();
setInterval(refresh, 10000);
