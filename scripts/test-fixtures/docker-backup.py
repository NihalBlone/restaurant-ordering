"""Strict Docker stub for backup shell tests; never contacts a Docker daemon."""
import io
import json
import os
from pathlib import Path
import sys
import tarfile

arguments = sys.argv[1:]
assert arguments[:2] == ["compose", "--env-file"] and arguments[3] == "-f"
command = arguments[5:]
with Path(os.environ["BACKUP_TEST_LOG"]).open("a") as log:
    log.write(json.dumps(command) + "\n")
failure = os.environ.get("BACKUP_TEST_FAILURE", "")

if command == ["ps", "--status", "running", "-q", "app"]:
    if os.environ.get("BACKUP_TEST_RUNNING") == "true":
        print("test-app-container")
elif command == ["stop", "app"]:
    pass
elif command == ["exec", "-T", "database", "pg_dump", "-U", "postgres", "-d",
                 "restaurant_ordering", "-Fc", "--no-owner", "--no-acl"]:
    if failure == "dump":
        sys.exit(23)
    sys.stdout.buffer.write(b"test-database-dump")
elif command == ["run", "-T", "--rm", "--no-deps", "--entrypoint", "tar", "app",
                 "-C", "/var/data", "-czf", "-", "menu-images"]:
    if failure == "archive":
        sys.exit(24)
    if failure == "invalid-archive":
        sys.stdout.buffer.write(b"invalid tar archive")
    else:
        with tarfile.open(fileobj=sys.stdout.buffer, mode="w|gz") as archive:
            marker = b"test-photo"
            entry = tarfile.TarInfo("menu-images/test-photo")
            entry.size = len(marker)
            archive.addfile(entry, io.BytesIO(marker))
elif command == ["exec", "-T", "database", "pg_restore", "--list"]:
    assert sys.stdin.buffer.read() == b"test-database-dump"
    if failure == "validate-dump":
        sys.exit(25)
elif command == ["up", "-d", "--no-deps", "--no-recreate", "--no-build", "--pull",
                 "never", "--wait", "--wait-timeout", "300", "app"]:
    if failure == "restart":
        sys.exit(26)
else:
    print("Unsupported backup Compose command: " + " ".join(command), file=sys.stderr)
    sys.exit(64)
