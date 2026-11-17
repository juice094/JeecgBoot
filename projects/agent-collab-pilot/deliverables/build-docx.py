#!/usr/bin/env python3
"""从 md 主稿导出 docx 交付件。

做四件事：
1. 把 md 里的 ```mermaid 代码块渲染成 PNG（headless Edge 截图 + ImageMagick 裁白边），
   输出到 assets/，替换为图片引用 —— md 保持可编辑的 mermaid 源，docx 拿到图；
2. 用 pandoc 以历史 docx 为参考样式导出（保留宋体/黑体标题风格）；
3. 补表格样式（边框、表头加粗底纹、跨页表头重复、行不断裂）；
4. 加页眉（标题右对齐）与页脚（第 X 页 / 共 Y 页）。

依赖：pandoc、Microsoft Edge、ImageMagick(`magick`)、python-docx、mermaid.min.js（首次自动下载到缓存目录）。

用法：python build-docx.py [--force-figures]
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

from docx import Document
from docx.enum.style import WD_STYLE_TYPE
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Pt, RGBColor

HERE = Path(__file__).resolve().parent
ASSETS = HERE / 'assets'
ARCHIVE = HERE / 'archive'
CACHE = Path(os.environ.get('TEMP', tempfile.gettempdir())) / 'docx-build-cache'

EDGE_CANDIDATES = [
    r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',
    r'C:\Program Files\Microsoft\Edge\Application\msedge.exe',
]

MERMAID_VERSION = '10.9.1'
MERMAID_URL = f'https://cdn.jsdelivr.net/npm/mermaid@{MERMAID_VERSION}/dist/mermaid.min.js'

# (md 文件名, 参考样式 docx, 页眉文字, 文件名前缀, 图题)
DOCS = [
    (
        '团队协作效率工作台需求规格说明书-V3.2.md',
        ARCHIVE / '团队协作效率工作台需求规格说明书-V3.1-内部版.docx',
        '团队协作效率工作台 · 需求规格说明书 V3.2',
        'req',
        ['系统构成', '双模式切换', '一次加班申请的端到端数据流', '审批状态机（已实现）', '流程实例状态机（设计）'],
    ),
    (
        '团队协作效率工作台接口文档-V3.2.md',
        ARCHIVE / '团队协作效率工作台接口文档-V3.1-内部版.docx',
        '团队协作效率工作台 · 接口文档 V3.2',
        'api',
        ['审批状态机', '助手工具调用与策略判定'],
    ),
]

MERMAID_BLOCK = re.compile(r'```mermaid\n(.*?)```', re.S)


def find_edge():
    for path in EDGE_CANDIDATES:
        if Path(path).exists():
            return path
    raise SystemExit('未找到 Microsoft Edge，无法渲染 mermaid 图')


def ensure_mermaid_js():
    CACHE.mkdir(parents=True, exist_ok=True)
    target = CACHE / 'mermaid.min.js'
    if not target.exists() or target.stat().st_size < 100_000:
        print(f'下载 mermaid {MERMAID_VERSION} …')
        urllib.request.urlretrieve(MERMAID_URL, target)
    return target


def render_mermaid(edge, mermaid_js, source, out_png):
    html = CACHE / 'diagram.html'
    png = CACHE / 'diagram-raw.png'
    html.write_text(
        '<!doctype html><html><head><meta charset="utf-8">\n'
        f'<script src="file:///{mermaid_js.as_posix()}"></script>\n'
        '<style>body{margin:0;padding:18px;background:#fff;'
        'font-family:"Microsoft YaHei","Segoe UI",sans-serif}</style></head><body>\n'
        f'<pre class="mermaid">{source}</pre>\n'
        "<script>mermaid.initialize({startOnLoad:true,theme:'neutral',"
        "flowchart:{htmlLabels:true,useMaxWidth:false},"
        "sequence:{useMaxWidth:false},state:{useMaxWidth:false},"
        "themeVariables:{fontFamily:'Microsoft YaHei',fontSize:'16px',"
        "primaryColor:'#f2f5f7',primaryBorderColor:'#c3ccd3',primaryTextColor:'#1f2933',"
        "lineColor:'#6b7a86',noteBkgColor:'#f6f8fa',noteBorderColor:'#c3ccd3',"
        "noteTextColor:'#1f2933',actorBkg:'#f2f5f7',actorBorder:'#c3ccd3'}});</script>\n"
        '</body></html>',
        encoding='utf-8',
    )
    subprocess.run(
        [edge, '--headless=new', '--disable-gpu', '--hide-scrollbars',
         '--virtual-time-budget=6000', '--window-size=1600,1800',
         f'--screenshot={png}', f'file:///{html.as_posix()}'],
        check=True, capture_output=True,
    )
    subprocess.run(
        ['magick', str(png), '-trim', '+repage', '-bordercolor', 'white',
         '-border', '14', str(out_png)],
        check=True,
    )


def style_code_block(doc):
    """给代码块样式加浅灰底与等宽字体，避免 JSON 与正文混在一起。"""
    try:
        style = doc.styles['Source Code']
    except KeyError:
        return
    style.font.name = 'Consolas'
    style.font.size = Pt(8.5)
    rpr = style.element.get_or_add_rPr()
    fonts = rpr.find(qn('w:rFonts'))
    if fonts is None:
        fonts = OxmlElement('w:rFonts')
        rpr.append(fonts)
    fonts.set(qn('w:ascii'), 'Consolas')
    fonts.set(qn('w:hAnsi'), 'Consolas')
    fonts.set(qn('w:eastAsia'), '宋体')

    ppr = style.element.get_or_add_pPr()
    if ppr.find(qn('w:shd')) is None:
        shading = OxmlElement('w:shd')
        shading.set(qn('w:fill'), 'F5F7F9')
        ppr.append(shading)
    if ppr.find(qn('w:spacing')) is None:
        spacing = OxmlElement('w:spacing')
        spacing.set(qn('w:before'), '60')
        spacing.set(qn('w:after'), '60')
        spacing.set(qn('w:line'), '240')
        spacing.set(qn('w:lineRule'), 'auto')
        ppr.append(spacing)


def build_reference(src_docx, out_docx):
    """以历史交付件为样式底本，确保存在名为 Table 的带边框表格样式。"""
    doc = Document(str(src_docx))
    if 'Table' not in {s.name for s in doc.styles}:
        style = doc.styles.add_style('Table', WD_STYLE_TYPE.TABLE)
        try:
            style.base_style = doc.styles['Table Grid']
        except KeyError:
            pass
    try:
        title = doc.styles['Title']
        title.font.size = Pt(20)
    except KeyError:
        pass
    style_code_block(doc)
    doc.save(str(out_docx))


def add_field(paragraph, instr):
    run = paragraph.add_run()
    begin = OxmlElement('w:fldChar')
    begin.set(qn('w:fldCharType'), 'begin')
    instr_el = OxmlElement('w:instrText')
    instr_el.set(qn('xml:space'), 'preserve')
    instr_el.text = instr
    end = OxmlElement('w:fldChar')
    end.set(qn('w:fldCharType'), 'end')
    run._r.append(begin)
    run._r.append(instr_el)
    run._r.append(end)
    run.font.size = Pt(8)
    run.font.color.rgb = RGBColor(0x66, 0x66, 0x66)


def polish_tables(doc):
    for table in doc.tables:
        tbl_pr = table._tbl.tblPr
        if tbl_pr.find(qn('w:tblCellMar')) is None:
            margins = OxmlElement('w:tblCellMar')
            for side, width in (('top', 60), ('left', 90), ('bottom', 60), ('right', 90)):
                el = OxmlElement(f'w:{side}')
                el.set(qn('w:w'), str(width))
                el.set(qn('w:type'), 'dxa')
                margins.append(el)
            tbl_pr.append(margins)

        # 让 Word 按内容自动分配列宽，避免 pandoc 的等宽列把长文本挤成竖排
        table.autofit = True
        tbl_w = tbl_pr.find(qn('w:tblW'))
        if tbl_w is None:
            tbl_w = OxmlElement('w:tblW')
            tbl_pr.append(tbl_w)
        tbl_w.set(qn('w:w'), '0')
        tbl_w.set(qn('w:type'), 'auto')

        header_row = table.rows[0]
        tr_pr = header_row._tr.get_or_add_trPr()
        tr_pr.append(OxmlElement('w:tblHeader'))
        for cell in header_row.cells:
            shading = OxmlElement('w:shd')
            shading.set(qn('w:fill'), 'F2F5F7')
            cell._tc.get_or_add_tcPr().append(shading)
            for paragraph in cell.paragraphs:
                for run in paragraph.runs:
                    run.bold = True

        for row in table.rows:
            row._tr.get_or_add_trPr().append(OxmlElement('w:cantSplit'))
            for cell in row.cells:
                for paragraph in cell.paragraphs:
                    for run in paragraph.runs:
                        run.font.size = Pt(9)


def add_header_footer(doc, header_text):
    section = doc.sections[0]
    header = section.header.paragraphs[0]
    header.text = header_text
    header.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    for run in header.runs:
        run.font.size = Pt(8)
        run.font.color.rgb = RGBColor(0x66, 0x66, 0x66)

    footer = section.footer.paragraphs[0]
    footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
    footer.add_run('第 ')
    add_field(footer, 'PAGE')
    footer.add_run(' 页 / 共 ')
    add_field(footer, 'NUMPAGES')
    footer.add_run(' 页')
    for run in footer.runs:
        if run.font.size is None:
            run.font.size = Pt(8)
            run.font.color.rgb = RGBColor(0x66, 0x66, 0x66)


def process(md_path, reference_docx, header_text, slug, captions, force_figures):
    text = md_path.read_text(encoding='utf-8')
    blocks = MERMAID_BLOCK.findall(text)
    if blocks:
        ASSETS.mkdir(exist_ok=True)
        edge = find_edge()
        mermaid_js = ensure_mermaid_js()
        for index, source in enumerate(blocks, start=1):
            figure = ASSETS / f'{slug}-fig{index:02d}.png'
            if force_figures or not figure.exists():
                print(f'  渲染图 {figure.name} …')
                render_mermaid(edge, mermaid_js, source.strip(), figure)
            caption = f'图 {index}'
            if index <= len(captions):
                caption = f'图 {index} {captions[index - 1]}'
            text = text.replace(
                f'```mermaid\n{source}```',
                f'![{caption}](assets/{figure.name})',
                1,
            )
        print(f'  替换 {len(blocks)} 张 mermaid 图')

    staged = HERE / f'.build-{slug}.md'
    staged.write_text(text, encoding='utf-8')
    ref = CACHE / f'ref-{slug}.docx'
    CACHE.mkdir(parents=True, exist_ok=True)
    build_reference(reference_docx, ref)

    out_docx = md_path.with_suffix('.docx')
    subprocess.run(
        ['pandoc', str(staged), '-o', str(out_docx),
         f'--reference-doc={ref}', '--resource-path=' + str(HERE),
         '--no-highlight'],
        check=True,
    )
    staged.unlink()

    doc = Document(str(out_docx))
    polish_tables(doc)
    add_header_footer(doc, header_text)
    doc.save(str(out_docx))
    print(f'  导出 {out_docx.name}')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--force-figures', action='store_true', help='强制重渲染 mermaid 图')
    args = parser.parse_args()

    for exe in ('pandoc', 'magick'):
        if shutil.which(exe) is None:
            raise SystemExit(f'缺少依赖：{exe}')

    for md_name, reference, header, slug, captions in DOCS:
        md_path = HERE / md_name
        if not md_path.exists():
            raise SystemExit(f'找不到 {md_path}')
        print(f'处理 {md_name}')
        process(md_path, reference, header, slug, captions, args.force_figures)


if __name__ == '__main__':
    sys.exit(main())
