function card(a) {
  const bt = btnInfo(a);
  const w = busy[a.pkg];
  const bar = w ? '<div class="pbar"><div class="pfill" style="width:' + (w.p || 10) + '%"></div></div>' : "";
  const sub = w
    ? '<div class="sub">' + (w.m || "Working...") + "</div>"
    : '<div class="meta">' + a.cat + " &bull; v" + a.ver + " &bull; &#9733; " + a.rate + "</div>";
  return '<div class="card" onclick="openApp(\'' + a.id + '\')">' +
    '<div class="ico" style="background:' + a.bg + '"><b>' + a.t1 + "</b><span>" + a.t2 + "</span></div>" +
    '<div class="inf"><div class="ttl">' + a.name + "</div>" + sub + bar + "</div>" +
    '<button class="btn ' + bt.c + '" onclick="act(\'' + a.id + '\',event)">' + bt.t + "</button></div>";
}

function render() {
  const q = (document.getElementById("q").value || "").trim().toLowerCase();
  const list = APPS.filter(function(a) {
    return !q || (a.name + " " + a.tag + " " + a.cat).toLowerCase().indexOf(q) !== -1;
  });
  let h = "";
  if (tab === "store" && !q) {
    const f = APPS[0];
    const fb = btnInfo(f);
    h += '<div class="hero" onclick="openApp(\'' + f.id + '\')">' +
      '<span class="tag">Featured Game</span><h2>' + f.name + "</h2><p>" + f.tag + "</p>" +
      '<div class="hfoot"><span>v' + f.ver + ' &bull; Free Indie</span>' +
      '<button class="btn ' + fb.c + '" onclick="act(\'' + f.id + '\',event)">' + fb.t + "</button></div></div>";
    const live = list.filter(function(a) { return !a.soon; });
    const soon = list.filter(function(a) { return a.soon; });
    h += '<div class="sec"><span>Available Now</span><span class="meta">' + live.length + " apps</span></div>";
    h += '<div class="list">' + live.map(card).join("") + "</div>";
    if (soon.length) {
      h += '<div class="sec"><span>Coming Soon</span><span class="meta">Pre-register</span></div>';
      h += '<div class="list">' + soon.map(card).join("") + "</div>";
    }
  } else if (tab === "games") {
    const g = list.filter(function(a) { return a.cat === "Games"; });
    h += '<div class="sec"><span>Games</span></div>';
    h += g.length ? '<div class="list">' + g.map(card).join("") + "</div>" : '<div class="empty">No games found.</div>';
  } else if (tab === "myapps") {
    const m = list.filter(function(a) {
      const s = getState(a);
      return s === "installed" || s === "update" || s === "downloading" || s === "installing" || (a.soon && reg[a.id]);
    });
    h += '<div class="sec"><span>My Apps</span></div>';
    h += m.length ? '<div class="list">' + m.map(card).join("") + "</div>" : '<div class="empty">Apps you install will appear here.</div>';
  } else {
    h += '<div class="sec"><span>Search Results</span></div>';
    h += list.length ? '<div class="list">' + list.map(card).join("") + "</div>" : '<div class="empty">No matches.</div>';
  }
  document.getElementById("main").innerHTML = h;
  if (modalId) drawModal(modalId);
}

function goTab(t) {
  tab = t;
  document.querySelectorAll(".ni").forEach(function(el) {
    el.classList.toggle("on", el.getAttribute("data-t") === t);
  });
  render();
}

function openApp(id) {
  modalId = id;
  drawModal(id);
  document.getElementById("modal").classList.add("on");
}

function closeApp() {
  modalId = null;
  document.getElementById("modal").classList.remove("on");
}

function drawModal(id) {
  const a = APPS.find(function(x) { return x.id === id; });
  if (!a) return;
  const bt = btnInfo(a);
  const w = busy[a.pkg];
  const bar = w ? '<div class="pbar" style="margin:-6px 0 14px"><div class="pfill" style="width:' + (w.p || 10) + '%"></div></div>' : "";
  document.getElementById("mbody").innerHTML =
    '<div class="dhead"><div class="ico" style="width:66px;height:66px;background:' + a.bg + '"><b>' + a.t1 + "</b><span>" + a.t2 + "</span></div>" +
    '<div><h3 style="margin:0">' + a.name + '</h3><div class="sub">' + a.dev + "</div></div></div>" +
    '<div class="stats"><div><b>&#9733; ' + a.rate + '</b><div class="meta">Rating</div></div>' +
    '<div><b>v' + a.ver + '</b><div class="meta">Version</div></div>' +
    '<div><b>' + a.cat + '</b><div class="meta">Category</div></div></div>' +
    '<button class="btn cta ' + bt.c + '" onclick="act(\'' + a.id + '\',event)">' + bt.t + "</button>" + bar +
    '<div class="box"><h4>About</h4><p>' + a.desc + "</p></div>" +
    '<div class="box"><h4>What\'s New</h4><p>' + a.news + "</p></div>";
}

initTheme();
render();
