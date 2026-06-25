function normalizeCodeFences(text) {
  if (!text || !text.includes('```')) return text;
  let s = text;
  // Fix 1: opening fence with language tag on same line as content
  s = s.replace(
    /(^|\n)(.*?)`{3,}([a-zA-Z0-9_+#.:-]{1,20})([^\n`])/gm,
    (match, prefix, before, lang, after) => {
      const needsSplit = before && /\S$/.test(before);
      const beforeFixed = needsSplit ? before + '\n' : before;
      return prefix + beforeFixed + '```' + lang + '\n' + after;
    }
  );
  // Fix 2: closing ``` on same line as content
  s = s.replace(
    /(^|\n)([^\n` ][^\n`]*)```[ \t]*$/gm,
    '$1$2\n```'
  );
  return s;
}

const input = `console.log(\`当前时间：\${year}年\${month}月\${day}日 \${hours}:\${minutes}:\${seconds}\`);\`\`\`

##方法四：使用第三方库（如moment.js）\`\`\`javascript
//如果使用moment.js库`;

console.log('=== INPUT ===');
console.log(input);
console.log('\n=== OUTPUT ===');
console.log(normalizeCodeFences(input));
