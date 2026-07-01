const API = '/api/v1';
let token = localStorage.getItem('fw_token') || '';
let username = localStorage.getItem('fw_user') || '';
let route = normalizeRoute(location.hash);
let configCache = null;
let configTab = 'db';

function normalizeRoute(hash) {
  const r = (hash || '').slice(1) || '/';
  return r.startsWith('/') ? r : '/' + r;
}

async function api(path, opts = {}) {
  const headers = { 'Content-Type': 'application/json', ...(opts.headers || {}) };
  if (token) headers.Authorization = 'Bearer ' + token;
  const res = await fetch(API + path, { ...opts, headers });
  let json;
  try {
    json = await res.json();
  } catch {
    throw new Error('服务响应异常 HTTP ' + res.status);
  }
  if (!json.success) throw new Error(json.message || '请求失败');
  return json.data;
}

function nav() {
  const items = [
    ['/', '首页'], ['/nodes', '节点管理'], ['/config', '配置中心'],
    ['/deploy', '部署'], ['/eci', 'ECI 容器'], ['/settings', '设置']
  ];
  return `<aside class="sidebar"><div class="brand"><h1>XJI Framework</h1><p>${escapeHtml(username || '管理员')}</p></div>
    <nav class="nav" id="main-nav">${items.map(([p,l]) =>
      `<a href="#${p === '/' ? '' : p}" data-route="${p}" class="nav-link ${route===p?'active':''}">${l}</a>`
    ).join('')}
    <a href="#" id="logout" class="nav-link">退出</a></nav></aside>`;
}

function gauge(label, pct, sub) {
  return `<div class="gauge"><div class="gauge-ring">${pct}%</div><div>${label}</div><div style="color:var(--muted);font-size:12px">${escapeHtml(sub||'')}</div></div>`;
}

async function renderDashboard() {
  const d = await api('/dashboard');
  const m = d.localMetrics || {};
  const bs = d.backendStatus || {};
  const memPct = m.memoryTotalBytes ? Math.round(m.memoryUsedBytes / m.memoryTotalBytes * 100) : 0;
  const diskPct = m.diskTotalBytes ? Math.round(m.diskUsedBytes / m.diskTotalBytes * 100) : 0;
  const backendLine = bs.reachable
    ? '<span style="color:var(--accent)">后端已连接</span>'
    : '<span style="color:var(--danger)">独立模式 — 后端未连接（可先配置节点/部署，稍后再连后端）</span>';
  return `<h2>概览</h2><div class="card">${backendLine}<p style="font-size:13px;color:var(--muted)">${escapeHtml(bs.lastError||'')}</p></div>
  <div class="grid card">
    ${gauge('CPU', m.cpuUsagePercent||0, (m.cpuCores||0)+' 核')}
    ${gauge('内存', memPct, formatBytes(m.memoryUsedBytes)+' / '+formatBytes(m.memoryTotalBytes))}
    ${gauge('磁盘', diskPct, formatBytes(m.diskUsedBytes)+' / '+formatBytes(m.diskTotalBytes))}
    ${gauge('节点', d.totalNodes?Math.round(d.onlineNodes/d.totalNodes*100):0, d.onlineNodes+' / '+d.totalNodes+' 在线')}
  </div>
  <div class="card"><p>配置 revision: <strong>${d.configRevision}</strong></p><p>活跃 ECI: ${d.eciActive}</p></div>`;
}

async function renderNodes() {
  const nodes = await api('/nodes');
  return `<h2>节点管理</h2>
  <div class="card"><h3>添加节点</h3>
  <input id="n-name" placeholder="名称"/><input id="n-host" placeholder="IP/主机"/>
  <select id="n-role"><option>FRONTEND</option><option>BACKEND</option><option>COMPUTE</option><option>K8S_MASTER</option><option>CUSTOM</option></select>
  <button type="button" id="add-node">添加</button></div>
  <div class="card"><table><thead><tr><th>名称</th><th>主机</th><th>角色</th><th>状态</th><th>操作</th></tr></thead>
  <tbody>${nodes.length ? nodes.map(n=>`<tr><td>${escapeHtml(n.name)}</td><td>${escapeHtml(n.host)}</td><td>${n.role}</td><td>${n.agentStatus}</td>
  <td><a href="#/terminal/${n.id}">终端</a></td></tr>`).join('') : '<tr><td colspan="5">暂无节点</td></tr>'}</tbody></table></div>`;
}

function configTabPanel(cfg) {
  const c = cfg || {};
  if (configTab === 'redis') {
    return `<label>主机</label><input id="redis-host" value="${attr(c.redis?.host)}"/>
      <label>端口</label><input id="redis-port" type="number" value="${c.redis?.port ?? 6379}"/>
      <label>密码</label><input id="redis-pass" type="password" placeholder="留空不修改"/>
      <label>队列键</label><input id="redis-queue" value="${attr(c.redis?.queueKey)}"/>
      <button type="button" class="secondary" id="test-redis">测试 Redis</button>`;
  }
  if (configTab === 'oss') {
    return `<label>Endpoint</label><input id="oss-endpoint" value="${attr(c.oss?.endpoint)}"/>
      <label>Region</label><input id="oss-region" value="${attr(c.oss?.region)}"/>
      <label>Bucket</label><input id="oss-bucket" value="${attr(c.oss?.bucket)}"/>
      <label>Access Key</label><input id="oss-ak" value="${attr(c.oss?.accessKey)}"/>
      <label>Secret Key</label><input id="oss-sk" type="password" placeholder="留空不修改"/>
      <label><input type="checkbox" id="oss-path" ${c.oss?.pathStyleAccess?'checked':''}/> Path Style</label>
      <button type="button" class="secondary" id="test-oss">测试 OSS</button>`;
  }
  if (configTab === 'biz') {
    return `<label>JWT Secret</label><input id="jwt-secret" type="password" placeholder="留空不修改"/>
      <label>CORS Origins</label><input id="cors-origins" value="${attr(c.cors?.allowedOrigins)}"/>
      <label>Worker Secret</label><input id="worker-secret" type="password" placeholder="留空不修改"/>
      <label>Storage Root</label><input id="storage-root" value="${attr(c.storage?.root)}"/>
      <label>Backend Public URL（后端部署后填写）</label><input id="backend-url" value="${attr(c.backend?.publicUrl)}" placeholder="http://10.0.1.20:8080"/>
      <label>Aliyun Region</label><input id="ali-region" value="${attr(c.aliyun?.regionId)}"/>
      <label>VSwitch ID</label><input id="ali-vsw" value="${attr(c.aliyun?.vSwitchId)}"/>
      <label>Security Group ID</label><input id="ali-sg" value="${attr(c.aliyun?.securityGroupId)}"/>
      <label>ECI 镜像</label><input id="ali-image" value="${attr(c.aliyun?.containerImage)}"/>`;
  }
  return `<label>JDBC URL</label><input id="db-url" value="${attr(c.database?.url)}"/>
    <label>用户名</label><input id="db-user" value="${attr(c.database?.username)}"/>
    <label>密码</label><input id="db-pass" type="password" placeholder="留空不修改"/>
    <button type="button" class="secondary" id="test-db">测试数据库</button>`;
}

async function renderConfig() {
  const c = await api('/config');
  configCache = c;
  const cfg = c.config || {};
  const tabs = [
    ['db', '数据库'], ['redis', 'Redis'], ['oss', 'OSS'], ['biz', '业务/后端']
  ];
  return `<h2>配置中心</h2>
  <p>revision: ${c.revision} · Framework 可独立于后端运行，后端地址请在「业务/后端」Tab 中稍后配置</p>
  <div class="tabs">${tabs.map(([k,l]) =>
    `<button type="button" class="cfg-tab ${configTab===k?'active':''}" data-tab="${k}">${l}</button>`
  ).join('')}</div>
  <div class="card" id="cfg-form">${configTabPanel(cfg)}
    <hr style="margin:16px 0;border:none;border-top:1px solid #e5e7eb"/>
    <button type="button" id="save-config">保存当前 Tab 配置</button>
  </div>`;
}

async function renderDeploy() {
  const tasks = await api('/deploy/tasks');
  const nodes = await api('/nodes');
  const opts = nodes.length
    ? nodes.map(n=>`<option value="${n.id}">${escapeHtml(n.name)} (${escapeHtml(n.host)})</option>`).join('')
    : '<option value="">请先添加节点</option>';
  return `<h2>部署</h2>
  <div class="card"><p>无需后端即可管理节点与发起部署；目标机需 SSH 可达。</p>
  <h3>新建部署</h3>
  <select id="d-type"><option value="BASIC">基本部署</option><option value="DOCKER">Docker</option><option value="K8S">K8s</option></select>
  <select id="d-node">${opts}</select>
  <button type="button" id="start-deploy">开始部署</button></div>
  <div class="card"><table><thead><tr><th>类型</th><th>状态</th><th>时间</th><th>日志</th></tr></thead>
  <tbody>${tasks.length ? tasks.map(t=>`<tr><td>${t.type}</td><td>${t.status}</td><td>${escapeHtml(String(t.createdAt||''))}</td>
  <td><pre class="log-box">${escapeHtml((t.log||'').slice(-500))}</pre></td></tr>`).join('') : '<tr><td colspan="4">暂无任务</td></tr>'}</tbody></table></div>`;
}

async function renderEci() {
  const list = await api('/eci');
  return `<h2>ECI 容器</h2>
  <div class="card"><p>手动创建 ECI；自动扩缩需后端上线且队列有任务。</p>
  <input id="eci-name" placeholder="实例名称"/><button type="button" id="eci-create">创建</button></div>
  <div class="card"><table><thead><tr><th>名称</th><th>ID</th><th>状态</th><th>操作</th></tr></thead>
  <tbody>${list.length ? list.map(e=>`<tr><td>${escapeHtml(e.name)}</td><td>${escapeHtml(e.containerGroupId||'-')}</td><td>${e.status}</td>
  <td>${e.status!=='STOPPED'?`<button type="button" class="danger eci-stop" data-id="${e.id}">停止</button>`:''}</td></tr>`).join('') : '<tr><td colspan="4">暂无实例</td></tr>'}</tbody></table></div>`;
}

function renderSettings() {
  return `<h2>Framework 设置</h2><div class="card">
  <p>运行参数请在「配置中心」维护。本页仅修改 Framework 管理员密码。</p>
  <label>原密码</label><input id="old-pass" type="password"/>
  <label>新密码</label><input id="new-pass" type="password"/>
  <button type="button" id="change-pass">修改密码</button></div>`;
}

function renderTerminal(nodeId) {
  return `<h2>远程终端</h2><div class="card"><div id="term" class="log-box" style="height:400px">连接中...</div>
  <input id="term-input" placeholder="输入命令后回车" style="margin-top:8px"/></div>`;
}

function renderLogin() {
  return `<div class="login-wrap card"><h2>Framework 登录</h2><p style="color:var(--muted);font-size:13px">可先安装 Framework，无需后端即可使用配置中心与部署功能</p>
  <div id="err" class="error"></div>
  <label>用户名</label><input id="user" value="admin"/>
  <label>密码</label><input id="pass" type="password" value="admin"/>
  <button type="button" id="login-btn">登录</button></div>`;
}

function attr(v) { return escapeHtml(v == null ? '' : String(v)); }
function formatBytes(b) {
  if (!b) return '0 B';
  const u = ['B','KB','MB','GB','TB'];
  let i = 0; while (b >= 1024 && i < u.length-1) { b/=1024; i++; }
  return b.toFixed(1)+' '+u[i];
}
function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}

async function render() {
  const app = document.getElementById('app');
  if (!token) { app.innerHTML = renderLogin(); bindLogin(); return; }
  app.innerHTML = `<div class="shell">${nav()}<main class="main"><p>加载中...</p></main></div>`;
  bindNav();
  try {
    let content = '';
    if (route.startsWith('/terminal/')) content = renderTerminal(route.split('/')[2]);
    else if (route === '/nodes') content = await renderNodes();
    else if (route === '/config') content = await renderConfig();
    else if (route === '/deploy') content = await renderDeploy();
    else if (route === '/eci') content = await renderEci();
    else if (route === '/settings') content = renderSettings();
    else content = await renderDashboard();
    app.innerHTML = `<div class="shell">${nav()}<main class="main">${content}</main></div>`;
    bindNav();
    bindShell();
  } catch (e) {
    app.innerHTML = `<div class="shell">${nav()}<main class="main"><div class="card error">${escapeHtml(e.message)}</div></main></div>`;
    bindNav();
    bindShell();
  }
}

function bindNav() {
  document.querySelectorAll('.nav-link[data-route]').forEach(el => {
    el.onclick = (ev) => {
      ev.preventDefault();
      route = el.getAttribute('data-route');
      location.hash = route === '/' ? '' : route;
      render();
    };
  });
  const lo = document.getElementById('logout');
  if (lo) lo.onclick = (ev) => {
    ev.preventDefault();
    token = '';
    localStorage.removeItem('fw_token');
    route = '/';
    location.hash = '';
    render();
  };
}

function collectConfigPayload() {
  const body = {};
  if (configTab === 'db') {
    body.database = {
      url: document.getElementById('db-url').value,
      username: document.getElementById('db-user').value,
      password: document.getElementById('db-pass').value || undefined
    };
  } else if (configTab === 'redis') {
    body.redis = {
      host: document.getElementById('redis-host').value,
      port: Number(document.getElementById('redis-port').value),
      password: document.getElementById('redis-pass').value || undefined,
      queueKey: document.getElementById('redis-queue').value
    };
  } else if (configTab === 'oss') {
    body.oss = {
      endpoint: document.getElementById('oss-endpoint').value,
      region: document.getElementById('oss-region').value,
      bucket: document.getElementById('oss-bucket').value,
      accessKey: document.getElementById('oss-ak').value,
      secretKey: document.getElementById('oss-sk').value || undefined,
      pathStyleAccess: document.getElementById('oss-path').checked
    };
  } else if (configTab === 'biz') {
    const jwt = document.getElementById('jwt-secret').value;
    const ws = document.getElementById('worker-secret').value;
    body.jwt = jwt ? { secret: jwt } : undefined;
    body.cors = { allowedOrigins: document.getElementById('cors-origins').value };
    body.worker = ws ? { sharedSecret: ws } : undefined;
    body.storage = { root: document.getElementById('storage-root').value };
    body.backend = { publicUrl: document.getElementById('backend-url').value };
    body.aliyun = {
      regionId: document.getElementById('ali-region').value,
      vSwitchId: document.getElementById('ali-vsw').value,
      securityGroupId: document.getElementById('ali-sg').value,
      containerImage: document.getElementById('ali-image').value
    };
  }
  return body;
}

function bindLogin() {
  document.getElementById('login-btn').onclick = async () => {
    try {
      const data = await api('/auth/login', { method:'POST', body: JSON.stringify({
        username: document.getElementById('user').value,
        password: document.getElementById('pass').value
      })});
      token = data.token; username = data.username;
      localStorage.setItem('fw_token', token);
      localStorage.setItem('fw_user', username);
      route = data.mustChangePassword ? '/settings' : '/';
      location.hash = route === '/' ? '' : route;
      render();
    } catch (e) { document.getElementById('err').textContent = e.message; }
  };
}

function bindShell() {
  document.querySelectorAll('.cfg-tab').forEach(btn => {
    btn.onclick = () => { configTab = btn.dataset.tab; render(); };
  });
  const addNode = document.getElementById('add-node');
  if (addNode) addNode.onclick = async () => {
    await api('/nodes', { method:'POST', body: JSON.stringify({
      name: document.getElementById('n-name').value,
      host: document.getElementById('n-host').value,
      role: document.getElementById('n-role').value
    })});
    render();
  };
  const saveCfg = document.getElementById('save-config');
  if (saveCfg) saveCfg.onclick = async () => {
    await api('/config', { method:'PUT', body: JSON.stringify(collectConfigPayload()) });
    alert('已保存');
    render();
  };
  const testDb = document.getElementById('test-db');
  if (testDb) testDb.onclick = async () => {
    await api('/config/test/database', { method:'POST', body: JSON.stringify({
      url: document.getElementById('db-url').value,
      username: document.getElementById('db-user').value,
      password: document.getElementById('db-pass').value
    })});
    alert('数据库连接成功');
  };
  const testRedis = document.getElementById('test-redis');
  if (testRedis) testRedis.onclick = async () => {
    await api('/config/test/redis', { method:'POST', body: JSON.stringify({
      host: document.getElementById('redis-host').value,
      port: Number(document.getElementById('redis-port').value),
      password: document.getElementById('redis-pass').value
    })});
    alert('Redis 连接成功');
  };
  const testOss = document.getElementById('test-oss');
  if (testOss) testOss.onclick = async () => {
    await api('/config/test/oss', { method:'POST', body: JSON.stringify({
      endpoint: document.getElementById('oss-endpoint').value,
      region: document.getElementById('oss-region').value,
      bucket: document.getElementById('oss-bucket').value,
      accessKey: document.getElementById('oss-ak').value,
      secretKey: document.getElementById('oss-sk').value,
      pathStyleAccess: document.getElementById('oss-path').checked
    })});
    alert('OSS 连接成功');
  };
  const startDeploy = document.getElementById('start-deploy');
  if (startDeploy) startDeploy.onclick = async () => {
    const nodeId = document.getElementById('d-node').value;
    if (!nodeId) { alert('请先添加节点'); return; }
    await api('/deploy/tasks', { method:'POST', body: JSON.stringify({
      type: document.getElementById('d-type').value,
      targetNodeId: nodeId,
      params: {}
    })});
    render();
  };
  const eciCreate = document.getElementById('eci-create');
  if (eciCreate) eciCreate.onclick = async () => {
    await api('/eci', { method:'POST', body: JSON.stringify({ name: document.getElementById('eci-name').value })});
    render();
  };
  document.querySelectorAll('.eci-stop').forEach(btn => btn.onclick = async () => {
    await api('/eci/'+btn.dataset.id+'/stop', { method:'POST', body:'{}' });
    render();
  });
  const chPass = document.getElementById('change-pass');
  if (chPass) chPass.onclick = async () => {
    await api('/auth/change-password', { method:'POST', body: JSON.stringify({
      oldPassword: document.getElementById('old-pass').value,
      newPassword: document.getElementById('new-pass').value
    })});
    alert('密码已更新');
  };
  if (route.startsWith('/terminal/')) bindTerminal(route.split('/')[2]);
}

function bindTerminal(nodeId) {
  const term = document.getElementById('term');
  const ws = new WebSocket((location.protocol==='https:'?'wss:':'ws:')+'//'+location.host+'/ws/terminal/'+nodeId);
  ws.onmessage = e => { term.textContent += e.data; term.scrollTop = term.scrollHeight; };
  ws.onopen = () => { term.textContent = 'Connected\r\n'; };
  ws.onerror = () => { term.textContent = '连接失败，请检查 SSH 密钥 XJI_FRAMEWORK_SSH_KEY\r\n'; };
  document.getElementById('term-input').onkeydown = e => {
    if (e.key === 'Enter' && ws.readyState === 1) {
      ws.send(e.target.value + '\n');
      e.target.value = '';
    }
  };
}

window.addEventListener('hashchange', () => { route = normalizeRoute(location.hash); render(); });
render();
