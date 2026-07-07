with open("app/src/main/java/com/arcadesoftware/musix/MainActivity.kt", "r") as f:
    text = f.read()

text = text.replace("checked = rememberPos, onCheckedChange = {", "selected = { rememberPos }, backdrop = mainBackdrop, onSelect = {")
text = text.replace("checked = alwaysShuffle, onCheckedChange = {", "selected = { alwaysShuffle }, backdrop = mainBackdrop, onSelect = {")
text = text.replace("checked = autoDownload, onCheckedChange = {", "selected = { autoDownload }, backdrop = mainBackdrop, onSelect = {")
text = text.replace("checked = wifiOnly, onCheckedChange = {", "selected = { wifiOnly }, backdrop = mainBackdrop, onSelect = {")

with open("app/src/main/java/com/arcadesoftware/musix/MainActivity.kt", "w") as f:
    f.write(text)

