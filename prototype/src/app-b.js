/* ---------- detail ---------- */
function closeBtn() {
  return `<button class="sheet-close" data-action="close-sheet" aria-label="关闭">${icon('close', 17)}</button>`;
}

function openDetail(appId) {
  const app = APPS[appId];
  if (!app) return;
  sheetContext = {
    type: 'detail',
    appId,
    variantId: app.installed ? (app.installedVariant || app.defaultVariant) : app.defaultVariant
  };
  renderDetail();
  showSheet();
  $('#sheetBody').scrollTop = 0;
}

function statusBadges(app) {
  let html = app.installed ? badge('已安装', 'green') : badge('未安装', 'muted');
  if (state.defaultHome === app.id) html += badge('默认桌面', 'green');
  if (app.isNew) html += badge('新上架', 'orange');
  if (app.testBuild) html += badge('测试版', 'amber');
  if (app.needsPc) html += badge('需电脑', 'amber');
  if (app.category === 'tool') html += badge('工具应用', 'blue');
  return html;
}

function renderDetail() {
  if (!sheetContext || sheetContext.type !== 'detail') return;
  const app = APPS[sheetContext.appId];
  if (!app) return;
  const variant = variantFor(app, sheetContext.variantId);
  let body;
  if (app.needsPc) body = app.installed ? installedBody(app, variant) : cleanBody(app);
  else if (app.installed) body = app.category === 'tool' ? installedToolBody(app, variant) : installedBody(app, variant);
  else body = app.category === 'tool' ? toolInstallBody(app, variant) : installBody(app, variant);

  $('#sheetBody').innerHTML = `
    <div class="detail-head">
      ${appLogo(app, 'detail-logo')}
      <div class="detail-head-copy">
        <h2 class="detail-name">${app.name}</h2>
        ${app.latin ? `<div class="detail-latin">${app.latin}</div>` : ''}
        <p class="detail-tagline">${app.tagline}</p>
        <div class="detail-status">${statusBadges(app)}</div>
      </div>
      ${closeBtn()}
    </div>
    <div class="detail-features">${featureChips(app)}</div>
    ${body}
  `;
}

function installedBody(app, variant) {
  const pkg = installedPackage(app);
  const isDefault = state.defaultHome === app.id;
  const versionLabel = variant ? variant.label : '官方构建';

  let notice = '';
  if (app.needsPc) {
    notice = `<div class="notice">
      ${icon('warning', 15)}
      <div><strong>卸载不会恢复被停用的系统应用</strong><p>请先在电脑运行官方 restore.sh 恢复清理项与默认 HOME，再回来卸载 launcher。</p></div>
    </div>`;
  } else if (app.id === 'planyourtv') {
    notice = `<div class="notice">
      ${icon('warning', 15)}
      <div><strong>数据会一起删除</strong><p>家长规则、题库、壁纸、照片和一次性恢复码都保存在应用数据里，卸载后不可恢复。</p></div>
    </div>`;
  } else if (app.id === 'aerial') {
    notice = `<div class="notice">
      ${icon('warning', 15)}
      <div><strong>包名共用提示</strong><p>app.orchard.tv 也可能属于旧版 HaloTV。卸载前请在电视上核对应用名和版本。</p></div>
    </div>`;
  } else if (app.id === 'halotv') {
    notice = `<div class="notice">
      ${icon('info', 15)}
      <div><strong>独立包名与签名</strong><p>只操作 app.halotv.launcher（Lite 为 .lite），不会动 Aerial 的 app.orchard.tv。</p></div>
    </div>`;
  } else if (app.id === 'quiettv') {
    notice = `<div class="notice">
      ${icon('info', 15)}
      <div><strong>测试版与厂商预装</strong><p>官方索引会把新版标为测试版；部分固件里的 com.coocaa.quiettv 不是普通用户安装包，不会被当作本应用处理。</p></div>
    </div>`;
  }

  return `
    <div class="action-row">
      ${state.connected
        ? `<button class="btn btn-primary" data-action="launch-app" data-app="${app.id}">${icon('play', 14)}在电视上打开</button>`
        : `<button class="btn btn-primary" data-action="open-connect">${icon('wifi', 14)}先连接电视</button>`}
      ${isDefault
        ? `<button class="btn btn-ghost" disabled>已是默认桌面</button>`
        : `<button class="btn btn-ghost" data-action="make-default" data-app="${app.id}">设为默认桌面</button>`}
    </div>

    <div class="info-list">
      <div class="info-row"><span class="info-key">当前版本</span><span class="info-val">${app.installedVersion}</span></div>
      <div class="info-row"><span class="info-key">包名</span><span class="info-val mono">${pkg}</span></div>
      <div class="info-row"><span class="info-key">版本档</span><span class="info-val">${versionLabel}</span></div>
      <div class="info-row"><span class="info-key">最低系统</span><span class="info-val">${app.minAndroid}</span></div>
      <div class="info-row"><span class="info-key">默认桌面</span><span class="info-val">${isDefault ? '是' : `否 · ${defaultHomeLabel()}`}</span></div>
    </div>

    ${notice}
    <div class="divider"></div>
    <div style="text-align:center">
      <button class="btn-text" data-action="copy-prompt" data-app="${app.id}">${icon('copy', 13)}复制官方电脑安装 / 修复指令 →</button>
    </div>
    <button class="btn btn-danger btn-block" style="margin-top:4px" data-action="uninstall" data-app="${app.id}">
      ${icon('trash', 15)}${app.needsPc ? '卸载 launcher（需先恢复）' : `卸载 ${app.name}`}
    </button>
    <p class="fineprint">卸载前会先停止守护并切回原桌面；不会卸载或停用电视系统桌面。</p>
  `;
}

function installedToolBody(app, variant) {
  const pkg = installedPackage(app);
  const versionLabel = variant ? variant.label : '官方构建';
  return `
    <div class="action-row">
      ${state.connected
        ? `<button class="btn btn-primary" data-action="launch-app" data-app="${app.id}">${icon('play', 14)}在电视上打开</button>`
        : `<button class="btn btn-primary" data-action="open-connect">${icon('wifi', 14)}先连接电视</button>`}
      <button class="btn btn-ghost" data-action="open-permissions" data-app="${app.id}">${icon('shield', 14)}权限引导</button>
    </div>

    <div class="notice blue">
      ${icon('info', 15)}
      <div><strong>它不会接管电视桌面</strong><p>${app.name} 是答题与观看时间管理工具，安装后默认 HOME 保持为 ${defaultHomeLabel()}，不会卸载或停用系统桌面。</p></div>
    </div>

    <div class="info-list" style="margin-top:12px">
      <div class="info-row"><span class="info-key">当前版本</span><span class="info-val">${app.installedVersion}</span></div>
      <div class="info-row"><span class="info-key">包名</span><span class="info-val mono">${pkg}</span></div>
      <div class="info-row"><span class="info-key">版本档</span><span class="info-val">${versionLabel}</span></div>
      <div class="info-row"><span class="info-key">家长面板</span><span class="info-val mono">http://${state.tv.ip}:8099</span></div>
      <div class="info-row"><span class="info-key">默认桌面</span><span class="info-val">未更改 · ${defaultHomeLabel()}</span></div>
      <div class="info-row"><span class="info-key">许可证</span><span class="info-val warn">${app.licenseNote || '以官方说明为准'}</span></div>
    </div>

    <div class="notice">
      ${icon('warning', 15)}
      <div><strong>卸载会删除题库、规则与排行榜</strong><p>如果开启过设备管理员，请先在电视的系统设置中取消，再执行卸载。远程管理页 8099 只在局域网内使用。</p></div>
    </div>

    <div class="divider"></div>
    <div style="text-align:center">
      <button class="btn-text" data-action="copy-prompt" data-app="${app.id}">${icon('copy', 13)}复制官方安装 / 权限指令 →</button>
    </div>
    <button class="btn btn-danger btn-block" style="margin-top:4px" data-action="uninstall" data-app="${app.id}">
      ${icon('trash', 15)}卸载 ${app.name}
    </button>
    <p class="fineprint">卸载不会更改默认桌面，也不会影响系统应用与系统桌面。</p>
  `;
}

function installNotice(app, variant) {
  if (app.id === 'planyourtv') {
    return `<div class="notice blue">
      ${icon('info', 15)}
      <div><strong>安装不只是装 APK</strong><p>官方安装器还会处理通知监听、悬浮窗、Home 接管与验收；如果固件拒绝桌面类 APK，改用 Guard。</p></div>
    </div>`;
  }
  if (app.id === 'quiettv') {
    return `<div class="notice">
      ${icon('warning', 15)}
      <div><strong>当前公开版是测试版</strong><p>官方 latest.json 把新版标为 prerelease。安装器会读取官方索引，并核对 url、bytes 与 sha256。</p></div>
    </div>`;
  }
  if (app.id === 'halotv') {
    return `<div class="notice">
      ${icon('warning', 15)}
      <div><strong>权限不会继承</strong><p>HaloTV 使用独立包名和签名，可与 Aerial 共存；安装后需要重新确认通知、无障碍和悬浮窗。</p></div>
    </div>`;
  }
  if (app.id === 'aerial' && variant && variant.id === 'lite') {
    return `<div class="notice">
      ${icon('warning', 15)}
      <div><strong>Lite 是低版本兼容档</strong><p>面向上游 Android 4.x，功能较少；新电视优先标准版。</p></div>
    </div>`;
  }
  if (app.id === 'studyontv') {
    return `<div class="notice ${variant && variant.id === 'localonly' ? 'blue' : ''}">
      ${icon(variant && variant.id === 'localonly' ? 'shield' : 'info', 15)}
      <div><strong>${variant && variant.id === 'localonly' ? '零公网 · 完全本地' : '标准版会联网使用在线能力'}</strong><p>${variant && variant.id === 'localonly'
        ? '本地版不访问公网，在线 AI、语音与题库更新不可用；所有核心规则在本机运行。'
        : '标准版可使用在线 AI、语音与更新；如只希望本机运行，切换到 TV 本地版。'}</p></div>
    </div>
    <div class="notice" style="margin-top:8px">
      ${icon('info', 15)}
      <div><strong>许可证：${app.licenseNote}</strong><p>该项目是“源码可见”的自用授权，不是 OSI 开源许可证。安装前请确认使用范围。</p></div>
    </div>`;
  }
  return '';
}

function installBody(app, variant) {
  const selected = variant || app.variants[0];
  const setHome = state.setHomeOnInstall;

  return `
    <div class="form-label" style="margin-top:15px">选择版本</div>
    <div class="segmented">
      ${app.variants.map((v) => `<button data-action="select-variant" data-variant="${v.id}" class="${v.id === selected.id ? 'active' : ''}">${v.label}</button>`).join('')}
    </div>
    <p class="field-hint">${selected.note} · ${selected.size} · ${selected.minAndroid}</p>

    <div class="info-list" style="margin-top:14px">
      <div class="info-row"><span class="info-key">最新版本</span><span class="info-val">${app.latestLabel}</span></div>
      <div class="info-row"><span class="info-key">包名</span><span class="info-val mono">${selected.packageName}</span></div>
      <div class="info-row"><span class="info-key">下载顺序</span><span class="info-val">${sourceOrder(app)}</span></div>
      <div class="info-row"><span class="info-key">来源</span><span class="info-val">${app.sourceShort}</span></div>
    </div>

    <div class="form-label">安装选项</div>
    <div class="option-list">
      <button class="option-row" data-action="toggle-set-home" role="switch" aria-checked="${setHome}">
        <span class="option-copy">
          <span class="option-title">安装后设为默认桌面</span>
          <span class="option-desc">Home 键直接进入 ${app.name}；可随时切回系统桌面</span>
        </span>
        <span class="switch ${setHome ? 'on' : ''}"></span>
      </button>
      <div class="option-row static">
        <span class="option-copy">
          <span class="option-title">安装后校验</span>
          <span class="option-desc">SHA-256、包名、版本、签名与前台应用</span>
        </span>
        <span class="option-pill">必选</span>
      </div>
    </div>

    ${installNotice(app, selected)}

    <button class="btn btn-primary btn-block" style="margin-top:14px" data-action="${state.connected ? 'start-install' : 'open-connect'}" data-app="${app.id}">
      ${state.connected ? icon('download', 15) : icon('wifi', 15)}
      ${state.connected ? '安装到电视' : '先连接电视'}
    </button>
    <div style="text-align:center;margin-top:4px">
      <button class="btn-text" data-action="copy-prompt" data-app="${app.id}">${icon('copy', 13)}复制官方 Agent 指令 →</button>
    </div>
    <p class="fineprint">同时安装多个桌面没问题，但不要同时开启多个 Home Guard。安装过程中请保持电视通电。</p>
  `;
}

function toolInstallBody(app, variant) {
  const selected = variant || app.variants[0];
  const guide = state.openPermissionGuide;

  return `
    <p class="detail-desc">${app.desc}</p>

    <div class="form-label">选择版本</div>
    <div class="segmented">
      ${app.variants.map((v) => `<button data-action="select-variant" data-variant="${v.id}" class="${v.id === selected.id ? 'active' : ''}">${v.label}</button>`).join('')}
    </div>
    <p class="field-hint">${selected.note} · ${selected.size} · ${selected.minAndroid}</p>

    <div class="info-list" style="margin-top:14px">
      <div class="info-row"><span class="info-key">最新版本</span><span class="info-val">${app.latestLabel}</span></div>
      <div class="info-row"><span class="info-key">包名</span><span class="info-val mono">${selected.packageName}</span></div>
      <div class="info-row"><span class="info-key">最低系统</span><span class="info-val">${selected.minAndroid}</span></div>
      <div class="info-row"><span class="info-key">下载顺序</span><span class="info-val">${sourceOrder(app)}</span></div>
      <div class="info-row"><span class="info-key">许可证</span><span class="info-val warn">${app.licenseNote}</span></div>
    </div>

    <div class="form-label">安装选项</div>
    <div class="option-list">
      <button class="option-row" data-action="toggle-permission-guide" role="switch" aria-checked="${guide}">
        <span class="option-copy">
          <span class="option-title">安装后打开权限引导</span>
          <span class="option-desc">悬浮窗、使用情况访问；无障碍与自启动可选</span>
        </span>
        <span class="switch ${guide ? 'on' : ''}"></span>
      </button>
      <div class="option-row static">
        <span class="option-copy">
          <span class="option-title">安装后校验</span>
          <span class="option-desc">SHA-256、包名、版本、签名与前台应用</span>
        </span>
        <span class="option-pill">必选</span>
      </div>
      <div class="option-row static">
        <span class="option-copy">
          <span class="option-title">默认桌面</span>
          <span class="option-desc">保持 ${defaultHomeLabel()}；工具应用不改 HOME</span>
        </span>
        <span class="option-pill">不更改</span>
      </div>
    </div>

    ${installNotice(app, selected)}

    <button class="btn btn-primary btn-block" style="margin-top:14px" data-action="${state.connected ? 'start-install' : 'open-connect'}" data-app="${app.id}">
      ${state.connected ? icon('download', 15) : icon('wifi', 15)}
      ${state.connected ? '安装到电视' : '先连接电视'}
    </button>
    <div style="text-align:center;margin-top:4px">
      <button class="btn-text" data-action="open-permissions" data-app="${app.id}">${icon('shield', 13)}先看权限说明 →</button>
    </div>
    <p class="fineprint">工具应用不会更改默认桌面；安装器也不会自动开启设备管理员。请保持电视通电。</p>
  `;
}

function cleanBody(app) {
  const prompt = promptFor(app);
  return `
    <div class="notice red" style="margin-top:14px">
      ${icon('warning', 15)}
      <div><strong>官方不提供可直接安装的 APK</strong><p>Release 说明明确要求用 launcher/build-and-install.sh 构建。完整价值也来自“按机型清理”，不是一个单独的桌面 APK。</p></div>
    </div>

    <div class="form-label">电脑三步完成</div>
    <div class="number-list">
      <div class="number-item">
        <div class="number-badge">1</div>
        <div class="number-copy"><strong>准备一台同局域网电脑</strong><br>Python 3、JDK 17–22、Android SDK 34 + Build Tools 34.x。</div>
      </div>
      <div class="number-item">
        <div class="number-badge">2</div>
        <div class="number-copy"><strong>把指令交给豆包电脑版 / Claude Code</strong><br>Agent 会备份、停用广告与预装应用、编译并切换默认桌面。</div>
      </div>
      <div class="number-item">
        <div class="number-badge">3</div>
        <div class="number-copy"><strong>回来检测安装状态</strong><br>只读取包名、版本和默认桌面，不会自动运行清理脚本。</div>
      </div>
    </div>

    <div class="prompt-box">
      <div class="prompt-head">
        <span>发给电脑上的 AI Agent</span>
        <button class="btn btn-ghost btn-sm" data-action="copy-prompt" data-app="${app.id}">${icon('copy', 11)}复制</button>
      </div>
      <div class="prompt-body">${prompt}</div>
    </div>

    <button class="btn btn-primary btn-block" style="margin-top:12px" data-action="copy-prompt" data-app="${app.id}">
      ${icon('copy', 15)}复制官方电脑指令
    </button>
    <a class="btn btn-ghost btn-block" style="margin-top:8px" href="${app.officialUrl}" target="_blank" rel="noreferrer">
      ${icon('external', 14)}打开官方项目页
    </a>
    <div style="text-align:center;margin-top:4px">
      <button class="btn-text" data-action="check-clean">安装完成后重新检测 →</button>
    </div>

    <div class="info-list" style="margin-top:14px">
      <div class="info-row"><span class="info-key">包名</span><span class="info-val mono">${app.packageName}</span></div>
      <div class="info-row"><span class="info-key">最新源码</span><span class="info-val">${app.latestLabel}</span></div>
      <div class="info-row"><span class="info-key">桌面最低系统</span><span class="info-val">${app.minAndroid}</span></div>
      <div class="info-row"><span class="info-key">下载源</span><span class="info-val">${sourceOrder(app)}</span></div>
    </div>

    <p class="fineprint">CleanYourTV 的完整安装会停用部分预装应用。卸载 launcher 之前，必须先在电脑运行官方 restore.sh。</p>
  `;
}

/* ---------- permissions ---------- */
function permissionsFor(app) {
  if (Array.isArray(app.permissionGuide) && app.permissionGuide.length) return app.permissionGuide;
  return [
    { title: '通知使用权', desc: '读取播放状态与通知，用于桌面场景适配。', optional: false },
    { title: '悬浮窗权限', desc: '用于桌面上的状态提示与辅助界面。', optional: false }
  ];
}

function permissionCommandsFor(app) {
  return app.permissionCommands || `adb shell dumpsys package ${installedPackage(app)} | grep granted=true`;
}

function renderPermissions(appId) {
  const app = APPS[appId];
  if (!app) return;
  sheetContext = { type: 'permissions', appId };
  const items = permissionsFor(app).map((item, index) => `<div class="permission-item ${item.optional ? 'optional' : ''}">
    <span class="permission-num">${index + 1}</span>
    <span class="permission-copy">
      <span class="permission-title">${item.title}${item.optional ? '<em>可选</em>' : ''}</span>
      <span class="permission-desc">${item.desc}</span>
    </span>
  </div>`).join('');

  const commands = permissionCommandsFor(app);

  setSheet(`
    <div class="sheet-title-row">
      <div>
        <h2 class="sheet-title">权限引导</h2>
        <p class="sheet-sub">${app.name} · 按需开启</p>
      </div>
      ${closeBtn()}
    </div>
    <div class="device-hero" style="margin-bottom:12px">
      ${appLogo(app, 'installed-logo')}
      <div class="device-hero-copy">
        <div class="device-hero-name">${app.name}</div>
        <div class="device-hero-meta">只在电视本机生效 · 不会更改默认桌面</div>
      </div>
    </div>
    <div class="permission-list">${items}</div>
    <div class="notice blue" style="margin-top:12px">
      ${icon('shield', 15)}
      <div><strong>最小权限原则</strong><p>先只开启悬浮窗与使用情况访问；无障碍与设备管理员按实际需要决定。任何权限都可以在电视的“应用权限”里关闭。</p></div>
    </div>
    <div class="code-box" style="margin-top:12px">
      <div class="code-head">可选：电脑端打开设置</div>
      <pre class="code-body">${commands}</pre>
    </div>
    <button class="btn btn-ghost btn-block" style="margin-top:10px" data-action="copy-permissions" data-app="${app.id}">${icon('copy', 14)}复制权限指令</button>
    <div class="action-row" style="margin-top:10px">
      ${state.connected ? `<button class="btn btn-primary" data-action="launch-app" data-app="${app.id}">${icon('play', 14)}打开应用</button>` : `<button class="btn btn-primary" data-action="open-connect">${icon('wifi', 14)}先连接电视</button>`}
      <button class="btn btn-ghost" data-action="close-sheet">完成</button>
    </div>
    <p class="fineprint">原型不会真的修改电视权限。实际安装请以官方说明和电视系统提示为准。</p>
  `);
}

/* ---------- install flow ---------- */
function stepsHTML(prefix, steps) {
  return `<div class="steps">${steps.map((step, i) => `
    <div class="step" id="${prefix}step-${i}">
      <div class="step-rail">
        <div class="step-dot">${i + 1}</div>
        <div class="step-line"></div>
      </div>
      <div class="step-copy">
        <div class="step-title">${step.title}</div>
        <div class="step-detail">${step.detail}</div>
        ${step.progress ? `<div class="step-progress" id="${prefix}prog-${i}">
          <div class="progress-track"><div class="progress-bar" id="${prefix}bar-${i}"></div></div>
          <div class="progress-meta"><span id="${prefix}left-${i}">准备下载</span><span id="${prefix}pct-${i}">0%</span></div>
        </div>` : ''}
      </div>
    </div>`).join('')}</div>`;
}

function setStepState(prefix, index, status, detail) {
  const step = document.getElementById(`${prefix}step-${index}`);
  if (!step) return;
  step.className = `step ${status}`;
  const dot = step.querySelector('.step-dot');
  if (dot) {
    if (status === 'done') dot.innerHTML = icon('check', 12);
    else if (status === 'active') dot.innerHTML = '<span class="spinner"></span>';
    else if (status === 'skipped') dot.textContent = '–';
    else if (status === 'fail') dot.textContent = '!';
    else dot.textContent = String(index + 1);
  }
  if (typeof detail === 'string') {
    const detailEl = step.querySelector('.step-detail');
    if (detailEl) detailEl.textContent = detail;
  }
  const prog = step.querySelector('.step-progress');
  if (prog) prog.style.display = status === 'active' ? 'block' : 'none';
}

function installProgressHTML(app, variant, opts) {
  const toolFlow = app.category === 'tool';
  const steps = toolFlow
    ? [
        { title: '检查电视与空间', detail: '正在读取 Android 版本与剩余空间' },
        { title: '下载安装包', detail: `${variant.size} · ${sourceOrder(app)}`, progress: true },
        { title: '校验 SHA-256', detail: '下载完成后比对官方校验值' },
        { title: '安装 APK', detail: variant.packageName },
        ...(opts.guide ? [{ title: '打开权限引导', detail: '悬浮窗 / 使用情况访问；无障碍可选' }] : []),
        { title: '启动并验收', detail: '检查应用是否正常启动；不更改默认桌面' }
      ]
    : [
        { title: '检查电视与空间', detail: '正在读取 Android 版本与剩余空间' },
        { title: '下载安装包', detail: `${variant.size} · ${sourceOrder(app)}`, progress: true },
        { title: '校验 SHA-256', detail: '下载完成后比对官方校验值' },
        { title: '安装 APK', detail: variant.packageName },
        { title: '授予必要权限', detail: '通知监听 / 悬浮窗 / 使用统计' },
        { title: opts.setHome ? '设为默认桌面' : '保持当前桌面', detail: opts.setHome ? `${defaultHomeLabel()} → ${app.name}` : '按设置跳过 HOME 切换' },
        { title: '启动并验收', detail: '检查前台应用与 Home 键' }
      ];

  return `
    <div class="progress-head">
      <div class="progress-app">
        ${appLogo(app)}
        <div>
          <div class="progress-kicker">正在安装</div>
          <div class="progress-title">${app.name}</div>
        </div>
      </div>
      <button class="btn btn-ghost btn-sm" data-action="cancel-install">取消</button>
    </div>
    <div class="progress-note">${variant.label} · ${variant.packageName}<br>下载源：${sourceOrder(app)}${toolFlow ? ' · 不会更改默认桌面' : ''}</div>
    ${stepsHTML('i', steps)}
    <details class="log-details">
      <summary>查看安装日志</summary>
      <pre>[00:00] adb connect ${state.tv.ip}:5555
[00:00] device authorized
[00:01] resolving ${app.id} / ${variant.id}
[00:01] cache: miss
[00:02] download source selected
[00:03] sha256 pending
[00:00] wait for sheet update…</pre>
    </details>
    <p class="fineprint">演示模式约 4 秒完成。真实安装会读取设备能力、官方版本索引并逐项验收。</p>
  `;
}

function startInstall(appId) {
  if (busy) return;
  const app = APPS[appId];
  if (!app || !app.installable) return;
  if (!state.connected) {
    toast('请先连接电视');
    openConnect('wireless');
    return;
  }
  const ctxVariant = sheetContext && sheetContext.type === 'detail' && sheetContext.appId === appId
    ? sheetContext.variantId
    : app.defaultVariant;
  const variant = variantFor(app, ctxVariant);
  if (!variant) return;

  const seq = ++installSeq;
  const opts = {
    setHome: app.category !== 'tool' && state.setHomeOnInstall,
    guide: app.category === 'tool' && state.openPermissionGuide
  };
  sheetContext = { type: 'install', appId, variantId: variant.id, ...opts };
  busy = true;
  $('#sheetBody').innerHTML = installProgressHTML(app, variant, opts);
  showSheet();
  $('#sheetBody').scrollTop = 0;
  setStepState('i', 0, 'active', '正在读取 Android 版本与剩余空间');
  runInstall(seq, app, variant, opts).catch(() => {
    if (seq === installSeq) {
      busy = false;
      toast('安装流程中断，请重试');
    }
  });
}

function animateDownload(prefix, index, duration, sizeText, seq) {
  return new Promise((resolve) => {
    const bar = document.getElementById(`${prefix}bar-${index}`);
    const left = document.getElementById(`${prefix}left-${index}`);
    const pct = document.getElementById(`${prefix}pct-${index}`);
    const parsed = parseFloat(String(sizeText).replace(/[^\d.]/g, ''));
    const size = Number.isFinite(parsed) ? parsed : 5;
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const actual = reduce ? 180 : duration;
    const started = performance.now();

    function tick(now) {
      if (seq !== installSeq) {
        resolve(false);
        return;
      }
      const t = Math.min(1, (now - started) / actual);
      const eased = 1 - Math.pow(1 - t, 3);
      const percent = Math.round(eased * 100);
      if (bar) bar.style.width = `${percent}%`;
      if (pct) pct.textContent = `${percent}%`;
      if (left) {
        const speed = (1.1 + Math.sin(percent / 18) * 0.25).toFixed(1);
        left.textContent = `${(size * eased).toFixed(1)} / ${size.toFixed(1)} MB · ${speed} MB/s`;
      }
      if (t < 1) requestAnimationFrame(tick);
      else resolve(true);
    }
    requestAnimationFrame(tick);
  });
}

async function runInstall(seq, app, variant, opts) {
  const alive = () => seq === installSeq;
  const toolFlow = app.category === 'tool';

  await wait(380);
  if (!alive()) return;
  setStepState('i', 0, 'done', `Android ${state.tv.android} · 空间 2.4 GB · ADB 已授权`);

  setStepState('i', 1, 'active', '正在读取官方最新版本…');
  await wait(280);
  if (!alive()) return;
  setStepState('i', 1, 'active', '正在下载安装包…');
  const downloaded = await animateDownload('i', 1, 1100, variant.size, seq);
  if (!downloaded) return;
  setStepState('i', 1, 'done', `已下载 · ${variant.size.replace('约 ', '')} · 校验值已获取`);

  setStepState('i', 2, 'active', '正在比对官方 SHA-256…');
  await wait(520);
  if (!alive()) return;
  setStepState('i', 2, 'done', '校验通过 · 文件完整');

  setStepState('i', 3, 'active', `正在安装 ${variant.packageName}`);
  await wait(850);
  if (!alive()) return;
  setStepState('i', 3, 'done', 'APK 安装成功');

  if (toolFlow) {
    let next = 4;
    if (opts.guide) {
      setStepState('i', next, 'active', '正在打开权限引导…');
      await wait(620);
      if (!alive()) return;
      setStepState('i', next, 'done', '权限引导已打开 · 可在电视上按需开启');
      next += 1;
    }
    setStepState('i', next, 'active', '正在启动并验收…');
    await wait(720);
    if (!alive()) return;
    setStepState('i', next, 'done', `前台应用正常 · ${defaultHomeLabel()} 保持不变`);
  } else {
    setStepState('i', 4, 'active', '正在授予通知监听 / 悬浮窗 / 使用统计');
    await wait(620);
    if (!alive()) return;
    setStepState('i', 4, 'done', '权限已授予');

    if (opts.setHome) {
      setStepState('i', 5, 'active', '正在切换 HOME role');
      await wait(620);
      if (!alive()) return;
      state.defaultHome = app.id;
      setStepState('i', 5, 'done', `默认桌面 → ${app.name}`);
    } else {
      setStepState('i', 5, 'skipped', '按设置保留当前桌面');
    }

    setStepState('i', 6, 'active', '正在启动并验收…');
    await wait(720);
    if (!alive()) return;
    setStepState('i', 6, 'done', '前台应用与 Home 键正常');
  }

  app.installed = true;
  app.installedVersion = app.latestVersion;
  app.installedVariant = variant.id;
  busy = false;
  renderMain();
  sheetContext = { type: 'install-success', appId: app.id, variantId: variant.id, ...opts };
  renderInstallSuccess();
}

function renderInstallSuccess() {
  if (!sheetContext || sheetContext.type !== 'install-success') return;
  const app = APPS[sheetContext.appId];
  const variant = variantFor(app, sheetContext.variantId);
  if (!app) return;
  const toolFlow = app.category === 'tool';
  const isDefault = state.defaultHome === app.id;

  if (toolFlow) {
    $('#sheetBody').innerHTML = `
      <div class="sheet-title-row">
        <span class="sheet-eyebrow">安装结果</span>
        ${closeBtn()}
      </div>
      <div class="result-hero">
        <div class="result-icon success">${icon('check', 29)}</div>
        <h2 class="result-title">已安装 ${app.name}</h2>
        <p class="result-desc">${app.name} ${app.installedVersion} 已安装到电视。它不是桌面应用，默认桌面保持为 ${defaultHomeLabel()}。</p>
      </div>
      <div class="info-list">
        <div class="info-row"><span class="info-key">应用</span><span class="info-val">${app.name} · ${variant ? variant.label : ''}</span></div>
        <div class="info-row"><span class="info-key">包名</span><span class="info-val mono">${variant ? variant.packageName : app.packageName}</span></div>
        <div class="info-row"><span class="info-key">SHA-256</span><span class="info-val ok">校验通过</span></div>
        <div class="info-row"><span class="info-key">默认桌面</span><span class="info-val">${defaultHomeLabel()} · 未更改</span></div>
        <div class="info-row"><span class="info-key">权限引导</span><span class="info-val ${sheetContext.guide ? 'ok' : ''}">${sheetContext.guide ? '已打开' : '未开启'}</span></div>
      </div>
      <div class="notice" style="margin-top:12px">
        ${icon('shield', 15)}
        <div><strong>先按需开启权限</strong><p>悬浮窗与使用情况访问是核心功能所需；无障碍与设备管理员按需决定，建议保持关闭。家长面板在电视 IP 的 8099 端口。</p></div>
      </div>
      <div class="action-row">
        <button class="btn btn-primary" data-action="launch-app" data-app="${app.id}">${icon('play', 14)}打开应用</button>
        <button class="btn btn-ghost" data-action="open-permissions" data-app="${app.id}">${icon('shield', 14)}权限引导</button>
      </div>
      <button class="btn btn-ghost btn-block" style="margin-top:8px" data-action="close-sheet">完成</button>
      <p class="fineprint">卸载不会更改默认桌面；如果开启过设备管理员，卸载前请先在电视上取消。</p>
    `;
    $('#sheetBody').scrollTop = 0;
    return;
  }

  $('#sheetBody').innerHTML = `
    <div class="sheet-title-row">
      <span class="sheet-eyebrow">安装结果</span>
      ${closeBtn()}
    </div>
    <div class="result-hero">
      <div class="result-icon success">${icon('check', 29)}</div>
      <h2 class="result-title">${isDefault ? '已安装并接管' : '安装完成'}</h2>
      <p class="result-desc">${app.name} ${app.installedVersion} 已安装${isDefault ? '，并设为默认桌面。按 Home 键验收即可。' : '，当前默认桌面保持不变。'}</p>
    </div>
    <div class="info-list">
      <div class="info-row"><span class="info-key">应用</span><span class="info-val">${app.name} · ${variant ? variant.label : ''}</span></div>
      <div class="info-row"><span class="info-key">包名</span><span class="info-val mono">${variant ? variant.packageName : app.packageName}</span></div>
      <div class="info-row"><span class="info-key">SHA-256</span><span class="info-val ok">校验通过</span></div>
      <div class="info-row"><span class="info-key">默认桌面</span><span class="info-val ${isDefault ? 'ok' : ''}">${isDefault ? app.name : defaultHomeLabel()}</span></div>
    </div>
    ${isDefault ? '' : `<div class="notice">
      ${icon('info', 15)}
      <div><strong>安装完成，但还没有接管 Home</strong><p>你没有勾选“设为默认桌面”。可以现在切换，也可以保留当前桌面。</p></div>
    </div>`}
    <div class="action-row">
      <button class="btn btn-ghost" data-action="launch-app" data-app="${app.id}">${icon('play', 14)}在电视上打开</button>
      <button class="btn btn-primary" data-action="close-sheet">完成</button>
    </div>
    ${isDefault ? '' : `<button class="btn btn-ghost btn-block" data-action="make-default" data-app="${app.id}">设为默认桌面</button>`}
    <p class="fineprint">如果 Home 键仍回到旧桌面，回到详情页复制官方电脑接管指令。</p>
  `;
  $('#sheetBody').scrollTop = 0;
}