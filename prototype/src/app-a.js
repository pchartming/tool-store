'use strict';

/* ---------- icons ---------- */
const ICONS = {
  check: '<path d="m5 12.5 4.5 4.5L19 5.5"/>',
  close: '<path d="M18 6 6 18M6 6l12 12"/>',
  chevron: '<path d="m9 18 6-6-6-6"/>',
  tv: '<rect x="3" y="7" width="18" height="12" rx="2.5"/><path d="m8.5 21 3.5-2 3.5 2"/><path d="M8 3l4 4 4-4"/>',
  warning: '<path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4"/><path d="M12 17h.01"/>',
  shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/><path d="m9 12 2 2 4-4"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5"/><path d="M12 8h.01"/>',
  trash: '<path d="M3 6h18"/><path d="M8 6V4h8v2"/><path d="m19 6-1 14H6L5 6"/><path d="M10 11v6M14 11v6"/>',
  download: '<path d="M12 3v12"/><path d="m7 10 5 5 5-5"/><path d="M5 21h14"/>',
  copy: '<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>',
  external: '<path d="M15 3h6v6"/><path d="M10 14 21 3"/><path d="M21 14v5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5"/>',
  terminal: '<path d="m4 17 6-6-6-6"/><path d="M12 19h8"/>',
  refresh: '<path d="M21 12a9 9 0 1 1-2.64-6.36"/><path d="M21 3v6h-6"/>',
  wifi: '<path d="M5 12.55a11 11 0 0 1 14 0"/><path d="M8.5 16.05a6 6 0 0 1 7 0"/><path d="M12 20h.01"/>',
  lock: '<rect x="4" y="10" width="16" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/>',
  play: '<path d="m8 5 11 7-11 7V5Z"/>',
  power: '<path d="M12 2v10"/><path d="M18.4 6.6a9 9 0 1 1-12.8 0"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  plus: '<circle cx="12" cy="12" r="9"/><path d="M12 8v8M8 12h8"/>',
  book: '<path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v16H6.5A2.5 2.5 0 0 0 4 21.5V5.5Z"/><path d="M8 7h8M8 11h6"/>',
  sliders: '<path d="M4 7h16M4 12h16M4 17h16"/><circle cx="9" cy="7" r="2"/><circle cx="15" cy="12" r="2"/><circle cx="8" cy="17" r="2"/>',
  store: '<path d="M5.2 8.2h13.6l-1.1 11.1H6.3L5.2 8.2Z"/><path d="M9 8.2V6.8a3 3 0 0 1 6 0v1.4"/><path d="M9.4 12.5h5.2"/>',
  usb: '<path d="M12 21V7"/><circle cx="12" cy="4.5" r="1.6"/><path d="m8.5 9 3.5-3.5L15.5 9"/><path d="M5.5 14h3v3h-3z"/><path d="M15.5 14h3v3h-3z"/><path d="M8.5 15.5H5.5M15.5 15.5h3"/>',
  link: '<path d="M10 13a5 5 0 0 0 7.1 0l2-2a5 5 0 0 0-7.1-7.1l-1.1 1.1"/><path d="M14 11a5 5 0 0 0-7.1 0l-2 2A5 5 0 0 0 12 20.1l1.1-1.1"/>'
};

function icon(name, size = 20) {
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONS[name] || ''}</svg>`;
}

/* ---------- catalog ---------- */
const APPS = {
  planyourtv: {
    id: 'planyourtv',
    name: 'PlanYourTV',
    tagline: '儿童桌面 · 答题 · 时间管理',
    desc: '专业的儿童电视桌面，把观看时间变成答题奖励，家长规则一目了然。',
    letter: 'P',
    color: '#2F6BFF',
    tint: 'rgba(47, 107, 255, .11)',
    border: 'rgba(47, 107, 255, .22)',
    category: 'launcher',
    features: ['儿童桌面', '答题系统', '时间管理'],
    packageName: 'com.planyourtv.tv',
    sourceShort: '官方 / Gitee',
    officialUrl: 'https://planyourtv.me/download/',
    repoUrl: 'https://planyourtv.me/',
    giteeUrl: 'https://gitee.com/liurumbo/planyourtv/releases',
    minAndroid: 'Android 5.0+',
    latestVersion: '1.96.000',
    latestLabel: '1.96.000',
    defaultVariant: 'standard',
    variants: [
      { id: 'standard', label: '标准版', note: '大多数电视', size: '约 29 MB', minAndroid: 'Android 5.0+', packageName: 'com.planyourtv.tv' },
      { id: 'guard', label: 'Guard', note: '固件拒绝桌面时', size: '约 29 MB', minAndroid: 'Android 5.0+', packageName: 'com.planyourtv.tv' }
    ],
    installed: true,
    installedVersion: '1.96.000',
    installedVariant: 'standard',
    testBuild: false,
    needsPc: false,
    installable: true,
    isNew: false,
    cardNote: null,
    agentPrompt: '请帮我把开源电视桌面 PlanYourTV 安装到我的电视上并设为默认桌面。电视 IP：【电视IP】。请先读取官方安装说明：https://planyourtv.me/agent.md ，优先使用官方安装器。最后贴出默认桌面和 mCurrentFocus 的验收结果，不要只报 Success。'
  },
  aerial: {
    id: 'aerial',
    name: 'Aerial',
    tagline: '去广告 · 保留系统桌面',
    desc: 'tvOS 风格轻桌面，去掉广告与推荐；不卸载、不停用电视原来的系统桌面。',
    letter: 'A',
    color: '#7350F2',
    tint: 'rgba(115, 80, 242, .1)',
    border: 'rgba(115, 80, 242, .22)',
    category: 'launcher',
    features: ['去广告', 'tvOS 风格', '不停用系统桌面'],
    packageName: 'app.orchard.tv',
    sourceShort: '官方 / GitHub',
    officialUrl: 'https://aerial.qust.me',
    repoUrl: 'https://github.com/pengchujin/aerial-tv',
    giteeUrl: '',
    minAndroid: 'Android 5.0+',
    latestVersion: '2.17',
    latestLabel: '2.17',
    defaultVariant: 'standard',
    variants: [
      { id: 'standard', label: '标准版', note: '大多数电视', size: '约 7.7 MB', minAndroid: 'Android 5.0+', packageName: 'app.orchard.tv' },
      { id: 'guard', label: 'Guard', note: '接管失败时', size: '约 7.7 MB', minAndroid: 'Android 5.0+', packageName: 'app.orchard.tv' },
      { id: 'lite', label: 'Lite', note: 'Android 4.x 老电视', size: '约 2.4 MB', minAndroid: 'Android 4.0–4.4', packageName: 'app.orchard.tv.lite' }
    ],
    installed: true,
    installedVersion: '2.17',
    installedVariant: 'standard',
    testBuild: false,
    needsPc: false,
    installable: true,
    isNew: false,
    cardNote: null,
    agentPrompt: '请帮我把开源电视桌面 Aerial 安装到我的电视上。电视 IP：【电视IP】。请先读取 https://aerial.qust.me/agent.md ，优先使用官方 install.sh，完成权限、默认桌面与验收；只操作 Aerial 自己的包，不要卸载电视系统桌面。卸载时使用官方 restore.sh。'
  },
  quiettv: {
    id: 'quiettv',
    name: 'Quiet TV',
    tagline: '极简安静 · 可配置',
    desc: '关闭推荐流与打扰，只留下干净、安静、可以按自己习惯配置的电视首页。',
    letter: 'Q',
    color: '#C2810A',
    tint: 'rgba(194, 129, 10, .11)',
    border: 'rgba(194, 129, 10, .23)',
    category: 'launcher',
    features: ['安静无推荐', '可配置', '轻量'],
    packageName: 'home.quiet.tv',
    sourceShort: '官方索引 / GitHub',
    officialUrl: 'https://beatsad.github.io/quiet-tv/',
    repoUrl: 'https://github.com/BeatsAD/quiet-tv',
    giteeUrl: '',
    minAndroid: 'Android 5.0+',
    latestVersion: '2.0.6',
    latestLabel: '2.0.6 · 测试版',
    defaultVariant: 'standard',
    variants: [
      { id: 'standard', label: '标准版', note: 'Android 5+ / 推荐', size: '约 4.9 MB', minAndroid: 'Android 5.0+', packageName: 'home.quiet.tv' },
      { id: 'legacy14', label: '4.x 兼容版', note: 'Android 4.0–4.4', size: '约 7.4 MB', minAndroid: 'Android 4.0–4.4', packageName: 'home.quiet.tv' }
    ],
    installed: false,
    installedVersion: '',
    installedVariant: '',
    testBuild: true,
    needsPc: false,
    installable: true,
    isNew: false,
    cardNote: null,
    agentPrompt: '请帮我把 Quiet TV 安装到我的电视上。电视 IP：【电视IP】。请先读取 https://beatsad.github.io/quiet-tv/agent.md ，从 latest.json 选择适配包并核对 SHA-256；先试用，再按官方路线接管 HOME。保留恢复记录，不卸载系统桌面。'
  },
  halotv: {
    id: 'halotv',
    name: 'HaloTV',
    tagline: '独立包名 · 可与 Aerial 共存',
    desc: 'Aerial 的独立包名分支，权限与数据互不影响，可与 Aerial 同时安装、各自管理。',
    letter: 'H',
    color: '#0E93B5',
    tint: 'rgba(14, 147, 181, .1)',
    border: 'rgba(14, 147, 181, .22)',
    category: 'launcher',
    features: ['独立包名', '与 Aerial 共存', '轻量'],
    packageName: 'app.halotv.launcher',
    sourceShort: '官方站 / GitHub',
    officialUrl: 'https://halo-tv.pages.dev',
    repoUrl: 'https://github.com/Null9797/halo-tv',
    giteeUrl: '',
    minAndroid: 'Android 5.0+',
    latestVersion: '3.5.4',
    latestLabel: '3.5.4',
    defaultVariant: 'standard',
    variants: [
      { id: 'standard', label: '标准版', note: '大多数电视', size: '约 7.7 MB', minAndroid: 'Android 5.0+', packageName: 'app.halotv.launcher' },
      { id: 'guard', label: 'Guard', note: '接管失败时', size: '约 7.7 MB', minAndroid: 'Android 5.0+', packageName: 'app.halotv.launcher' },
      { id: 'lite', label: 'Lite', note: 'Android 4+ / 轻量', size: '约 2.5 MB', minAndroid: 'Android 4.0+', packageName: 'app.halotv.launcher.lite' }
    ],
    installed: false,
    installedVersion: '',
    installedVariant: '',
    testBuild: false,
    needsPc: false,
    installable: true,
    isNew: false,
    cardNote: null,
    agentPrompt: '请帮我把独立版 HaloTV 安装到我的电视上。电视 IP：【电视IP】。请先读取 https://github.com/Null9797/halo-tv 的 INSTALL.md，只使用 HaloTV 自己的脚本，不要套用 Aerial 脚本，也不要卸载 app.orchard.tv。完成后验收权限、默认桌面和前台应用。'
  },
  cleanyourtv: {
    id: 'cleanyourtv',
    name: 'CleanYourTV',
    tagline: '精简快速 · 需电脑安装',
    desc: '精简启动器加系统清理，启动更快；安装需要电脑构建，卸载前必须先恢复系统项。',
    letter: 'C',
    color: '#0F9A67',
    tint: 'rgba(15, 154, 103, .1)',
    border: 'rgba(15, 154, 103, .22)',
    category: 'launcher',
    features: ['精简桌面', '响应更快', '系统清理'],
    packageName: 'com.gathin.tvhome',
    sourceShort: '官方脚本 · 无 APK',
    officialUrl: 'https://cleanyour.tv',
    repoUrl: 'https://github.com/Gathin23/cleanyourtv',
    giteeUrl: '',
    minAndroid: 'Android 8.0+（桌面）',
    latestVersion: '3.6.0',
    latestLabel: '3.6.0 · 源码',
    defaultVariant: '',
    variants: [],
    installed: false,
    installedVersion: '',
    installedVariant: '',
    testBuild: false,
    needsPc: true,
    installable: false,
    isNew: false,
    cardNote: { tone: 'warn', text: '安装需电脑构建；卸载前要先用官方 restore.sh 恢复系统项。' },
    agentPrompt: '请帮我把开源电视清理桌面 CleanYourTV 配置到我的电视上。电视 IP：【电视IP】。请先完整阅读 https://github.com/Gathin23/cleanyourtv 的说明和 PROMPT.md，按官方脚本备份、停用广告与预装应用、编译 launcher 并切换默认桌面。保留恢复路径，不要卸载系统桌面，最后报告未验证项与恢复方法。'
  },
  studyontv: {
    id: 'studyontv',
    name: '题了个题',
    latin: 'StudyOnTV',
    tagline: '答题换电视时间',
    desc: '先学习，后娱乐：孩子答题解锁电视时间，家长扫码管理题库与规则。',
    letter: '题',
    color: '#D9542B',
    tint: 'rgba(217, 84, 43, .1)',
    border: 'rgba(217, 84, 43, .22)',
    category: 'tool',
    features: ['答题解锁', '家长面板', '本地运行'],
    packageName: 'com.studyontv.tv',
    sourceShort: 'GitHub / Gitee',
    officialUrl: 'https://github.com/chadyi/StudyOnTV',
    releasesUrl: 'https://github.com/chadyi/StudyOnTV/releases/latest',
    repoUrl: 'https://github.com/chadyi/StudyOnTV',
    giteeUrl: 'https://gitee.com/chadyi/StudyOnTV/releases',
    minAndroid: 'Android 5.0+',
    latestVersion: '0.5.2',
    latestLabel: '0.5.2 · 2026-11-22',
    defaultVariant: 'standard',
    variants: [
      {
        id: 'standard',
        label: 'TV 标准版',
        note: '推荐 · 在线 AI / 语音',
        size: '约 3.5 MB',
        minAndroid: 'Android 5.0+',
        packageName: 'com.studyontv.tv',
        apkName: 'StudyOnTV-Android-TV-v0.5.2.apk',
        downloadUrl: 'https://github.com/chadyi/StudyOnTV/releases/download/v0.5.2/StudyOnTV-Android-TV-v0.5.2.apk'
      },
      {
        id: 'localonly',
        label: 'TV 本地版',
        note: '零公网 · 隐私优先',
        size: '约 3.5 MB',
        minAndroid: 'Android 5.0+',
        packageName: 'com.studyontv.tv',
        apkName: 'StudyOnTV-Android-TV-EU-LocalOnly-v0.5.2.apk',
        downloadUrl: 'https://github.com/chadyi/StudyOnTV/releases/download/v0.5.2/StudyOnTV-Android-TV-EU-LocalOnly-v0.5.2.apk'
      }
    ],
    installed: false,
    installedVersion: '',
    installedVariant: '',
    testBuild: false,
    needsPc: false,
    installable: true,
    isNew: true,
    kind: 'tool',
    setupOnInstall: true,
    permissions: '悬浮窗、使用情况访问；无障碍与自启动可选',
    permissionGuide: [
      { title: '显示在其他应用上层', desc: '用于答题层与解锁提醒；可在系统设置中随时关闭。', optional: false },
      { title: '使用情况访问', desc: '读取前台应用和使用时长，是计时与解锁规则的基础。', optional: false },
      { title: '无障碍服务', desc: '部分机型用于增强前台识别，属于可选项；不开启也能使用核心功能。', optional: true },
      { title: '开机自启动 / 后台运行', desc: '重启电视后恢复计时与家长规则；可在电视上关闭。', optional: true },
      { title: '设备管理员', desc: '仅用于加强防卸载。安装器不会自动开启；卸载前必须先取消。', optional: true }
    ],
    permissionCommands: `adb shell appops set com.studyontv.tv SYSTEM_ALERT_WINDOW allow
adb shell am start -a android.settings.USAGE_ACCESS_SETTINGS
adb shell am start -a android.settings.ACCESSIBILITY_SETTINGS`,
    licenseNote: '源码可见 · 仅个人使用',
    cardNote: { tone: 'info', text: '不是桌面，不会更改默认 HOME；权限可在电视上按需开启。' },
    agentPrompt: '请帮我在电视上安装 StudyOnTV（题了个题）。电视 IP：【电视IP】。优先从 https://github.com/chadyi/StudyOnTV/releases/latest 下载 TV 版 APK；GitHub 不可达时使用 Gitee 镜像 https://gitee.com/chadyi/StudyOnTV/releases 。执行 adb connect 与 adb install -r 后启动应用，并列出可在电视上按需开启的权限：悬浮窗、使用情况访问、无障碍、自启动。不要开启设备管理员，不要更改默认桌面，不要卸载系统应用。'
  }
};

const ORDER = ['planyourtv', 'aerial', 'quiettv', 'halotv', 'cleanyourtv', 'studyontv'];
const GROUPS = [
  { id: 'launcher', title: '热门桌面', desc: '换一个更安静、更顺手的电视首页' },
  { id: 'tool', title: '热门工具', desc: '不只换桌面，也让电视更好用' }
];
const STOCK_HOME = 'PatchWall（系统）';
const DEFAULT_TV = {
  label: '小米电视',
  brand: 'Xiaomi',
  model: 'MiTV-MFTR0',
  android: '14',
  sdk: 34,
  ip: '192.168.1.100',
  port: 37125,
  transport: 'wireless',
  latency: '18 ms'
};

const state = {
  tab: 'market',
  connected: true,
  tv: { ...DEFAULT_TV },
  lastTv: { ...DEFAULT_TV },
  defaultHome: 'planyourtv',
  source: 'official',
  proxy: false,
  setHomeOnInstall: true,
  openPermissionGuide: true,
  keepApk: false,
  autoReconnect: true,
  filter: 'all',
  search: ''
};

const connectDraft = {
  mode: 'wireless',
  phase: 'pair',
  ip: DEFAULT_TV.ip,
  pairPort: '',
  pairCode: '',
  mainPort: String(DEFAULT_TV.port),
  classicPort: '5555',
  scanDone: false,
  transport: DEFAULT_TV.transport,
  error: '',
  usbSteps: [false, false, false]
};

let sheetContext = null;
let sheetOpen = false;
let sheetCloseTimer = null;
let modalCloseTimer = null;
let toastTimer = null;
let busy = false;
let installSeq = 0;
let uninstallSeq = 0;
let connectTask = 0;

/* ---------- helpers ---------- */
const $ = (sel) => document.querySelector(sel);

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function appLogo(app, extraClass = '') {
  return `<span class="app-logo ${extraClass}" style="--logo-color:${app.color};--logo-tint:${app.tint};--logo-border:${app.border}">${app.letter}</span>`;
}

function badge(text, tone = 'muted') {
  return `<span class="badge badge-${tone}">${text}</span>`;
}

function variantFor(app, id) {
  if (!app || !app.variants || !app.variants.length) return null;
  return app.variants.find((v) => v.id === id) || app.variants.find((v) => v.id === app.defaultVariant) || app.variants[0];
}

function installedPackage(app) {
  const v = variantFor(app, app.installedVariant);
  return v ? v.packageName : app.packageName;
}

function defaultHomeLabel() {
  if (state.defaultHome === 'stock') return STOCK_HOME;
  return APPS[state.defaultHome] ? APPS[state.defaultHome].name : '未知';
}

function promptFor(app) {
  const ip = state.connected ? state.tv.ip : '【电视IP】';
  return app.agentPrompt.replace(/【电视IP】/g, ip);
}

function isTool(app) {
  return app.category === 'tool';
}

function sourceOrder(app) {
  if (app.needsPc) return '官方页面（无 APK）';
  if (app.id === 'studyontv') {
    if (state.source === 'gitee') return 'Gitee 镜像 → GitHub 官方 → 蒲公英备用';
    return 'GitHub 官方 → Gitee 镜像 → 蒲公英备用';
  }
  const hasGitee = Boolean(app.giteeUrl);
  if (state.source === 'gitee') {
    return hasGitee ? 'Gitee → 官方 → GitHub' : '官方 → GitHub（无 Gitee 镜像）';
  }
  if (state.source === 'github') {
    return hasGitee ? 'GitHub → 官方 → Gitee' : 'GitHub → 官方';
  }
  return hasGitee ? '官方 → Gitee → GitHub' : '官方 → GitHub';
}

function featureChips(app, extra = '') {
  return app.features.map((text) => (
    `<span class="feature-chip ${extra}" style="--chip-color:${app.color};--chip-bg:${app.tint};--chip-border:${app.border}">${text}</span>`
  )).join('');
}

function appBadges(app) {
  let html = '';
  if (app.isNew) html += badge('新上架', 'orange');
  if (app.testBuild) html += badge('测试版', 'amber');
  if (state.defaultHome === app.id) html += badge('默认桌面', 'green');
  else if (app.installed) html += badge('已安装', 'green');
  if (app.needsPc) html += badge('需电脑', 'amber');
  return html;
}

function cardAction(app) {
  if (app.installed) return { action: 'open-app', label: '管理', cls: 'ghost' };
  if (app.needsPc) return { action: 'open-app', label: '安装指引', cls: 'amber' };
  if (!state.connected) return { action: 'open-connect', label: '先连接', cls: 'ghost' };
  return { action: 'open-app', label: '安装', cls: 'primary' };
}

function appCard(app) {
  const cta = cardAction(app);
  return `<article class="tool-card ${app.isNew ? 'is-new' : ''}">
    <button class="card-open" data-action="open-app" data-app="${app.id}" aria-label="查看 ${app.name}"></button>
    <div class="tool-top">
      ${appLogo(app)}
      <div class="tool-copy">
        <div class="tool-name-row">
          <h3>${app.name}</h3>
          ${appBadges(app)}
        </div>
        <p class="tool-tagline">${app.latin ? `${app.latin} · ` : ''}${app.tagline}</p>
      </div>
      <button class="store-btn ${cta.cls}" data-action="${cta.action}" ${cta.action === 'open-app' ? `data-app="${app.id}"` : ''}>${cta.label}</button>
    </div>
    <p class="tool-desc">${app.desc}</p>
    <div class="tool-features">${featureChips(app)}</div>
    ${app.cardNote ? `<div class="card-note ${app.cardNote.tone === 'info' ? 'info' : ''}">
      ${icon(app.cardNote.tone === 'info' ? 'info' : 'warning', 12)}
      <span>${app.cardNote.text}</span>
    </div>` : ''}
  </article>`;
}

function matchesSearch(app) {
  const q = state.search.trim().toLowerCase();
  if (!q) return true;
  const haystack = [
    app.name,
    app.latin || '',
    app.tagline,
    app.desc,
    app.packageName,
    ...app.features
  ].join(' ').toLowerCase();
  return haystack.includes(q);
}

/* ---------- main screen ---------- */
function renderSections() {
  const sections = GROUPS
    .filter((group) => state.filter === 'all' || state.filter === group.id)
    .map((group) => {
      const apps = ORDER
        .map((id) => APPS[id])
        .filter((app) => app.category === group.id && matchesSearch(app));
      return { group, apps };
    })
    .filter(({ apps }) => apps.length);

  if (!sections.length) {
    $('#appSections').innerHTML = `<div class="empty-state">
      <strong>没有找到相关工具</strong>
      换个关键词，或查看全部已收录工具。
      <br><button class="btn btn-ghost btn-sm" data-action="reset-filters">清除筛选</button>
    </div>`;
    return;
  }

  $('#appSections').innerHTML = sections.map(({ group, apps }) => {
    const all = ORDER.map((id) => APPS[id]).filter((app) => app.category === group.id);
    const installed = all.filter((app) => app.installed).length;
    return `<section class="store-section">
      <div class="section-head">
        <div class="section-copy">
          <h2>${group.title}</h2>
          <p>${group.desc}</p>
        </div>
        <span class="section-count">${installed} / ${all.length} 已安装</span>
      </div>
      <div class="tool-list">${apps.map(appCard).join('')}</div>
    </section>`;
  }).join('');
}

function syncFilterUI() {
  document.querySelectorAll('.filter-chip').forEach((chip) => {
    chip.classList.toggle('active', chip.dataset.filter === state.filter);
  });
  const clear = $('#searchClear');
  if (clear) clear.classList.toggle('show', Boolean(state.search));
}

function transportLabel(transport) {
  return transport === 'classic' ? '网络 ADB' : '无线调试';
}

function tvEndpoint(tv) {
  return `${tv.ip}:${tv.port || 5555}`;
}

function syncChrome() {
  const deviceTab = state.tab === 'device';
  const logo = $('#brandLogo');
  const title = $('#brandTitle');
  const sub = $('#brandSub');
  if (logo) logo.innerHTML = deviceTab ? icon('tv', 22) : icon('store', 22);
  if (title) title.textContent = deviceTab ? '我的电视' : '工具市场';
  if (sub) sub.textContent = deviceTab ? '连接、初始准备与设备管理' : 'Tool Store · 精选电视工具';

  const market = $('#marketView');
  const device = $('#deviceView');
  if (market) market.hidden = deviceTab;
  if (device) device.hidden = !deviceTab;

  const navMarket = $('#navMarket');
  const navDevice = $('#navDevice');
  if (navMarket) {
    navMarket.classList.toggle('active', !deviceTab);
    navMarket.toggleAttribute('aria-current', !deviceTab);
    if (!deviceTab) navMarket.setAttribute('aria-current', 'page');
  }
  if (navDevice) {
    navDevice.classList.toggle('active', deviceTab);
    navDevice.toggleAttribute('aria-current', deviceTab);
    if (deviceTab) navDevice.setAttribute('aria-current', 'page');
  }
  const dot = $('#navDeviceDot');
  if (dot) {
    dot.classList.toggle('online', state.connected);
    dot.classList.toggle('offline', !state.connected);
  }
}

function switchTab(tab, scroll = true) {
  state.tab = tab === 'device' ? 'device' : 'market';
  renderMain();
  if (scroll) {
    const content = $('#content');
    if (content) content.scrollTop = 0;
  }
}

function renderMain() {
  const tv = state.tv;
  const home = defaultHomeLabel();

  $('#deviceCard').innerHTML = state.connected
    ? `<button class="device-card" data-action="open-device" type="button">
        <span class="device-icon">${icon('tv', 18)}</span>
        <span class="device-copy">
          <span class="device-status"><span class="dot"></span>已连接 · ${transportLabel(tv.transport)}</span>
          <span class="device-name">${tv.label} · ${tv.model}</span>
          <span class="device-meta">${tv.ip} · 默认桌面 ${home}</span>
        </span>
        <span class="device-chevron">${icon('chevron', 17)}</span>
      </button>`
    : `<button class="device-card offline" data-action="open-device" type="button">
        <span class="device-icon">${icon('wifi', 18)}</span>
        <span class="device-copy">
          <span class="device-status"><span class="dot"></span>未连接电视</span>
          <span class="device-name">先完成初始准备</span>
          <span class="device-meta">同一 Wi-Fi · 无线配对或网络 ADB</span>
        </span>
        <span class="device-cta">去准备</span>
      </button>`;

  const launchers = ORDER.map((id) => APPS[id]).filter((app) => app.category === 'launcher');
  const tools = ORDER.map((id) => APPS[id]).filter((app) => app.category === 'tool');
  const countAll = $('#countAll');
  const countLaunchers = $('#countLaunchers');
  const countTools = $('#countTools');
  if (countAll) countAll.textContent = ORDER.length;
  if (countLaunchers) countLaunchers.textContent = launchers.length;
  if (countTools) countTools.textContent = tools.length;

  syncChrome();
  if (state.tab === 'device') {
    renderDevicePage();
  } else {
    renderSections();
    syncFilterUI();
  }
}

/* ---------- sheet / modal / toast ---------- */
function showSheet() {
  clearTimeout(sheetCloseTimer);
  sheetOpen = true;
  $('#overlay').classList.add('show');
  $('#overlay').setAttribute('aria-hidden', 'false');
  $('#sheet').classList.add('open');
  $('#sheet').setAttribute('aria-hidden', 'false');
}

function closeSheet(force = false) {
  if (busy && !force) return;
  sheetOpen = false;
  $('#overlay').classList.remove('show');
  $('#overlay').setAttribute('aria-hidden', 'true');
  $('#sheet').classList.remove('open');
  $('#sheet').setAttribute('aria-hidden', 'true');
  clearTimeout(sheetCloseTimer);
  sheetCloseTimer = setTimeout(() => {
    if (!sheetOpen) {
      $('#sheetBody').innerHTML = '';
      sheetContext = null;
    }
  }, 300);
}

function setSheet(html) {
  $('#sheetBody').innerHTML = html;
  showSheet();
  $('#sheetBody').scrollTop = 0;
}

function openModal(html) {
  $('#modal').innerHTML = html;
  $('#modalWrap').classList.add('show');
  $('#modalWrap').setAttribute('aria-hidden', 'false');
}

function closeModal() {
  $('#modalWrap').classList.remove('show');
  $('#modalWrap').setAttribute('aria-hidden', 'true');
  clearTimeout(modalCloseTimer);
  modalCloseTimer = setTimeout(() => {
    if (!$('#modalWrap').classList.contains('show')) $('#modal').innerHTML = '';
  }, 220);
}

function toast(message) {
  const el = $('#toast');
  el.textContent = message;
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 2400);
}

async function copyText(text, message = '已复制') {
  try {
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(text);
    } else {
      throw new Error('clipboard unavailable');
    }
  } catch (err) {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.setAttribute('readonly', '');
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    try { document.execCommand('copy'); } catch (_) { /* noop */ }
    document.body.removeChild(ta);
  }
  toast(message);
}