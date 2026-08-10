# -*- coding: utf-8 -*-
"""국립수목원 국가표준식물목록(KPNI) 속 단위 조회 → 학명·국명·종분류 파서."""
import urllib.request, urllib.parse, re, html as htmlmod

URL = ('https://www.nature.go.kr/kpni/stndasrch/dtl/selectNtnStndaPlantList2.do'
       '?mn=KFS_29_06_01&orgId=kpni')
UA = {'User-Agent': 'Mozilla/5.0 (Macintosh) AppleWebKit/537.36 Chrome/120 Safari/537.36',
      'Referer': 'https://www.nature.go.kr/kpni/stndasrch/dtl/selectNtnStndaPlantList1.do',
      'Content-Type': 'application/x-www-form-urlencoded'}


def fetch(genus, page=1, unit=100, timeout=60):
    d = {'genusNm': genus, 'pageUnit': str(unit), 'pageIndex': str(page)}
    req = urllib.request.Request(
        URL, data=urllib.parse.urlencode(d, encoding='utf-8').encode(), headers=UA)
    return urllib.request.urlopen(req, timeout=timeout).read().decode('utf-8', 'replace')


ROW = re.compile(r'<tr[^>]*>(.*?)</tr>', re.S)
CELL = re.compile(r'<t[dh][^>]*>(.*?)</t[dh]>', re.S)


def strip(s):
    s = re.sub(r'<script.*?</script>', '', s, flags=re.S)
    s = re.sub(r'<[^>]+>', ' ', s)
    return re.sub(r'\s+', ' ', htmlmod.unescape(s)).strip()


def parse(page_html):
    """→ [(종분류, 구분, 학명, 국명, 수정일)]  구분='정명'만 유효하다."""
    out = []
    for r in ROW.findall(page_html):
        cells = [strip(c) for c in CELL.findall(r)]
        if len(cells) < 4:
            continue
        if cells[0] not in ('자생식물', '외래식물', '재배식물'):
            continue
        out.append(tuple(cells[:5]) if len(cells) >= 5 else tuple(cells[:4]) + ('',))
    return out


def total(page_html):
    t = re.sub(r'<[^>]+>', '|', re.sub(r'<script.*?</script>', '', page_html, flags=re.S))
    m = re.search(r'전체\s*\|?\s*([\d,]+)\s*건', re.sub(r'\|+', '|', t))
    return int(m.group(1).replace(',', '')) if m else None


if __name__ == '__main__':
    h = fetch('Convallaria')
    print('전체', total(h))
    for r in parse(h):
        print(r)
