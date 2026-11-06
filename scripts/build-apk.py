from pathlib import Path
import subprocess,zipfile,shutil,xml.etree.ElementTree as E
root=Path(__file__).resolve().parents[1]; sdk=Path('/workspace/toolchains/android-sdk'); tools=sdk/'build-tools/35.0.0'; jar=sdk/'platforms/android-35/android.jar'; build=root/'android/offline-build'; build.mkdir(exist_ok=True)
def run(*args):subprocess.run([str(x) for x in args],check=True)
manifest=E.parse(root/'android/app/src/main/AndroidManifest.xml');manifest.getroot().set('package','app.birdquest.mobile');E.register_namespace('android','http://schemas.android.com/apk/res/android');manifest.write(build/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
run(tools/'aapt2','compile','--dir',root/'android/app/src/main/res','-o',build/'resources.zip')
run(tools/'aapt2','link','-I',jar,'--manifest',build/'AndroidManifest.xml','--min-sdk-version','26','--target-sdk-version','35','--version-code','3','--version-name','0.1.2','-o',build/'base.apk',build/'resources.zip')
(build/'classes').mkdir(exist_ok=True);(build/'dex').mkdir(exist_ok=True)
run('java','-jar','/workspace/toolchains/ecj.jar','-source','1.8','-target','1.8','-bootclasspath',str(jar)+':'+str(tools/'core-lambda-stubs.jar'),'-d',build/'classes',*list((root/'android/app/src/main/java').rglob('*.java')))
run('java','-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--lib',jar,'--min-api','26','--output',build/'dex',*list((build/'classes').rglob('*.class')))
with zipfile.ZipFile(build/'base.apk','a',zipfile.ZIP_DEFLATED) as z:
 for f in (build/'dex').glob('*.dex'):z.write(f,f.name)
 for f in (root/'dist').rglob('*'):
  if f.is_file():z.write(f,'assets/www/'+str(f.relative_to(root/'dist')))
run(tools/'zipalign','-f','4',build/'base.apk',build/'aligned.apk')
key=root/'android/.debug.keystore'
if not key.exists():run('keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Android Debug,O=Birdquest,C=US','-keyalg','RSA','-keysize','2048','-validity','10000')
apk=root/'artifacts/Birdquest-0.1.2-test.apk'
run(tools/'apksigner','sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android','--out',apk,build/'aligned.apk')
run(tools/'apksigner','verify','--verbose',apk)
print('APK:',apk)
