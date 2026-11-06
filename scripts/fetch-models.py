"""Fetch pinned BirdNET/TFJS assets through the configured network proxy, verify Git blob hashes."""
import concurrent.futures, hashlib, json, pathlib, urllib.request
ROOT = pathlib.Path(__file__).resolve().parents[1] / 'dist'
COMMIT = '6ab67ac09fa64d98858b90f14318229aca9bb7dc'
API = f'https://api.github.com/repos/birdnet-team/real-time-pwa/git/trees/{COMMIT}?recursive=1'

def get(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'Birdquest-build/0.1'})
    with urllib.request.urlopen(request, timeout=90) as response:
        return response.read()

tree = json.loads(get(API))['tree']
files = [f for f in tree if f['type'] == 'blob' and f['path'].startswith('public/models/birdnet/')
         and ('/labels/' not in f['path'] or f['path'].endswith('/en_us.txt'))]

def fetch(f):
    relative = f['path'].removeprefix('public/')
    target = ROOT / relative
    data = target.read_bytes() if target.exists() else get(f'https://cdn.jsdelivr.net/gh/birdnet-team/real-time-pwa@{COMMIT}/{f["path"]}')
    actual = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
    if actual != f['sha']:
        raise RuntimeError(f'Upstream checksum mismatch: {relative}')
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return {'file': relative, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}

with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
    manifest = list(pool.map(fetch, files))
for relative, url in [
    ('vendor/tf.min.js', 'https://cdn.jsdelivr.net/npm/@tensorflow/tfjs@4.14.0/dist/tf.min.js'),
    ('vendor/TENSORFLOW-LICENSE.txt', 'https://raw.githubusercontent.com/tensorflow/tfjs/tfjs-v4.14.0/LICENSE'),
]:
    data=get(url);target=ROOT/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
    manifest.append({'file':relative,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
(ROOT / 'model-assets.json').write_text(json.dumps({'sourceCommit':COMMIT,'assets':manifest}, indent=2))
print(f'Verified and bundled {len(manifest)} files ({sum(f["bytes"] for f in manifest)/1e6:.1f} MB).')
