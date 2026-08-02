/**
 * 35: HTML 安全净化工具 — 白名单标签 + 属性过滤
 *
 * 使用方式:
 *   import { sanitizeHtml } from '@/utils/sanitize'
 *   <div v-html="sanitizeHtml(show.description)"></div>
 *
 * 或注册为全局过滤器/指令:
 *   app.directive('safe-html', (el, binding) => {
 *     el.innerHTML = sanitizeHtml(binding.value)
 *   })
 */

/** 允许的安全标签 */
const ALLOWED_TAGS = new Set([
    'p', 'br', 'b', 'i', 'u', 'em', 'strong', 'span',
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'ul', 'ol', 'li', 'dl', 'dt', 'dd',
    'a', 'img', 'table', 'thead', 'tbody', 'tr', 'th', 'td',
    'div', 'pre', 'code', 'blockquote', 'hr',
    'sub', 'sup', 'small', 's', 'del',
])

/** 允许的标签上的属性 */
const ALLOWED_ATTRS = new Set([
    'href', 'title', 'target',           // <a>
    'src', 'alt', 'width', 'height',     // <img>
    'class', 'style',                     // common
    'colspan', 'rowspan',                // <td>
])

/** 禁止的协议（href/src 中的危险协议） */
const FORBIDDEN_PROTOCOLS = /^(javascript|data|vbscript):/i

/**
 * 净化 HTML 字符串，移除危险标签/属性
 */
export function sanitizeHtml(html) {
    if (!html || typeof html !== 'string') return ''

    // 临时 DOM 解析
    const doc = new DOMParser().parseFromString(html, 'text/html')
    if (!doc || !doc.body) return ''

    // 遍历 body 的子节点（不能直接 sanitize body 自身，否则 body 会被移除）
    const children = [...doc.body.childNodes]
    for (const child of children) {
        sanitizeNode(child)
    }

    return doc.body.innerHTML
}

/**
 * 递归净化 DOM 节点
 */
function sanitizeNode(node) {
    // 文本节点: 保留
    if (node.nodeType === Node.TEXT_NODE) return

    // 注释节点: 移除
    if (node.nodeType === Node.COMMENT_NODE) {
        node.remove()
        return
    }

    if (node.nodeType === Node.ELEMENT_NODE) {
        const tagName = node.tagName.toLowerCase()

        // 不允许的标签: 替换为其文本内容
        if (!ALLOWED_TAGS.has(tagName)) {
            const replacement = document.createTextNode(node.textContent || '')
            node.parentNode.replaceChild(replacement, node)
            return
        }

        // 清理属性
        const attrs = [...node.attributes]
        for (const attr of attrs) {
            const attrName = attr.name.toLowerCase()

            // 去除所有事件处理器
            if (attrName.startsWith('on')) {
                node.removeAttribute(attr.name)
                continue
            }

            // 检查是否在白名单中
            if (!ALLOWED_ATTRS.has(attrName)) {
                node.removeAttribute(attr.name)
                continue
            }

            // 检查 href/src 中的危险协议
            if ((attrName === 'href' || attrName === 'src') && FORBIDDEN_PROTOCOLS.test(attr.value)) {
                node.removeAttribute(attr.name)
            }

            // 限制 style 为安全属性
            if (attrName === 'style') {
                const safeStyle = sanitizeStyle(attr.value)
                if (safeStyle) {
                    node.setAttribute('style', safeStyle)
                } else {
                    node.removeAttribute('style')
                }
            }
        }

        // 递归处理子节点
        const children = [...node.childNodes]
        for (const child of children) {
            sanitizeNode(child)
        }
    }
}

/**
 * 净化 style 属性值
 */
function sanitizeStyle(style) {
    if (!style) return ''
    // 去除 expression 和 javascript: 注入
    return style
        .replace(/expression\s*\(/gi, '')
        .replace(/javascript\s*:/gi, '')
        .replace(/behavior\s*:/gi, '')
}

export default { sanitizeHtml }
