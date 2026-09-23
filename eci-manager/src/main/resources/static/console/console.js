(function () {
  "use strict";

  var STORAGE_KEY = "eci.apiKey";
  var API = "/api/v1/eci";
  var TERMINAL = { SUCCEEDED: true, FAILED: true, CANCELLED: true };
  var STATUS_LABELS = { CANCELLING: "正在清理" };

  var meta = null;
  var selectedTaskId = null;
  var selectedInstanceId = null;
  var pollTimer = null;
  var clockTimer = null;
  var selectedTask = null;
  var currentTab = "tasks";

  function $(id) {
    return document.getElementById(id);
  }

  function show(el, on) {
    if (!el) return;
    el.hidden = !on;
  }

  function setText(el, text) {
    if (el) el.textContent = text == null ? "" : String(text);
  }

  function statusLabel(status) {
    return STATUS_LABELS[status] || status || "?";
  }

  function getKey() {
    return sessionStorage.getItem(STORAGE_KEY) || "";
  }

  function setKey(value) {
    if (value) sessionStorage.setItem(STORAGE_KEY, value);
    else sessionStorage.removeItem(STORAGE_KEY);
  }

  function api(path, options) {
    options = options || {};
    var headers = Object.assign({ "X-Api-Key": getKey() }, options.headers || {});
    if (options.body && !headers["Content-Type"]) {
      headers["Content-Type"] = "application/json";
    }
    return fetch(API + path, {
      method: options.method || "GET",
      headers: headers,
      body: options.body || undefined
    }).then(function (res) {
      return res.text().then(function (raw) {
        var body = {};
        if (raw) {
          try {
            body = JSON.parse(raw);
          } catch (e) {
            body = { success: false, message: raw };
          }
        }
        if (res.status === 401) {
          showGate(body.message || "Api-Key 无效或缺失");
          var err = new Error(body.message || "未授权");
          err.status = 401;
          throw err;
        }
        if (!res.ok || body.success === false) {
          var fail = new Error(body.message || ("HTTP " + res.status));
          fail.status = res.status;
          throw fail;
        }
        return body.data;
      });
    });
  }

  function showGate(message) {
    show($("key-gate"), true);
    var err = $("key-error");
    if (message) {
      show(err, true);
      setText(err, message);
    } else {
      show(err, false);
    }
    $("key-input").value = "";
    $("key-input").focus();
  }

  function hideGate() {
    show($("key-gate"), false);
    show($("key-error"), false);
  }

  function parseJsonField(raw, label, fallback) {
    var text = (raw || "").trim();
    if (!text) return fallback;
    try {
      return JSON.parse(text);
    } catch (e) {
      throw new Error(label + " 不是合法 JSON");
    }
  }

  function optionalNumber(raw) {
    if (raw == null || String(raw).trim() === "") return undefined;
    var n = Number(raw);
    if (!Number.isFinite(n)) throw new Error("数字字段无法解析");
    return n;
  }

  function optionalText(raw) {
    var text = (raw || "").trim();
    return text ? text : undefined;
  }

  function fillTaskForm(data) {
    data = data || {};
    $("f-name").value = data.name || "";
    $("f-image").value = data.image || (meta && meta.defaultImage) || "";
    $("f-cpu").value = data.cpu != null ? data.cpu : (meta ? meta.defaultCpu : "");
    $("f-memory").value = data.memory != null ? data.memory : (meta ? meta.defaultMemory : "");
    $("f-instanceType").value = data.instanceType || "";
    $("f-gpu").value = data.gpu != null ? data.gpu : "";
    $("f-shmSize").value = data.shmSize != null ? data.shmSize : (meta && meta.defaultShmSize) || "";
    $("f-restartPolicy").value = data.restartPolicy || (meta && meta.defaultRestartPolicy) || "Never";
    $("f-maxRuntimeSec").value = data.maxRuntimeSec != null ? data.maxRuntimeSec : (meta ? meta.defaultMaxRuntimeSec : "");
    $("f-imageSnapshotId").value = data.imageSnapshotId || "";
    $("f-command").value = data.command ? JSON.stringify(data.command) : "";
    $("f-args").value = data.args ? JSON.stringify(data.args) : "";
    $("f-env").value = data.env ? JSON.stringify(data.env, null, 2) : "{}";
    var storageMode = data.storage && data.storage.mode ? data.storage.mode : "";
    $("f-storage").value = storageMode;
    $("f-outputKey").value = data.storage && data.storage.outputKey ? data.storage.outputKey : "";
    show($("output-key-wrap"), storageMode === "keys");
  }

  function readTaskForm() {
    var body = {
      image: optionalText($("f-image").value)
    };
    if (!body.image) throw new Error("image 为必填项");
    var name = optionalText($("f-name").value);
    if (name) body.name = name;
    var cpu = optionalNumber($("f-cpu").value);
    var memory = optionalNumber($("f-memory").value);
    var gpu = optionalNumber($("f-gpu").value);
    var maxRuntimeSec = optionalNumber($("f-maxRuntimeSec").value);
    if (cpu != null) body.cpu = cpu;
    if (memory != null) body.memory = memory;
    if (gpu != null) body.gpu = gpu;
    if (maxRuntimeSec != null) body.maxRuntimeSec = maxRuntimeSec;
    var instanceType = optionalText($("f-instanceType").value);
    var shmSize = optionalText($("f-shmSize").value);
    var imageSnapshotId = optionalText($("f-imageSnapshotId").value);
    if (instanceType) body.instanceType = instanceType;
    if (shmSize) body.shmSize = shmSize;
    if (imageSnapshotId) body.imageSnapshotId = imageSnapshotId;
    body.restartPolicy = $("f-restartPolicy").value || undefined;
    var command = parseJsonField($("f-command").value, "command", undefined);
    var args = parseJsonField($("f-args").value, "args", undefined);
    var env = parseJsonField($("f-env").value, "env", {});
    if (command) body.command = command;
    if (args) body.args = args;
    if (env && Object.keys(env).length) body.env = env;
    var storageMode = $("f-storage").value;
    if (storageMode) {
      body.storage = { mode: storageMode };
      if (storageMode === "keys") {
        var outputKey = optionalText($("f-outputKey").value);
        if (!outputKey) throw new Error("keys 模式必须提供 outputKey");
        body.storage.outputKey = outputKey;
      }
    }
    return body;
  }

  function applyPreset(name) {
    show($("json-paste"), name === "json");
    if (name === "json") return;
    if (name === "hello") {
      fillTaskForm({
        name: "hello-python",
        image: meta ? meta.defaultImage : "",
        cpu: 0.5,
        memory: 1,
        restartPolicy: "Never",
        maxRuntimeSec: 600,
        command: ["python3", "-c"],
        args: ["print('hello, world')"]
      });
      return;
    }
    if (name === "oss") {
      if (!meta || !meta.ossEnabled) {
        setText($("task-form-error"), "OSS 未启用，不能使用 OSS test 预设。切换须改配置并重启。");
        show($("task-form-error"), true);
        return;
      }
      fillTaskForm({
        name: "oss-test",
        image: meta.defaultImage,
        cpu: 0.5,
        memory: 1,
        restartPolicy: "Never",
        maxRuntimeSec: 600,
        storage: { mode: "test" }
      });
      return;
    }
    fillTaskForm({});
  }

  function formatDuration(ms) {
    var sec = Math.max(0, Math.floor(ms / 1000));
    var h = Math.floor(sec / 3600);
    var m = Math.floor((sec % 3600) / 60);
    var s = sec % 60;
    if (h > 0) return h + "h " + m + "m " + s + "s";
    if (m > 0) return m + "m " + s + "s";
    return s + "s";
  }

  function elapsedMs(task, now) {
    var startRaw = task.startedAt || task.createdAt;
    if (!startRaw) return 0;
    var start = Date.parse(startRaw);
    var end = task.endedAt ? Date.parse(task.endedAt) : now;
    return Math.max(0, end - start);
  }

  function renderTimer(task) {
    if (!task) return;
    var now = Date.now();
    var elapsed = elapsedMs(task, now);
    setText($("task-timer"), "已运行 " + formatDuration(elapsed) + (task.endedAt ? "（已停表）" : ""));
    var maxSec = task.maxRuntimeSec || 0;
    if (maxSec > 0) {
      var remain = Math.max(0, maxSec * 1000 - elapsed);
      setText($("task-remain"), "剩余 " + formatDuration(remain) + " / 上限 " + formatDuration(maxSec * 1000) + "（非任务进度）");
      var used = Math.min(1, elapsed / (maxSec * 1000));
      $("timeout-fill").style.width = (used * 100) + "%";
    } else {
      setText($("task-remain"), "未设置 maxRuntimeSec");
      $("timeout-fill").style.width = "0%";
    }
  }

  function renderTaskList(tasks) {
    var list = $("task-list");
    list.innerHTML = "";
    show($("task-empty"), !tasks || !tasks.length);
    (tasks || []).forEach(function (task) {
      var li = document.createElement("li");
      if (task.id === selectedTaskId) li.className = "is-active";
      li.dataset.id = task.id;
      var pill = document.createElement("span");
      pill.className = "status-pill " + (task.status || "");
      pill.textContent = statusLabel(task.status);
      var name = document.createElement("span");
      name.textContent = task.name || task.id;
      var id = document.createElement("span");
      id.className = "muted mono small";
      id.textContent = String(task.id).slice(0, 8);
      li.appendChild(pill);
      li.appendChild(name);
      li.appendChild(id);
      li.addEventListener("click", function () {
        selectTask(task.id);
      });
      list.appendChild(li);
    });
  }

  function renderTaskDetail(task) {
    selectedTask = task;
    show($("task-detail"), true);
    setText($("task-detail-title"), task.name || task.id);
    setText($("task-status-line"), "status=" + statusLabel(task.status) + " instanceId=" + (task.instanceId || "-"));
    setText($("task-json"), JSON.stringify(task, null, 2));
    renderTimer(task);
    $("cancel-task").disabled = !!TERMINAL[task.status] || task.status === "CANCELLING";
  }

  function loadLogs(task) {
    if (!task) return Promise.resolve();
    return api("/tasks/" + task.id + "/logs?tailLines=200").then(function (data) {
      setText($("log-note"), (data && (data.note || data.source)) || "");
      setText($("task-log"), (data && data.content) || "");
    }).catch(function (err) {
      if (err.status === 401) return;
      setText($("task-log"), err.message || "拉取日志失败");
    });
  }

  function selectTask(id) {
    selectedTaskId = id;
    return api("/tasks/" + id).then(function (task) {
      renderTaskDetail(task);
      return loadLogs(task).then(function () {
        return refreshTaskList(false);
      });
    }).catch(function (err) {
      if (err.status === 401) return;
      setText($("task-list-status"), err.message);
    });
  }

  function refreshTaskList(selectFirst) {
    return api("/tasks").then(function (tasks) {
      setText($("task-list-status"), "共 " + (tasks || []).length + " 条");
      renderTaskList(tasks || []);
      if (selectFirst && tasks && tasks.length && !selectedTaskId) {
        return selectTask(tasks[0].id);
      }
    }).catch(function (err) {
      if (err.status === 401) return;
      setText($("task-list-status"), err.message);
    });
  }

  function pollLoop() {
    if (!selectedTask || TERMINAL[selectedTask.status]) return;
    api("/tasks/" + selectedTask.id).then(function (task) {
      renderTaskDetail(task);
      return loadLogs(task);
    }).catch(function (err) {
      if (err.status === 401) return;
    });
  }

  function startPolling() {
    if (pollTimer) clearInterval(pollTimer);
    if (clockTimer) clearInterval(clockTimer);
    pollTimer = setInterval(function () {
      if (currentTab === "tasks") {
        refreshTaskList(false);
        pollLoop();
      } else if (currentTab === "instances") {
        refreshInstances(false);
      }
    }, 2000);
    clockTimer = setInterval(function () {
      if (selectedTask) renderTimer(selectedTask);
    }, 1000);
  }

  function applyMeta(data) {
    meta = data;
    var mock = !!data.mock;
    $("mode-lamp").className = "lamp " + (mock ? "mock" : "cloud");
    var badge = $("mode-badge");
    badge.className = "badge " + (mock ? "mock" : "cloud");
    setText(badge, mock ? "MOCK" : "CLOUD");
    var oss = data.ossEnabled ? (data.ossConfigured ? "OSS 已启用" : "OSS 启用但目标未配齐") : "OSS 关闭";
    setText($("meta-line"), [
      "region=" + (data.regionId || "-"),
      oss,
      "poll=" + data.taskPollIntervalSec + "s"
    ].join("  "));
    show($("cloud-warn"), !mock);
    show($("mock-hint"), mock);
    $("preset-oss").disabled = !data.ossEnabled;
    fillTaskForm({});
    if (!optionalText($("i-image").value)) {
      $("i-image").placeholder = data.defaultImage || "空则用默认镜像";
    }
  }

  function renderInstanceList(items) {
    var list = $("instance-list");
    list.innerHTML = "";
    show($("instance-empty"), !items || !items.length);
    (items || []).forEach(function (inst) {
      var li = document.createElement("li");
      if (inst.id === selectedInstanceId) li.className = "is-active";
      li.dataset.id = inst.id;
      var pill = document.createElement("span");
      pill.className = "status-pill " + (inst.status || "");
      pill.textContent = inst.status || "?";
      var name = document.createElement("span");
      name.textContent = inst.name || inst.id;
      var id = document.createElement("span");
      id.className = "muted mono small";
      id.textContent = String(inst.id).slice(0, 8);
      li.appendChild(pill);
      li.appendChild(name);
      li.appendChild(id);
      li.addEventListener("click", function () {
        selectInstance(inst.id);
      });
      list.appendChild(li);
    });
  }

  function selectInstance(id) {
    selectedInstanceId = id;
    return api("/instances/" + id).then(function (inst) {
      show($("instance-detail"), true);
      setText($("instance-json"), JSON.stringify(inst, null, 2));
      show($("instance-action-error"), false);
      return refreshInstances(false);
    }).catch(function (err) {
      if (err.status === 401) return;
      setText($("instance-list-status"), err.message);
    });
  }

  function refreshInstances(selectFirst) {
    return api("/instances").then(function (items) {
      setText($("instance-list-status"), "共 " + (items || []).length + " 条");
      renderInstanceList(items || []);
      if (selectFirst && items && items.length && !selectedInstanceId) {
        return selectInstance(items[0].id);
      }
    }).catch(function (err) {
      if (err.status === 401) return;
      setText($("instance-list-status"), err.message);
    });
  }

  function loadCredentials() {
    return api("/runtime-credentials").then(function (data) {
      setText($("cred-snapshot"), JSON.stringify(data, null, 2));
      show($("cred-error"), false);
    }).catch(function (err) {
      if (err.status === 401) return;
      show($("cred-error"), true);
      setText($("cred-error"), err.message);
    });
  }

  function switchTab(name) {
    currentTab = name;
    document.querySelectorAll(".tab").forEach(function (btn) {
      var on = btn.getAttribute("data-tab") === name;
      btn.classList.toggle("is-active", on);
      btn.setAttribute("aria-selected", on ? "true" : "false");
    });
    document.querySelectorAll(".panel").forEach(function (panel) {
      show(panel, panel.getAttribute("data-panel") === name);
    });
    if (name === "instances") refreshInstances(true);
    if (name === "creds") loadCredentials();
  }

  function boot() {
    if (!getKey()) {
      showGate("");
      return;
    }
    api("/meta").then(function (data) {
      hideGate();
      applyMeta(data);
      return refreshTaskList(true);
    }).then(function () {
      startPolling();
    }).catch(function (err) {
      if (err.status !== 401) showGate(err.message);
    });
  }

  $("key-form").addEventListener("submit", function (ev) {
    ev.preventDefault();
    setKey($("key-input").value.trim());
    boot();
  });

  $("change-key").addEventListener("click", function () {
    showGate("");
  });

  document.querySelector(".tabs").addEventListener("click", function (ev) {
    var btn = ev.target.closest(".tab");
    if (btn) switchTab(btn.getAttribute("data-tab"));
  });

  document.querySelector(".presets").addEventListener("click", function (ev) {
    var btn = ev.target.closest("[data-preset]");
    if (!btn) return;
    show($("task-form-error"), false);
    applyPreset(btn.getAttribute("data-preset"));
  });

  $("f-storage").addEventListener("change", function () {
    show($("output-key-wrap"), $("f-storage").value === "keys");
  });

  $("apply-json").addEventListener("click", function () {
    show($("task-form-error"), false);
    try {
      var data = JSON.parse($("f-json").value || "{}");
      fillTaskForm(data);
    } catch (e) {
      show($("task-form-error"), true);
      setText($("task-form-error"), "JSON 无法解析");
    }
  });

  $("task-form").addEventListener("submit", function (ev) {
    ev.preventDefault();
    show($("task-form-error"), false);
    var body;
    try {
      body = readTaskForm();
    } catch (e) {
      show($("task-form-error"), true);
      setText($("task-form-error"), e.message);
      return;
    }
    if (meta && !meta.mock) {
      var ok = window.confirm("当前是 CLOUD 模式，提交会创建计费的阿里云 ECI。确定提交？");
      if (!ok) return;
    }
    $("task-submit").disabled = true;
    api("/tasks", { method: "POST", body: JSON.stringify(body) }).then(function (task) {
      return selectTask(task.id);
    }).catch(function (err) {
      if (err.status === 401) return;
      show($("task-form-error"), true);
      setText($("task-form-error"), err.message);
    }).then(function () {
      $("task-submit").disabled = false;
    });
  });

  $("refresh-tasks").addEventListener("click", function () {
    refreshTaskList(false);
  });

  $("refresh-task").addEventListener("click", function () {
    if (selectedTaskId) selectTask(selectedTaskId);
  });

  $("refresh-task-instance").addEventListener("click", function () {
    if (!selectedTask || !selectedTask.instanceId) return;
    api("/instances/" + selectedTask.instanceId + "/refresh", { method: "POST" })
      .then(function () { return selectTask(selectedTaskId); })
      .catch(function (err) {
        if (err.status === 401) return;
        setText($("task-list-status"), err.message);
      });
  });

  $("cancel-task").addEventListener("click", function () {
    if (!selectedTaskId) return;
    api("/tasks/" + selectedTaskId + "/cancel", { method: "POST" })
      .then(function (task) { renderTaskDetail(task); return refreshTaskList(false); })
      .catch(function (err) {
        if (err.status === 401) return;
        setText($("task-list-status"), err.message);
      });
  });

  $("instance-form").addEventListener("submit", function (ev) {
    ev.preventDefault();
    show($("instance-form-error"), false);
    var body = {};
    try {
      var name = optionalText($("i-name").value);
      var image = optionalText($("i-image").value);
      var cpu = optionalNumber($("i-cpu").value);
      var memory = optionalNumber($("i-memory").value);
      var gpu = optionalNumber($("i-gpu").value);
      if (name) body.name = name;
      if (image) body.image = image;
      if (cpu != null) body.cpu = cpu;
      if (memory != null) body.memory = memory;
      if (gpu != null) body.gpu = gpu;
      var instanceType = optionalText($("i-instanceType").value);
      var shmSize = optionalText($("i-shmSize").value);
      if (instanceType) body.instanceType = instanceType;
      if (shmSize) body.shmSize = shmSize;
      var policy = optionalText($("i-restartPolicy").value);
      if (policy) body.restartPolicy = policy;
      var command = parseJsonField($("i-command").value, "command", undefined);
      var args = parseJsonField($("i-args").value, "args", undefined);
      if (command) body.command = command;
      if (args) body.args = args;
    } catch (e) {
      show($("instance-form-error"), true);
      setText($("instance-form-error"), e.message);
      return;
    }
    if (meta && !meta.mock) {
      var ok = window.confirm("当前是 CLOUD 模式，创建实例会计费。确定创建？");
      if (!ok) return;
    }
    api("/instances", { method: "POST", body: JSON.stringify(body) }).then(function (inst) {
      return selectInstance(inst.id);
    }).catch(function (err) {
      if (err.status === 401) return;
      show($("instance-form-error"), true);
      setText($("instance-form-error"), err.message);
    });
  });

  $("refresh-instances").addEventListener("click", function () {
    refreshInstances(false);
  });

  $("refresh-instance").addEventListener("click", function () {
    if (!selectedInstanceId) return;
    api("/instances/" + selectedInstanceId + "/refresh", { method: "POST" })
      .then(function (inst) {
        setText($("instance-json"), JSON.stringify(inst, null, 2));
        return refreshInstances(false);
      })
      .catch(function (err) {
        show($("instance-action-error"), true);
        setText($("instance-action-error"), err.message);
      });
  });

  $("delete-instance").addEventListener("click", function () {
    if (!selectedInstanceId) return;
    if (!window.confirm("确定删除该实例？CREATING 会返回 409。")) return;
    api("/instances/" + selectedInstanceId, { method: "DELETE" })
      .then(function (inst) {
        setText($("instance-json"), JSON.stringify(inst, null, 2));
        return refreshInstances(false);
      })
      .catch(function (err) {
        show($("instance-action-error"), true);
        setText($("instance-action-error"), err.message);
      });
  });

  $("cred-form").addEventListener("submit", function (ev) {
    ev.preventDefault();
    show($("cred-error"), false);
    var body = {
      resource: $("c-resource").value,
      accessKeyId: $("c-id").value,
      accessKeySecret: $("c-secret").value
    };
    api("/runtime-credentials", { method: "PUT", body: JSON.stringify(body) }).then(function (data) {
      setText($("cred-snapshot"), JSON.stringify(data, null, 2));
      $("c-secret").value = "";
    }).catch(function (err) {
      if (err.status === 401) return;
      show($("cred-error"), true);
      setText($("cred-error"), err.message);
    });
  });

  $("test-cred").addEventListener("click", function () {
    show($("cred-error"), false);
    var body = { resource: $("c-resource").value };
    var id = $("c-id").value.trim();
    var secret = $("c-secret").value;
    if (id || secret) {
      body.accessKeyId = id;
      body.accessKeySecret = secret;
    }
    api("/runtime-credentials/test", { method: "POST", body: JSON.stringify(body) }).then(function (data) {
      show($("cred-test-result"), true);
      setText($("cred-test-result"), JSON.stringify(data, null, 2));
      $("c-secret").value = "";
    }).catch(function (err) {
      if (err.status === 401) return;
      show($("cred-error"), true);
      setText($("cred-error"), err.message);
    });
  });

  boot();
})();
