/**
 * 项目装的 markdown-it 是 12.x 且未安装 @types（既有 6 处 TS7016 同源）。
 * 这里给一个显式的宽松声明，避免每个使用点都报"隐式 any"，也坚持不为此新增依赖。
 */
declare module 'markdown-it' {
  const MarkdownIt: any;
  export default MarkdownIt;
}
