/* ---------- uninstall ---------- */
function openUninstallModal(appId) {
  const app = APPS[appId];
  if (!app || !app.installed) return;
  const pkg = installedPackage(app);
  const toolFlow = app.category === 'tool';
  const isDefault = !toolFlow && state.defaultHome === app.id;
  const warnings = [];

  if (toolFlow) {
    warnings.push(`默认桌面保持为 ${defaultHomeLabel()}；卸载工具不会更改 HOME。`);
    warnings.push('题库、学习记录、观看时长和家长规则会被删除，无法恢复。');
    warnings.push('如果曾在电视上开启“设备管理员”，请先取消，否则系统会拒绝卸载。');
    warnings.push('家长面板 8099 端口将随应用一起停止，本地数据不保留。');
  } else {
    if (isDefault) warnings.push('卸载前会先把默认桌面切回系统桌面，避免主页空白。');
    warnings.push('应用数据、排序、壁纸和设置会被删除，无法恢复。');
    if (app.id === 'aerial') warnings.push('app.orchard.tv 也可能属于旧版 HaloTV，请确认电视上显示的应用名是 Aerial。');
    if (app.id === 'planyourtv') warnings.push('家长规则、题库、照片和一次性恢复码会一起删除。');
    if (app.id === 'halotv') warnings.push('只会卸载 app.halotv.launcher（或 .lite），不会动 Aerial 的 app.orchard.tv。');
    if (app.id === 'quiettv') warnings.push('home.quiet.tv 是用户安装包；厂商预装的 com.coocaa.quiettv 不会被处理。');
    if (app.needsPc) warnings.push('只卸载 launcher 不会恢复被停用的广告服务和系统应用，必须先运行官方 restore.sh。');
  }

  const warningHtml = warnings.map((text) => `<div class="warn-item">${icon('warning', 13)}<span>${text}</span></div>`).join('');
  const cleanCheck = app.needsPc
    ? `<label class="check-row">
        <input type="checkbox" id="cleanRestoreCheck" data-action="toggle-clean-check">
        <span>我已在电脑执行官方 restore.sh，恢复被停用的系统应用和原桌面</span>
      </label>`
    : '';

  openModal(`
    <h3 class="modal-title">卸载 ${app.name}？</h3>
    <p class="modal-sub">检测到的应用：${app.name} · ${app.installedVersion || '已安装'}<br>包名：${pkg}</p>
    <div class="warn-list">${warningHtml}</div>
    ${cleanCheck}
    <div class="modal-actions">
      <button class="btn btn-ghost" data-action="close-modal">取消</button>
      <button class="btn btn-danger" id="confirmUninstall" data-action="confirm-uninstall" data-app="${app.id}" ${app.needsPc ? 'disabled' : ''}>确认卸载</button>
    </div>
  `);
}

function uninstallProgressHTML(app, wasDefault) {
  const pkg = installedPackage(app);
  const toolFlow = app.category === 'tool';
  const steps = toolFlow
    ? [
        { title: '停止计时与悬浮层', detail: '正在停止前台服务并隐藏答题层' },
        { title: '检查设备管理员', detail: '如果开启，需要在电视上先取消' },
        { title: '卸载 APK', detail: pkg },
        { title: '验收默认桌面与包状态', detail: '确认 HOME 未发生变化' }
      ]
    : [
        { title: '停止守护与通知监听', detail: app.needsPc ? '已确认官方恢复脚本 · 正在停止守护' : '正在停止无障碍 / 通知监听' },
        { title: '切回电视原桌面', detail: wasDefault ? '正在切换默认 HOME' : '当前不是默认桌面' },
        { title: '卸载 APK', detail: pkg },
        { title: '验收主页与包状态', detail: '等待卸载完成' }
      ];

  return `
    <div class="progress-head">
      <div class="progress-app">
        ${appLogo(app)}
        <div>
          <div class="progress-kicker">正在卸载</div>
          <div class="progress-title">${app.name}</div>
        </div>
      </div>
      <span class="badge badge-amber">不可取消</span>
    </div>
    <div class="progress-note">${toolFlow ? '安全顺序：停止服务 → 检查设备管理员 → 卸载 APK → 验收。默认桌面不会被改动。' : '安全顺序：停守护 → 切原桌面 → 卸载 APK → 验收。'}<br>请保持电视通电，应用数据会被删除。</div>
    ${app.needsPc ? `<div class="notice red">
      ${icon('warning', 15)}
      <div><strong>CleanYourTV 需要额外恢复</strong><p>你已确认电脑上的 restore.sh 完成；本次只卸载 launcher，不会再次清理系统应用。</p></div>
    </div>` : ''}
    ${stepsHTML('u', steps)}
    <div class="log-details">
      <summary>查看卸载日志</summary>
      <pre>[00:00] stop guard / listener
[00:00] home restore: ${wasDefault ? 'required' : 'not default'}
[00:01] pm uninstall ${pkg}
[00:02] verify default home
[00:02] done</pre>
    </div>
    <p class="fineprint">卸载完成后会读回默认桌面与包状态，确认没有留下空主页。</p>
  `;
}

function startUninstall(app) {
  if (busy) return;
  const wasDefault = app.category !== 'tool' && state.defaultHome === app.id;
  const seq = ++uninstallSeq;
  closeModal();
  sheetContext = { type: 'uninstall', appId: app.id };
  busy = true;
  $('#sheetBody').innerHTML = uninstallProgressHTML(app, wasDefault);
  showSheet();
  $('#sheetBody').scrollTop = 0;
  setStepState('u', 0, 'active', app.category === 'tool' ? '正在停止计时与悬浮层…' : '正在停止守护与通知监听…');
  runUninstall(seq, app, wasDefault).catch(() => {
    if (seq === uninstallSeq) {
      busy = false;
      toast('卸载流程中断，请检查电视连接');
    }
  });
}

async function runUninstall(seq, app, wasDefault) {
  const pkg = installedPackage(app);
  const toolFlow = app.category === 'tool';

  await wait(480);
  setStepState('u', 0, 'done', toolFlow ? '前台服务已停止 · 悬浮层已关闭' : (app.needsPc ? '已确认官方恢复 · 守护已停止' : '无障碍 / 通知监听已停止'));

  if (toolFlow) {
    setStepState('u', 1, 'active', '正在检查设备管理员…');
    await wait(460);
    setStepState('u', 1, 'done', '未检测到设备管理员限制');
  } else if (wasDefault) {
    setStepState('u', 1, 'active', '正在切回系统桌面…');
    await wait(560);
    state.defaultHome = 'stock';
    renderMain();
    setStepState('u', 1, 'done', `默认桌面 → ${STOCK_HOME}`);
  } else {
    setStepState('u', 1, 'skipped', '当前不是默认桌面，无需切换');
  }

  setStepState('u', 2, 'active', `正在卸载 ${pkg}…`);
  await wait(760);
  app.installed = false;
  app.installedVersion = '';
  app.installedVariant = '';
  setStepState('u', 2, 'done', 'APK 已卸载 · 应用数据已删除');

  setStepState('u', 3, 'active', toolFlow ? '正在确认默认桌面与包状态…' : '正在读回默认桌面与包状态…');
  await wait(520);
  setStepState('u', 3, 'done', toolFlow ? `默认桌面仍为 ${defaultHomeLabel()} · 包已移除` : `主页正常 · ${defaultHomeLabel()}`);

  busy = false;
  renderMain();
  sheetContext = { type: 'uninstall-success', appId: app.id, wasDefault, pkg };
  renderUninstallSuccess(app, wasDefault, pkg);
}

function renderUninstallSuccess(app, wasDefault, pkg) {
  const toolFlow = app.category === 'tool';
  $('#sheetBody').innerHTML = `
    <div class="sheet-title-row">
      <span class="sheet-eyebrow">卸载结果</span>
      ${closeBtn()}
    </div>
    <div class="result-hero">
      <div class="result-icon success">${icon('check', 29)}</div>
      <h2 class="result-title">已卸载 ${app.name}</h2>
      <p class="result-desc">${toolFlow
        ? `默认桌面保持为 ${defaultHomeLabel()}，主页与系统桌面均未受影响。`
        : wasDefault ? `默认桌面已切回 ${STOCK_HOME}，主页未留空。` : `当前默认桌面保持为 ${defaultHomeLabel()}。`}${app.needsPc ? ' 请确认电脑上的官方恢复脚本已执行完成。' : ''}</p>
    </div>
    <div class="info-list">
      <div class="info-row"><span class="info-key">包名</span><span class="info-val mono">${pkg}</span></div>
      <div class="info-row"><span class="info-key">应用数据</span><span class="info-val">已删除</span></div>
      <div class="info-row"><span class="info-key">默认桌面</span><span class="info-val ok">${defaultHomeLabel()}</span></div>
      ${toolFlow ? `<div class="info-row"><span class="info-key">设备管理员</span><span class="info-val">未更改 · 建议复查</span></div>` : ''}
    </div>
    ${toolFlow ? `<div class="notice">
      ${icon('info', 15)}
      <div><strong>如果电视上还看到“设备管理”</strong><p>请在电视设置中手动取消该应用的管理员权限；卸载器不会替用户更改这项系统授权。</p></div>
    </div>` : ''}
    ${app.needsPc ? `<div class="notice">
      ${icon('info', 15)}
      <div><strong>系统清理项需要单独恢复</strong><p>本应用只卸载了 CleanYourTV launcher；广告、遥测和预装应用的恢复由官方 restore.sh 完成。</p></div>
    </div>` : ''}
    <button class="btn btn-primary btn-block" style="margin-top:14px" data-action="close-sheet">完成</button>
    <p class="fineprint">系统桌面没有被卸载或停用。需要重装时，可以从详情页重新开始。</p>
  `;
  $('#sheetBody').scrollTop = 0;
}

/* ---------- my tv / initial setup ---------- */
function escapeAttr(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;');
}

function setupPrepPage() {
  const last = state.lastTv;
  return `
    <div class="page-head">
      <div>
        <span class="page-kicker">INITIAL SETUP · 初始准备</span>
        <h2 class="page-title">先把电视连起来</h2>
        <p class="page-sub">不用改电视系统。手机和电视连同一个 Wi-Fi，首次在电视上允许调试即可。</p>
      </div>
      ${badge('未连接', 'amber')}
    </div>

    <div class="setup-hero">
      <div class="setup-topline">
        <span>3 步建立局域网连接</span>
        <span>约 2 分钟</span>
      </div>
      <div class="setup-route" aria-hidden="true">
        <span class="route-node">${icon('wifi', 19)}</span>
        <span class="route-line"><i></i></span>
        <span class="route-badge">ADB</span>
        <span class="route-line"><i></i></span>
        <span class="route-node tv">${icon('tv', 19)}</span>
      </div>
      <div class="setup-hero-copy">
        <strong>推荐使用无线调试</strong>
        <p>Android 11+ 首次需要 6 位配对码；老电视可尝试经典网络 ADB 的 5555 端口。</p>
      </div>
      <button class="btn btn-primary btn-block" data-action="open-connect" data-mode="wireless">
        ${icon('link', 15)}开始无线连接
      </button>
      <button class="btn-text setup-usb-link" data-action="open-connect" data-mode="usb">
        电视没有无线调试？查看 USB 调试说明 →
      </button>
    </div>

    ${last ? `<button class="last-device-card" data-action="open-connect" data-mode="${last.transport === 'classic' ? 'classic' : 'wireless'}">
      <span class="last-device-icon">${icon('tv', 16)}</span>
      <span class="last-device-copy">
        <strong>上次连接 · ${last.label} ${last.model}</strong>
        <span class="mono">${tvEndpoint(last)} · ${transportLabel(last.transport)}</span>
      </span>
      <span class="last-device-cta">重连</span>
    </button>` : ''}

    <section class="page-section">
      <div class="page-section-head">
        <h3>初始准备清单</h3>
        <span>电视端操作</span>
      </div>
      <div class="prep-list">
        <div class="prep-step">
          <span class="prep-num">01</span>
          <span class="prep-copy">
            <strong>打开开发者选项</strong>
            <span>设置 → 关于电视 → 版本号，用遥控器连续点 7 次。</span>
          </span>
          <span class="prep-tag">电视</span>
        </div>
        <div class="prep-step">
          <span class="prep-num">02</span>
          <span class="prep-copy">
            <strong>开启无线调试或网络调试</strong>
            <span>Android 11+ 选“无线调试”；老固件找“网络调试 / ADB over network”。</span>
          </span>
          <span class="prep-tag">电视</span>
        </div>
        <div class="prep-step">
          <span class="prep-num">03</span>
          <span class="prep-copy">
            <strong>配对并在电视上允许</strong>
            <span>配对时保持弹窗打开；首次连接在电视上选择“始终允许”。</span>
          </span>
          <span class="prep-tag">配对</span>
        </div>
      </div>
    </section>

    <section class="page-section">
      <div class="page-section-head">
        <h3>选择连接方式</h3>
        <span>都走局域网</span>
      </div>
      <div class="method-list">
        <button class="method-card recommended" data-action="open-connect" data-mode="wireless" type="button">
          <span class="method-icon">${icon('wifi', 17)}</span>
          <span class="method-copy">
            <span class="method-title">无线调试 ${badge('推荐', 'blue')}</span>
            <span class="method-desc">Android 11+ · IP + 配对端口 + 6 位配对码</span>
          </span>
          <span class="method-tail">${icon('chevron', 14)}</span>
        </button>
        <button class="method-card" data-action="open-connect" data-mode="classic" type="button">
          <span class="method-icon amber">${icon('terminal', 17)}</span>
          <span class="method-copy">
            <span class="method-title">经典网络 ADB</span>
            <span class="method-desc">部分电视开放 5555 端口，可直接连接</span>
          </span>
          <span class="method-tail">${icon('chevron', 14)}</span>
        </button>
        <button class="method-card" data-action="open-connect" data-mode="usb" type="button">
          <span class="method-icon slate">${icon('usb', 17)}</span>
          <span class="method-copy">
            <span class="method-title">USB 调试（电视端）</span>
            <span class="method-desc">查看开关位置与授权步骤；连接仍建议无线</span>
          </span>
          <span class="method-tail">${icon('chevron', 14)}</span>
        </button>
      </div>
    </section>

    <div class="network-card">
      <span class="network-icon">${icon('wifi', 16)}</span>
      <div>
        <strong>手机与电视必须在同一局域网</strong>
        <p>访客 Wi-Fi 或路由器 AP 隔离会阻断 ADB。手机应用无法在连接前远程打开电视的调试开关。</p>
      </div>
    </div>
    <p class="fineprint">ADB 只在局域网内直连，不经过下载代理；请勿把 5555 端口映射到公网。</p>
  `;
}

function installedRowsHTML() {
  const installed = ORDER.filter((id) => APPS[id].installed).map((id) => {
    const app = APPS[id];
    return `<button class="installed-row" data-action="open-app" data-app="${app.id}" type="button">
      ${appLogo(app, 'installed-logo')}
      <span class="installed-copy">
        <span class="installed-name">${app.name}</span>
        <span class="installed-meta">${installedPackage(app)} · ${app.installedVersion}</span>
      </span>
      <span class="installed-tail">${state.defaultHome === id ? '默认' : (app.category === 'tool' ? '工具' : icon('chevron', 13))}</span>
    </button>`;
  }).join('');

  return `<div class="installed-list">
    ${installed}
    <div class="installed-row static">
      <span class="installed-logo system">${icon('lock', 14)}</span>
      <span class="installed-copy">
        <span class="installed-name">${STOCK_HOME}</span>
        <span class="installed-meta">系统桌面 · 不会卸载或停用</span>
      </span>
      <span class="installed-tail locked">${icon('lock', 12)}系统</span>
    </div>
  </div>`;
}

function connectedTvPage() {
  const tv = state.tv;
  const installed = ORDER.filter((id) => APPS[id].installed);
  const installedTool = installed.map((id) => APPS[id]).find((app) => app.category === 'tool');

  return `
    <div class="page-head">
      <div>
        <span class="page-kicker">MY TV · 已连接</span>
        <h2 class="page-title">${tv.label}</h2>
        <p class="page-sub">${tv.brand} · ${tv.model} · Android ${tv.android}（SDK ${tv.sdk}）</p>
      </div>
      ${badge('在线', 'green')}
    </div>

    <div class="tv-hero-card">
      <div class="tv-hero-top">
        <div class="tv-hero-icon">${icon('tv', 24)}</div>
        <div class="tv-hero-copy">
          <span class="tv-hero-name">${tv.label} · ${tv.model}</span>
          <span class="tv-endpoint mono">${tvEndpoint(tv)}</span>
        </div>
        <span class="live-pill"><i></i>已连接</span>
      </div>
      <div class="tv-tag-row">
        <span class="tv-tag">${transportLabel(tv.transport)}</span>
        <span class="tv-tag">ADB 已授权</span>
        <span class="tv-tag">仅局域网</span>
      </div>
      <div class="tv-stat-grid">
        <div class="tv-stat"><span>默认桌面</span><strong>${defaultHomeLabel()}</strong></div>
        <div class="tv-stat"><span>已安装</span><strong>${installed.length} / ${ORDER.length}</strong></div>
        <div class="tv-stat"><span>延迟</span><strong>${tv.latency || '18 ms'}</strong></div>
      </div>
    </div>

    <div class="device-quick-actions">
      <button class="quick-action" data-action="refresh-device" type="button">
        <span>${icon('refresh', 17)}</span>
        刷新状态
      </button>
      <button class="quick-action" data-action="open-connect" data-mode="${tv.transport}" type="button">
        <span>${icon('link', 17)}</span>
        配对信息
      </button>
      <button class="quick-action" data-action="open-help" type="button">
        <span>${icon('book', 17)}</span>
        连接帮助
      </button>
    </div>

    <section class="page-section">
      <div class="page-section-head">
        <h3>连接检查</h3>
        <span>只测必要项</span>
      </div>
      <div class="health-list">
        <div class="health-row">
          <span class="health-icon ok">${icon('check', 12)}</span>
          <span class="health-copy"><strong>调试授权</strong><span>电视已允许本机的调试密钥</span></span>
          <span class="health-state ok">已授权</span>
        </div>
        <div class="health-row">
          <span class="health-icon ok">${icon('check', 12)}</span>
          <span class="health-copy"><strong>ADB 通路</strong><span>${transportLabel(tv.transport)} · 端口 ${tv.port}</span></span>
          <span class="health-state ok">正常</span>
        </div>
        <div class="health-row">
          <span class="health-icon ok">${icon('check', 12)}</span>
          <span class="health-copy"><strong>网络边界</strong><span>局域网直连 · 下载代理不影响 ADB</span></span>
          <span class="health-state">直连</span>
        </div>
      </div>
    </section>

    <section class="page-section">
      <div class="page-section-head">
        <h3>已安装应用</h3>
        <span>${installed.length} / ${ORDER.length}</span>
      </div>
      ${installedRowsHTML()}
      ${installedTool ? `<button class="page-inline-link" data-action="open-permissions" data-app="${installedTool.id}" type="button">
        ${icon('shield', 13)}${installedTool.name} · 查看权限引导 ${icon('chevron', 12)}
      </button>` : ''}
    </section>

    <section class="page-section">
      <div class="page-section-head">
        <h3>系统配置</h3>
        <span>只影响本机</span>
      </div>
      <div class="config-list">
        <button class="switch-row" data-action="toggle-auto-reconnect" role="switch" aria-checked="${state.autoReconnect}" type="button">
          <span class="switch-copy">
            <span class="switch-title">下次自动找回电视</span>
            <span class="switch-desc">只记住上次的 IP 与连接方式，不保存配对码</span>
          </span>
          <span class="switch ${state.autoReconnect ? 'on' : ''}"></span>
        </button>
        <div class="config-row static">
          <span class="config-icon">${icon('search', 14)}</span>
          <span class="config-copy"><strong>发现方式</strong><span>mDNS 广播 + 5555 端口探测</span></span>
        </div>
        <button class="config-row" data-action="open-connect" data-mode="${tv.transport}" type="button">
          <span class="config-icon">${icon('link', 14)}</span>
          <span class="config-copy"><strong>配对 / 端口</strong><span>${tvEndpoint(tv)} · 重新配对不更改默认桌面</span></span>
          ${icon('chevron', 12)}
        </button>
        <button class="config-row" data-action="open-connect" data-mode="usb" type="button">
          <span class="config-icon">${icon('usb', 14)}</span>
          <span class="config-copy"><strong>USB 调试说明</strong><span>电视端开关位置与首次授权</span></span>
          ${icon('chevron', 12)}
        </button>
        <button class="config-row" data-action="restore-home" type="button" ${state.defaultHome === 'stock' ? 'disabled' : ''}>
          <span class="config-icon">${icon('power', 14)}</span>
          <span class="config-copy"><strong>切回电视原桌面</strong><span>当前：${defaultHomeLabel()}</span></span>
          ${icon('chevron', 12)}
        </button>
      </div>
      <button class="disconnect-row" data-action="disconnect" type="button">
        ${icon('power', 14)}断开连接
        <span>不会关闭电视上的调试开关</span>
      </button>
    </section>

    <p class="fineprint">ADB 等同电视的调试入口：只在可信局域网使用，不要暴露 5555 到公网。断开连接不会卸载任何应用，也不会更改默认桌面。</p>
  `;
}

function renderDevicePage() {
  const host = $('#deviceView');
  if (!host) return;
  host.innerHTML = state.connected ? connectedTvPage() : setupPrepPage();
}

/* ---------- connect sheet ---------- */
function connectTabsHTML() {
  const tabs = [
    { id: 'wireless', label: '无线调试' },
    { id: 'classic', label: '网络 ADB' },
    { id: 'usb', label: 'USB 调试' }
  ];
  return `<div class="segmented connect-modes">
    ${tabs.map((tab) => `<button class="${connectDraft.mode === tab.id ? 'active' : ''}" data-action="connect-mode" data-mode="${tab.id}" type="button">${tab.label}</button>`).join('')}
  </div>`;
}

function connectContextHTML() {
  return `<div class="connect-context">
    <span class="connect-context-icon">${icon('wifi', 14)}</span>
    <span>手机已连接 Wi-Fi · ADB 只在局域网内直连，不走下载代理</span>
  </div>`;
}

function scanResultHTML() {
  const tv = state.lastTv || DEFAULT_TV;
  const portNote = connectDraft.mode === 'classic'
    ? `${tv.ip}:5555 · 网络 ADB 可直接连接`
    : `${tv.ip} · 未发现配对端口，需从电视弹窗读取`;
  return `<div class="scan-result">
    <span class="scan-result-icon">${icon('tv', 16)}</span>
    <span class="scan-result-copy">
      <strong>${tv.label} · 同一网络</strong>
      <span>${portNote}</span>
    </span>
    <button class="btn btn-ghost btn-sm" data-action="use-scan" type="button">使用</button>
  </div>`;
}

function connectDetailsHTML(mode = 'wireless') {
  const classic = mode === 'classic';
  return `<div class="connect-details">
    <div class="connect-details-head">电视端怎么操作？</div>
    <ol>
      <li>设置 → 关于电视 → 版本号，连续点 7 次，开启开发者选项。</li>
      <li>开发者选项 → ${classic ? '网络调试 / ADB over network' : '无线调试（或网络调试 / ADB over network）'}。</li>
      ${classic
        ? '<li>网络 ADB 通常不需要配对码；不要做路由器端口转发，5555 只留在家庭局域网内。</li>'
        : '<li>首次无线配对时，打开“使用配对码配对设备”，保持弹窗不关闭。</li>'}
      <li>连接成功后，电视弹出的“允许 USB 调试”请选择始终允许。</li>
    </ol>
  </div>`;
}

function errorHTML() {
  return `<div class="form-error" id="connectError" ${connectDraft.error ? '' : 'hidden'} role="alert">${connectDraft.error}</div>`;
}

function wirelessPairFormHTML() {
  return `
    <div class="mode-note">
      <span class="mode-badge">推荐</span>
      <span>Android 11+ 首次连接需要配对。配对端口和 6 位配对码来自电视上当前打开的“使用配对码配对设备”弹窗。</span>
    </div>
    <div class="form-label">电视地址</div>
    <label class="field full">
      <span class="field-label">IP 地址</span>
      <input class="text-input" id="connectIp" value="${escapeAttr(connectDraft.ip)}" inputmode="decimal" placeholder="192.168.1.100" autocomplete="off" spellcheck="false">
    </label>
    <div class="scan-inline-row">
      <button class="btn btn-ghost btn-sm" data-action="scan-tv" type="button">${icon('search', 12)}搜索同一 Wi-Fi</button>
      <span>只探测，不自动连接</span>
    </div>
    ${connectDraft.scanDone ? scanResultHTML() : ''}
    <div class="connect-fields">
      <label class="field">
        <span class="field-label">配对端口</span>
        <input class="text-input" id="pairPort" value="${escapeAttr(connectDraft.pairPort)}" inputmode="numeric" placeholder="如 37001" autocomplete="off">
      </label>
      <label class="field">
        <span class="field-label">6 位配对码</span>
        <input class="text-input code-input" id="pairCode" value="${escapeAttr(connectDraft.pairCode)}" inputmode="numeric" maxlength="6" placeholder="000000" autocomplete="one-time-code">
      </label>
    </div>
    <p class="field-hint">配对码不是家长 PIN。它只用于本次配对，不会写入日志或上传。</p>
    ${errorHTML()}
    <button class="btn btn-primary btn-block" data-action="pair-start" type="button">
      ${icon('link', 15)}配对电视
    </button>
    <div class="connect-alt-row">
      <button class="btn-text" data-action="connect-mode" data-mode="classic" type="button">没有配对入口？改用网络 ADB →</button>
    </div>
    ${connectDetailsHTML()}
  `;
}

function wirelessLinkFormHTML() {
  return `
    <div class="pair-success-banner">
      <span class="pair-success-icon">${icon('check', 14)}</span>
      <div>
        <strong>配对成功</strong>
        <span>调试密钥已写入本机，不会上传；接下来连接电视的主调试端口。</span>
      </div>
    </div>
    <div class="form-label">连接端口</div>
    <label class="field full">
      <span class="field-label">电视“无线调试”页显示的 IP 地址和端口</span>
      <input class="text-input" id="mainPort" value="${escapeAttr(connectDraft.mainPort)}" inputmode="numeric" placeholder="如 37125" autocomplete="off">
    </label>
    <p class="field-hint">这是连接端口，不是刚才的配对端口。电视关闭无线调试或重启后，端口可能变化。</p>
    ${errorHTML()}
    <button class="btn btn-primary btn-block" data-action="wireless-link" type="button">
      ${icon('wifi', 15)}连接 ${connectDraft.ip}
    </button>
    <div class="connect-alt-row">
      <button class="btn-text" data-action="reset-pair" type="button">重新配对 / 修改配对码</button>
      <button class="btn-text" data-action="connect-abort" type="button">取消</button>
    </div>
    ${connectDetailsHTML(connectDraft.mode)}
  `;
}

function classicFormHTML() {
  return `
    <div class="mode-note neutral">
      <span class="mode-badge amber">兼容</span>
      <span>部分电视在开发者选项里开放“网络调试 / ADB over network”，端口通常是 5555。它不需要配对码。</span>
    </div>
    <div class="network-card compact">
      <span class="network-icon">${icon('warning', 15)}</span>
      <div><strong>只在自己家的局域网使用</strong><p>不要做路由器端口转发，也不要在公共 Wi-Fi 下开放 5555；ADB 授权等同于电脑调试权限。</p></div>
    </div>
    <div class="form-label">电视地址</div>
    <label class="field full">
      <span class="field-label">IP 地址</span>
      <input class="text-input" id="connectIp" value="${escapeAttr(connectDraft.ip)}" inputmode="decimal" placeholder="192.168.1.100" autocomplete="off" spellcheck="false">
    </label>
    <div class="connect-fields">
      <label class="field full">
        <span class="field-label">ADB 端口</span>
        <input class="text-input" id="classicPort" value="${escapeAttr(connectDraft.classicPort)}" inputmode="numeric" placeholder="5555" autocomplete="off">
      </label>
    </div>
    <div class="scan-inline-row">
      <button class="btn btn-ghost btn-sm" data-action="scan-tv" type="button">${icon('search', 12)}搜索开放 5555 的电视</button>
      <span>只探测，不自动连接</span>
    </div>
    ${connectDraft.scanDone ? scanResultHTML() : ''}
    <p class="field-hint">首次连接时电视会弹出“允许 USB 调试”，请在电视上选择允许。没有网络调试入口时，请改用无线调试或电脑。</p>
    ${errorHTML()}
    <button class="btn btn-primary btn-block" data-action="classic-link" type="button">
      ${icon('wifi', 15)}连接电视
    </button>
    <div class="connect-alt-row">
      <button class="btn-text" data-action="connect-mode" data-mode="wireless" type="button">Android 11+ 请改用无线调试配对 →</button>
    </div>
    ${connectDetailsHTML(connectDraft.mode)}
  `;
}

function usbGuideHTML() {
  const steps = [
    {
      title: '打开开发者选项',
      desc: '设置 → 系统 → 关于电视 → 版本号，用遥控器连续点 7 次。入口名称因品牌而异。'
    },
    {
      title: '打开 USB 调试',
      desc: '开发者选项 → USB 调试。若同时有“网络调试 / ADB over network”，也一并打开。'
    },
    {
      title: '在电视上允许授权',
      desc: '首次连接会弹出“允许 USB 调试”，用遥控器勾选始终允许并确认。'
    }
  ];
  const done = connectDraft.usbSteps.filter(Boolean).length;
  const stepsHtml = steps.map((step, index) => `
    <button class="usb-step ${connectDraft.usbSteps[index] ? 'checked' : ''}" data-action="toggle-usb-step" data-step="${index}" type="button">
      <span class="usb-check">${connectDraft.usbSteps[index] ? icon('check', 12) : ''}</span>
      <span class="usb-step-num">0${index + 1}</span>
      <span class="usb-step-copy">
        <strong>${step.title}</strong>
        <span>${step.desc}</span>
      </span>
    </button>
  `).join('');

  return `
    <div class="usb-callout">
      <span class="usb-callout-icon">${icon('usb', 18)}</span>
      <div>
        <strong>USB 调试是电视端的开关</strong>
        <p>手机应用无法在连接前远程打开它。这里指导你用遥控器完成设置；连接本身仍推荐走无线调试。</p>
      </div>
    </div>
    <div class="usb-facts">
      <div class="usb-fact">
        <span class="fact-label ok">能做</span>
        <p>在电视上开启开发者选项、USB 调试和网络调试，再用配对码完成一次授权。</p>
      </div>
      <div class="usb-fact">
        <span class="fact-label no">不能</span>
        <p>通过手机 App 远程打开这个开关；大多数手机也不能用数据线作为 ADB 主机直接安装。</p>
      </div>
    </div>
    <div class="form-label">电视端 3 步</div>
    <div class="usb-steps">${stepsHtml}</div>
    <div class="usb-progress">
      <span>已完成 ${done} / 3</span>
      <span class="usb-progress-track"><i style="width:${Math.round(done / 3 * 100)}%"></i></span>
    </div>
    <button class="btn btn-primary btn-block" data-action="usb-continue" type="button" ${done === 3 ? '' : 'disabled'}>
      ${icon('wifi', 15)}${done === 3 ? '去无线连接' : '先完成电视端设置'}
    </button>
    <div class="connect-alt-row">
      <button class="btn-text" data-action="connect-mode" data-mode="classic" type="button">改用网络 ADB（5555）</button>
      <button class="btn-text" data-action="copy-usb-steps" type="button">${icon('copy', 12)}复制步骤</button>
    </div>
    <div class="connect-details">
      <div class="connect-details-head">为什么不能用数据线直接装？</div>
      <ol>
        <li>大多数手机的 USB 口是“设备模式”，不是主机模式，不能像电脑一样向电视发 adb 命令。</li>
        <li>USB 调试开关只表示系统允许调试，不代表手机和电视之间已经有可用的数据通路。</li>
        <li>部分电视打开 USB 调试后会同时出现网络调试入口；仍以电视实际菜单为准。</li>
      </ol>
    </div>
    <div class="network-card compact">
      <span class="network-icon">${icon('warning', 15)}</span>
      <div><strong>只有 USB 调试、没有网络调试的电视</strong><p>本应用无法直接连接。需要先在电视上启用网络调试，或改用电脑按官方说明安装。</p></div>
    </div>
  `;
}

function connectProgressHTML() {
  const pairing = connectDraft.phase === 'pairing';
  const scanning = connectDraft.phase === 'scanning';
  const title = scanning ? '正在搜索同一 Wi-Fi 的电视'
    : pairing ? '正在配对调试密钥'
      : '正在建立 ADB 连接';
  const desc = scanning ? '只做局域网端口探测，不会自动连接，也不会触发电视授权弹窗。'
    : pairing ? '请保持电视上的“使用配对码配对设备”弹窗打开，配对码不会写入日志。'
      : '连接后请在电视上留意“允许 USB 调试”的授权弹窗。';
  const stageRows = scanning
    ? `<div class="connect-stage active"><span>1</span><div><strong>探测局域网</strong><span>mDNS 广播 + 5555 端口</span></div></div>
       <div class="connect-stage"><span>2</span><div><strong>整理候选</strong><span>不会自动连接</span></div></div>`
    : pairing
      ? `<div class="connect-stage active"><span>1</span><div><strong>发送配对请求</strong><span>${connectDraft.ip}:${connectDraft.pairPort || '配对端口'}</span></div></div>
         <div class="connect-stage"><span>2</span><div><strong>保存调试密钥</strong><span>等待电视确认</span></div></div>`
      : `<div class="connect-stage done"><span>${icon('check', 11)}</span><div><strong>${connectDraft.mode === 'classic' ? '端口可达' : '配对完成'}</strong><span>${connectDraft.ip}</span></div></div>
         <div class="connect-stage active"><span>2</span><div><strong>建立 ADB 连接</strong><span>${connectDraft.ip}:${connectDraft.mode === 'classic' ? connectDraft.classicPort : connectDraft.mainPort}</span></div></div>
         <div class="connect-stage"><span>3</span><div><strong>等待电视授权</strong><span>手机不会自动授权</span></div></div>`;

  return `
    <div class="connect-progress">
      <span class="progress-spinner">${icon('refresh', 19)}</span>
      <h3>${title}</h3>
      <p>${desc}</p>
      <div class="connect-stages">${stageRows}</div>
      <details class="log-details connect-log">
        <summary>查看连接日志</summary>
        <pre>${scanning ? `[00:00] scan lan (${connectDraft.mode === 'classic' ? 'port 5555' : 'mDNS + port'})
[00:01] probing, no auto-connect` : pairing ? `[00:00] adb pair ${connectDraft.ip}:${connectDraft.pairPort || '—'} ******
[00:01] waiting for tv confirmation`
      : `[00:00] ${connectDraft.mode === 'classic' ? `adb connect ${connectDraft.ip}:${connectDraft.classicPort}` : 'paired, key saved\n[00:00] adb connect ' + connectDraft.ip + ':' + connectDraft.mainPort}
[00:01] waiting for authorization`}</pre>
      </details>
      <button class="btn btn-ghost btn-block" data-action="${scanning ? 'cancel-scan' : 'connect-abort'}" type="button">
        ${scanning ? '取消搜索' : '取消连接'}
      </button>
    </div>
  `;
}

function authorizeHTML() {
  return `
    <div class="authorize-card">
      <div class="tv-screen">
        <div class="tv-screen-bar"><span></span><span></span><span></span> 电视屏幕</div>
        <div class="tv-prompt">
          <span class="tv-prompt-icon">${icon('shield', 17)}</span>
          <div>
            <strong>允许 USB 调试吗？</strong>
            <p>“工具市场”将获得对这台设备的调试权限。</p>
            <span class="prompt-check">${icon('check', 10)} 始终允许此计算机</span>
          </div>
        </div>
      </div>
      <h3>请在电视上选择“允许”</h3>
      <p>用遥控器确认。首次连接必须由电视端授权，手机应用不会替你点“允许”。</p>
      <button class="btn btn-primary btn-block" data-action="auth-confirm" type="button">
        ${icon('check', 15)}我已在电视上允许
      </button>
      <details class="connect-details">
        <summary>没看到授权弹窗？</summary>
        <ol>
          <li>确认电视没有熄屏，返回主页后再试一次。</li>
          <li>确认电视的 USB 调试或无线调试仍处于开启状态。</li>
          <li>在电视开发者选项里“撤销 USB 调试授权”，然后重新连接。</li>
          <li>检查路由器是否开启了 AP 隔离或访客网络。</li>
        </ol>
      </details>
      <button class="btn-text center-link" data-action="connect-abort" type="button">返回并重新填写</button>
    </div>
    <details class="log-details connect-log">
      <summary>查看连接日志</summary>
      <pre>[00:00] ${connectDraft.mode === 'classic' ? `adb connect ${connectDraft.ip}:${connectDraft.classicPort}` : `adb connect ${connectDraft.ip}:${connectDraft.mainPort}`}
[00:01] device unauthorized
[00:01] waiting for tv-side approval</pre>
    </details>
  `;
}

function connectSuccessHTML() {
  const tv = state.tv;
  return `
    <div class="result-hero">
      <div class="result-icon success">${icon('check', 29)}</div>
      <h2 class="result-title">已连接 ${tv.label}</h2>
      <p class="result-desc">${tvEndpoint(tv)} · ${transportLabel(tv.transport)} · ADB 授权成功</p>
    </div>
    <div class="info-list">
      <div class="info-row"><span class="info-key">IP / ADB</span><span class="info-val mono">${tvEndpoint(tv)}</span></div>
      <div class="info-row"><span class="info-key">连接方式</span><span class="info-val">${transportLabel(tv.transport)}</span></div>
      <div class="info-row"><span class="info-key">调试授权</span><span class="info-val ok">已允许</span></div>
      ${tv.transport === 'classic'
        ? `<div class="info-row"><span class="info-key">网络边界</span><span class="info-val">仅局域网 · 未做端口转发</span></div>`
        : `<div class="info-row"><span class="info-key">配对码</span><span class="info-val">已用完即丢弃</span></div>`}
      <div class="info-row"><span class="info-key">默认桌面</span><span class="info-val">${defaultHomeLabel()} · 未更改</span></div>
    </div>
    <div class="notice blue" style="margin-top:12px">
      ${icon('info', 15)}
      <div><strong>现在可以安装或管理工具</strong><p>连接只建立调试通路，不会安装应用、不会更改默认桌面，也不会打开设备管理员。</p></div>
    </div>
    <button class="btn btn-primary btn-block" style="margin-top:13px" data-action="connect-done" data-target="device" type="button">
      查看我的电视
    </button>
    <button class="btn btn-ghost btn-block" style="margin-top:8px" data-action="connect-done" data-target="market" type="button">
      去工具市场
    </button>
    <p class="fineprint">下次连接时 IP 或端口可能变化；应用只记住上次的地址，不保存配对码。</p>
  `;
}

function renderConnectSheet() {
  if (!sheetContext || sheetContext.type !== 'connect') {
    sheetContext = { type: 'connect', mode: connectDraft.mode };
  }
  sheetContext.mode = connectDraft.mode;
  const showTabs = connectDraft.mode === 'usb' || connectDraft.phase === 'idle' || connectDraft.phase === 'paired';
  let body;
  if (connectDraft.mode === 'usb') {
    body = usbGuideHTML();
  } else if (connectDraft.phase === 'success') {
    body = connectSuccessHTML();
  } else if (connectDraft.phase === 'pairing' || connectDraft.phase === 'linking' || connectDraft.phase === 'scanning') {
    body = connectProgressHTML();
  } else if (connectDraft.phase === 'authorize') {
    body = authorizeHTML();
  } else if (connectDraft.mode === 'classic') {
    body = classicFormHTML();
  } else if (connectDraft.phase === 'paired') {
    body = wirelessLinkFormHTML();
  } else {
    body = wirelessPairFormHTML();
  }

  setSheet(`
    <div class="sheet-title-row">
      <div>
        <span class="sheet-eyebrow">CONNECT · 局域网</span>
        <h2 class="sheet-title">连接电视</h2>
        <p class="sheet-sub">手机与电视在同一 Wi-Fi · 首次需要电视端授权</p>
      </div>
      ${closeBtn()}
    </div>
    ${showTabs ? connectTabsHTML() : ''}
    ${connectContextHTML()}
    ${body}
    <p class="fineprint">ADB 只连接你填写的电视 IP；不上传设备数据，不把 5555 暴露到公网。原型中的连接与配对为交互演示。</p>
  `);
}

function openConnect(mode = 'wireless', phase = 'idle') {
  const validMode = ['wireless', 'classic', 'usb'].includes(mode) ? mode : 'wireless';
  const tv = state.connected ? state.tv : (state.lastTv || DEFAULT_TV);
  connectTask += 1;
  connectDraft.mode = validMode;
  connectDraft.phase = phase === 'paired' ? 'paired' : 'idle';
  connectDraft.ip = tv.ip || DEFAULT_TV.ip;
  connectDraft.pairPort = '';
  connectDraft.pairCode = '';
  connectDraft.mainPort = String(tv.port || DEFAULT_TV.port || 37125);
  connectDraft.classicPort = String(tv.transport === 'classic' ? tv.port : 5555);
  connectDraft.scanDone = false;
  connectDraft.error = '';
  connectDraft.transport = validMode === 'classic' ? 'classic' : 'wireless';
  connectDraft.usbSteps = [false, false, false];
  sheetContext = { type: 'connect', mode: validMode };
  renderConnectSheet();
}

function setConnectError(message) {
  connectDraft.error = message || '';
  const el = $('#connectError');
  if (el) {
    el.textContent = connectDraft.error;
    el.hidden = !connectDraft.error;
    if (connectDraft.error) el.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }
}

function validHost(value) {
  const host = String(value || '').trim();
  if (/^(\d{1,3}\.){3}\d{1,3}$/.test(host)) {
    return host.split('.').every((part) => Number(part) >= 0 && Number(part) <= 255);
  }
  return /^[a-zA-Z0-9]([a-zA-Z0-9.-]{0,251}[a-zA-Z0-9])?$/.test(host);
}

function validPort(value) {
  const port = Number.parseInt(String(value || '').trim(), 10);
  return Number.isInteger(port) && port > 0 && port <= 65535;
}

function scheduleConnect(delay, fn) {
  const task = ++connectTask;
  setTimeout(() => {
    if (task !== connectTask) return;
    if (!sheetContext || sheetContext.type !== 'connect') return;
    fn();
  }, delay);
}

function completeConnection(transport) {
  const portValue = transport === 'classic' ? connectDraft.classicPort : connectDraft.mainPort;
  const port = Number.parseInt(portValue, 10);
  state.tv = {
    ...state.tv,
    ip: connectDraft.ip.trim(),
    port,
    transport,
    latency: transport === 'classic' ? '22 ms' : '16 ms'
  };
  state.connected = true;
  state.lastTv = { ...state.tv };
  connectDraft.pairCode = '';
  connectDraft.phase = 'success';
  connectDraft.transport = transport;
  renderConnectSheet();
  renderMain();
  toast(`已连接 ${state.tv.label} · ${transportLabel(transport)}`);
}

function startPair() {
  const ip = ($('#connectIp')?.value || connectDraft.ip || '').trim();
  const pairPort = ($('#pairPort')?.value || '').trim();
  const pairCode = ($('#pairCode')?.value || '').trim();
  if (!validHost(ip)) { setConnectError('请填写电视的 IP 地址，例如 192.168.1.100。'); return; }
  if (!validPort(pairPort)) { setConnectError('请填写电视无线调试弹窗里的配对端口（1–65535）。'); return; }
  if (!/^\d{6}$/.test(pairCode)) { setConnectError('配对码是电视上显示的 6 位数字，不是家长 PIN。'); return; }
  connectDraft.ip = ip;
  connectDraft.pairPort = pairPort;
  connectDraft.pairCode = pairCode;
  connectDraft.error = '';
  connectDraft.phase = 'pairing';
  renderConnectSheet();
  scheduleConnect(1050, () => {
    connectDraft.phase = 'paired';
    connectDraft.mainPort = connectDraft.mainPort || '37125';
    renderConnectSheet();
    toast('配对成功，请填写连接端口');
  });
}

function startClassicConnect() {
  const ip = ($('#connectIp')?.value || connectDraft.ip || '').trim();
  const port = ($('#classicPort')?.value || '5555').trim();
  if (!validHost(ip)) { setConnectError('请填写电视的 IP 地址，例如 192.168.1.100。'); return; }
  if (!validPort(port)) { setConnectError('连接端口必须是 1–65535 之间的数字，经典网络 ADB 通常是 5555。'); return; }
  connectDraft.ip = ip;
  connectDraft.classicPort = port;
  connectDraft.error = '';
  connectDraft.phase = 'linking';
  renderConnectSheet();
  scheduleConnect(850, () => {
    connectDraft.phase = 'authorize';
    renderConnectSheet();
  });
}

function startWirelessLink() {
  const port = ($('#mainPort')?.value || connectDraft.mainPort || '').trim();
  if (!validPort(port)) { setConnectError('请填写电视“无线调试”页面显示的连接端口（1–65535）。'); return; }
  connectDraft.mainPort = port;
  connectDraft.error = '';
  connectDraft.phase = 'linking';
  renderConnectSheet();
  scheduleConnect(850, () => {
    connectDraft.phase = 'authorize';
    renderConnectSheet();
  });
}

function cancelConnectTask(message) {
  connectTask += 1;
  if (message) toast(message);
  renderConnectSheet();
}

function useScanResult() {
  const tv = state.lastTv || DEFAULT_TV;
  connectDraft.ip = tv.ip;
  connectDraft.scanDone = false;
  if (connectDraft.mode === 'classic') connectDraft.classicPort = '5555';
  renderConnectSheet();
  toast(`已填入 ${tv.ip}，请确认端口后连接`);
}

function toggleUsbStep(index) {
  const i = Number(index);
  if (!Number.isInteger(i) || i < 0 || i > 2) return;
  connectDraft.usbSteps[i] = !connectDraft.usbSteps[i];
  renderConnectSheet();
}

const USB_STEPS_TEXT = `电视端 3 步：
1. 设置 → 系统 → 关于电视 → 版本号，连续点 7 次。
2. 开发者选项 → USB 调试（有网络调试 / ADB over network 也一并打开）。
3. 首次连接在电视上选择“始终允许”调试授权。`;
/* ---------- settings / projects / help ---------- */
function radioRow(value, label, desc) {
  return `<button class="radio-row" data-action="set-source" data-source="${value}" type="button">
    <span class="radio-dot ${state.source === value ? 'active' : ''}"></span>
    <span class="radio-copy">
      <span class="radio-label">${label}</span>
      <span class="radio-desc">${desc}</span>
    </span>
  </button>`;
}

function renderSettings() {
  sheetContext = { type: 'settings' };
  const proxyFields = state.proxy ? `
    <div class="proxy-fields">
      <label class="field">
        <span class="field-label">主机</span>
        <input class="text-input" value="127.0.0.1" aria-label="代理主机">
      </label>
      <label class="field port">
        <span class="field-label">端口</span>
        <input class="text-input" value="10815" aria-label="代理端口">
      </label>
    </div>
    <p class="settings-note" style="margin:0 12px 11px">开发环境请按需配置 HTTPS 代理。代理只用于下载安装包，ADB 始终直连电视。</p>
  ` : '';

  setSheet(`
    <div class="sheet-title-row">
      <div>
        <h2 class="sheet-title">设置</h2>
        <p class="sheet-sub">下载源、代理与安全策略</p>
      </div>
      ${closeBtn()}
    </div>

    <div class="settings-group">
      <h3 class="group-title">下载源优先级</h3>
      <div class="radio-group">
        ${radioRow('official', '官方优先（推荐）', '官方站 → Gitee → GitHub，失败时自动回退')}
        ${radioRow('gitee', 'Gitee 优先', 'Gitee 镜像优先，未收录时自动回退官方源')}
        ${radioRow('github', 'GitHub 优先', 'GitHub Release 优先，失败时回退官方站 / Gitee')}
      </div>
      <p class="settings-note">不写死文件名：桌面读取官方 Release / latest.json，题了个题读取 GitHub Release 或 Gitee 镜像。下载后始终校验 SHA-256 与包名。</p>
    </div>

    <div class="settings-group">
      <h3 class="group-title">网络</h3>
      <div class="option-list">
        <button class="switch-row" data-action="toggle-proxy" role="switch" aria-checked="${state.proxy}" type="button">
          <span class="switch-copy">
            <span class="switch-title">下载代理</span>
            <span class="switch-desc">只影响 APK 下载；局域网 ADB 不走代理</span>
          </span>
          <span class="switch ${state.proxy ? 'on' : ''}"></span>
        </button>
        ${proxyFields}
      </div>
    </div>

    <div class="settings-group">
      <h3 class="group-title">安全策略</h3>
      <div class="option-list">
        <div class="switch-row static">
          <span class="switch-copy">
            <span class="switch-title">桌面卸载前切回原桌面</span>
            <span class="switch-desc">不能关闭，避免 Home 键落在空桌面</span>
          </span>
          <span class="switch on locked"></span>
        </div>
        <div class="switch-row static">
          <span class="switch-copy">
            <span class="switch-title">安装后校验</span>
            <span class="switch-desc">SHA-256、包名、版本、签名、前台应用</span>
          </span>
          <span class="switch on locked"></span>
        </div>
        <button class="switch-row" data-action="toggle-keep-apk" role="switch" aria-checked="${state.keepApk}" type="button">
          <span class="switch-copy">
            <span class="switch-title">保留已校验的安装包</span>
            <span class="switch-desc">默认安装完成后清理缓存</span>
          </span>
          <span class="switch ${state.keepApk ? 'on' : ''}"></span>
        </button>
      </div>
      <p class="settings-note">不会卸载或停用系统桌面、系统应用。Aerial 的 app.orchard.tv 在卸载前必须核对实际应用名；工具应用不会更改默认 HOME。</p>
    </div>

    <div class="settings-group">
      <h3 class="group-title">工具市场</h3>
      <div class="action-list">
        <button class="action-item" data-action="open-curation">
          ${icon('plus', 16)}
          <span class="action-copy"><span class="action-title">收录新工具</span><span class="action-sub">查看审核标准与推荐模板</span></span>
          <span class="action-arrow">${icon('chevron', 14)}</span>
        </button>
      </div>
    </div>

    <div class="settings-group">
      <h3 class="group-title">关于</h3>
      <div class="installed-list">
        <div class="about-row"><span>版本</span><span class="about-val">0.2.0 · 设计原型</span></div>
        <button class="about-row actionable" data-action="open-projects" type="button">
          <span>项目与许可证</span>
          <span class="about-val">5 个桌面 + 1 个工具 ${icon('chevron', 12)}</span>
        </button>
      </div>
      <button class="btn-text reset-link" data-action="reset-demo">重置演示数据</button>
      <p class="settings-footer">仅演示界面，不会连接电视或下载文件。<br>所有版本与下载地址以官方渠道为准。</p>
    </div>
  `);
}

function renderProjects() {
  sheetContext = { type: 'projects' };
  const rows = ORDER.map((id) => {
    const app = APPS[id];
    return `<a class="project-row" href="${app.officialUrl}" target="_blank" rel="noreferrer">
      ${appLogo(app, 'project-logo')}
      <span class="project-copy">
        <span class="project-name">${app.name}${app.latin ? ` <span style="color:var(--muted);font-weight:500">${app.latin}</span>` : ''}</span>
        <span class="project-meta">${app.packageName}</span>
      </span>
      ${icon('external', 14)}
    </a>`;
  }).join('');

  setSheet(`
    <div class="sheet-title-row">
      <div>
        <h2 class="sheet-title">项目与许可证</h2>
        <p class="sheet-sub">5 个开源桌面 + 1 个答题工具</p>
      </div>
      ${closeBtn()}
    </div>
    <div class="project-list">${rows}</div>
    <div class="notice blue" style="margin-top:12px">
      ${icon('info', 15)}
      <div><strong>原型不包含任何 APK</strong><p>版本、大小和校验值应从官方 Release、latest.json 或 version.txt 动态读取。各项目遵循各自仓库许可证。</p></div>
    </div>
    <div class="notice">
      ${icon('info', 15)}
      <div><strong>题了个题使用“源码可见”许可</strong><p>StudyOnTV 的许可证为仅个人使用（Personal Use Only），不是 OSI 开源许可证。上架说明中会明确标注这一点。</p></div>
    </div>
    <p class="fineprint">PlanYourTV 与题了个题提供 Gitee 镜像；其他项目以官方站和 GitHub 为准。社区构建不是官方发布。</p>
  `);
}

function renderHelp() {
  sheetContext = { type: 'help' };
  setSheet(`
    <div class="sheet-title-row">
      <div>
        <h2 class="sheet-title">连接电视</h2>
        <p class="sheet-sub">局域网 ADB · 授权与排错</p>
      </div>
      ${closeBtn()}
    </div>

    <div class="help-block">
      <h3>1. 打开开发者选项</h3>
      <p>设置 → 关于电视 → 版本号，连续点击 7 次。不同品牌的入口名称略有不同。</p>
    </div>
    <div class="help-block">
      <h3>2. 开启网络调试</h3>
      <p>开发者选项 → USB 调试 / 网络调试 / 无线调试。Android 11+ 通常需要“无线调试 → 使用配对码配对”。</p>
    </div>
    <div class="help-block">
      <h3>3. 确认同一局域网</h3>
      <p>手机和电视连接同一个 Wi-Fi。企业网络或访客网络可能开启 AP 隔离，会阻断 ADB。</p>
    </div>
    <div class="help-block">
      <h3>4. 在电视上允许授权</h3>
      <p>首次连接时电视会弹出“允许 USB 调试”，勾选始终允许。若显示 unauthorized，先撤销授权再重试。</p>
    </div>
    <div class="help-block">
      <h3>5. 工具类应用的权限</h3>
      <p>题了个题不是桌面：安装后默认 HOME 不变，悬浮窗与使用情况访问按需开启；无障碍与设备管理员建议保持关闭。</p>
    </div>

    <div class="code-box">
      <div class="code-head">电脑排错命令</div>
      <pre class="code-body">adb connect ${state.tv.ip}:5555
adb devices
adb shell dumpsys window | grep mCurrentFocus
adb shell cmd package resolve-activity --brief -c android.intent.category.HOME</pre>
    </div>

    <div class="notice">
      ${icon('warning', 15)}
      <div><strong>不要暴露 5555 端口</strong><p>ADB 等同于电视的调试后门。只在可信局域网使用，不要做公网端口映射。</p></div>
    </div>
    <div class="notice blue">
      ${icon('info', 15)}
      <div><strong>复杂接管失败怎么办</strong><p>收录项目都提供官方 Agent 说明。复制详情页里的电脑指令，交给豆包电脑版或 Claude Code；不要套用其他项目的脚本。</p></div>
    </div>
    <p class="fineprint">原型不会真的发起 ADB 连接。</p>
  `);
}

/* ---------- curation / topic ---------- */
const CURATION_TEMPLATE = `工具名称：
官网 / 仓库：
适用设备：
核心特色（2–3 个）：
安装方式：APK / 脚本 / 电脑构建
卸载与恢复方式：
需要的权限及用途：
许可证：
已知风险：`;

function renderCuration() {
  sheetContext = { type: 'curation' };
  const featured = ['planyourtv', 'aerial', 'studyontv'];
  setSheet(`
    <div class="sheet-title-row">
      <div>
        <h2 class="sheet-title">收录说明</h2>
        <p class="sheet-sub">工具市场 · 人工审核</p>
      </div>
      ${closeBtn()}
    </div>

    <div class="topic-hero">
      <div class="topic-kicker">CURATION · 精选而非堆积</div>
      <h2 class="topic-title">好工具，值得被看见</h2>
      <p class="topic-desc">工具市场不追求数量。每个上架项目都要能说清楚来源、权限、安装与卸载路径，再由站长人工收录。</p>
      <div class="topic-icons">${featured.map((id) => appLogo(APPS[id])).join('')}</div>
    </div>

    <div class="curation-list">
      <div class="curation-item">${icon('check', 13)}<span><strong>来源可追溯：</strong>官网、官方 Release 或仓库，有固定包名、版本与校验值。</span></div>
      <div class="curation-item">${icon('check', 13)}<span><strong>权限讲得清：</strong>每项权限说明用途，默认最小化，敏感权限不自动授予。</span></div>
      <div class="curation-item">${icon('check', 13)}<span><strong>可安全卸载：</strong>有恢复路径，不卸载或停用系统桌面与系统应用。</span></div>
      <div class="curation-item">${icon('check', 13)}<span><strong>电视上可用：</strong>遥控器可完成主要操作，适配 Android TV / 大屏。</span></div>
    </div>

    <div class="curation-template">
      <div class="curation-template-head">
        <span>推荐模板</span>
        <button class="btn btn-ghost btn-sm" data-action="copy-curation">${icon('copy', 11)}复制</button>
      </div>
      <pre>${CURATION_TEMPLATE}</pre>
    </div>

    <div class="notice blue" style="margin-top:12px">
      ${icon('info', 15)}
      <div><strong>我会逐项核对后上架</strong><p>当前目录内置于客户端，后续会改为远程目录：客户端只展示已审核条目，并可单独更新，不需要发版。</p></div>
    </div>
    <button class="btn btn-primary btn-block" style="margin-top:12px" data-action="copy-curation">
      ${icon('copy', 15)}复制推荐模板
    </button>
    <p class="topic-footnote">本站不托管 APK。收录不代表安全背书，安装前仍应核对官方来源、签名与权限；许可证以项目仓库为准。</p>
  `);
}

function renderTopic() {
  sheetContext = { type: 'topic', id: 'study-first' };
  const featured = ['planyourtv', 'studyontv'];
  setSheet(`
    <div class="sheet-title-row">
      <div>
        <h2 class="sheet-title">精选专题</h2>
        <p class="sheet-sub">家庭观看 · 先学习后娱乐</p>
      </div>
      ${closeBtn()}
    </div>
    <div class="topic-hero">
      <div class="topic-kicker">COLLECTION · 02</div>
      <h2 class="topic-title">先学习，后娱乐</h2>
      <p class="topic-desc">一个负责桌面与时间规则，一个负责答题解锁。组合使用，让孩子自己完成“看什么、看多久”。</p>
      <div class="topic-icons">${featured.map((id) => appLogo(APPS[id])).join('')}</div>
    </div>
    <div class="topic-apps">
      ${featured.map((id) => {
        const app = APPS[id];
        return `<button class="topic-app" data-action="open-app" data-app="${app.id}" type="button">
          ${appLogo(app)}
          <span class="topic-app-copy">
            <span class="topic-app-name">${app.name}${badge(app.category === 'tool' ? '工具' : '桌面', app.category === 'tool' ? 'blue' : 'green')}</span>
            <span class="topic-app-meta">${app.tagline}</span>
          </span>
          <span class="action-arrow">${icon('chevron', 14)}</span>
        </button>`;
      }).join('')}
    </div>
    <p class="topic-footnote">专题是人工编辑的内容位，后续可在远程目录中配置；不适合的条目不会被放进专题。</p>
    <button class="btn btn-ghost btn-block" style="margin-top:12px" data-action="close-sheet">关闭</button>
  `);
}

/* ---------- shared actions ---------- */
function makeDefault(appId) {
  const app = APPS[appId];
  if (!app || !app.installed || app.category === 'tool') return;
  if (!state.connected) {
    toast('请先连接电视');
    return;
  }
  state.defaultHome = appId;
  renderMain();
  if (sheetContext && sheetContext.type === 'detail') renderDetail();
  else if (sheetContext && sheetContext.type === 'install-success') renderInstallSuccess();
  toast(`默认桌面已设为 ${app.name}`);
}

function restoreHome() {
  if (!state.connected) {
    toast('请先连接电视');
    return;
  }
  if (state.defaultHome === 'stock') {
    toast('当前已经是电视原桌面');
    return;
  }
  state.defaultHome = 'stock';
  renderMain();
  if (sheetContext && sheetContext.type === 'detail') renderDetail();
  toast(`已切回 ${STOCK_HOME}`);
}

function launchApp(appId) {
  const app = APPS[appId];
  if (!app) return;
  if (!state.connected) {
    toast('请先连接电视');
    return;
  }
  toast(`正在电视上打开 ${app.name}…`);
  setTimeout(() => toast(`已发送启动指令 · ${app.name}`), 700);
}

function disconnectDevice() {
  state.lastTv = { ...state.tv };
  state.connected = false;
  connectTask += 1;
  closeSheet(true);
  renderMain();
  toast('已断开设备连接，电视上的调试开关和默认桌面均未改变');
}

function cancelInstall() {
  if (!busy) return;
  installSeq += 1;
  busy = false;
  closeSheet(true);
  renderMain();
  toast('已取消安装');
}

function checkClean() {
  const app = APPS.cleanyourtv;
  if (app.installed) {
    toast(`已检测到 ${app.packageName} · ${app.installedVersion}`);
  } else {
    toast('未检测到 com.gathin.tvhome。完成电脑流程后再回来检测。');
  }
}

/* ---------- events ---------- */
document.addEventListener('click', (e) => {
  const el = e.target.closest('[data-action]');
  if (!el) return;
  const action = el.dataset.action;
  const appId = el.dataset.app;

  switch (action) {
    case 'open-app':
    case 'open-detail':
      openDetail(appId);
      break;
    case 'set-filter':
      state.filter = el.dataset.filter || 'all';
      renderSections();
      syncFilterUI();
      break;
    case 'clear-search': {
      state.search = '';
      const input = $('#searchInput');
      if (input) input.value = '';
      renderSections();
      syncFilterUI();
      break;
    }
    case 'reset-filters': {
      state.filter = 'all';
      state.search = '';
      const input = $('#searchInput');
      if (input) input.value = '';
      renderMain();
      break;
    }
    case 'open-curation':
      renderCuration();
      break;
    case 'copy-curation':
      copyText(CURATION_TEMPLATE, '推荐模板已复制');
      break;
    case 'open-permissions':
      renderPermissions(appId);
      break;
    case 'copy-permissions': {
      const app = APPS[appId];
      if (app) copyText(permissionCommandsFor(app), '权限指令已复制');
      break;
    }
    case 'switch-tab':
      switchTab(el.dataset.tab || 'market');
      break;
    case 'open-device':
      switchTab('device');
      break;
    case 'open-connect':
      openConnect(el.dataset.mode || (state.connected ? state.tv.transport : 'wireless'));
      break;
    case 'open-settings':
      renderSettings();
      break;
    case 'open-help':
      renderHelp();
      break;
    case 'open-projects':
      renderProjects();
      break;
    case 'close-sheet':
      closeSheet();
      break;
    case 'select-variant':
      if (sheetContext && sheetContext.type === 'detail') {
        sheetContext.variantId = el.dataset.variant;
        renderDetail();
      }
      break;
    case 'toggle-set-home':
      state.setHomeOnInstall = !state.setHomeOnInstall;
      if (sheetContext && sheetContext.type === 'detail') renderDetail();
      break;
    case 'toggle-permission-guide':
      state.openPermissionGuide = !state.openPermissionGuide;
      if (sheetContext && sheetContext.type === 'detail') renderDetail();
      break;
    case 'start-install':
      startInstall(appId);
      break;
    case 'cancel-install':
      cancelInstall();
      break;
    case 'copy-prompt': {
      const app = APPS[appId];
      if (app) copyText(promptFor(app), '已复制，粘贴给电脑上的 AI Agent');
      break;
    }
    case 'check-clean':
      checkClean();
      break;
    case 'uninstall':
      openUninstallModal(appId);
      break;
    case 'confirm-uninstall': {
      const app = APPS[appId];
      if (!app || !app.installed) break;
      if (app.needsPc) {
        const check = $('#cleanRestoreCheck');
        if (!check || !check.checked) {
          toast('请先确认已执行官方恢复脚本');
          break;
        }
      }
      startUninstall(app);
      break;
    }
    case 'close-modal':
      closeModal();
      break;
    case 'make-default':
      makeDefault(appId);
      break;
    case 'launch-app':
      launchApp(appId);
      break;
    case 'restore-home':
      restoreHome();
      break;
    case 'reconnect':
      openConnect(state.connected ? state.tv.transport : (state.lastTv?.transport || 'wireless'));
      break;
    case 'refresh-device':
      if (!state.connected) {
        openConnect('wireless');
        break;
      }
      renderMain();
      toast(`已刷新 ${state.tv.label} · ADB 正常 · ${state.tv.latency || '16 ms'}`);
      break;
    case 'toggle-auto-reconnect':
      state.autoReconnect = !state.autoReconnect;
      renderMain();
      toast(state.autoReconnect ? '已开启自动找回上次的电视' : '已关闭自动找回；仍可手动连接');
      break;
    case 'clean-cache':
      toast('下载缓存已清理（演示）');
      break;
    case 'disconnect':
      disconnectDevice();
      break;
    case 'connect-mode': {
      const mode = el.dataset.mode || 'wireless';
      if (mode === connectDraft.mode) break;
      connectTask += 1;
      connectDraft.mode = mode;
      connectDraft.phase = 'idle';
      connectDraft.error = '';
      connectDraft.scanDone = false;
      if (mode === 'classic') {
        connectDraft.transport = 'classic';
        connectDraft.classicPort = connectDraft.classicPort || '5555';
      } else if (mode === 'wireless') {
        connectDraft.transport = 'wireless';
      }
      renderConnectSheet();
      break;
    }
    case 'scan-tv':
      connectDraft.phase = 'scanning';
      connectDraft.error = '';
      renderConnectSheet();
      scheduleConnect(1000, () => {
        connectDraft.phase = 'idle';
        connectDraft.scanDone = true;
        renderConnectSheet();
        toast('找到同一网络的电视，已填入 IP；请核对端口');
      });
      break;
    case 'cancel-scan':
      connectTask += 1;
      connectDraft.phase = 'idle';
      renderConnectSheet();
      break;
    case 'use-scan':
      useScanResult();
      break;
    case 'pair-start':
      startPair();
      break;
    case 'wireless-link':
      startWirelessLink();
      break;
    case 'classic-link':
      startClassicConnect();
      break;
    case 'auth-confirm':
      completeConnection(connectDraft.mode === 'classic' ? 'classic' : 'wireless');
      break;
    case 'connect-abort':
      connectTask += 1;
      connectDraft.phase = connectDraft.mode === 'wireless' && connectDraft.pairCode ? 'pair' : 'idle';
      connectDraft.error = '';
      renderConnectSheet();
      break;
    case 'reset-pair':
      connectDraft.pairCode = '';
      connectDraft.pairPort = '';
      connectDraft.phase = 'pair';
      connectDraft.error = '';
      renderConnectSheet();
      break;
    case 'connect-done':
      closeSheet(true);
      switchTab(el.dataset.target === 'market' ? 'market' : 'device');
      break;
    case 'toggle-usb-step':
      toggleUsbStep(el.dataset.step);
      break;
    case 'usb-continue':
      connectDraft.mode = 'wireless';
      connectDraft.phase = 'idle';
      connectDraft.scanDone = false;
      connectDraft.error = '';
      renderConnectSheet();
      break;
    case 'copy-usb-steps':
      copyText(USB_STEPS_TEXT, '电视端步骤已复制');
      break;
    case 'set-source': {
      state.source = el.dataset.source;
      renderSettings();
      const labels = { official: '官方优先', gitee: 'Gitee 优先', github: 'GitHub 优先' };
      toast(`下载源已切换：${labels[state.source] || state.source}`);
      break;
    }
    case 'toggle-proxy':
      state.proxy = !state.proxy;
      renderSettings();
      toast(state.proxy ? '下载代理已开启（仅下载）' : '下载代理已关闭');
      break;
    case 'toggle-keep-apk':
      state.keepApk = !state.keepApk;
      renderSettings();
      break;
    case 'reset-demo':
      window.location.replace(window.location.pathname);
      break;
    default:
      break;
  }
});

document.addEventListener('input', (e) => {
  if (e.target.matches('#searchInput')) {
    state.search = e.target.value;
    renderSections();
    syncFilterUI();
    return;
  }
  if (e.target.matches('#connectIp')) {
    connectDraft.ip = e.target.value;
    return;
  }
  if (e.target.matches('#pairPort')) {
    connectDraft.pairPort = e.target.value;
    return;
  }
  if (e.target.matches('#pairCode')) {
    const digits = e.target.value.replace(/\D/g, '').slice(0, 6);
    if (e.target.value !== digits) e.target.value = digits;
    connectDraft.pairCode = digits;
    return;
  }
  if (e.target.matches('#mainPort')) {
    connectDraft.mainPort = e.target.value;
    return;
  }
  if (e.target.matches('#classicPort')) {
    connectDraft.classicPort = e.target.value;
  }
});

document.addEventListener('change', (e) => {
  if (e.target.matches('[data-action="toggle-clean-check"]')) {
    const btn = $('#confirmUninstall');
    if (btn) btn.disabled = !e.target.checked;
  }
});

document.addEventListener('keydown', (e) => {
  if (e.key !== 'Escape') return;
  if ($('#modalWrap').classList.contains('show')) {
    closeModal();
    return;
  }
  if (sheetOpen && !busy) {
    closeSheet();
    return;
  }
  if (!sheetOpen && state.search) {
    state.search = '';
    const input = $('#searchInput');
    if (input) input.value = '';
    renderSections();
    syncFilterUI();
  }
});

/* ---------- init / debug views ---------- */
function markInstalled(appId) {
  const app = APPS[appId];
  if (!app) return null;
  app.installed = true;
  app.installedVersion = app.latestVersion;
  app.installedVariant = app.defaultVariant || (app.variants[0] && app.variants[0].id) || '';
  return app;
}

function initPrototype() {
  const params = new URLSearchParams(window.location.search);
  const view = params.get('view');

  if (params.get('disconnected') === '1') state.connected = false;
  if (params.get('filter')) state.filter = params.get('filter');
  if (params.get('q')) state.search = params.get('q');
  if (params.get('home')) {
    const home = params.get('home');
    state.defaultHome = home === 'stock' || APPS[home] ? home : state.defaultHome;
  }
  if (params.get('installed')) {
    params.get('installed').split(',').forEach((id) => markInstalled(id.trim()));
  }
  if (params.get('studyInstalled') === '1') {
    markInstalled('studyontv');
  }
  if (params.get('cleanInstalled') === '1') {
    markInstalled('cleanyourtv');
  }

  const input = $('#searchInput');
  if (input) input.value = state.search;

  if (view) document.documentElement.classList.add('debug-view');
  renderMain();

  if (!view) return;

  const appId = params.get('app');
  switch (view) {
    case 'device':
      switchTab('device');
      break;
    case 'settings':
      renderSettings();
      break;
    case 'projects':
      renderProjects();
      break;
    case 'curation':
      renderCuration();
      break;
    case 'topic':
      renderTopic();
      break;
    case 'connect': {
      const mode = params.get('mode') || 'wireless';
      openConnect(mode);
      const phase = params.get('phase');
      if (phase) {
        connectDraft.phase = phase;
        if (phase === 'paired') {
          connectDraft.pairPort = '37001';
          connectDraft.pairCode = '123456';
        }
        renderConnectSheet();
      }
      break;
    }
    case 'help':
      renderHelp();
      break;
    case 'detail':
      if (appId) openDetail(appId);
      break;
    case 'permissions':
      if (appId) renderPermissions(appId);
      break;
    case 'install':
      if (appId) {
        openDetail(appId);
        startInstall(appId);
      }
      break;
    case 'success':
      if (appId) {
        const app = markInstalled(appId);
        if (app) {
          if (app.category !== 'tool') state.defaultHome = app.id;
          renderMain();
          sheetContext = {
            type: 'install-success',
            appId: app.id,
            variantId: app.defaultVariant,
            setHome: app.category !== 'tool',
            guide: app.category === 'tool'
          };
          showSheet();
          renderInstallSuccess();
        }
      }
      break;
    case 'uninstall':
      if (appId) {
        markInstalled(appId);
        renderMain();
        openDetail(appId);
        openUninstallModal(appId);
      }
      break;
    case 'uninstall-success':
      if (appId) {
        const app = markInstalled(appId);
        if (app) {
          const wasDefault = app.category !== 'tool' && state.defaultHome === app.id;
          const pkg = installedPackage(app);
          app.installed = false;
          app.installedVersion = '';
          app.installedVariant = '';
          if (wasDefault) state.defaultHome = 'stock';
          renderMain();
          sheetContext = { type: 'uninstall-success', appId: app.id, wasDefault, pkg };
          showSheet();
          renderUninstallSuccess(app, wasDefault, pkg);
        }
      }
      break;
    default:
      break;
  }
}

window.addEventListener('DOMContentLoaded', initPrototype);