#!/usr/bin/env python3
"""기본 스킨 팩 생성기 — 코드로 직접 그린 폰 그림(제조사·통신사 디자인·로고 없음, MIT).

  python3 demo/skins/make_skins.py      # 저장소 루트에서

결과: demo/skins/<id>/{skin.json, body*.png, pressed*.png} 와 앱 내장용
      android/app/src/main/assets/skins/<id>.zip
규격: docs/fold-retro/SKIN_FORMAT.md. 필요: Pillow, DejaVu Sans 폰트.
좌표는 모두 그림 px. 키 영역(skin.json)과 그림이 같은 숫자를 쓰도록 한 곳(아래 키 정의)에서 만든다.
"""
import json
import os
import zipfile

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "demo", "skins")
ASSETS = os.path.join(ROOT, "android", "app", "src", "main", "assets", "skins")
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
SS = 2  # 2배로 그려 줄여서 가장자리를 매끈하게

LETTERS = {"2": "ABC", "3": "DEF", "4": "GHI", "5": "JKL", "6": "MNO", "7": "PQRS", "8": "TUV", "9": "WXYZ", "0": "+", "1": ""}


def font(size, bold=True):
    return ImageFont.truetype(FONT if bold else FONT_REG, int(size * SS))


def s(v):
    return [int(round(x * SS)) for x in v]


def vgrad(size, top, bottom):
    w, h = size
    g = Image.new("RGBA", (1, h))
    for y in range(h):
        t = y / max(1, h - 1)
        g.putpixel((0, y), tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,))
    return g.resize((w, h))


def rounded(img, box, radius, fill=None, grad=None, outline=None, width=0):
    """box = (x, y, w, h) 그림 px"""
    x, y, w, h = box
    bx = s((x, y, x + w, y + h))
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(bx, radius=int(radius * SS), fill=255)
    if grad:
        layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
        layer.paste(vgrad((bx[2] - bx[0], bx[3] - bx[1]), *grad), (bx[0], bx[1]))
        img.paste(layer, (0, 0), mask)
    elif fill:
        img.paste(Image.new("RGBA", img.size, fill), (0, 0), mask)
    if outline:
        ImageDraw.Draw(img).rounded_rectangle(bx, radius=int(radius * SS), outline=outline, width=int(width * SS))


def shadow(img, box, radius, blur=10, offset=(0, 6), alpha=110):
    x, y, w, h = box
    sh = Image.new("L", img.size, 0)
    ImageDraw.Draw(sh).rounded_rectangle(s((x + offset[0], y + offset[1], x + w + offset[0], y + h + offset[1])), radius=int(radius * SS), fill=alpha)
    sh = sh.filter(ImageFilter.GaussianBlur(blur * SS))
    img.paste(Image.new("RGBA", img.size, (0, 0, 0, 255)), (0, 0), sh)


def text_center(img, cx, cy, text, size, color, bold=True):
    d = ImageDraw.Draw(img)
    f = font(size, bold)
    d.text((cx * SS, cy * SS), text, font=f, fill=color, anchor="mm")


def triangle(img, cx, cy, r, direction, color):
    pts = {
        "UP": [(cx, cy - r), (cx - r, cy + r * 0.7), (cx + r, cy + r * 0.7)],
        "DOWN": [(cx, cy + r), (cx - r, cy - r * 0.7), (cx + r, cy - r * 0.7)],
        "LEFT": [(cx - r, cy), (cx + r * 0.7, cy - r), (cx + r * 0.7, cy + r)],
        "RIGHT": [(cx + r, cy), (cx - r * 0.7, cy - r), (cx - r * 0.7, cy + r)],
    }[direction]
    ImageDraw.Draw(img).polygon([(x * SS, y * SS) for x, y in pts], fill=color)


def handset(img, cx, cy, size, color):
    """통화 키 그림(수화기): 굵은 호 + 양 끝 귀·입 부분"""
    n = int(size * 4 * SS)
    layer = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    c = n / 2
    r = n * 0.34
    w = int(n * 0.12)
    d.arc((c - r, c - r, c + r, c + r), 200, 340, fill=color, width=w)
    for ang in (200, 340):
        import math
        a = math.radians(ang)
        ex, ey = c + r * math.cos(a), c + r * math.sin(a)
        e = n * 0.1
        d.rounded_rectangle((ex - e * 1.3, ey - e * 0.1, ex + e * 1.3, ey + e * 0.9), radius=int(e * 0.45), fill=color)
    img.alpha_composite(layer, (int(cx * SS - c), int(cy * SS - c + n * 0.08)))


class Style:
    def __init__(self, body_top, body_bottom, key_top, key_bottom, key_text, key_text_pressed, key_pressed_top, key_pressed_bottom,
                 accent, edge, glass=(10, 11, 13, 255)):
        self.__dict__.update(locals())


def draw_key(img, k, st, pressed):
    x, y, w, h = k["rect"]
    r = k.get("radius", min(w, h) / 2)
    if not pressed:
        shadow(img, (x, y, w, h), r, blur=6, offset=(0, 5), alpha=90)
    top, bottom = (st.key_pressed_top, st.key_pressed_bottom) if pressed else (st.key_top, st.key_bottom)
    rounded(img, (x, y + (3 if pressed else 0), w, h), r, grad=(top, bottom), outline=st.edge, width=2)
    tc = st.key_text_pressed if pressed else st.key_text
    cx, cy = x + w / 2, y + h / 2 + (3 if pressed else 0)
    code = k["code"]
    if code in LETTERS:
        letters = LETTERS[code]
        if letters and w < h * 1.4:  # 좁은 키: 글자는 숫자 아래
            text_center(img, cx, cy - h * 0.1, code, w * 0.42, tc)
            text_center(img, cx, cy + h * 0.24, letters, w * 0.15, tc, bold=False)
        elif letters:
            text_center(img, cx - w * 0.12, cy, code, h * 0.5, tc)
            text_center(img, cx + w * 0.2, cy + h * 0.04, letters, h * 0.2, tc, bold=False)
        else:
            text_center(img, cx, cy, code, h * 0.5, tc)
    elif code in ("*", "#"):
        text_center(img, cx, cy + (h * 0.08 if code == "*" else 0), code, h * 0.55, tc)
    elif code == "CALL":
        handset(img, cx, cy, h * 0.3, (40, 170, 80, 255) if not pressed else (90, 230, 130, 255))
    elif code == "CLR":
        text_center(img, cx, cy, "CLR", h * 0.36, tc)
    elif code in ("SOFT_L", "SOFT_R"):
        ImageDraw.Draw(img).rounded_rectangle(s((cx - w * 0.22, cy - 4, cx + w * 0.22, cy + 4)), radius=4 * SS, fill=tc)


def draw_dpad(img, d, st, pressed_codes):
    cx, cy, ro, ri = d["cx"], d["cy"], d["outer"], d["inner"]
    shadow(img, (cx - ro, cy - ro, ro * 2, ro * 2), ro, blur=8, offset=(0, 6), alpha=100)
    rounded(img, (cx - ro, cy - ro, ro * 2, ro * 2), ro, grad=(st.key_top, st.key_bottom), outline=st.edge, width=2)
    dr = ImageDraw.Draw(img)
    # 눌린 방향: 링의 그 쪽 부채꼴을 어둡게
    for code, (a0, a1) in {"UP": (225, 315), "RIGHT": (315, 405), "DOWN": (45, 135), "LEFT": (135, 225)}.items():
        if code in pressed_codes:
            dr.pieslice(s((cx - ro + 3, cy - ro + 3, cx + ro - 3, cy + ro - 3)), a0, a1, fill=st.key_pressed_bottom)
    rounded(img, (cx - ri - 10, cy - ri - 10, (ri + 10) * 2, (ri + 10) * 2), ri + 10, fill=st.edge)
    ok_pressed = "OK" in pressed_codes
    rounded(img, (cx - ri, cy - ri, ri * 2, ri * 2), ri,
            grad=(st.key_pressed_top, st.key_pressed_bottom) if ok_pressed else (st.accent, tuple(max(0, c - 40) for c in st.accent[:3]) + (255,)))
    text_center(img, cx, cy, "OK", ri * 0.55, (255, 255, 255, 255))
    arrow = (ro + ri) / 2
    for code, (dx, dy) in {"UP": (0, -1), "DOWN": (0, 1), "LEFT": (-1, 0), "RIGHT": (1, 0)}.items():
        col = st.key_text_pressed if code in pressed_codes else st.key_text
        triangle(img, cx + dx * arrow, cy + dy * arrow, ro * 0.11, code, col)


def dpad_keys(d):
    """방향키 링을 누르기 영역(사각형) 다섯 개로: 링의 위·아래·왼·오른 띠 + 가운데 OK"""
    cx, cy, ro, ri = d["cx"], d["cy"], d["outer"], d["inner"]
    band = ro - ri  # 링 두께
    return {
        "UP": [cx - ri, cy - ro, ri * 2, band],
        "DOWN": [cx - ri, cy + ri, ri * 2, band],
        "LEFT": [cx - ro, cy - ri, band, ri * 2],
        "RIGHT": [cx + ri, cy - ri, band, ri * 2],
        "OK": {"rect": [cx - ri, cy - ri, ri * 2, ri * 2], "shape": "oval"},
    }


def render(size, body_fn, keys, dpad, st, pressed):
    img = Image.new("RGBA", (size[0] * SS, size[1] * SS), (0, 0, 0, 0))
    body_fn(img, st)
    codes = set(k["code"] for k in keys) | {"UP", "DOWN", "LEFT", "RIGHT", "OK"} if pressed else set()
    draw_dpad(img, dpad, st, codes)
    for k in keys:
        draw_key(img, k, st, pressed)
    return img.resize(size, Image.LANCZOS)


def key_json(keys, dpad):
    out = {}
    for k in keys:
        x, y, w, h = k["rect"]
        out[k["code"]] = {"rect": [x, y, w, h], "shape": "round", "radius": k.get("radius", min(w, h) / 2)}
    out.update(dpad_keys(dpad))
    return out


# ── 실버 폴더폰: 세로(커버 화면 904x2316) ──

def flip_portrait():
    W, H = 904, 2316
    screen = [92, 128, 720, 960]

    def body(img, st):
        shadow(img, (24, 24, 856, 1160), 70, blur=12, alpha=140)
        rounded(img, (24, 24, 856, 1160), 70, grad=(st.body_top, st.body_bottom), outline=st.edge, width=3)
        rounded(img, (60, 100, 784, 1016), 40, fill=st.glass)
        rounded(img, (392, 52, 120, 18), 9, fill=(40, 42, 46, 255))  # 수화 스피커
        # 경첩
        rounded(img, (70, 1178, 764, 84), 40, grad=((120, 124, 130, 255), (70, 73, 78, 255)), outline=st.edge, width=2)
        rounded(img, (110, 1200, 684, 40), 20, grad=((200, 204, 210, 255), (140, 144, 150, 255)))
        shadow(img, (24, 1250, 856, 1042), 70, blur=12, alpha=140)
        rounded(img, (24, 1250, 856, 1042), 70, grad=(st.body_bottom, st.body_top), outline=st.edge, width=3)
        ImageDraw.Draw(img).ellipse(s((444, 2252, 460, 2268)), fill=(50, 52, 56, 255))  # 마이크

    dpad = {"cx": 452, "cy": 1505, "outer": 185, "inner": 72}
    keys = [
        {"code": "SOFT_L", "rect": [64, 1300, 196, 92], "radius": 46},
        {"code": "SOFT_R", "rect": [644, 1300, 196, 92], "radius": 46},
        {"code": "CALL", "rect": [64, 1610, 196, 92], "radius": 46},
        {"code": "CLR", "rect": [644, 1610, 196, 92], "radius": 46},
    ]
    nums = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"]
    for i, c in enumerate(nums):
        r, col = divmod(i, 3)
        keys.append({"code": c, "rect": [64 + col * 266, 1735 + r * 132, 244, 112], "radius": 50})
    return (W, H), screen, body, keys, dpad


# ── 실버 폴더폰: 가로(펼친 화면 — 게임 가운데, 왼쪽 방향키, 오른쪽 숫자) ──

def flip_landscape():
    W, H = 2000, 1560
    screen = [515, 110, 970, 1293]  # 3:4

    def body(img, st):
        shadow(img, (20, 20, 1960, 1500), 120, blur=14, alpha=140)
        rounded(img, (20, 20, 1960, 1500), 120, grad=(st.body_top, st.body_bottom), outline=st.edge, width=3)
        rounded(img, (480, 70, 1040, 1373), 40, fill=st.glass)
        for x in (330, 1670):  # 스피커 구멍
            for i in range(5):
                ImageDraw.Draw(img).ellipse(s((x - 50 + i * 25, 1400, x - 38 + i * 25, 1412)), fill=(60, 62, 66, 255))

    dpad = {"cx": 255, "cy": 760, "outer": 205, "inner": 80}
    keys = [
        {"code": "SOFT_L", "rect": [50, 330, 190, 104], "radius": 52},
        {"code": "SOFT_R", "rect": [270, 330, 190, 104], "radius": 52},
        {"code": "CALL", "rect": [50, 1086, 190, 104], "radius": 52},
        {"code": "CLR", "rect": [270, 1086, 190, 104], "radius": 52},
    ]
    nums = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"]
    for i, c in enumerate(nums):
        r, col = divmod(i, 3)
        keys.append({"code": c, "rect": [1528 + col * 146, 380 + r * 196, 134, 170], "radius": 40})
    return (W, H), screen, body, keys, dpad


SILVER = Style(
    body_top=(222, 225, 230, 255), body_bottom=(168, 173, 180, 255),
    key_top=(246, 247, 249, 255), key_bottom=(206, 209, 214, 255),
    key_text=(40, 44, 52, 255), key_text_pressed=(90, 200, 255, 255),
    key_pressed_top=(120, 126, 136, 255), key_pressed_bottom=(80, 86, 96, 255),
    accent=(70, 120, 200, 255), edge=(120, 124, 132, 255),
)

BLACK = Style(
    body_top=(48, 50, 54, 255), body_bottom=(16, 17, 19, 255),
    key_top=(40, 42, 46, 255), key_bottom=(18, 19, 21, 255),
    key_text=(220, 224, 230, 255), key_text_pressed=(120, 220, 255, 255),
    key_pressed_top=(70, 90, 110, 255), key_pressed_bottom=(40, 60, 80, 255),
    accent=(150, 155, 162, 255), edge=(90, 92, 98, 255),
)

SKINS = [
    ("silver_flip", "Silver flip (drawn)", "실버 폴더폰 (그림)", SILVER),
    ("black_flip", "Black flip (drawn)", "검정 폴더폰 (그림)", BLACK),
]


def build(skin_id, name, name_ko, st):
    d = os.path.join(OUT, skin_id)
    os.makedirs(d, exist_ok=True)
    layouts = []
    for tag, fn in (("portrait", flip_portrait), ("landscape", flip_landscape)):
        size, screen, body, keys, dpad = fn()
        render(size, body, keys, dpad, st, False).save(os.path.join(d, f"body_{tag}.png"), optimize=True)
        render(size, body, keys, dpad, st, True).save(os.path.join(d, f"pressed_{tag}.png"), optimize=True)
        layouts.append({
            "image": f"body_{tag}.png",
            "pressed": f"pressed_{tag}.png",
            "size": list(size),
            "background": "#141414",
            "screen": screen,
            "keys": key_json(keys, dpad),
        })
    spec = {"format": 1, "name": name, "names": {"ko": name_ko}, "layouts": layouts}
    with open(os.path.join(d, "skin.json"), "w") as f:
        json.dump(spec, f, indent=2, ensure_ascii=False)
        f.write("\n")
    os.makedirs(ASSETS, exist_ok=True)
    with zipfile.ZipFile(os.path.join(ASSETS, f"{skin_id}.zip"), "w", zipfile.ZIP_STORED) as z:
        for fn_ in sorted(os.listdir(d)):
            info = zipfile.ZipInfo(fn_, date_time=(2026, 1, 1, 0, 0, 0))
            with open(os.path.join(d, fn_), "rb") as src:
                z.writestr(info, src.read())
    print(skin_id, "ok")


if __name__ == "__main__":
    for args in SKINS:
        build(*args)
