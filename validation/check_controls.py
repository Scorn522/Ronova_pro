from pathlib import Path
import subprocess,zipfile,sys,base64,os,datetime
r=Path(__file__).resolve().parents[1]
jdk=Path(os.environ['JAVA_HOME']);out=r/'build'/('control-check-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'));out.mkdir(parents=True)
mode=sys.argv[1] if len(sys.argv)>1 else 'attach'
if mode not in ('attach','premain'):raise SystemExit('Use attach or premain')
if os.name=='nt':
 command=['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(r/'native/build-control.ps1'),'-JdkHome',str(jdk)]
 if os.environ.get('RONOVA_ZIG'):command+=['-Zig',os.environ['RONOVA_ZIG']]
 native=subprocess.run(command,capture_output=True,creationflags=0x08000000)
 (out/'native-build.log').write_bytes(native.stdout+native.stderr)
 if native.returncode:raise SystemExit('Native compilation failed: '+str(out))
for source in ['bootstrap','agent']:
 dest=out/source;dest.mkdir()
 files=sorted((r/source/'src/main/java').rglob('*.java'))
 args=['-proc:none','-encoding','UTF-8','-d',str(dest)]
 if source=='agent':args+=['--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED','--add-exports','java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED','--add-modules','jdk.attach']
 argfile=out/(source+'-args.txt');argfile.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in args+files),encoding='gbk' if os.name=='nt' else 'utf-8')
 compiled=subprocess.run([str(jdk/'bin/javac.exe'),'@'+str(argfile)],capture_output=True,creationflags=0x08000000 if os.name=='nt' else 0)
 (out/(source+'-compile.log')).write_bytes(compiled.stdout+compiled.stderr)
 if compiled.returncode:raise SystemExit(source+' compilation failed: '+str(out))
for source,destination,cp in [('agentCheck','checks',str(out/'bootstrap')),('agentPolicy','policy','.')]:
 files=list((r/'validation/src'/source/'java').rglob('*.java'));dest=out/destination;dest.mkdir(exist_ok=True)
 args=['-encoding','UTF-8','-d',str(dest),'-cp',cp]+list(map(str,files));p=subprocess.run([str(jdk/'bin/javac.exe')]+args,capture_output=True,creationflags=0x08000000)
 if p.returncode:print(p.stderr.decode('gbk',errors='replace'));sys.exit(p.returncode)
manifest='Manifest-Version: 1.0\nPremain-Class: dev.ronova.pro.agent.RecoveryAgent\nAgent-Class: dev.ronova.pro.agent.RecoveryAgent\nCan-Retransform-Classes: true\nCan-Redefine-Classes: true\n\n'
for source in ['agent','bootstrap']:
 with zipfile.ZipFile(out/(source+'.jar'),'w',zipfile.ZIP_DEFLATED) as z:
  z.writestr('META-INF/MANIFEST.MF',manifest if source=='agent' else 'Manifest-Version: 1.0\n\n')
  for f in (out/source).rglob('*.class'):z.write(f,f.relative_to(out/source).as_posix())
  if source=='bootstrap':
   for f in (r/'build/control-native').rglob('*.dll'):z.write(f,f.relative_to(r/'build/control-native').as_posix())
mode=sys.argv[1] if len(sys.argv)>1 else 'premain'
args=[str(jdk/'bin/java.exe'),'-Xverify:all']
if mode=='premain':args+=['-javaagent:'+str(out/'agent.jar')+'=base64:'+base64.urlsafe_b64encode(str(out/'bootstrap.jar').encode()).decode().rstrip('=')]
args+=['-cp',str(out/'checks'),'check.BoundaryCheck',mode,str(out/'agent.jar'),str(out/'bootstrap.jar'),str(out/'policy')]
try:
 p=subprocess.run(args,capture_output=True,creationflags=0x08000000,timeout=75,cwd=out)
 data=p.stdout+p.stderr;(out/('boundary-'+mode+'.log')).write_bytes(data);print(data.decode('utf-8',errors='replace')[-13000:]);sys.exit(p.returncode)
except subprocess.TimeoutExpired as e:
 (out/('boundary-'+mode+'.log')).write_bytes((e.stdout or b'')+(e.stderr or b''));print('TIMEOUT see log');sys.exit(2)
