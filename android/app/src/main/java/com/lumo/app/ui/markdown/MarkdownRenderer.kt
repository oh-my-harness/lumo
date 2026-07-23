package com.lumo.app.ui.markdown

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Full Markdown renderer using WebView + marked.js + KaTeX + Mermaid.js + highlight.js.
 *
 * Supports everything from the P0 spec:
 * - Basic Markdown: headings, lists, bold/italic, blockquotes, horizontal rules
 * - Code blocks: syntax highlighting + copy button + horizontal scroll
 * - Tables
 * - LaTeX: $...$ inline / $$...$$ block, KaTeX rendering
 * - Mermaid diagrams
 * - Images: URL + local
 * - Links: clickable
 * - Inline code
 */
@Composable
fun MarkdownRenderer(
    content: String,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false,
) {
    val html = remember(content, isDarkTheme) { buildHtml(content, isDarkTheme) }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                loadDataWithBaseURL("about:blank", html, "text/html", "utf-8", null)
            }
        },
        update = { webview ->
            webview.loadDataWithBaseURL("about:blank", html, "text/html", "utf-8", null)
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun buildHtml(markdown: String, isDark: Boolean): String {
    val escaped = markdown
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("$", "\\$")
        .replace("'", "\\'")
        .replace("\n", "\\n")

    val bgColor = if (isDark) "#1c1b1f" else "#ffffff"
    val textColor = if (isDark) "#e6e1e5" else "#1c1b1f"
    val codeBgColor = if (isDark) "#2d2d2d" else "#f5f5f5"
    val linkColor = if (isDark) "#d0bcff" else "#6750a4"

    return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/highlight.js@11.9.0/styles/github${if (isDark) "-dark" else ""}.min.css">
<script src="https://cdn.jsdelivr.net/npm/marked@12.0.2/marked.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/highlight.js@11.9.0/lib/highlight.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.1/dist/mermaid.min.js"></script>
<style>
  body {
    font-family: -apple-system, 'Segoe UI', Roboto, 'Noto Sans SC', sans-serif;
    font-size: 13px;
    line-height: 1.6;
    color: $textColor;
    background: $bgColor;
    margin: 0;
    padding: 4px;
    -webkit-text-size-adjust: 100%;
    word-wrap: break-word;
    overflow-x: hidden;
  }
  h1, h2, h3, h4, h5, h6 { margin-top: 1em; margin-bottom: 0.5em; font-weight: 600; }
  h1 { font-size: 1.5em; }
  h2 { font-size: 1.3em; }
  h3 { font-size: 1.15em; }
  p { margin: 0.5em 0; }
  ul, ol { margin: 0.5em 0; padding-left: 1.5em; }
  li { margin: 0.25em 0; }
  code {
    font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
    font-size: 0.88em;
    background: $codeBgColor;
    padding: 0.15em 0.35em;
    border-radius: 4px;
  }
  pre {
    background: $codeBgColor;
    padding: 12px;
    border-radius: 8px;
    overflow-x: auto;
    margin: 0.5em 0;
    position: relative;
  }
  pre code {
    background: none;
    padding: 0;
    font-size: 0.85em;
    line-height: 1.5;
  }
  blockquote {
    border-left: 3px solid $linkColor;
    margin: 0.5em 0;
    padding: 0.25em 0 0.25em 1em;
    opacity: 0.8;
  }
  table {
    border-collapse: collapse;
    width: 100%;
    margin: 0.5em 0;
    display: block;
    overflow-x: auto;
  }
  th, td {
    border: 1px solid ${if (isDark) "#444" else "#ddd"};
    padding: 6px 10px;
    text-align: left;
  }
  th { background: $codeBgColor; font-weight: 600; }
  a { color: $linkColor; text-decoration: none; }
  a:hover { text-decoration: underline; }
  img { max-width: 100%; height: auto; border-radius: 8px; }
  hr { border: none; border-top: 1px solid ${if (isDark) "#444" else "#ddd"}; margin: 1em 0; }
  .copy-btn {
    position: absolute;
    top: 4px;
    right: 4px;
    background: ${if (isDark) "#555" else "#e0e0e0"};
    color: $textColor;
    border: none;
    border-radius: 4px;
    padding: 2px 8px;
    font-size: 11px;
    cursor: pointer;
    opacity: 0.7;
  }
  .copy-btn:hover { opacity: 1; }
  .mermaid {
    display: flex;
    justify-content: center;
    margin: 0.5em 0;
  }
</style>
</head>
<body>
<div id="content"></div>
<script>
  const raw = '$escaped';

  // Configure marked
  marked.setOptions({
    breaks: true,
    gfm: true,
  });

  // Custom renderer: wrap code blocks with copy button
  const renderer = new marked.Renderer();
  const origCode = renderer.code;
  renderer.code = function(code, lang) {
    const highlighted = lang && hljs.getLanguage(lang)
      ? hljs.highlight(code, { language: lang }).value
      : hljs.highlightAuto(code).value;
    const id = 'code_' + Math.random().toString(36).slice(2, 8);
    return '<pre><button class="copy-btn" onclick="copyCode(\'' + id + '\')">复制</button><code id="' + id + '" class="hljs language-' + (lang || '') + '">' + highlighted + '</code></pre>';
  };
  marked.use({ renderer });

  // Render markdown
  document.getElementById('content').innerHTML = marked.parse(raw);

  // Render LaTeX
  renderMathInElement(document.getElementById('content'), {
    delimiters: [
      { left: '$$', right: '$$', display: true },
      { left: '$', right: '$', display: false },
    ],
    throwOnError: false,
  });

  // Initialize Mermaid
  mermaid.initialize({
    startOnLoad: false,
    theme: '${if (isDark) "dark" else "default"}',
    securityLevel: 'loose',
  });

  // Find and render mermaid code blocks
  document.querySelectorAll('code.language-mermaid').forEach((el, i) => {
    const graph = el.textContent;
    const container = el.parentElement;
    container.removeAttribute('class');
    container.classList.add('mermaid');
    container.setAttribute('data-mermaid', 'true');
    container.innerHTML = graph;
    mermaid.run({ nodes: container }).catch(() => {});
  });

  // Copy function
  function copyCode(id) {
    const code = document.getElementById(id);
    if (code) {
      const text = code.textContent;
      if (navigator.clipboard) {
        navigator.clipboard.writeText(text);
      } else {
        const ta = document.createElement('textarea');
        ta.value = text;
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        document.body.removeChild(ta);
      }
      const btn = document.querySelector('button[onclick="copyCode(\'' + id + '\')"]');
      if (btn) {
        const orig = btn.textContent;
        btn.textContent = '已复制';
        setTimeout(() => { btn.textContent = orig; }, 1500);
      }
    }
  }

  // Auto-resize
  function resize() {
    const h = document.body.scrollHeight;
    if (window.AndroidBridge) {
      window.AndroidBridge.setHeight(h);
    } else {
      // Fallback: use postMessage
      window.postMessage({ type: 'height', height: h }, '*');
    }
  }

  // Observe DOM changes for dynamic content (mermaid, katex)
  const observer = new MutationObserver(() => resize());
  observer.observe(document.getElementById('content'), {
    childList: true, subtree: true, attributes: true,
  });

  window.addEventListener('load', resize);
  setTimeout(resize, 100);
  setTimeout(resize, 500);
  setTimeout(resize, 2000);
</script>
</body>
</html>
    """.trimIndent()
}
