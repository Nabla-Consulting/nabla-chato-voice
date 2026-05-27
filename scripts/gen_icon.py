from PIL import Image, ImageDraw, ImageFont
import os, sys

EMOJI = "🤘"
BG = (28, 28, 46)  # #1C1C2E dark navy
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FONT_PATH = r"C:\Windows\Fonts\seguiemj.ttf"

for density, size in SIZES.items():
    img = Image.new("RGBA", (size, size), BG)
    draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype(FONT_PATH, int(size * 0.65))
        bbox = draw.textbbox((0, 0), EMOJI, font=font, embedded_color=True)
        x = (size - (bbox[2] - bbox[0])) // 2 - bbox[0]
        y = (size - (bbox[3] - bbox[1])) // 2 - bbox[1]
        draw.text((x, y), EMOJI, font=font, embedded_color=True)
    except Exception as e:
        print(f"Font error: {e}", file=sys.stderr)
    out = f"app/src/main/res/mipmap-{density}/ic_launcher.png"
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out, "PNG")
    print(f"✓ {out} ({size}x{size})")
