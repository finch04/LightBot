function normalizeCodeFences(text) {
  if (!text || !text.includes('```')) return text;
  const KNOWN_LANGS = ['javascript','typescript','python','java','csharp','ruby','go','rust','c','cpp','php','swift','kotlin','scala','bash','shell','sql','html','css','json','yaml','xml','markdown','latex','r','lua','perl','haskell','elixir','clojure','groovy','powershell','dockerfile','makefile','toml'];
  let s = text;
  s = s.replace(
    /(^|\n)(.*?)`{3,}([a-zA-Z0-9_+#.:-]{1,20})((?:\n)|[^\n`])/gm,
    (match, prefix, before, lang, after) => {
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
      if (codeAfter === '\n') return prefix + beforeFixed + '```' + actualLang + '\n';
      return prefix + beforeFixed + '```' + actualLang + '\n' + codeAfter;
    }
  );
  s = s.replace(/(^|\n)([^\n` ][^\n`]*)`{3,}[ \t]*$/gm, '$1$2\n```');
  return s;
}

// Build test string with actual triple backticks
const bt = '```';
const input = '## 方法三：获取详细时间组件' + bt + 'javascriptconst now = newDate();\n' +
  'const year= now.getFullYear();\n' +
  'console.log(`当前时间：${year}`);' + bt + '\n\n' +
  '##方法四：使用第三方库（如moment.js）' + bt + 'javascript\n' +
  '//如果使用moment.js库const moment =require(\'moment\');\n' +
  'console.log("当前时间：", moment().format("YYYY-MM-DD HH:mm:ss"));\n' +
  bt;

console.log('=== INPUT ===');
console.log(input);
console.log('\n=== OUTPUT ===');
console.log(normalizeCodeFences(input));
