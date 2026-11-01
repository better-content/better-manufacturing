#!/usr/bin/env python3
"""Authored 16px sprites for the seven identities. Standard library only; no borrowed textures."""
import json
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/better_manufacturing'
OUTLINE = (42, 48, 55, 255)


def canvas(): return [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]

def rect(image, x, y, width, height, color):
    for row in range(y, y + height):
        for col in range(x, x + width): image[row][col] = color

def point(image, x, y, color): image[y][x] = color

def stock(base, light, dark):
    image = canvas()
    for x, y in [(2, 8), (4, 5), (6, 2)]:
        rect(image, x, y, 8, 5, OUTLINE); rect(image, x + 1, y + 1, 6, 3, dark)
        rect(image, x + 1, y + 1, 6, 2, base); rect(image, x + 1, y + 1, 6, 1, light)
    return image

def catalyst(base, light, carrier):
    image = canvas()
    for x, y in [(2, 9), (7, 10), (10, 7), (6, 5), (3, 4)]:
        rect(image, x, y, 4, 4, OUTLINE); rect(image, x + 1, y + 1, 2, 2, base); point(image, x + 1, y + 1, light)
        if carrier: point(image, x + 2, y + 2, carrier)
    return image

def lactide():
    image = canvas(); shadow = (116, 128, 133, 255); white = (240, 239, 225, 255)
    for x, y, h in [(3, 5, 7), (7, 2, 10), (10, 6, 7)]:
        rect(image, x, y, 3, h, OUTLINE); rect(image, x + 1, y, 1, h - 1, white)
        point(image, x + 2, y + h - 2, shadow)
    return image

def propylene():
    image = canvas(); carbon = (107, 121, 130, 255); light = (179, 196, 199, 255)
    rect(image, 4, 8, 8, 1, light); rect(image, 4, 10, 4, 1, light)
    rect(image, 9, 5, 1, 3, light); rect(image, 10, 4, 2, 1, light)
    for x, y in [(1, 7), (7, 7), (11, 2)]:
        rect(image, x, y, 4, 4, OUTLINE); rect(image, x + 1, y + 1, 2, 2, carbon); point(image, x + 1, y + 1, light)
    return image

def save(name, image):
    def chunk(kind, value): return struct.pack('>I', len(value)) + kind + value + struct.pack('>I', zlib.crc32(kind + value) & 0xffffffff)
    pixels = b''.join(b'\0' + bytes(channel for pixel in row for channel in pixel) for row in image)
    encoded = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(pixels, 9)) + chunk(b'IEND', b'')
    texture = ROOT / 'textures/item' / (name + '.png'); texture.parent.mkdir(parents=True, exist_ok=True); texture.write_bytes(encoded)
    model = ROOT / 'models/item' / (name + '.json'); model.parent.mkdir(parents=True, exist_ok=True)
    model.write_text(json.dumps({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'better_manufacturing:item/' + name}}, indent=2) + '\n')

if __name__ == '__main__':
    save('pla_stock', stock((216, 201, 164, 255), (249, 238, 204, 255), (160, 143, 115, 255)))
    save('pp_stock', stock((129, 178, 196, 255), (190, 222, 230, 255), (82, 127, 151, 255)))
    save('lactide', lactide()); save('propylene', propylene())
    save('tin_carboxylate_catalyst', catalyst((175, 180, 172, 255), (231, 231, 211, 255), None))
    save('supported_dehydrogenation_catalyst', catalyst((131, 137, 157, 255), (201, 202, 223, 255), (198, 172, 115, 255)))
    save('coordination_polymerisation_catalyst', catalyst((149, 99, 126, 255), (219, 170, 183, 255), (163, 173, 144, 255)))
