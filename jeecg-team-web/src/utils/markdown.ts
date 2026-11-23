/**
 * 极简 Markdown 渲染器（零依赖）
 *
 * 约束：
 * 1. 只覆盖 Issue/Task 正文实际使用的语法子集
 * 2. 输入先转义 HTML，再拼接我们自己的标签，用户内容无法注入
 * 3. 链接只放行 http 与 https
 * 4. 全部用字符串方法实现，便于维护与审计
 */

const FENCE = String.fromCharCode(96, 96, 96);
const TICK = String.fromCharCode(96);

function escapeHtml(input: string): string {
  return input
    .split('&').join('&amp;')
    .split('<').join('&lt;')
    .split('>').join('&gt;')
    .split('"').join('&quot;')
    .split("'").join('&#39;');
}

/** 行内：行内代码 -> 加粗 -> 链接 */
function renderInline(raw: string): string {
  let text = escapeHtml(raw);
  const codes: string[] = [];

  const parts = text.split(TICK);
  if (parts.length > 1) {
    const merged: string[] = [];
    for (let i = 0; i < parts.length; i += 1) {
      if (i % 2 === 1) {
        const index = codes.length;
        codes.push("<code class='md-inline-code'>" + parts[i] + '</code>');
        merged.push('@@IC' + index + '@@');
      } else {
        merged.push(parts[i]);
      }
    }
    text = merged.join('');
  }

  const boldParts = text.split('**');
  if (boldParts.length > 1) {
    const merged: string[] = [];
    for (let i = 0; i < boldParts.length; i += 1) {
      if (i % 2 === 1) {
        merged.push('<strong>' + boldParts[i] + '</strong>');
      } else {
        merged.push(boldParts[i]);
      }
    }
    text = merged.join('');
  }

  const linkParts = text.split('](');
  if (linkParts.length > 1) {
    const merged: string[] = [];
    for (let i = 0; i < linkParts.length; i += 1) {
      const segment = linkParts[i];
      if (i > 0) {
        const close = segment.indexOf(')');
        if (close > 0) {
          const url = segment.slice(0, close);
          const rest = segment.slice(close + 1);
          const prev = merged.pop() ?? '';
          const open = prev.lastIndexOf('[');
          const safe = url.startsWith('http://') || url.startsWith('https://');
          if (open >= 0 && safe) {
            const label = prev.slice(open + 1);
            merged.push(prev.slice(0, open));
            merged.push("<a href='" + url + "' target='_blank' rel='noopener noreferrer'>" + label + '</a>');
          } else {
            merged.push(prev);
            merged.push('](');
          }
          merged.push(rest);
          continue;
        }
      }
      merged.push(segment);
    }
    text = merged.join('');
  }

  for (let i = 0; i < codes.length; i += 1) {
    text = text.split('@@IC' + i + '@@').join(codes[i]);
  }

  return text;
}

function headingLevel(line: string): number {
  let level = 0;
  while (level < 6 && line.charAt(level) === '#') {
    level += 1;
  }
  if (level === 0) return 0;
  if (line.charAt(level) !== ' ') return 0;
  return level;
}

interface ListItem {
  kind: 'task' | 'ul' | 'ol';
  checked: boolean;
  content: string;
}

function listPrefix(line: string): ListItem | null {
  const trimmed = line.trimStart();
  const head = trimmed.charAt(0);

  if (head === '-' || head === '*') {
    if (trimmed.charAt(1) !== ' ') return null;
    const rest = trimmed.slice(2);
    if (rest.charAt(0) === '[' && rest.charAt(2) === ']') {
      const mark = rest.charAt(1).toLowerCase();
      if (mark === ' ' || mark === 'x') {
        return { kind: 'task', checked: mark === 'x', content: rest.slice(3).trimStart() };
      }
    }
    return { kind: 'ul', checked: false, content: rest };
  }

  let index = 0;
  while (index < trimmed.length) {
    const ch = trimmed.charAt(index);
    if (ch < '0' || ch > '9') break;
    index += 1;
  }
  if (index > 0 && trimmed.charAt(index) === '.' && trimmed.charAt(index + 1) === ' ') {
    return { kind: 'ol', checked: false, content: trimmed.slice(index + 2) };
  }

  return null;
}

export function renderMarkdown(source: string): string {
  if (!source || !source.trim()) return '';

  const lines = source.split('\n');
  const out: string[] = [];
  let listType: 'ul' | 'ol' | null = null;
  let inParagraph = false;
  let inFence = false;

  const closeList = () => {
    if (listType) {
      out.push('</' + listType + '>');
      listType = null;
    }
  };

  const closeParagraph = () => {
    if (inParagraph) {
      out.push('</p>');
      inParagraph = false;
    }
  };

  for (const rawLine of lines) {
    const line = rawLine.trimEnd();

    if (line.trimStart().startsWith(FENCE)) {
      closeParagraph();
      closeList();
      if (inFence) {
        out.push('</code></pre>');
        inFence = false;
      } else {
        out.push("<pre class='md-code'><code>");
        inFence = true;
      }
      continue;
    }

    if (inFence) {
      out.push(escapeHtml(line) + '\n');
      continue;
    }

    if (!line.trim()) {
      closeParagraph();
      closeList();
      continue;
    }

    const level = headingLevel(line.trimStart());
    if (level > 0) {
      closeParagraph();
      closeList();
      const content = line.trimStart().slice(level + 1);
      out.push('<h' + level + " class='md-h" + level + "'>" + renderInline(content) + '</h' + level + '>');
      continue;
    }

    const item = listPrefix(line);
    if (item) {
      closeParagraph();
      const wantType: 'ul' | 'ol' = item.kind === 'ol' ? 'ol' : 'ul';
      if (listType !== wantType) {
        closeList();
        if (wantType === 'ol') {
          out.push("<ol class='md-ol'>");
        } else {
          out.push("<ul class='md-ul" + (item.kind === 'task' ? ' md-tasklist' : '') + "'>");
        }
        listType = wantType;
      }
      if (item.kind === 'task') {
        const checked = item.checked ? ' checked' : '';
        out.push("<li class='md-task'><input type='checkbox' disabled" + checked + '>' + renderInline(item.content) + '</li>');
      } else {
        out.push('<li>' + renderInline(item.content) + '</li>');
      }
      continue;
    }

    closeList();
    if (!inParagraph) {
      out.push('<p>');
      inParagraph = true;
    } else {
      out.push('<br>');
    }
    out.push(renderInline(line));
  }

  if (inFence) out.push('</code></pre>');
  closeParagraph();
  closeList();

  return out.join('');
}
