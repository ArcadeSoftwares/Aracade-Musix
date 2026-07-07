import re

with open("app/src/main/java/com/arcadesoftware/musix/MainActivity.kt", "r") as f:
    text = f.read()

# We know the block starts with `                if (settingsScreen == "Main") {`
# and ends right before `                Spacer(modifier = Modifier.height(48.dp))`
# But we need to make sure we don't mess up the closing braces.

start_pattern = '                if (settingsScreen == "Main") {\n'
end_pattern = '                }\n                Spacer(modifier = Modifier.height(48.dp))'

start_idx = text.find(start_pattern)
end_idx = text.find(end_pattern)

print("start_idx:", start_idx)
print("end_idx:", end_idx)

if start_idx != -1 and end_idx != -1:
    block = text[start_idx:end_idx]
    print(block[-100:]) # print last 100 chars to see what it ends with

