$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$sdk  = "$env:LOCALAPPDATA\Android\Sdk"
$bt   = "$sdk\build-tools\36.0.0"
$jar  = "$sdk\platforms\android-36\android.jar"
$out  = "$here\build"
$ks   = "$here\debug.keystore"

Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory "$out\classes","$out\gen" | Out-Null

& "$bt\aapt2.exe" compile --dir "$here\res" -o "$out\res.zip"; if ($LASTEXITCODE) { throw "aapt2 compile" }
& "$bt\aapt2.exe" link -o "$out\base.apk" -I $jar --manifest "$here\AndroidManifest.xml" `
    --min-sdk-version 29 --target-sdk-version 35 --version-code 1 --version-name 1.0.0 --java "$out\gen" "$out\res.zip"; if ($LASTEXITCODE) { throw "aapt2 link" }

$src = @(Get-ChildItem "$here\src","$out\gen" -Recurse -Filter *.java | % FullName)
& javac --release 17 -Xlint:-options -cp $jar -d "$out\classes" $src; if ($LASTEXITCODE) { throw "javac" }
$cls = @(Get-ChildItem "$out\classes" -Recurse -Filter *.class | % FullName)
& "$bt\d8.bat" --lib $jar --min-api 29 --output $out $cls; if ($LASTEXITCODE) { throw "d8" }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$out\base.apk", 'Update')
[void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$out\classes.dex", "classes.dex")
$zip.Dispose()

& "$bt\zipalign.exe" -f 4 "$out\base.apk" "$out\aligned.apk"; if ($LASTEXITCODE) { throw "zipalign" }
if (-not (Test-Path $ks)) {
    & keytool -genkeypair -keystore $ks -storepass android -keypass android -alias debug -keyalg RSA -keysize 2048 `
        -validity 10000 -dname "CN=GCamRedirect"; if ($LASTEXITCODE) { throw "keytool" }
}
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android --out "$here\GalleryRedirect.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw "apksigner" }
Remove-Item "$here\GalleryRedirect.apk.idsig" -ErrorAction SilentlyContinue   # helper file apksigner leaves behind
"Built: $here\GalleryRedirect.apk"
