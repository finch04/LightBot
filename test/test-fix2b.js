const bt = String.fromCharCode(96, 96, 96); // three backticks

// Test fix 2 regex alone
const line1 = 'console.log(`当前时间：${year}`);' + bt;
console.log('Line1:', JSON.stringify(line1));

const re = /(^|\n)([^\n` ][^\n`]*)`{3,}[ \t]*$/gm;
const m = re.exec(line1);
console.log('Match:', m ? JSON.stringify(m[0]) : 'null');

// Test with simpler string
const line2 = 'abc;' + bt;
console.log('Line2:', JSON.stringify(line2));
re.lastIndex = 0;
const m2 = re.exec(line2);
console.log('Match2:', m2 ? JSON.stringify(m2[0]) : 'null');
