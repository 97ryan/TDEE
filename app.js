// TDEE Tracker PWA (local-only)
const KCALS_PER_KG = 7700;
const STORAGE_KEY = 'tdee.entries.v1';
const SETTINGS_KEY = 'tdee.settings.v1';

// --- State ---
let entries = []; // [{date:'YYYY-MM-DD', weight:kg, calories:kcal}]
let settings = { goalKgPerWeek: -0.5, windowDays: 7 };

// --- Utilities ---
function fmtDate(d) { return new Date(d + 'T00:00:00'); }
function toYMD(dateObj) {
  const d = new Date(dateObj);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2,'0');
  const da = String(d.getDate()).padStart(2,'0');
  return `${y}-${m}-${da}`;
}
function sortEntries(arr) { return arr.slice().sort((a,b)=> a.date.localeCompare(b.date)); }

// --- Storage ---
function loadState() {
  try { entries = JSON.parse(localStorage.getItem(STORAGE_KEY)) || []; } catch(e) { entries = []; }
  try { settings = Object.assign(settings, JSON.parse(localStorage.getItem(SETTINGS_KEY)) || {}); } catch(e) {}
}
function saveEntries() { localStorage.setItem(STORAGE_KEY, JSON.stringify(entries)); }
function saveSettings() { localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings)); }

// --- Compute TDEE series ---
function computeSeries(entries, goalKgPerWeek, windowDays) {
  if (!entries.length) return [];
  const sorted = sortEntries(entries);
  const series = [];
  const goalDeltaPerDay = (goalKgPerWeek / 7.0) * KCALS_PER_KG;

  for (let i=0; i<sorted.length; i++) {
    const cur = sorted[i];
    const curDate = fmtDate(cur.date);
    const windowStart = new Date(curDate.getTime() - windowDays*86400000);
    const window = sorted.filter(e => {
      const ed = fmtDate(e.date);
      return ed >= windowStart && ed <= curDate;
    });
    let avgIntake = window.reduce((sum, e) => sum + Number(e.calories || 0), 0) / (window.length || 1);
    let tdee = avgIntake;
    if (window.length >= 2) {
      const first = fmtDate(window[0].date);
      const last = fmtDate(window[window.length-1].date);
      const days = Math.max(1, (last - first)/86400000);
      const deltaW = (window[window.length-1].weight - window[0].weight);
      const dWperDay = deltaW / days;
      tdee = avgIntake + dWperDay * KCALS_PER_KG;
    }
    const target = avgIntake - goalDeltaPerDay;
    series.push({ date: cur.date, tdee, target, avgIntake, windowCount: window.length });
  }
  return series;
}

// --- Minimal SVG charting (no external deps) ---
function clearSVG(svg) { while (svg.firstChild) svg.removeChild(svg.firstChild); }
function makeSVG(tag, attrs={}) {
  const el = document.createElementNS('http://www.w3.org/2000/svg', tag);
  for (const [k,v] of Object.entries(attrs)) el.setAttribute(k,v);
  return el;
}

function scaleLinear(domainMin, domainMax, rangeMin, rangeMax) {
  const m = (rangeMax - rangeMin) / (domainMax - domainMin || 1);
  return x => rangeMin + (x - domainMin) * m;
}

function drawLineChart(svg, points, xAccessor, yAccessor, {color='#5cc8ff'}={}) {
  clearSVG(svg);
  const w = svg.clientWidth || 600;
  const h = svg.clientHeight || 240;
  const padL=40, padR=10, padT=10, padB=24;
  svg.setAttribute('viewBox', `0 0 ${w} ${h}`);

  if (!points.length) return;

  const xs = points.map(xAccessor);
  const ys = points.map(yAccessor).filter(v=>Number.isFinite(v));
  const xMin = Math.min(...xs), xMax = Math.max(...xs);
  const yMin = Math.min(...ys), yMax = Math.max(...ys);
  const xScale = scaleLinear(xMin, xMax, padL, w-padR);
  const yScale = scaleLinear(yMin, yMax, h-padB, padT);

  const grid = makeSVG('g', {stroke:'#2a2f3a','stroke-width':1, 'stroke-opacity':'0.6'});
  for (let i=0;i<5;i++){
    const y = padT + (h-padT-padB)*i/4;
    grid.appendChild(makeSVG('line',{x1:padL,y1:y,x2:w-padR,y2:y}));
  }
  svg.appendChild(grid);

  const labelMin = makeSVG('text', {x:4, y:yScale(yMin), fill:'#a1a1ab','font-size':'10'});
  labelMin.textContent = yMin.toFixed(0);
  const labelMax = makeSVG('text', {x:4, y:yScale(yMax), fill:'#a1a1ab','font-size':'10'});
  labelMax.textContent = yMax.toFixed(0);
  svg.appendChild(labelMin); svg.appendChild(labelMax);

  const path = points.map((p,i)=> (i===0?'M':'L') + xScale(xAccessor(p)) + ' ' + yScale(yAccessor(p))).join(' ');
  const line = makeSVG('path', {d:path, fill:'none', stroke:color, 'stroke-width':2});
  svg.appendChild(line);

  for (const p of points) {
    const cx = xScale(xAccessor(p)), cy = yScale(yAccessor(p));
    if (!Number.isFinite(cy)) continue;
    svg.appendChild(makeSVG('circle',{cx, cy, r:2.5, fill:color}));
  }
}

function drawTwoLineChart(svg, points, xAccessor, y1Accessor, y2Accessor, {color1='#5cc8ff', color2='#8ef18e'}={}) {
  clearSVG(svg);
  const w = svg.clientWidth || 600;
  const h = svg.clientHeight || 260;
  const padL=40, padR=10, padT=10, padB=24;
  svg.setAttribute('viewBox', `0 0 ${w} ${h}`);
  if (!points.length) return;

  const xs = points.map(xAccessor);
  const y1s = points.map(y1Accessor).filter(Number.isFinite);
  const y2s = points.map(y2Accessor).filter(Number.isFinite);
  const yAll = y1s.concat(y2s);
  const xMin = Math.min(...xs), xMax = Math.max(...xs);
  const yMin = Math.min(...yAll), yMax = Math.max(...yAll);
  const xScale = scaleLinear(xMin, xMax, padL, w-padR);
  const yScale = scaleLinear(yMin, yMax, h-padB, padT);

  const grid = makeSVG('g', {stroke:'#2a2f3a','stroke-width':1, 'stroke-opacity':'0.6'});
  for (let i=0;i<5;i++){
    const y = padT + (h-padT-padB)*i/4;
    grid.appendChild(makeSVG('line',{x1:padL,y1:y,x2:w-padR,y2:y}));
  }
  svg.appendChild(grid);

  const labelMin = makeSVG('text', {x:4, y:yScale(yMin), fill:'#a1a1ab','font-size':'10'});
  labelMin.textContent = yMin.toFixed(0);
  const labelMax = makeSVG('text', {x:4, y:yScale(yMax), fill:'#a1a1ab','font-size':'10'});
  labelMax.textContent = yMax.toFixed(0);
  svg.appendChild(labelMin); svg.appendChild(labelMax);

  const path1 = points.map((p,i)=> (i===0?'M':'L') + xScale(xAccessor(p)) + ' ' + yScale(y1Accessor(p))).join(' ');
  svg.appendChild(makeSVG('path',{d:path1, fill:'none', stroke:color1, 'stroke-width':2}));
  const path2 = points.map((p,i)=> (i===0?'M':'L') + xScale(xAccessor(p)) + ' ' + yScale(y2Accessor(p))).join(' ');
  svg.appendChild(makeSVG('path',{d:path2, fill:'none', stroke:color2, 'stroke-width':2, 'stroke-dasharray':'4 3'}));
}

function drawBarChart(svg, points, xAccessor, yAccessor, {color='#5cc8ff'}={}) {
  clearSVG(svg);
  const w = svg.clientWidth || 600;
  const h = svg.clientHeight || 240;
  const padL=40, padR=10, padT=10, padB=24;
  svg.setAttribute('viewBox', `0 0 ${w} ${h}`);
  if (!points.length) return;

  const xs = points.map(xAccessor);
  const ys = points.map(yAccessor).filter(Number.isFinite);
  const xMin = Math.min(...xs), xMax = Math.max(...xs);
  const yMin = 0, yMax = Math.max(1000, Math.max(...ys));
  const xScale = scaleLinear(xMin, xMax, padL, w-padR);
  const yScale = scaleLinear(yMin, yMax, h-padB, padT);

  const grid = makeSVG('g', {stroke:'#2a2f3a','stroke-width':1, 'stroke-opacity':'0.6'});
  for (let i=0;i<5;i++){
    const y = padT + (h-padT-padB)*i/4;
    grid.appendChild(makeSVG('line',{x1:padL,y1:y,x2:w-padR,y2:y}));
  }
  svg.appendChild(grid);

  const bw = Math.max(2, (w - padL - padR) / (points.length*1.3));
  for (const p of points) {
    const x = xScale(xAccessor(p)) - bw/2;
    const y = yScale(yAccessor(p));
    const y0 = yScale(0);
    const rect = makeSVG('rect', {x, y, width:bw, height:Math.max(0, y0 - y), fill:color, opacity:'0.9'});
    svg.appendChild(rect);
  }

  const labelMax = makeSVG('text', {x:4, y:yScale(yMax), fill:'#a1a1ab','font-size':'10'});
  labelMax.textContent = yMax.toFixed(0);
  svg.appendChild(labelMax);
}

// --- Summary update ---
// --- Summary update ---
function updateSummary() {
  const tdeeEl = document.getElementById('currentTDEE');
  const noteEl = document.getElementById('tdeeNote');
  const needEl = document.getElementById('needToEat');
  const changeEl = document.getElementById('calChange');

  if (!entries.length) {
    tdeeEl.textContent = '—';
    noteEl.textContent = `Add entries to compute TDEE`;
    if (needEl) needEl.textContent = '—';
    if (changeEl) changeEl.textContent = '—';
    return;
  }
  const series = computeSeries(entries, settings.goalKgPerWeek, settings.windowDays);
  if (!series.length) {
    tdeeEl.textContent = '—';
    noteEl.textContent = `Not enough data`;
    if (needEl) needEl.textContent = '—';
    if (changeEl) changeEl.textContent = '—';
    return;
  }
  const last = series[series.length-1];
  const lastEntry = sortEntries(entries)[entries.length-1];
  const curTDEE = last.tdee;
  const goalDeltaPerDay = (settings.goalKgPerWeek/7.0) * KCALS_PER_KG;
  const needToEat = curTDEE + goalDeltaPerDay; // intake that hits target weight change
  const delta = needToEat - Number(lastEntry.calories || 0); // change vs last logged day

  // populate
  const tdeeVal = Math.round(curTDEE);
  tdeeEl.textContent = isFinite(tdeeVal) ? tdeeVal.toString() : '—';
  noteEl.textContent = `Based on last ${settings.windowDays} days`;

  if (needEl) {
    const n = Math.round(needToEat);
    needEl.textContent = isFinite(n) ? n.toString() : '—';
  }
  if (changeEl) {
    if (!isFinite(delta)) {
      changeEl.textContent = '—';
    } else {
      const d = Math.round(delta);
      const sign = d > 0 ? '+' : '';
      changeEl.textContent = sign + d.toString();
      // color hint
      changeEl.classList.remove('delta-pos','delta-neg');
      if (d > 0) changeEl.classList.add('delta-pos');
      if (d < 0) changeEl.classList.add('delta-neg');
    }
  }
}

// --- CSV parsing ---
// --- CSV parsing ---
function parseCSV(text) {
  const lines = text.replace(/\r/g,'').trim().split('\n');
  if (!lines.length) return [];
  const headers = lines[0].split(',').map(h=>h.trim().toLowerCase());
  const idxDate = headers.findIndex(h=> h==='date');
  const idxWeight = headers.findIndex(h=> ['weight_kg','weight'].includes(h));
  const idxCals = headers.findIndex(h=> ['calories_kcal','calories'].includes(h));
  if (idxDate<0 || idxWeight<0 || idxCals<0) return [];

  const out = [];
  for (let i=1;i<lines.length;i++) {
    const cols = lines[i].split(',').map(c=>c.trim());
    if (cols.length < 3) continue;
    const dateRaw = cols[idxDate];
    const m = dateRaw.match(/(\d{4}-\d{2}-\d{2})/);
    const date = m ? m[1] : null;
    const weight = parseFloat(cols[idxWeight].replace(/[^0-9.\-]/g,''));
    const calories = parseFloat(cols[idxCals].replace(/[^0-9.\-]/g,''));
    if (!date || !isFinite(weight) || !isFinite(calories)) continue;
    out.push({date, weight, calories});
  }
  return out;
}

// --- UI ---
function renderHistory() {
  const wrap = document.getElementById('history');
  wrap.innerHTML = '';
  if (!entries.length) { wrap.innerHTML = '<p class="muted">No entries yet.</p>'; return; }
  const sorted = sortEntries(entries).reverse();
  for (let i=0; i<sorted.length; i++) {
    const e = sorted[i];
    const row = document.createElement('div');
    row.className = 'entry-row';
    row.innerHTML = `<div>${e.date}</div>
      <div>${Number(e.weight).toFixed(1)} kg</div>
      <div>${Number(e.calories).toFixed(0)} kcal</div>`;
    const del = document.createElement('button');
    del.textContent = 'Delete';
    del.className = 'danger';
    del.addEventListener('click', ()=>{
      const idx = entries.findIndex(x=>x.date===e.date);
      if (idx>=0) {
        entries.splice(idx,1);
        saveEntries(); refreshAll();
      }
    });
    row.appendChild(del);
    wrap.appendChild(row);
  }
}

function renderCharts() {
  const weightSVG = document.getElementById('weightChart');
  const calSVG = document.getElementById('calChart');
  const tdeeSVG = document.getElementById('tdeeChart');

  if (!entries.length) { clearSVG(weightSVG); clearSVG(calSVG); clearSVG(tdeeSVG); return; }
  const sorted = sortEntries(entries);
  const xAcc = p => fmtDate(p.date).getTime();

  drawLineChart(weightSVG, sorted, xAcc, p=>Number(p.weight), {color:'#8ecbff'});
  drawBarChart(calSVG, sorted, xAcc, p=>Number(p.calories), {color:'#ffd166'});

  const series = computeSeries(sorted, settings.goalKgPerWeek, settings.windowDays);
  drawTwoLineChart(tdeeSVG, series, p=>fmtDate(p.date).getTime(), p=>p.tdee, p=>p.target, {color1:'#5cc8ff', color2:'#8ef18e'});
}

function refreshAll() {
  renderHistory();
  renderCharts();
  updateSummary();
}

// --- Handlers ---
document.addEventListener('DOMContentLoaded', () => {
  document.getElementById('date').value = toYMD(new Date());
  loadState();
  document.getElementById('goal').value = settings.goalKgPerWeek;
  document.getElementById('windowDays').value = settings.windowDays;
  refreshAll();

  document.getElementById('entryForm').addEventListener('submit', (e)=>{
    e.preventDefault();
    const date = document.getElementById('date').value;
    const weight = parseFloat(document.getElementById('weight').value);
    const calories = parseFloat(document.getElementById('calories').value);
    if (!date || !isFinite(weight) || !isFinite(calories)) return;

    const existing = entries.findIndex(x=>x.date===date);
    const obj = {date, weight, calories};
    if (existing>=0) entries[existing] = obj; else entries.push(obj);
    saveEntries();
    document.getElementById('weight').value='';
    document.getElementById('calories').value='';
    refreshAll();
  });

  document.getElementById('saveSettings').addEventListener('click', ()=>{
    const goal = parseFloat(document.getElementById('goal').value);
    const windowDays = parseInt(document.getElementById('windowDays').value,10);
    if (!isFinite(goal) || !Number.isInteger(windowDays) || windowDays<1) return;
    settings.goalKgPerWeek = goal;
    settings.windowDays = windowDays;
    saveSettings();
    refreshAll();
  });

  // Danger Zone only
  document.getElementById('clearAllData').addEventListener('click', ()=>{
    if (!confirm('Are you sure? This will erase all local data.')) return;
    localStorage.removeItem(STORAGE_KEY);
    localStorage.removeItem(SETTINGS_KEY);
    entries = [];
    settings = { goalKgPerWeek: -0.5, windowDays: 7 };
    saveSettings(); // persist defaults back
    refreshAll();
    alert('All local data cleared.');
  });

  // Exports
  document.getElementById('exportJSON').addEventListener('click', ()=>{
    const payload = { entries, settings, exportedAt: new Date().toISOString() };
    const text = JSON.stringify(payload, null, 2);
    const pre = document.getElementById('exportOutput');
    pre.textContent = text;
    const blob = new Blob([text], {type:'application/json'});
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'tdee-export.json';
    a.click();
    URL.revokeObjectURL(a.href);
  });
  document.getElementById('exportCSV').addEventListener('click', ()=>{
    const rows = [['date','weight_kg','calories_kcal']].concat(
      sortEntries(entries).map(e=>[e.date, e.weight, e.calories])
    );
    const csv = rows.map(r=>r.join(',')).join('\n');
    const blob = new Blob([csv], {type:'text/csv'});
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'tdee-export.csv';
    a.click();
    URL.revokeObjectURL(a.href);
  });

  // Clean import buttons trigger hidden inputs
  const pickJSON = document.getElementById('btnImportJSON');
  const pickCSV = document.getElementById('btnImportCSV');
  const fileJSON = document.getElementById('importFile');
  const fileCSV = document.getElementById('importCSV');
  pickJSON.addEventListener('click', ()=> fileJSON.click());
  pickCSV.addEventListener('click', ()=> fileCSV.click());

  // JSON import
  fileJSON.addEventListener('change', async (e)=>{
    const file = e.target.files[0];
    if (!file) return;
    const text = await file.text();
    try {
      const obj = JSON.parse(text);
      if (Array.isArray(obj.entries)) {
        for (const it of obj.entries) {
          if (!it || !it.date || !isFinite(it.weight) || !isFinite(it.calories)) continue;
          const idx = entries.findIndex(x=>x.date===it.date);
          if (idx>=0) entries[idx] = it; else entries.push(it);
        }
        saveEntries();
      }
      if (obj.settings) {
        settings = Object.assign(settings, obj.settings);
        saveSettings();
        document.getElementById('goal').value = settings.goalKgPerWeek;
        document.getElementById('windowDays').value = settings.windowDays;
      }
      refreshAll();
    } catch(err) {
      alert('Invalid JSON');
    }
    e.target.value = '';
  });

  // CSV import
  fileCSV.addEventListener('change', async (e)=>{
    const file = e.target.files[0];
    if (!file) return;
    const text = await file.text();
    const rows = parseCSV(text);
    if (!rows.length) { alert('No valid rows found. Expected headers: date, weight_kg, calories_kcal'); e.target.value=''; return; }
    for (const it of rows) {
      const idx = entries.findIndex(x=>x.date===it.date);
      if (idx>=0) entries[idx] = it; else entries.push(it);
    }
    saveEntries();
    refreshAll();
    e.target.value = '';
  });

  // PWA
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('service-worker.js');
  }
});
