package com.tech.feelers.templating.model;

import java.util.function.UnaryOperator;

/**
 * 设定意图：集中定义一次渲染的可选策略，并保持与前端 FEELers 选项一致。
 * 作用边界：控制 strict、debug 和输出 sanitizer；不负责 FEEL 函数安全策略或模板解析。
 */
public record RenderOptions(boolean strict, boolean debug, UnaryOperator<String> sanitizer) {
  public static final RenderOptions DEFAULT = new RenderOptions(false, false, null);
}
