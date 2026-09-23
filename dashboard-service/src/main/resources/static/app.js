'use strict';

const byId = id => document.getElementById(id);
const pageSize = 12;
let offset = 0;
let refreshing = false;
let historySelection = null;
let historyOffset = 0;
let historyLoading = false;
let repositoryScan = null;
let repositoryPoll = null;
let dependencyOffset = 0;
let repositoryInsightsLoading = false;
const dependencyPageSize = 100;

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

async function submitRepository(event) {
  event.preventDefault();
  const button = byId('repository-submit');
  button.disabled = true;
  byId('repository-error').hidden = true;
  try {
    const response = await fetch('/repositories', {
      method: 'POST', headers: {'Content-Type': 'application/json', 'Accept': 'application/json'},
      body: JSON.stringify({repositoryUrl: byId('repository-url').value.trim()})
    });
    if (!response.ok) {
      const problem = await response.json().catch(() => ({}));
      throw new Error(problem.detail || `Repository request failed (HTTP ${response.status}).`);
    }
    repositoryScan = await response.json();
    dependencyOffset = 0;
    showRepositoryScan(repositoryScan);
    startRepositoryPolling();
    await loadRecentRepositories();
  } catch (error) {
    byId('repository-error').textContent = error.message;
    byId('repository-error').hidden = false;
  } finally { button.disabled = false; }
}

function showRepositoryScan(scan) {
  repositoryScan = scan;
  byId('repository-progress').hidden = false;
  byId('repository-name').textContent = scan.repositoryUrl.replace('https://github.com/', '');
  byId('repository-status').textContent = scan.status.toUpperCase();
  byId('repository-status').className = `status-pill ${scan.status}`;
  byId('repository-message').textContent = scan.message;
  const finished = ['complete', 'partial', 'failed'].includes(scan.status);
  byId('repository-result').hidden = !finished || scan.status === 'failed';
  if (finished && scan.status !== 'failed') {
    loadDependencies();
    loadRepositoryInsights();
  }
  if (finished && repositoryPoll) { clearInterval(repositoryPoll); repositoryPoll = null; }
}

function startRepositoryPolling() {
  if (repositoryPoll) clearInterval(repositoryPoll);
  repositoryPoll = setInterval(async () => {
    if (!repositoryScan) return;
    try {
      const response = await fetch(`/repositories/${encodeURIComponent(repositoryScan.requestId)}`);
      if (!response.ok) throw new Error(`Status request failed (HTTP ${response.status}).`);
      showRepositoryScan(await response.json());
      await loadRecentRepositories();
    } catch (error) {
      byId('repository-error').textContent = error.message;
      byId('repository-error').hidden = false;
    }
  }, 2000);
}

async function loadDependencies() {
  if (!repositoryScan) return;
  try {
    const page = await request(`/repositories/${encodeURIComponent(repositoryScan.requestId)}/dependencies`,
      {limit: dependencyPageSize, offset: dependencyOffset});
    byId('repository-dependencies').replaceChildren();
    for (const dependency of page.items) {
      const row = node('tr');
      row.append(node('td', dependency.packageName), node('td', dependency.ecosystem),
        node('td', dependency.version), node('td', dependency.direct ? 'Direct' : 'Transitive'),
        node('td', dependency.declaredLicense));
      byId('repository-dependencies').append(row);
    }
    byId('dependency-count').textContent = `${page.total} supported dependencies`;
    byId('dependency-page-status').textContent = page.total
      ? `${dependencyOffset + 1}–${Math.min(dependencyOffset + dependencyPageSize, page.total)} of ${page.total}` : '0 dependencies';
    byId('dependency-previous').disabled = dependencyOffset === 0;
    byId('dependency-next').disabled = dependencyOffset + dependencyPageSize >= page.total;
  } catch (error) {
    byId('repository-error').textContent = error.message;
    byId('repository-error').hidden = false;
  }
}

async function loadRepositoryInsights() {
  if (!repositoryScan || repositoryInsightsLoading || repositoryScan.status === 'failed') return;
  repositoryInsightsLoading = true;
  try {
    const page = await request(`/repositories/${encodeURIComponent(repositoryScan.requestId)}/events`,
      {limit: 200, offset: 0});
    const container = byId('repository-insights');
    container.replaceChildren();
    const activityContainer = byId('repository-activity');
    activityContainer.replaceChildren();
    const activity = page.items.find(event => event.source === 'github-activity' && event.ecosystem === 'github');
    activityContainer.hidden = !activity;
    if (activity) {
      const meta = node('div', undefined, 'finding-meta');
      meta.append(node('strong', 'Repository activity'), badge(activity.severity));
      activityContainer.append(meta, node('p', activity.summary));
      activityContainer.title = timestamp(activity.timestamp);
    }
    const latest = new Map();
    for (const event of page.items) {
      if (event.ecosystem === 'github') continue;
      const key = `${event.ecosystem}\u0000${event.packageName}\u0000${event.source}`;
      if (!latest.has(key)) latest.set(key, event);
    }
    const grouped = new Map();
    for (const event of latest.values()) {
      const key = `${event.ecosystem}\u0000${event.packageName}`;
      if (!grouped.has(key)) grouped.set(key, []);
      grouped.get(key).push(event);
    }
    if (!grouped.size) {
      container.append(node('p', 'Analysis is in progress. Scanner findings will appear here automatically.', 'empty'));
    }
    for (const events of grouped.values()) {
      const first = events[0];
      const card = node('article', undefined, 'package');
      const heading = node('div', undefined, 'package-title');
      heading.append(node('strong', first.packageName), node('span', first.ecosystem, 'ecosystem'));
      card.append(heading);
      events.sort((a, b) => a.source.localeCompare(b.source));
      for (const event of events) {
        const finding = node('div', undefined, 'finding');
        const meta = node('div', undefined, 'finding-meta');
        meta.append(node('span', event.source), badge(event.severity));
        finding.append(meta, node('p', event.summary));
        finding.title = timestamp(event.timestamp);
        card.append(finding);
      }
      container.append(card);
    }
    const dependencyFindingCount = page.items.filter(event => event.ecosystem !== 'github').length;
    byId('repository-insight-count').textContent = grouped.size
      ? `${grouped.size} dependencies analyzed · ${dependencyFindingCount} findings` : 'Waiting for scanners…';
  } catch (_) {
    byId('repository-insight-count').textContent = 'Insights temporarily unavailable';
  } finally { repositoryInsightsLoading = false; }
}

async function loadRecentRepositories() {
  try {
    const page = await request('/repositories', {limit: 8, offset: 0});
    const list = byId('recent-repositories');
    list.replaceChildren();
    if (!page.items.length) list.append(node('span', 'No repository scans yet.', 'muted'));
    for (const scan of page.items) {
      const button = node('button', undefined, 'repository-list-item');
      button.type = 'button';
      button.append(node('span', scan.repositoryUrl.replace('https://github.com/', '')),
        node('span', scan.status.toUpperCase(), `status-pill ${scan.status}`));
      button.addEventListener('click', () => {
        dependencyOffset = 0; showRepositoryScan(scan);
        if (scan.status === 'queued') startRepositoryPolling();
      });
      list.append(button);
    }
  } catch (_) { /* Keep the existing findings dashboard usable during a transient repository API failure. */ }
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
      if (repositoryScan && ['complete', 'partial'].includes(repositoryScan.status)) loadRepositoryInsights();
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
byId('repository-form').addEventListener('submit', submitRepository);
byId('dependency-previous').addEventListener('click', () => {
  dependencyOffset = Math.max(0, dependencyOffset - dependencyPageSize); loadDependencies();
});
byId('dependency-next').addEventListener('click', () => {
  dependencyOffset += dependencyPageSize; loadDependencies();
});
refresh();
loadRecentRepositories();
setInterval(refresh, 10000);
