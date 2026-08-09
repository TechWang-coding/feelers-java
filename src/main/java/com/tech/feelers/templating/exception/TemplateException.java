package com.tech.feelers.templating.exception;

/**
 * 设定意图：提供模板领域的统一错误类型，使调用方不依赖 Parser 或 Camunda 的异常类型。
 * 作用边界：保存错误消息、模板偏移量及可选原因；不承载具体 FEEL 引擎的诊断模型。
 */
public final class TemplateException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final int offset;

  public TemplateException(String message, int offset) {
    super(message);
    this.offset = offset;
  }

  public TemplateException(String message, int offset, Throwable cause) {
    super(message, cause);
    this.offset = offset;
  }

  public int offset() {
    return offset;
  }
}
