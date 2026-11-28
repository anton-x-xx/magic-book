from argparse import ArgumentParser
from hashlib import sha256
from pathlib import Path
from zipfile import ZipFile
import copy
import re
import struct

SOURCE = Path(__file__).resolve().parent
BASE_SHA256 = 'ea4b804a1419dd116241f1b8fa60ed75bee94ca9e27f5d2070c46d31728c37e7'


def apply_patch(original, patch):
    original_lines = original.splitlines(keepends=True)
    patch_lines = patch.splitlines(keepends=True)
    output = []
    cursor = 0
    index = 2
    while index < len(patch_lines):
        header = re.fullmatch(r'@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@\n?', patch_lines[index])
        if not header:
            raise ValueError('Invalid patch header')
        start = int(header[1])
        count = int(header[2]) if header[2] is not None else 1
        position = start if count == 0 else start - 1
        if position < cursor:
            raise ValueError('Overlapping patch hunks')
        output.extend(original_lines[cursor:position])
        cursor = position
        removed = added = 0
        index += 1
        while index < len(patch_lines) and not patch_lines[index].startswith('@@ '):
            line = patch_lines[index]
            if line[0] in ' -':
                if cursor >= len(original_lines) or original_lines[cursor] != line[1:]:
                    raise ValueError('Patch does not match the baseline')
                cursor += 1
                removed += 1
            if line[0] in ' +':
                output.append(line[1:])
                added += 1
            index += 1
        expected_added = int(header[4]) if header[4] is not None else 1
        if removed != count or added != expected_added:
            raise ValueError('Invalid patch line count')
    output.extend(original_lines[cursor:])
    return ''.join(output)


def update_manifest(raw):
    data = bytearray(raw)
    pool = 8
    kind, header, size, count, styles, flags, offset, style_offset = struct.unpack_from('<HH6I', data, pool)
    if kind != 1 or flags or styles:
        raise ValueError('Unsupported manifest string pool')
    strings = []
    for index in range(count):
        position = pool + offset + struct.unpack_from('<I', data, pool + header + index * 4)[0]
        length = struct.unpack_from('<H', data, position)[0]
        strings.append(data[position + 2:position + 2 + 2 * length].decode('utf-16le'))
    version_index = strings.index('3.17')
    position = pool + offset + struct.unpack_from('<I', data, pool + header + version_index * 4)[0]
    data[position + 2:position + 10] = '3.28'.encode('utf-16le')
    position = pool + size
    while position < len(data):
        kind, header, size = struct.unpack_from('<HHI', data, position)
        if kind == 0x102 and strings[struct.unpack_from('<I', data, position + 20)[0]] == 'manifest':
            for index in range(struct.unpack_from('<H', data, position + 28)[0]):
                if strings[struct.unpack_from('<I', data, position + 40 + index * 20)[0]] == 'versionCode':
                    struct.pack_into('<I', data, position + 52 + index * 20, 58)
        position += size
    return data


def main():
    parser = ArgumentParser(description='Restore and package Magic Book Android 3.28 sources')
    parser.add_argument('--base', type=Path, default=SOURCE.parents[1] / 'magic-wordbook-3.17.apk')
    parser.add_argument('--out', type=Path, default=SOURCE / 'build')
    parser.add_argument('--html', type=Path, help='Package an edited HTML file instead of restoring the patch')
    args = parser.parse_args()
    if sha256(args.base.read_bytes()).hexdigest() != BASE_SHA256:
        raise ValueError('The baseline APK must be the repository version of Android 3.17')
    args.out.mkdir(parents=True, exist_ok=True)
    with ZipFile(args.base) as archive:
        html = args.html.read_text(encoding='utf-8') if args.html else apply_patch(
            archive.read('assets/index.html').decode('utf-8'),
            (SOURCE / 'index-from-3.17.patch').read_text(encoding='utf-8'))
        (args.out / 'index.html').write_text(html, encoding='utf-8')
        with ZipFile(args.out / 'magic-wordbook-3.28-unsigned.apk', 'w') as target:
            for entry in archive.infolist():
                if entry.filename.startswith('META-INF/'):
                    continue
                data = archive.read(entry)
                if entry.filename == 'assets/index.html':
                    data = html.encode('utf-8')
                if entry.filename == 'AndroidManifest.xml':
                    data = update_manifest(data)
                info = copy.copy(entry)
                if info.compress_type == 0:
                    info.extra = b''
                    padding = (-(target.fp.tell() + 30 + len(info.filename.encode()))) % 4
                    if padding:
                        info.extra = struct.pack('<HH', 0xd935, padding) + bytes(padding)
                target.writestr(info, data)
    print(args.out / 'index.html')
    print(args.out / 'magic-wordbook-3.28-unsigned.apk')


if __name__ == '__main__':
    main()
