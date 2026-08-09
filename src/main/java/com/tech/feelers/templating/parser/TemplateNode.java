package com.tech.feelers.templating.parser;

import java.util.List;

/**
 * 设定意图：以不可变 AST 表达 FEELers 模板外壳，分离模板结构解析与渲染。
 * 作用边界：保存标签结构、表达式原文和偏移量；不校验 FEEL 语法、不求值、不决定输出内容。
 */
public sealed interface TemplateNode permits TemplateNode.Text, TemplateNode.Insert,
    TemplateNode.TopLevelFeel, TemplateNode.Block, TemplateNode.If, TemplateNode.Loop {

  /** 设定意图：表示普通文本。作用边界：原样输出，不进入 FEEL 求值。 */
  record Text(String value) implements TemplateNode { }

  /** 设定意图：表示内联插值标签。作用边界：仅保存表达式，求值和字符串转换由渲染层负责。 */
  record Insert(String expression, int offset) implements TemplateNode { }

  /** 设定意图：表示以 {@code =} 开始的顶层 FEEL 表达式。作用边界：仅记录表达式和位置。 */
  record TopLevelFeel(String expression, int offset) implements TemplateNode { }

  /** 设定意图：承载根节点、条件和循环的有序子节点。作用边界：只描述层级，不参与渲染。 */
  record Block(List<TemplateNode> children) implements TemplateNode { }

  /** 设定意图：表示条件块。作用边界：只保存条件和块体，布尔判定由渲染层负责。 */
  record If(String condition, Block body, boolean closeHadNewline, int offset) implements TemplateNode { }

  /** 设定意图：表示循环块。作用边界：只保存集合表达式和块体，迭代及作用域由渲染层负责。 */
  record Loop(String collection, Block body, boolean closeHadNewline, int offset) implements TemplateNode { }
}
