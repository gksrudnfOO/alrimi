// Fetches the Sunday Maple prediction page and returns its data as JSON; the page embeds the data in an inline Vue script.
const SRC = 'https://maplessunday.com/sunday';
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36';

const str = (s, key) => {
  const m = s.match(new RegExp(key + String.raw`\s*:\s*(['"])((?:\\.|(?!\1)[^\\])*)\1`));
  return m ? m[2].replace(/\\(.)/g, '$1').trim() : '';
};

function parse(html) {
  const date = k => (html.match(new RegExp(k + String.raw`\s*=\s*ref\(\s*['"]([^'"]*)['"]`)) || [])[1] || '';
  const block = (html.match(/const\s+predictions\s*=\s*ref\(\[([\s\S]*?)\]\);/) || [])[1] || '';
  const predictions = (block.match(/\{[^{}]*\}/g) || [])
    .map(o => ({ rank: +((o.match(/rank\s*:\s*(\d+)/) || [])[1] || 0), probability: str(o, 'probability'), benefit: str(o, 'benefit'), note: str(o, 'note') }))
    .filter(p => p.rank && p.benefit)
    .slice(0, 10);
  return { predictions, starforce: date('nextStarForceDate'), miracle: date('nextMiracleDayDate'), source: SRC };
}

module.exports = async (req, res) => {
  res.setHeader('content-type', 'application/json; charset=utf-8');
  try {
    const r = await fetch(SRC, { headers: { 'user-agent': UA, 'accept-language': 'ko-KR,ko;q=0.9' } });
    if (!r.ok) throw new Error('upstream ' + r.status);
    const data = parse(await r.text());
    if (!data.predictions.length) throw new Error('parse');
    res.setHeader('cache-control', 's-maxage=1800, stale-while-revalidate=86400');
    res.end(JSON.stringify(data));
  } catch (e) {
    res.statusCode = 502;
    res.setHeader('cache-control', 'no-store');
    res.end(JSON.stringify({ error: 'unavailable', source: SRC }));
  }
};
module.exports.parse = parse;
