"""스킨 "플레이용 재조립" 도구 — 폰 정면 그림을 부품으로 잘라 커버 화면(904x2192)에 다시 조립할 때 쓴다.

규칙·순서: docs/fold-retro/SKIN_FORMAT.md 8절. 필요: Pillow.
이 파일은 일반 도구만 담는다. 실제 제품 사진과 그 좌표(개인용 스킨)는 저장소에 넣지 않는다(AGENTS.md 규칙 8).

  from skin_compose import *
  S = Src("phone.png")                     # 투명 배경 정면 그림
  part = scale(S.crop(x0, y0, x1, y1), 1.2) # 부품 잘라 키우기
  row = spread(part, [(0, 40), (300, 420), (part.width - 40, part.width)], W)  # 지킬 곳만 두고 사이를 늘리기
  save_pack("my_phone", "My phone", "내 폰", body, keys, screen, (60, 130, 255), 0.6, 0.3)
"""
import json, os, zipfile
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

W, H = 904, 2192  # Fold5 커버 가용 영역(px)

class Src:
    def __init__(self, path): self.im = Image.open(path).convert('RGBA')
    def crop(self, x0, y0, x1, y1): return self.im.crop((x0, y0, x1, y1))
    def color(self, x, y): return self.im.getpixel((x, y))

def scale(im, s): return im.resize((max(1, round(im.width * s)), max(1, round(im.height * s))), Image.LANCZOS)

def spread(im, keeps, target_w):
    """가로로 늘리기: keeps=[(x0,x1),...] 구간은 그대로 두고 그 사이만 고르게 늘린다(맨 앞·뒤 구간 포함)"""
    keeps = sorted(keeps)
    kept = sum(b - a for a, b in keeps)
    gaps = []
    pos = 0
    for a, b in keeps:
        gaps.append((pos, a)); pos = b
    gaps.append((pos, im.width))
    gap_total = sum(b - a for a, b in gaps)
    extra = target_w - kept
    out = Image.new('RGBA', (target_w, im.height))
    x = 0
    pieces = []
    for i, (ga, gb) in enumerate(gaps):
        pieces.append(('g', ga, gb))
        if i < len(keeps): pieces.append(('k',) + keeps[i])
    for kind, a, b in pieces:
        if b <= a and kind == 'k': continue
        if kind == 'k':
            out.paste(im.crop((a, 0, b, im.height)), (x, 0)); x += b - a
        else:
            w = round(extra * (b - a) / gap_total) if gap_total else 0
            if w > 0 and b > a:
                out.paste(im.crop((a, 0, b, im.height)).resize((w, im.height), Image.LANCZOS), (x, 0))
            x += w
    return out

def slice_h(im, left, right, target_w): return spread(im, [(0, left), (im.width - right, im.width)], target_w)
def slice_v(im, top, bottom, target_h): return slice_h(im.transpose(Image.TRANSPOSE), top, bottom, target_h).transpose(Image.TRANSPOSE)
def nine(im, l, r, t, b, tw, th): return slice_v(slice_h(im, l, r, tw), t, b, th)

def round_cut(im, radius, blur=2):
    m = Image.new('L', im.size, 0)
    cx, cy = im.width / 2, im.height / 2
    ImageDraw.Draw(m).ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=255)
    m = m.filter(ImageFilter.GaussianBlur(blur))
    out = im.copy(); out.putalpha(Image.composite(im.getchannel('A'), Image.new('L', im.size, 0), m))
    return out

def place_box(rect, src_box, dst_xy, f):
    """사진 좌표 사각형 rect를, 사진 src_box(왼쪽 위)를 dst_xy에 f배로 붙인 결과 좌표로"""
    x, y, w, h = rect
    return [round(dst_xy[0] + (x - src_box[0]) * f), round(dst_xy[1] + (y - src_box[1]) * f), round(w * f), round(h * f)]

def save_pack(sid, name, name_ko, body, keys, screen, tint, bright, mix, game_label=True):
    pressed = ImageEnhance.Brightness(body.convert('RGB')).enhance(bright)
    pressed = Image.blend(pressed, Image.new('RGB', body.size, tint), mix).convert('RGBA'); pressed.putalpha(body.getchannel('A'))
    os.makedirs(sid, exist_ok=True)
    body.save(f'{sid}/body.png', optimize=True); pressed.save(f'{sid}/pressed.png', optimize=True)
    spec = {"format": 1, "name": name, "names": {"ko": name_ko}, "layouts": [{"image": "body.png", "pressed": "pressed.png",
            "size": [body.width, body.height], "background": "#000000", "screen": screen, "screenColor": "#000000", "keys": keys}]}
    json.dump(spec, open(f'{sid}/skin.json', 'w'), indent=2, ensure_ascii=False)
    with zipfile.ZipFile(f'{sid}.zip', 'w') as z:
        for n in ('skin.json', 'body.png', 'pressed.png'): z.write(f'{sid}/{n}', n)
    chk = Image.new('RGBA', body.size, (20, 20, 20, 255)); chk.alpha_composite(body); dr = ImageDraw.Draw(chk)
    x, y, w, h = screen; dr.rectangle((x, y, x + w, y + h), fill=(90, 150, 200, 255))
    plain = chk.copy()
    for c, v in keys.items():
        rc = v["rect"] if isinstance(v, dict) else v
        dr.rectangle((rc[0], rc[1], rc[0] + rc[2], rc[1] + rc[3]), outline=(255, 0, 255, 255), width=3)
        dr.text((rc[0] + 4, rc[1] + 4), c, fill=(255, 0, 255, 255))
    pr = Image.new('RGBA', body.size, (20, 20, 20, 255)); pr.alpha_composite(pressed)
    out = Image.new('RGBA', (body.width * 3 + 60, body.height), (40, 40, 40, 255))
    out.alpha_composite(plain, (0, 0)); out.alpha_composite(chk, (body.width + 30, 0)); out.alpha_composite(pr, (body.width * 2 + 60, 0))
    out.convert('RGB').resize((out.width // 2, out.height // 2), Image.LANCZOS).save(f'{sid}_preview.png')
