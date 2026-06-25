const bt = '`'.repeat(3);
const KNOWN_LANGS = ['javascript','typescript','python','java','csharp','ruby','go','rust','c','cpp','php','swift','kotlin','scala','bash','shell','sql','html','css','json','yaml','xml','markdown','latex','r','lua','perl','haskell','elixir','clojure','groovy','powershell','dockerfile','makefile','toml'];

function normalizeCodeFences(text) {
  if (!text || !text.includes(bt)) return text;
  let s = text;

  // Fix 1: opening fence with language tag stuck to content
  const re1 = new RegExp('(^|\\n)(.*?)' + bt.replace(/([`])/g, '\\$1') + '+([a-zA-Z0-9_+#.:-]{1,20})((?:\\n)|[^\\n`])', 'gm');
  s = s.replace(re1, (match, prefix, before, lang, after) => {
    let actualLang = lang, codeAfter = after;
    if (!KNOWN_LANGS.includes(lang.toLowerCase())) {
      for (let i = lang.length - 1; i >= 2; i--) {
        if (KNOWN_LANGS.includes(lang.substring(0, i).toLowerCase())) {
          actualLang = lang.substring(0, i);
          codeAfter = lang.substring(i) + after;
          break;
        }
      }
    }
    const needsSplit = before && /\S$/.test(before);
    const beforeFixed = needsSplit ? before + '\n' : before;
    if (codeAfter === '\n') return prefix + beforeFixed + bt + actualLang + '\n';
    return prefix + beforeFixed + bt + actualLang + '\n' + codeAfter;
  });

  // Fix 2: closing ``` at end of line stuck to content
  const re2 = new RegExp('(^|\\n)(.*\\S)' + bt.replace(/([`])/g, '\\$1') + '+[ \\t]*$', 'gm');
  s = s.replace(re2, '$1$2\n' + bt);

  return s;
}

const input = '## 方法二：获取ISO格式时间\n' +
  bt + 'javascriptconst now =new Date();\n' +
  'console.log("ISO时间：", now.toISOString());\n' +
  '// 输出示例："ISO时间： 2024-01-15T06:30:25.000Z"' + bt + '\n\n' +
  '## 方法三：获取详细时间组件' + bt + 'javascriptconst now = newDate();\n' +
  'const year= now.getFullYear();\n' +
  'console.log(`当前时间：${year}`);' + bt + '\n\n' +
  '##方法四：使用第三方库（如moment.js）' + bt + 'javascript\n' +
  '//code\n' + bt;

console.log('=== INPUT ===');
console.log(input);
console.log('\n=== OUTPUT ===');
console.log(normalizeCodeFences(input));
