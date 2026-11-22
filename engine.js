let tab = "store";
let modalId = null;
const busy = {};
let reg = {};
let tTimer = null;

try { reg = JSON.parse(localStorage.getItem("ph_reg") || "{}"); } catch (e) {}

function bridge() { return window.StoreBridge || null; }

function initTheme() {
  let th = "light";
  try { th = localStorage.getItem("ph_th") || "light"; } catch (e) {}
  if (th === "dark") document.documentElement.classList.add("dark");
  updateThemeBtn();
}

function toggleTheme() {
  const dk = document.documentElement.classList.toggle("dark");
  try { localStorage.setItem("ph_th", dk ? "dark" : "light"); } catch (e) {}
  updateThemeBtn();
}

function updateThemeBtn() {
  const dk = document.documentElement.classList.contains("dark");
  document.getElementById("tbtn").innerHTML = dk ? "&#9728;" : "&#9790;";
}

function getState(a) {
  if (a.soon) return "soon";
  if (busy[a.pkg]) return busy[a.pkg].st;
  const b = bridge();
  if (b) {
    try {
      if (b.isInstalled(a.pkg)) {
        const v = Number(b.getInstalledVersionCode(a.pkg));
        return (v > 0 && a.vCode > v) ? "update" : "installed";
      }
    } catch (e) {}
  }
  return "avail";
}

function btnInfo(a) {
  const s = getState(a);
  if (s === "soon") return reg[a.id] ? { t: "Registered", c: "b-reg" } : { t: "Coming Soon", c: "b-sn" };
  if (s === "downloading") return { t: (busy[a.pkg].p || 0) + "%", c: "b-bsy" };
  if (s === "installing") return { t: "Installing...", c: "b-bsy" };
  if (s === "installed") return { t: "Open", c: "b-opn" };
  if (s === "update") return { t: "Update", c: "b-pri" };
  return { t: "Install", c: "b-pri" };
}

function toast(msg, err) {
  const el = document.getElementById("toast");
  el.textContent = msg;
  el.className = "toast on" + (err ? " err" : "");
  clearTimeout(tTimer);
  tTimer = setTimeout(function() { el.className = "toast"; }, 3200);
}

function act(id, e) {
  if (e) e.stopPropagation();
  const a = APPS.find(function(x) { return x.id === id; });
  if (!a) return;
  if (a.soon) {
    reg[a.id] = !reg[a.id];
    try { localStorage.setItem("ph_reg", JSON.stringify(reg)); } catch (err) {}
    toast(reg[a.id] ? "Pre-registered for " + a.name : "Removed pre-registration");
    render();
    return;
  }
  const s = getState(a);
  if (s === "downloading" || s === "installing") return;
  const b = bridge();
  if (s === "installed") {
    if (b && !b.openApp(a.pkg)) toast("Could not open " + a.name, true);
    return;
  }
  if (b) {
    busy[a.pkg] = { st: "downloading", p: 0, m: "Connecting..." };
    render();
    b.installApp(a.url, a.pkg);
  } else {
    toast("Downloading APK...");
    window.location.href = a.url;
  }
}

window.onInstallProgress = function(pkg, p) {
  busy[pkg] = { st: "downloading", p: p, m: "Downloading " + p + "%" };
  render();
};

window.onInstallStatus = function(pkg, st, msg) {
  if (st === "downloading" || st === "installing") {
    const prev = busy[pkg] ? busy[pkg].p : 0;
    busy[pkg] = { st: st, p: st === "installing" ? 100 : prev, m: msg };
  } else {
    delete busy[pkg];
    toast(msg || (st === "success" ? "Installed!" : "Install failed"), st === "error");
  }
  render();
};

window.refreshAppStates = function() { render(); };
