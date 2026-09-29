# Offline GDB adapter. Run with -nx -nh and auto-load disabled, then set
# JNATIVE_CORE_REQUEST to a UTF-8 JSON request file before sourcing this script.
import gdb
import hashlib
import json
import os
import struct
from pathlib import Path

def quoted(value):
    return '"' + raw_path(value).replace("\\", "\\\\").replace('"', '\\"') + '"'

def raw_path(value):
    text = str(value)
    if any(ord(c) < 32 for c in text):
        raise ValueError("Control characters in debugger path")
    return text

def elf_segments(path):
    with Path(path).open("rb") as file:
        header = file.read(64)
        if len(header) != 64 or header[:6] != b"\x7fELF\x02\x01":
            raise ValueError("Only little-endian ELF64 cores/images are supported")
        fields = struct.unpack("<16sHHIQQQIHHHHHH", header)
        if fields[2] != 62:
            raise ValueError("Only x64 ELF cores/images are supported")
        offset, size, count = fields[5], fields[9], fields[10]
        if size < 56 or count > 65535:
            raise ValueError("Invalid ELF segment table")
        segments = []
        for index in range(count):
            file.seek(offset + index * size)
            segments.append(struct.unpack("<IIQQQQQQ", file.read(56)))
        return fields[1], segments

def core_modules(core):
    _, segments = elf_segments(core)
    mappings = {}
    with Path(core).open("rb") as file:
        for segment in segments:
            if segment[0] != 4 or segment[5] > 32 * 1024 * 1024:
                continue
            file.seek(segment[2])
            data = file.read(segment[5])
            at = 0
            while at + 12 <= len(data):
                names, length, kind = struct.unpack_from("<III", data, at)
                at += 12 + ((names + 3) & ~3)
                note = data[at:at + length]
                at += (length + 3) & ~3
                if kind != 0x46494c45 or len(note) < 16:  # NT_FILE
                    continue
                count, page = struct.unpack_from("<QQ", note)
                if count > 100000 or 16 + count * 24 > len(note):
                    raise ValueError("Invalid core file mapping note")
                paths = note[16 + count * 24:].split(b"\0")
                for index in range(count):
                    start, end, offset = struct.unpack_from("<QQQ", note, 16 + index * 24)
                    if offset == 0 and index < len(paths):
                        name = Path(os.fsdecode(paths[index])).name
                        mappings.setdefault(name, []).append(start)
    return segments, mappings

def verified_library(image, core, core_segments, bases):
    # Verify GNU build-id notes from bytes actually present in the core. Never
    # let GDB's file-backed memory fallback authenticate an absent core segment.
    kind, segments = elf_segments(image)
    with Path(image).open("rb") as file, Path(core).open("rb") as dump:
        for segment in segments:
            if segment[0] != 4 or segment[5] > 1024 * 1024:
                continue
            file.seek(segment[2])
            data = file.read(segment[5])
            at = 0
            while at + 12 <= len(data):
                start = at
                names, length, note_kind = struct.unpack_from("<III", data, at)
                at += 12
                name = data[at:at + names]
                at += (names + 3) & ~3
                descriptor = data[at:at + length]
                descriptor_offset = at
                at += (length + 3) & ~3
                if note_kind != 3 or name.rstrip(b"\0") != b"GNU" or not descriptor:
                    continue
                for base in bases:
                    address = (0 if kind == 2 else base) + segment[3] + descriptor_offset
                    for memory in core_segments:
                        if memory[0] == 1 and memory[3] <= address and address + length <= memory[3] + memory[5]:
                            dump.seek(memory[2] + address - memory[3])
                            if dump.read(length) == descriptor:
                                return True
    return False

def decode():
    request = json.loads(Path(os.environ["JNATIVE_CORE_REQUEST"]).read_text(encoding="utf-8"))
    archive = Path(request["archive"]).resolve()
    manifest = json.loads((archive / "manifest.json").read_text(encoding="utf-8"))
    if manifest["buildId"] != request["buildId"]:
        raise ValueError("Core archive identity mismatch")
    for name, expected in manifest["files"].items():
        file = (archive / name).resolve()
        if not file.is_relative_to(archive) or not file.is_file():
            raise ValueError("Invalid archived artifact")
        with file.open("rb") as stream:
            actual = hashlib.file_digest(stream, "sha256").hexdigest()
        if actual != expected:
            raise ValueError("Core artifact checksum mismatch: " + name)
    executable = manifest["executable"]
    if Path(executable).name != executable or "linked/" + executable not in manifest["files"]:
        raise ValueError("Unverified core executable")
    gdb.execute("set pagination off")
    gdb.execute("set print frame-arguments none")
    gdb.execute("set auto-load off")
    gdb.execute("set debuginfod enabled off")
    gdb.execute("set sysroot " + raw_path(archive / "unavailable-system-root"))
    gdb.execute("set solib-search-path " + raw_path(archive / "linked"))
    gdb.execute("set debug-file-directory " + raw_path(archive / "symbols"))
    gdb.execute("file " + quoted(archive / "linked" / manifest["executable"]))
    gdb.execute("core-file " + raw_path(Path(request["core"]).resolve()))
    # This is a dirty writable runtime copy, captured in the core itself. The
    # corresponding archived ELF initializes it to zeros, so a missing memory
    # segment cannot accidentally verify against the image on disk.
    identity = gdb.parse_and_eval("jnative_live_build_identity").string()
    if identity != manifest["buildId"]:
        raise ValueError("Core and archived image have different build identities")
    core_segments, mappings = core_modules(request["core"])
    verified = set()
    for module in manifest["modules"]:
        name = module["name"]
        if name != manifest["executable"] and name in mappings:
            try:
                if verified_library(archive / "linked" / name, request["core"], core_segments, mappings[name]):
                    verified.add(name)
            except (ValueError, OSError, struct.error):
                pass
    threads = []
    for thread in gdb.selected_inferior().threads()[:128]:
        thread.switch()
        frame = gdb.newest_frame()
        frames = []
        while frame is not None and len(frames) < 64:
            pc = int(frame.pc())
            sal = frame.find_sal()
            location = None
            if sal.symtab and sal.line:
                location = sal.symtab.filename.replace("\\", "/") + ":" + str(sal.line)
            library = gdb.solib_name(pc)
            trusted = library is None or Path(library).name in verified
            if not trusted:
                location = None
            frames.append({"pc": hex(pc), "module": library or manifest["executable"],
                           "function": (frame.name() or "??") if trusted else "??",
                           "location": location, "inline": frame.type() == gdb.INLINE_FRAME,
                           "status": "decoded" if location else "symbols-unavailable"})
            try:
                frame = frame.older()
            except gdb.error:
                break
        threads.append({"thread": thread.ptid[1], "threadName": thread.name or "",
                        "decodedFrames": frames, "truncated": len(frames) == 64})
    result = {"schema": 1, "buildId": identity, "event": "core",
              "platform": "linux-x64", "captureSite": "fault",
              "status": "core; unavailable system libraries remain unresolved",
              "threads": threads, "sourceArchive": str(archive / "sources.zip")}
    Path(request["output"]).write_text(json.dumps(result, ensure_ascii=False) + "\n", encoding="utf-8")

try:
    decode()
except Exception as error:
    gdb.write("jNative core decode failed: " + str(error) + "\n", gdb.STDERR)
    gdb.execute("quit 2")
