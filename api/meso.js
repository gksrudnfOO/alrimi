// Current meso-market price (메포 per 1억 메소) and today's change from maplechart.com, which has no CORS.
// "Today" follows the source: change against the last close at or before 09:00 KST of the latest data's day.
const BASE = 'https://maplechart.com';
const HEADERS = {
  'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36',
  'referer': BASE + '/mesomarket.html'
};
const get = async p => { const r = await fetch(BASE + p, { headers: HEADERS }); if (!r.ok) throw new Error(p + ' ' + r.status); return r.json(); };

function todayRef(candles) {
  const bars = candles.filter(b => !b.filled && b.c > 0);
  if (!bars.length) return null;
  const nine = Math.floor(bars[bars.length - 1].t / 86400) * 86400; // 09:00 KST is 00:00 UTC
  let ref = null;
  for (const b of bars) if (b.t <= nine) ref = b.c;
  return ref == null ? bars[0].o : ref;
}

module.exports = async (req, res) => {
  res.setHeader('content-type', 'application/json; charset=utf-8');
  try {
    const [latest, candles] = await Promise.all([get('/api/mesomarket/latest'), get('/api/mesomarket/candles?interval=5m&limit=600')]);
    const q = latest.latest || {};
    if (!(q.avg > 0)) throw new Error('no price');
    const ref = todayRef(candles.candles || []);
    const pct = ref > 0 ? (q.avg / ref - 1) * 100 : null;
    res.setHeader('cache-control', 's-maxage=20, stale-while-revalidate=120');
    res.end(JSON.stringify({ price: q.avg, ref, pct, at: q.at || latest.last_at || null, source: BASE + '/mesomarket.html' }));
  } catch (e) {
    res.statusCode = 502;
    res.setHeader('cache-control', 'no-store');
    res.end(JSON.stringify({ error: 'unavailable' }));
  }
};
module.exports.todayRef = todayRef;
