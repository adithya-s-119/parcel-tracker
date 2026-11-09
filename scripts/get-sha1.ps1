param(
    [string]$Keystore = "$env:USERPROFILE\.android\debug.keystore",
    [string]$Alias = "androiddebugkey",
    [string]$StorePass = "android",
    [string]$KeyPass = "android"
)

if (-not (Test-Path $Keystore)) {
    Write-Error "Debug keystore not found at $Keystore. Build the app once to create it."
    exit 1
}

$javaHome = $env:JAVA_HOME
$keytool = if ($javaHome -and (Test-Path "$javaHome\bin\keytool.exe")) {
    "$javaHome\bin\keytool.exe"
} else {
    "keytool"
}

& $keytool -list -v -keystore $Keystore -alias $Alias -storepass $StorePass -keypass $KeyPass |
    Select-String -Pattern "SHA1:|SHA-1:"

Write-Host ""
Write-Host "Package name: com.parceltracker.app"
Write-Host "Add the SHA1 above to an Android OAuth client in Google Cloud Console."
