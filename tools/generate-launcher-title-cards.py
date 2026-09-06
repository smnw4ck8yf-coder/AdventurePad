#!/usr/bin/env python3
"""Generate first-party neutral launcher cards without commercial imagery."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "artwork"
SIZE = (600, 768)
TITLES = {
    "atlantis": "Indiana Jones and the Fate of Atlantis",
    "comi": "The Curse of Monkey Island",
    "discworld": "Discworld",
    "emi": "Escape from Monkey Island",
    "ft": "Full Throttle",
    "gk1": "Gabriel Knight: Sins of the Fathers",
    "gk2": "The Beast Within: A Gabriel Knight Mystery",
    "indy3": "Indiana Jones and the Last Crusade",
    "loom": "Loom",
    "maniac": "Maniac Mansion",
    "monkey": "The Secret of Monkey Island",
    "monkey2": "Monkey Island 2: LeChuck's Revenge",
    "queen": "Flight of the Amazon Queen",
    "samnmax": "Sam & Max Hit the Road",
    "simon1": "Simon the Sorcerer",
    "sky": "Beneath a Steel Sky",
    "sword1": "Broken Sword: The Shadow of the Templars",
    "sword2": "Broken Sword II: The Smoking Mirror",
    "tentacle": "Day of the Tentacle",
    "thedig": "The Dig",
}
PALETTES = (
    ("#16122c", "#5f3dc4", "#ffcf56"),
    ("#10283a", "#167d9a", "#8ee3ef"),
    ("#29152e", "#a13d63", "#f3c677"),
    ("#17291f", "#3f8f62", "#b8e986"),
)


def wrapped_lines(draw, title, font, max_width):
    words = title.split()
    lines = []
    current = []
    for word in words:
        candidate = " ".join([*current, word])
        if current and draw.textbbox((0, 0), candidate, font=font)[2] > max_width:
            lines.append(" ".join(current))
            current = [word]
        else:
            current.append(word)
    if current:
        lines.append(" ".join(current))
    return lines


def generate(slug, title, palette_index):
    background, accent, highlight = PALETTES[palette_index % len(PALETTES)]
    image = Image.new("RGB", SIZE, background)
    draw = ImageDraw.Draw(image)
    width, height = SIZE

    for offset in range(-height, width, 96):
        draw.polygon(
            [(offset, 0), (offset + 44, 0), (offset + height + 44, height), (offset + height, height)],
            fill=accent,
        )
    draw.rectangle((32, 32, width - 32, height - 32), outline=highlight, width=6)
    draw.rectangle((50, 50, width - 50, height - 50), fill=background)
    draw.ellipse((width - 210, 80, width + 30, 320), outline=accent, width=18)
    draw.ellipse((-50, height - 270, 190, height - 30), outline=accent, width=18)

    small = ImageFont.load_default(size=24)
    title_font = ImageFont.load_default(size=56)
    draw.text((width // 2, 92), "ADVENTUREPAD", font=small, fill=highlight, anchor="ma")
    lines = wrapped_lines(draw, title.upper(), title_font, width - 140)
    line_height = 66
    start_y = height // 2 - (len(lines) * line_height) // 2
    for index, line in enumerate(lines):
        draw.text(
            (width // 2, start_y + index * line_height),
            line,
            font=title_font,
            fill="#ffffff",
            anchor="ma",
            stroke_width=2,
            stroke_fill=background,
        )
    draw.line((120, height - 120, width - 120, height - 120), fill=highlight, width=4)
    draw.text((width // 2, height - 88), "LAUNCHER TITLE CARD", font=small, fill=highlight, anchor="ma")

    destination = OUTPUT / slug / "box.png"
    destination.parent.mkdir(parents=True, exist_ok=True)
    image.save(destination, format="PNG", optimize=True)


def main():
    for index, (slug, title) in enumerate(TITLES.items()):
        generate(slug, title, index)


if __name__ == "__main__":
    main()
