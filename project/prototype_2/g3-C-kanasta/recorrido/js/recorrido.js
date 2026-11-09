// Tour logic: steps, diagram viewer and highlighting. Works by opening index.html directly (no server).

let current = 0;
let currentView = "cc";
let zoom = null; // null = fit to the viewer width
let highlight = { nodes: [] };

const $ = (id) => document.getElementById(id);

// ---------- diagram helpers ----------

// Graphviz writes the node name inside <title>; edges look like "web:rest:s->j2"
function nodeName(text) {
  return text.split(":")[0];
}

function isJunction(name) {
  return /^j\d+$/.test(name);
}

function readDiagram(svg) {
  const nodes = [...svg.querySelectorAll("g.node")].map((g) => ({ g, name: g.querySelector("title").textContent }));
  const edges = [...svg.querySelectorAll("g.edge")].map((g) => {
    const [from, to] = g.querySelector("title").textContent.split("->").map(nodeName);
    return { g, from, to, label: [...g.querySelectorAll("text")].map((t) => t.textContent).join(" ") };
  });

  // For each connector junction: who provides, who consumes and which type it is
  const junctions = {};
  for (const e of edges) {
    if (isJunction(e.from)) {
      const j = (junctions[e.from] = junctions[e.from] || { consumers: [] });
      j.provider = e.to;
      j.type = (e.label.match(/<<(.+)>>/) || [])[1];
    }
    if (isJunction(e.to)) {
      (junctions[e.to] = junctions[e.to] || { consumers: [] }).consumers.push(e.from);
    }
  }
  return { nodes, edges, junctions };
}

function applyHighlight() {
  const svg = $("diagram").querySelector("svg");
  if (!svg) return;
  const { nodes, edges, junctions } = readDiagram(svg);
  const on = new Set(highlight.nodes || []);
  const type = highlight.connectorType;

  svg.querySelectorAll(".on").forEach((el) => el.classList.remove("on"));
  svg.classList.toggle("focus", on.size > 0 || !!type);
  svg.querySelectorAll("g.cluster").forEach((g) => g.classList.add("on"));

  // Connector type filter: light up only the connectors of that type
  if (type) {
    on.clear();
    for (const j of Object.values(junctions)) {
      if (j.type === type) {
        on.add(j.provider);
        j.consumers.forEach((c) => on.add(c));
      }
    }
  }

  const edgeOn = (e) => {
    if (isJunction(e.to)) {
      const j = junctions[e.to];
      return on.has(e.from) && on.has(j.provider) && (!type || j.type === type);
    }
    if (isJunction(e.from)) {
      const j = junctions[e.from];
      return on.has(e.to) && j.consumers.some((c) => on.has(c)) && (!type || j.type === type);
    }
    return on.has(e.from) && on.has(e.to);
  };

  const litJunctions = new Set();
  for (const e of edges) {
    if (edgeOn(e)) {
      e.g.classList.add("on");
      if (isJunction(e.from)) litJunctions.add(e.from);
    }
  }
  for (const n of nodes) {
    if (on.has(n.name) || litJunctions.has(n.name) || n.name === "legend") n.g.classList.add("on");
  }
  scrollToHighlight(nodes.filter((n) => on.has(n.name)));
}

// Moves the viewer so the highlighted elements are in sight
function scrollToHighlight(litNodes) {
  if (litNodes.length === 0) return;
  const box = $("diagram");
  const area = box.getBoundingClientRect();
  const rects = litNodes.map((n) => n.g.getBoundingClientRect());
  const top = Math.min(...rects.map((r) => r.top)) - area.top + box.scrollTop;
  const bottom = Math.max(...rects.map((r) => r.bottom)) - area.top + box.scrollTop;
  const left = Math.min(...rects.map((r) => r.left)) - area.left + box.scrollLeft;
  const right = Math.max(...rects.map((r) => r.right)) - area.left + box.scrollLeft;
  box.scrollTo({
    top: Math.max(0, (top + bottom) / 2 - box.clientHeight / 2),
    left: Math.max(0, (left + right) / 2 - box.clientWidth / 2),
    behavior: "smooth",
  });
}

function applyZoom() {
  const svg = $("diagram").querySelector("svg");
  if (!svg) return;
  const naturalWidth = svg.viewBox.baseVal.width;
  svg.style.width = zoom === null ? "100%" : `${Math.round(naturalWidth * zoom)}px`;
  svg.style.maxWidth = zoom === null ? `${naturalWidth * 1.6}px` : "none";
}

function showView(view) {
  currentView = view;
  const box = $("diagram");
  box.innerHTML = DIAGRAMS[view];
  const svg = box.querySelector("svg");
  svg.removeAttribute("width");
  svg.removeAttribute("height");
  svg.querySelectorAll("g.node").forEach((g) => {
    g.addEventListener("click", () => showElement(g.querySelector("title").textContent));
  });
  [...$("view-tabs").children].forEach((b) => b.classList.toggle("active", b.dataset.view === view));
  applyZoom();
  applyHighlight();
}

function showElement(name) {
  const info = ELEMENTS[name];
  $("element-info").innerHTML = info
    ? `<strong>${info[0]}</strong><br>${info[1]}`
    : `<span class="muted">Elemento: ${name}</span>`;
}

// ---------- steps ----------

function buildChips(containerId, items, onPick) {
  const box = $(containerId);
  if (!box) return;
  items.forEach((item) => {
    const chip = document.createElement("button");
    chip.className = "chip";
    chip.textContent = item;
    chip.addEventListener("click", () => {
      const active = chip.classList.contains("active");
      box.querySelectorAll(".chip").forEach((c) => c.classList.remove("active"));
      if (!active) chip.classList.add("active");
      onPick(active ? null : item);
    });
    box.appendChild(chip);
  });
}

function showStep(index) {
  current = Math.max(0, Math.min(STEPS.length - 1, index));
  const step = STEPS[current];
  $("step").innerHTML = step.html;
  highlight = { nodes: step.nodes };

  if (step.connectorFilter) {
    buildChips("connector-chips", CONNECTOR_TYPES, (type) => {
      highlight = type ? { connectorType: type } : { nodes: step.nodes };
      if (currentView !== "cc") showView("cc");
      else applyHighlight();
    });
  }
  if (step.languageFilter) {
    buildChips("language-chips", Object.keys(LANGUAGES), (language) => {
      highlight = { nodes: language ? LANGUAGES[language] : step.nodes };
      if (currentView !== "cc") showView("cc");
      else applyHighlight();
    });
  }
  $("step").querySelectorAll("[data-view]").forEach((a) =>
    a.addEventListener("click", (event) => {
      event.preventDefault();
      showView(a.dataset.view);
    })
  );

  [...$("steps").querySelectorAll("button")].forEach((b) => b.classList.toggle("active", Number(b.dataset.index) === current));
  $("prev").disabled = current === 0;
  $("next").disabled = current === STEPS.length - 1;
  $("counter").textContent = `${current + 1} de ${STEPS.length}`;
  $("progress-bar").style.width = `${((current + 1) / STEPS.length) * 100}%`;
  history.replaceState(null, "", `#paso-${current + 1}`);
  showView(step.view);
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function buildMenu() {
  let group = null;
  STEPS.forEach((step, index) => {
    if (step.group !== group) {
      group = step.group;
      const title = document.createElement("div");
      title.className = "group";
      title.textContent = group;
      $("steps").appendChild(title);
    }
    const button = document.createElement("button");
    button.dataset.index = index;
    button.innerHTML = `<span class="num">${step.num}</span><span>${step.title}</span>`;
    button.addEventListener("click", () => showStep(index));
    $("steps").appendChild(button);
  });

  Object.entries(VIEWS).forEach(([key, label]) => {
    const tab = document.createElement("button");
    tab.dataset.view = key;
    tab.textContent = label;
    tab.addEventListener("click", () => showView(key));
    $("view-tabs").appendChild(tab);
  });
}

// ---------- start ----------

buildMenu();
$("prev").addEventListener("click", () => showStep(current - 1));
$("next").addEventListener("click", () => showStep(current + 1));
$("zoom-in").addEventListener("click", () => { zoom = (zoom || 0.6) * 1.25; applyZoom(); });
$("zoom-out").addEventListener("click", () => { zoom = (zoom || 0.6) / 1.25; applyZoom(); });
$("zoom-fit").addEventListener("click", () => { zoom = null; applyZoom(); });
document.addEventListener("keydown", (event) => {
  if (event.target.closest("input, textarea")) return;
  if (event.key === "ArrowRight") showStep(current + 1);
  if (event.key === "ArrowLeft") showStep(current - 1);
});

const start = Number((location.hash.match(/paso-(\d+)/) || [])[1]) || 1;
showStep(start - 1);
