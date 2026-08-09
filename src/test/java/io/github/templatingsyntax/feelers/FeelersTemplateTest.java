package io.github.templatingsyntax.feelers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Java counterparts of templating-syntax/js-templating-syntax.test.js.
 * These tests intentionally exercise the Camunda FEEL Scala adapter.
 */
class FeelersTemplateTest {
  private final TemplateRenderer renderer = new FeelersTemplate();

  @Test
  void keepsPlainTextUnchanged() {
    assertEquals("My simple string", renderer.render("My simple string", Map.of()));
  }

  @Test
  void evaluatesACompleteFeelExpression() {
    assertEquals("14", renderer.render("= 2 + secondNumber", Map.of("secondNumber", 12)));
  }

  @Test
  void insertsVariablesPathsAndConditionalExpressions() {
    Map<String, Object> context = Map.of("user", Map.of("name", "Dave"), "age", 24);

    assertEquals("Hello Dave!", renderer.render("Hello {{ user.name }}!", context));
    assertEquals("adult", renderer.render("{{ if age >= 18 then \"adult\" else \"minor\" }}", context));
  }

  @Test
  void rendersEmptyInsertAsEmptyString() {
    assertEquals("ab", renderer.render("a{{}}b", Map.of()));
    assertEquals("ab", renderer.render("a{{=}}b", Map.of()));
  }

  @Test
  void rendersConditionalSectionsAndBlockNewlines() {
    assertEquals("There are multiple users", renderer.render(
        "{{#if count(users) > 1}}There are multiple users{{/if}}", Map.of("users", List.of("Bob", "Dave"))));
    assertEquals("", renderer.render(
        "{{#if count(users) > 1}}There are multiple users{{/if}}", Map.of("users", List.of("Bob"))));
    assertEquals("visible\nafter", renderer.render("{{#if enabled}}\nvisible\n{{/if}}\nafter", Map.of("enabled", true)));
  }

  @Test
  void loopsWithThisAndParent() {
    assertEquals("- surfing\n- coding\n", renderer.render(
        "{{#loop hobbies}}\n- {{this}}\n{{/loop}}", Map.of("hobbies", List.of("surfing", "coding"))));

    Map<String, Object> context = Map.of(
        "title", "Tasks",
        "items", List.of(Map.of("name", "Write tests"), Map.of("name", "Implement Java")));
    assertEquals("Tasks: Write tests\nTasks: Implement Java\n", renderer.render(
        "{{#loop items}}{{parent.title}}: {{name}}\n{{/loop}}", context));
  }

  @Test
  void supportsNestedBlocksAndSanitizers() {
    Map<String, Object> context = Map.of("items", List.of(Map.of("name", "A", "done", true), Map.of("name", "B", "done", false)));
    assertEquals("✓ A\n", renderer.render("{{#loop items}}{{#if done}}✓ {{name}}\n{{/if}}{{/loop}}", context));

    RenderOptions options = new RenderOptions(false, false, value -> value.replace("<", "&lt;"));
    assertEquals("<p>&lt;script></p>", renderer.render("<p>{{ value }}</p>", Map.of("value", "<script>"), options));
  }

  @Test
  void appliesStrictAndDebugPolicies() {
    RenderOptions strict = new RenderOptions(true, false, null);
    assertThrows(TemplateException.class, () -> renderer.render("{{#if 1}}x{{/if}}", Map.of(), strict));
    assertThrows(TemplateException.class, () -> renderer.render("{{#loop name}}x{{/loop}}", Map.of("name", "Dave"), strict));

    RenderOptions debug = new RenderOptions(false, true, null);
    assertEquals("{{ feel expression  1 +  couldn't be evaluated }}", renderer.render("{{ 1 + }}", Map.of(), debug));
  }

  @Test
  void reportsTemplateSyntaxWithAnOffset() {
    TemplateException error = assertThrows(TemplateException.class,
        () -> renderer.render("{{#if true}}missing close", Map.of()));

    assertEquals("Missing closing {{/if}}", error.getMessage());
    assertEquals(25, error.offset());
  }
}
