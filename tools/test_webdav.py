"""本地最小 WebDAV 服务，仅用于自动同步的端到端测试。

- 支持 PROPFIND / MKCOL / PUT / GET / MOVE / DELETE
- 模拟「不允许 MOVE 覆盖」的服务端：目标已存在时返回 409，用于验证客户端 fallback
- 根目录：工作区 .tmp_webdav

运行： python tools/test_webdav.py [port]
"""

import hashlib
import os
import sys
import urllib.parse
import xml.sax.saxutils as saxutils
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), ".tmp_webdav")
PREFIX = "/dav"


def fs_path(url_path: str) -> str:
    rel = url_path[len(PREFIX):] if url_path.startswith(PREFIX) else url_path
    rel = rel.strip("/")
    return os.path.join(ROOT, *[p for p in rel.split("/") if p])


def etag(path: str) -> str:
    st = os.stat(path)
    digest = hashlib.md5(f"{path}:{st.st_mtime_ns}:{st.st_size}".encode()).hexdigest()
    return f'"{digest}"'


def href(fs: str) -> str:
    rel = os.path.relpath(fs, ROOT).replace(os.sep, "/")
    return PREFIX + ("/" + rel if rel != "." else "/")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def _url_path(self) -> str:
        return urllib.parse.unquote(urllib.parse.urlparse(self.path).path)

    def _dir_xml(self, fs: str) -> str:
        items = [fs]
        if os.path.isdir(fs):
            items += [os.path.join(fs, name) for name in sorted(os.listdir(fs))]
        blocks = []
        for item in items:
            is_dir = os.path.isdir(item)
            props = ""
            if not is_dir:
                props = (
                    f"<D:getcontentlength>{os.path.getsize(item)}</D:getcontentlength>"
                    f"<D:getetag>{saxutils.escape(etag(item))}</D:getetag>"
                )
            else:
                props = "<D:resourcetype><D:collection/></D:resourcetype>"
            blocks.append(
                "<D:response>"
                f"<D:href>{saxutils.escape(href(item))}</D:href>"
                "<D:propstat><D:prop>"
                f"{props}"
                "</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>"
                "</D:response>"
            )
        return (
            '<?xml version="1.0" encoding="utf-8"?>'
            '<D:multistatus xmlns:D="DAV:">' + "".join(blocks) + "</D:multistatus>"
        )

    def do_PROPFIND(self):
        fs = fs_path(self._url_path())
        if not os.path.exists(fs):
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        body = self._dir_xml(fs).encode()
        self.send_response(207)
        self.send_header("Content-Type", "application/xml; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_MKCOL(self):
        os.makedirs(fs_path(self._url_path()), exist_ok=True)
        self.send_response(201)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_PUT(self):
        fs = fs_path(self._url_path())
        os.makedirs(os.path.dirname(fs), exist_ok=True)
        length = int(self.headers.get("Content-Length", "0"))
        tmp = fs + ".part"
        with open(tmp, "wb") as f:
            remaining = length
            while remaining > 0:
                chunk = self.rfile.read(min(65536, remaining))
                if not chunk:
                    break
                f.write(chunk)
                remaining -= len(chunk)
        os.replace(tmp, fs)
        self.send_response(201)
        self.send_header("ETag", etag(fs))
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_GET(self):
        fs = fs_path(self._url_path())
        if not os.path.isfile(fs):
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        with open(fs, "rb") as f:
            body = f.read()
        self.send_response(200)
        self.send_header("Content-Type", "application/zip")
        self.send_header("ETag", etag(fs))
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_MOVE(self):
        src = fs_path(self._url_path())
        dest_url = urllib.parse.urlparse(self.headers.get("Destination", ""))
        dest = fs_path(urllib.parse.unquote(dest_url.path))
        overwrite = self.headers.get("Overwrite", "T").upper() == "T"
        if os.path.exists(dest) and not overwrite:
            self.send_response(412)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        if os.path.exists(dest):
            # 模拟不允许覆盖的服务端，客户端应回退为「先删除再 MOVE」
            self.send_response(409)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        os.replace(src, dest)
        self.send_response(201)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_DELETE(self):
        fs = fs_path(self._url_path())
        if os.path.isdir(fs):
            for root, dirs, files in os.walk(fs, topdown=False):
                for name in files:
                    os.remove(os.path.join(root, name))
                for name in dirs:
                    os.rmdir(os.path.join(root, name))
            os.rmdir(fs)
        elif os.path.exists(fs):
            os.remove(fs)
        self.send_response(204)
        self.send_header("Content-Length", "0")
        self.end_headers()


def main():
    os.makedirs(ROOT, exist_ok=True)
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print(f"WebDAV test server: http://127.0.0.1:{port}{PREFIX}  root={ROOT}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
