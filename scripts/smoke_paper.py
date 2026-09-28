"""Isolated Paper startup/reload/shutdown smoke test; requires explicit --accept-eula."""
import sys
sys.stdout.reconfigure(errors="replace")
import argparse, hashlib, json, os, queue, shutil, subprocess, threading, time, urllib.request
from pathlib import Path
parser = argparse.ArgumentParser()
parser.add_argument('version')
parser.add_argument('java_home')
parser.add_argument('--accept-eula', action='store_true', required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
folder = root / 'build' / 'smoke' / args.version
folder.mkdir(parents=True, exist_ok=True)
def request(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'CoreSuite-local-compatibility-check/1.0'}), timeout=90)
with request(f'https://fill.papermc.io/v3/projects/paper/versions/{args.version}/builds/latest') as response:
    info = json.load(response)
artifact = info['downloads']['server:default']
server = folder / artifact['name']
if not server.exists() or hashlib.sha256(server.read_bytes()).hexdigest() != artifact['checksums']['sha256']:
    with request(artifact['url']) as response: data = response.read()
    assert hashlib.sha256(data).hexdigest() == artifact['checksums']['sha256'], 'Download checksum mismatch'
    server.write_bytes(data)
(folder / 'eula.txt').write_text('eula=true\n')
(folder / 'server.properties').write_text('level-name=smoke_world_' + str(int(time.time())) + '\nserver-ip=127.0.0.1\nserver-port=25591\nonline-mode=true\nlevel-type=minecraft:normal\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nmax-players=2\nenable-query=false\nenable-rcon=false\n')
plugins = folder / 'plugins'
plugins.mkdir(exist_ok=True)
shutil.copy2(root / 'build/libs/CoreSuite-1.0.0.jar', plugins / 'CoreSuite.jar')
shutil.copy2(root / 'build/libs/CoreSuite-SmokeProbe.jar', plugins / 'CoreSuite-SmokeProbe.jar')
modules = plugins / 'CoreSuite/modules'
modules.mkdir(parents=True, exist_ok=True)
(modules / 'website.yml').write_text('bind-ip: "127.0.0.1"\nport: 25592\napi-key: "isolated-smoke-test-only"\n')
java = Path(args.java_home) / 'bin/java.exe'
process = subprocess.Popen([str(java), '-Xms512M', '-Xmx1536M', '-jar', str(server), '--nogui'], cwd=folder,
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace', creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
lines = queue.Queue()
def read_output():
    for line in process.stdout: lines.put(line)
threading.Thread(target=read_output, daemon=True).start()
started = time.monotonic(); ready_at = None; reloaded = False; stopped = False; probe_finished = False
with (folder / 'smoke-console.log').open('w', encoding='utf-8') as log:
    try:
        while process.poll() is None or not lines.empty():
            try: line = lines.get(timeout=1)
            except queue.Empty: line = ''
            if line:
                log.write(line); log.flush()
                if any(word in line for word in ('ERROR', 'WARN', 'CoreSuite', 'Done (', 'Exception')): print(line.strip(), flush=True)
                if 'Done (' in line: ready_at = time.monotonic()
                if 'CORESUITE_PROBE_PASS' in line or 'CORESUITE_PROBE_FAIL' in line: probe_finished = True
            elapsed = time.monotonic() - started
            if ready_at and probe_finished and not reloaded and time.monotonic() - ready_at > 8:
                process.stdin.write('coresuite modules\ncoresuite reload\n'); process.stdin.flush(); reloaded = True
            if not stopped and ((ready_at and probe_finished and reloaded and time.monotonic() - ready_at > 22) or elapsed > 240):
                process.stdin.write('stop\n'); process.stdin.flush(); stopped = True
            if elapsed > 300: process.kill(); raise RuntimeError('Server test timed out')
    finally:
        if process.poll() is None:
            try:
                process.stdin.write('stop\n'); process.stdin.flush(); process.wait(timeout=30)
            except Exception:
                process.terminate(); process.wait(timeout=15)
log_text = (folder / 'smoke-console.log').read_text(encoding='utf-8')
failures = [line for line in log_text.splitlines() if ('CoreSuite' in line and ('ERROR' in line or 'generated an exception' in line or 'Failed to' in line)) or 'NoClassDefFoundError' in line or 'NoSuchMethodError' in line or 'NoSuchFieldError' in line]
print(f'Server exit={process.returncode}; ready={ready_at is not None}; plugin failures={len(failures)}; log={folder / "smoke-console.log"}')
raise SystemExit(0 if ready_at is not None and process.returncode == 0 and not failures and 'CORESUITE_PROBE_PASS' in log_text else 1)
