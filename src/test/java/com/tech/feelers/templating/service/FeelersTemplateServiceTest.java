package com.tech.feelers.templating.service;

import com.tech.feelers.templating.exception.TemplateException;
import com.tech.feelers.templating.model.RenderOptions;
import com.tech.feelers.templating.parser.TemplateNode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Java counterparts of templating-syntax/js-templating-syntax.test.js.
 * These tests intentionally exercise the Camunda FEEL Scala adapter.
 */
class FeelersTemplateServiceTest {
  @Test
  void keepsPlainTextUnchanged() {
    assertEquals("My simple string", FeelersTemplateService.evaluate("My simple string", Map.of()));
  }

  @Test
  void evaluatesACompleteFeelExpression() {
    assertEquals("14", FeelersTemplateService.evaluate("= 2 + secondNumber", Map.of("secondNumber", 12)));
  }

  @Test
  void insertsVariablesPathsAndConditionalExpressions() {
    Map<String, Object> context = Map.of("user", Map.of("name", "Dave"), "age", 24);

    assertEquals("Hello Dave!", FeelersTemplateService.evaluate("Hello {{ user.name }}!", context));
    assertEquals("adult", FeelersTemplateService.evaluate("{{ if age >= 18 then \"adult\" else \"minor\" }}", context));
  }

  @Test
  void rendersEmptyInsertAsEmptyString() {
    assertEquals("ab", FeelersTemplateService.evaluate("a{{}}b", Map.of()));
    assertEquals("ab", FeelersTemplateService.evaluate("a{{=}}b", Map.of()));
  }

  @Test
  void rendersConditionalSectionsAndBlockNewlines() {
    assertEquals("There are multiple users", FeelersTemplateService.evaluate(
        "{{#if count(users) > 1}}There are multiple users{{/if}}", Map.of("users", List.of("Bob", "Dave"))));
    assertEquals("", FeelersTemplateService.evaluate(
        "{{#if count(users) > 1}}There are multiple users{{/if}}", Map.of("users", List.of("Bob"))));
    assertEquals("visible\nafter", FeelersTemplateService.evaluate("{{#if enabled}}\nvisible\n{{/if}}\nafter", Map.of("enabled", true)));
  }

  @Test
  void loopsWithThisAndParent() {
    assertEquals("- surfing\n- coding\n", FeelersTemplateService.evaluate(
        "{{#loop hobbies}}\n- {{this}}\n{{/loop}}", Map.of("hobbies", List.of("surfing", "coding"))));

    Map<String, Object> context = Map.of(
        "title", "Tasks",
        "items", List.of(Map.of("name", "Write tests"), Map.of("name", "Implement Java")));
    assertEquals("Tasks: Write tests\nTasks: Implement Java\n", FeelersTemplateService.evaluate(
        "{{#loop items}}{{parent.title}}: {{name}}\n{{/loop}}", context));
  }

  @Test
  void supportsNestedBlocksAndSanitizers() {
    Map<String, Object> context = Map.of("items", List.of(Map.of("name", "A", "done", true), Map.of("name", "B", "done", false)));
    assertEquals("✓ A\n", FeelersTemplateService.evaluate("{{#loop items}}{{#if done}}✓ {{name}}\n{{/if}}{{/loop}}", context));

    RenderOptions options = new RenderOptions(false, false, value -> value.replace("<", "&lt;"));
    assertEquals("<p>&lt;script></p>", FeelersTemplateService.evaluate("<p>{{ value }}</p>", Map.of("value", "<script>"), options));
  }

  @Test
  void appliesStrictAndDebugPolicies() {
    RenderOptions strict = new RenderOptions(true, false, null);
    assertThrows(TemplateException.class, () -> FeelersTemplateService.evaluate("{{#if 1}}x{{/if}}", Map.of(), strict));
    assertThrows(TemplateException.class, () -> FeelersTemplateService.evaluate("{{#loop name}}x{{/loop}}", Map.of("name", "Dave"), strict));

    RenderOptions debug = new RenderOptions(false, true, null);
    assertEquals("{{ feel expression  1 +  couldn't be evaluated }}", FeelersTemplateService.evaluate("{{ 1 + }}", Map.of(), debug));
  }

  @Test
  void reportsTemplateSyntaxWithAnOffset() {
    TemplateException error = assertThrows(TemplateException.class,
        () -> FeelersTemplateService.evaluate("{{#if true}}missing close", Map.of()));

    assertEquals("Missing closing {{/if}}", error.getMessage());
    assertEquals(25, error.offset());
  }

  @Test
  void exposesFrontendEquivalentEvaluationAndParsingMethods() {
    RenderOptions debug = new RenderOptions(false, true, null);
    assertEquals("{{ feel expression  1 +  couldn't be evaluated }}",
        FeelersTemplateService.evaluate("{{ 1 + }}", Map.of(), debug));

    TemplateNode parsed = FeelersTemplateService.parse("{{#if active}}yes{{/if}}");
    assertEquals(parsed, FeelersTemplateService.parseToSimpleTree("{{#if active}}yes{{/if}}"));
    assertThrows(TemplateException.class, () -> FeelersTemplateService.parse("{{#if active}}yes"));
  }
}
