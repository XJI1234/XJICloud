const API = '/api/v1';
let token = localStorage.getItem('fw_token') || '';
let username = localStorage.getItem('fw_user') || '';
let route = location.hash.slice(1) || '/';

async function api(path, opts = {}) {
  const headers = { 'Content-Type': 'application/json', ...(opts.headers || {}) };
  if (token) headers.Authorization = 'Bearer ' + token;
  const res = await fetch(API + path, { ...opts, headers });
  const json = await res.json();
  if (!json.success) throw new Error(json.message || '请求失败');
  return json.data;
}

function nav() {
  const items = [
    ['/', '首页'], ['/nodes', '节点管理'], ['/config', '配置中心'],
    ['/deploy', '部署'], ['/eci', 'ECI 容器'], ['/settings', '设置']
  ];
  return `<aside class="sidebar"><div class="brand"><h1>XJI Framework</h1><p>${username || '管理员'}</p></div>
    <nav class="nav">${items.map(([p,l]) => `<a href="#${p}" class="${route===p?'active':''}">${l}</a>`).join('')}
    <a href="#" id="logout">退出</a></nav></aside>`;
}

function gauge(label, pct, sub) {
  return `<div class="gauge"><div class="gauge-ring">${pct}%</div><div>${label}</div><div style="color:var(--muted);font-size:12px">${sub||''}</div></div>`;
}

async function renderDashboard() {
  const d = await api('/dashboard');
  const m = d.localMetrics || {};
  const memPct = m.memoryTotalBytes ? Math.round(m.memoryUsedBytes / m.memoryTotalBytes * 100) : 0;
  const diskPct = m.diskTotalBytes ? Math.round(m.diskUsedBytes / m.diskTotalBytes * 100) : 0;
  return `<h2>概览</h2><div class="grid card">
    ${gauge('CPU', m.cpuUsagePercent||0, (m.cpuCores||0)+' 核')}
    ${gauge('内存', memPct, formatBytes(m.memoryUsedBytes)+' / '+formatBytes(m.memoryTotalBytes))}
    ${gauge('磁盘', diskPct, formatBytes(m.diskUsedBytes)+' / '+formatBytes(m.diskTotalBytes))}
    ${gauge('节点', d.totalNodes?Math.round(d.onlineNodes/d.totalNodes*100):0, d.onlineNodes+' / '+d.totalNodes+' 在线')}
  </div>
  <div class="card"><p>配置版本 revision: <strong>${d.configRevision}</strong></p>
  <p>活跃 ECI: ${d.eciActive}</p></div>`;
}

async function renderNodes() {
  const nodes = await api('/nodes');
  return `<h2>节点管理</h2>
  <div class="card"><h3>添加节点</h3>
  <input id="n-name" placeholder="名称"/><input id="n-host" placeholder="IP/主机"/>
  <select id="n-role"><option>FRONTEND</option><option>BACKEND</option><option>COMPUTE</option><option>K8S_MASTER</option><option>CUSTOM</option></select>
  <button id="add-node">添加</button></div>
  <div class="card"><table><thead><tr><th>名称</th><th>主机</th><th>角色</th><th>状态</th><th>操作</th></tr></thead>
  <tbody>${nodes.map(n=>`<tr><td>${n.name}</td><td>${n.host}</td><td>${n.role}</td><td>${n.agentStatus}</td>
  <td><a href="#/terminal/${n.id}">终端</a></td></tr>`).join('')}</tbody></table></div>`;
}

async function renderConfig() {
  const c = await api('/config');
  const cfg = c.config || {};
  return `<h2>配置中心</h2><p>revision: ${c.revision}</p>
  <div class="tabs"><button class="active" data-tab="db">数据库</button><button data-tab="redis">Redis</button><button data-tab="oss">OSS</button><button data-tab="biz">业务</button></div>
  <div class="card" id="cfg-form">
    <label>JDBC URL</label><input id="db-url" value="${cfg.database?.url||''}"/>
    <label>用户名</label><input id="db-user" value="${cfg.database?.username||''}"/>
    <label>密码（留空不修改）</label><input id="db-pass" type="password"/>
    <button id="save-config">保存全部配置</button>
    <button class="secondary" id="test-db">测试数据库</button>
  </div>`;
}

async function renderDeploy() {
  const tasks = await api('/deploy/tasks');
  const nodes = await api('/nodes');
  return `<h2>部署</h2>
  <div class="card"><h3>新建部署</h3>
  <select id="d-type"><option value="BASIC">基本部署</option><option value="DOCKER">Docker</option><option value="K8S">K8s</option></select>
  <select id="d-node">${nodes.map(n=>`<option value="${n.id}">${n.name} (${n.host})</option>`).join('')}</select>
  <button id="start-deploy">开始部署</button></div>
  <div class="card"><table><thead><tr><th>类型</th><th>状态</th><th>时间</th><th>日志</th></tr></thead>
  <tbody>${tasks.map(t=>`<tr><td>${t.type}</td><td>${t.status}</td><td>${t.createdAt||''}</td>
  <td><pre class="log-box">${escapeHtml((t.log||'').slice(-500))}</pre></td></tr>`).join('')}</tbody></table></div>`;
}

async function renderEci() {
  const list = await api('/eci');
  return `<h2>ECI 容器</h2>
  <div class="card"><input id="eci-name" placeholder="实例名称"/><button id="eci-create">创建</button></div>
  <div class="card"><table><thead><tr><th>名称</th><th>ID</th><th>状态</th><th>操作</th></tr></thead>
  <tbody>${list.map(e=>`<tr><td>${e.name}</td><td>${e.containerGroupId||'-'}</td><td>${e.status}</td>
  <td>${e.status!=='STOPPED'?`<button class="danger eci-stop" data-id="${e.id}">停止</button>`:''}</td></tr>`).join('')}</tbody></table></div>`;
}

function renderSettings() {
  return `<h2>Framework 设置</h2><div class="card">
  <p>Aliyun ECI 网络与镜像请在「配置中心」的业务/Aliyun 段维护。</p>
  <label>修改密码 - 原密码</label><input id="old-pass" type="password"/>
  <label>新密码</label><input id="new-pass" type="password"/>
  <button id="change-pass">修改密码</button></div>`;
}

function renderTerminal(nodeId) {
  return `<h2>远程终端</h2><div class="card"><div id="term" class="log-box" style="height:400px">WebSocket 终端：ws://${location.host}/ws/terminal/${nodeId}</div>
  <input id="term-input" placeholder="输入命令后回车" style="margin-top:8px"/></div>`;
}

function renderLogin() {
  return `<div class="login-wrap card"><h2>Framework 登录</h2><div id="err" class="error"></div>
  <label>用户名</label><input id="user" value="admin"/>
  <label>密码</label><input id="pass" type="password" value="admin"/>
  <button id="login-btn">登录</button></div>`;
}

function formatBytes(b) {
  if (!b) return '0 B';
  const u = ['B','KB','MB','GB','TB'];
  let i = 0; while (b >= 1024 && i < u.length-1) { b/=1024; i++; }
  return b.toFixed(1)+' '+u[i];
}
function escapeHtml(s) { return s.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }

async function render() {
  const app = document.getElementById('app');
  if (!token) { app.innerHTML = renderLogin(); bindLogin(); return; }
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
    bindShell();
  } catch (e) {
    app.innerHTML = `<div class="shell">${nav()}<main class="main"><div class="error">${e.message}</div></main></div>`;
    bindShell();
  }
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
      if (data.mustChangePassword) route = '/settings';
      else route = '/';
      location.hash = route;
      render();
    } catch (e) { document.getElementById('err').textContent = e.message; }
  };
}

function bindShell() {
  const lo = document.getElementById('logout');
  if (lo) lo.onclick = () => { token=''; localStorage.removeItem('fw_token'); route='/'; render(); };
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
    await api('/config', { method:'PUT', body: JSON.stringify({
      database: { url: document.getElementById('db-url').value, username: document.getElementById('db-user').value,
        password: document.getElementById('db-pass').value || undefined }
    })});
    alert('已保存');
    render();
  };
  const testDb = document.getElementById('test-db');
  if (testDb) testDb.onclick = async () => {
    await api('/config/test/database', { method:'POST', body: JSON.stringify({
      url: document.getElementById('db-url').value, username: document.getElementById('db-user').value,
      password: document.getElementById('db-pass').value
    })});
    alert('数据库连接成功');
  };
  const startDeploy = document.getElementById('start-deploy');
  if (startDeploy) startDeploy.onclick = async () => {
    await api('/deploy/tasks', { method:'POST', body: JSON.stringify({
      type: document.getElementById('d-type').value,
      targetNodeId: document.getElementById('d-node').value,
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
  ws.onopen = () => term.textContent = 'Connected\r\n';
  document.getElementById('term-input').onkeydown = e => {
    if (e.key === 'Enter' && ws.readyState === 1) ws.send(e.target.value + '\n');
  };
}

window.addEventListener('hashchange', () => { route = location.hash.slice(1) || '/'; render(); });
render();
