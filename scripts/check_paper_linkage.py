"""Check plugin symbolic API references against a resolved Paper classpath.
Usage: python scripts/check_paper_linkage.py plugin.jar version.classpath full-jdk-directory
This is a static linkage check, not a substitute for server integration tests.
"""
import sys, struct, zipfile, json, hashlib
from pathlib import Path
from functools import lru_cache

class ClassFile:
    def __init__(self, data):
        self.data, self.pos = data, 8
        self.cp = [None] * self.u2()
        i = 1
        while i < len(self.cp):
            tag = self.u1()
            if tag == 1:
                n = self.u2(); value = self.take(n).decode('utf-8', errors='replace')
            elif tag in (7, 8, 16, 19, 20): value = self.u2()
            elif tag in (9, 10, 11, 12, 17, 18): value = (self.u2(), self.u2())
            elif tag in (3, 4): value = self.take(4)
            elif tag in (5, 6): value = self.take(8)
            elif tag == 15: value = (self.u1(), self.u2())
            else: raise ValueError(f'Unknown constant tag {tag}')
            self.cp[i] = (tag, value)
            i += 2 if tag in (5, 6) else 1
        self.access = self.u2()
        self.name = self.classname(self.u2())
        self.parent = self.classname(self.u2())
        self.interfaces = [self.classname(self.u2()) for _ in range(self.u2())]
        self.fields, self.methods = self.members(), self.members()
    def take(self, n):
        result = self.data[self.pos:self.pos+n]; self.pos += n; return result
    def u1(self): return self.take(1)[0]
    def u2(self): return struct.unpack('>H', self.take(2))[0]
    def u4(self): return struct.unpack('>I', self.take(4))[0]
    def classname(self, index): return self.cp[self.cp[index][1]][1] if index else None
    def members(self):
        result = set()
        for _ in range(self.u2()):
            self.u2(); name, descriptor = self.u2(), self.u2()
            result.add((self.cp[name][1], self.cp[descriptor][1]))
            for _ in range(self.u2()): self.u2(); self.take(self.u4())
        return result
    def references(self):
        for entry in self.cp:
            if entry and entry[0] in (9, 10, 11):
                owner, pair = entry[1]
                name, desc = self.cp[pair][1]
                yield entry[0], self.classname(owner), self.cp[name][1], self.cp[desc][1]

jars = [zipfile.ZipFile(p) for p in open(sys.argv[2], encoding='utf-8').read().splitlines() if p.endswith('.jar')]
index = {}
if len(sys.argv) < 4 or not (Path(sys.argv[3]) / 'jmods').is_dir():
    sys.exit('Pass a full JDK directory with jmods as the third argument.')
if len(sys.argv) > 3:
    for path in (Path(sys.argv[3]) / 'jmods').glob('*.jmod'):
        jar = zipfile.ZipFile(path)
        for name in jar.namelist():
            if name.startswith('classes/') and name.endswith('.class'):
                index[name[8:-6]] = (jar, name)
for jar in jars:
    for name in jar.namelist():
        if name.endswith('.class') and not name.startswith('META-INF/'):
            index.setdefault(name[:-6], (jar, name))
@lru_cache(None)
def load(name):
    entry = index.get(name)
    return ClassFile(entry[0].read(entry[1])) if entry else None
@lru_cache(None)
def has_member(owner, kind, name, desc):
    cls = load(owner)
    if cls is None: return False
    if (name, desc) in (cls.fields if kind == 9 else cls.methods): return True
    if name == '<init>': return False
    return any(has_member(base, kind, name, desc) for base in [cls.parent, *cls.interfaces] if base)
errors = set(); checked = set()
with zipfile.ZipFile(sys.argv[1]) as plugin:
    for file in plugin.namelist():
        if not file.endswith('.class') or '/libs/' in file: continue
        cls = ClassFile(plugin.read(file))
        for kind, owner, name, desc in cls.references():
            if not owner.startswith(('org/bukkit/', 'io/papermc/', 'com/destroystokyo/', 'net/kyori/')): continue
            checked.add((kind, owner, name, desc))
            target = load(owner)
            reason = None
            if target is None: reason = 'missing class'
            elif kind in (10, 11) and bool(target.access & 0x200) != (kind == 11): reason = 'class/interface invocation changed'
            elif not has_member(owner, kind, name, desc): reason = 'missing member/descriptor'
            if reason: errors.add(f'{cls.name}: {owner}.{name}{desc}: {reason}')
for error in sorted(errors): print(error)
print(f'{len(checked)} distinct API references checked; {len(errors)} unresolved references')
if len(sys.argv) > 4:
    Path(sys.argv[4]).write_text(json.dumps({"plugin_sha256": hashlib.sha256(Path(sys.argv[1]).read_bytes()).hexdigest(), "classpath": sys.argv[2], "references_checked": len(checked), "errors": sorted(errors)}, indent=2), encoding='utf-8')
sys.exit(bool(errors))
