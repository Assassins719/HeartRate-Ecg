del Ecg.apk
ren app-release.apk Ecg.apk
adb uninstall com.ecgproduct
adb install .\Ecg.apk

adb shell monkey -p com.ecgproduct -c android.intent.category.LAUNCHER 1