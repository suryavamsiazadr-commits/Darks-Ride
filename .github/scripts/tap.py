# Taps the first on-screen element whose text contains the given words (uiautomator dump).
import re, subprocess, sys
want, xml = sys.argv[1].lower(), open(sys.argv[2], encoding="utf-8", errors="ignore").read()
for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    text, x1, y1, x2, y2 = m.group(1), *map(int, m.groups()[1:])
    if want in text.lower():
        subprocess.run(["adb", "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)])
        print("tapped", text)
        sys.exit(0)
print("not found:", want)
