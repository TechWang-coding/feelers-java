import { describe, expect, test } from '@jest/globals';
import { evaluate, parse as parseTemplate, parseToSimpleTree } from 'feelers';

function validateTemplateSyntax(template: string): Array<{ from: number; to: number }> {
  const errors = [];
  const cursor = parseTemplate(template).cursor();

  do {
    if (cursor.type.isError) errors.push({ from: cursor.from, to: cursor.to });
  } while (cursor.next());

  return errors;
}

/**
 * Executable behaviour contract for the front-end FEELers implementation.
 * Keep these examples in sync with the Java renderer's test cases.
 */
describe('FEELers template syntax', () => {
  test('keeps plain text unchanged', () => {
    expect(evaluate('My simple string')).toBe('My simple string');
  });

  test('evaluates a complete expression when the template starts with =', () => {
    expect(evaluate('= 2 + secondNumber', { secondNumber: 12 })).toBe('14');
  });

  test('inserts variables, paths, and FEEL conditional expressions', () => {
    const context = { user: { name: 'Dave' }, age: 24 };

    expect(evaluate('Hello {{ user.name }}!', context)).toBe('Hello Dave!');
    expect(evaluate('{{ if age >= 18 then "adult" else "minor" }}', context)).toBe('adult');
  });

  test('renders an empty insert as an empty string', () => {
    expect(evaluate('a{{}}b')).toBe('ab');
    expect(evaluate('a{{=}}b')).toBe('ab');
  });

  test('renders conditional sections only when their FEEL condition is truthy', () => {
    const template = '{{#if count(users) > 1}}There are multiple users{{/if}}';

    expect(evaluate(template, { users: [ 'Bob', 'Dave' ] })).toBe('There are multiple users');
    expect(evaluate(template, { users: [ 'Bob' ] })).toBe('');
  });

  test('preserves FEELers block newline behaviour', () => {
    const template = '{{#if enabled}}\nvisible\n{{/if}}\nafter';

    expect(evaluate(template, { enabled: true })).toBe('visible\nafter');
    expect(evaluate(template, { enabled: false })).toBe('after');
  });

  test('loops over primitive values and exposes this', () => {
    const template = '{{#loop hobbies}}\n- {{this}}\n{{/loop}}';

    expect(evaluate(template, { hobbies: [ 'surfing', 'coding' ] })).toBe('- surfing\n- coding\n');
  });

  test('loops over objects and exposes the parent context', () => {
    const template = '{{#loop items}}{{parent.title}}: {{name}}\n{{/loop}}';
    const context = { title: 'Tasks', items: [ { name: 'Write tests' }, { name: 'Implement Java' } ] };

    expect(evaluate(template, context)).toBe('Tasks: Write tests\nTasks: Implement Java\n');
  });

  test('supports nested conditional sections inside a loop', () => {
    const template = '{{#loop items}}{{#if done}}✓ {{name}}\n{{/if}}{{/loop}}';
    const context = { items: [ { name: 'A', done: true }, { name: 'B', done: false } ] };

    expect(evaluate(template, context)).toBe('✓ A\n');
  });

  test('can sanitize each inserted FEEL result', () => {
    expect(evaluate('<p>{{ value }}</p>', { value: '<script>' }, {
      sanitizer: (value: string) => value.replaceAll('<', '&lt;')
    })).toBe('<p>&lt;script></p>');
  });

  test('enforces boolean and array types in strict mode', () => {
    expect(() => evaluate('{{#if 1}}x{{/if}}', {}, { strict: true }))
      .toThrow('FEEL expression 1 expected to evaluate to a boolean');
    expect(() => evaluate('{{#loop name}}x{{/loop}}', { name: 'Dave' }, { strict: true }))
      .toThrow('FEEL expression name expected to evaluate to an array');
  });

  test('shows malformed-expression errors inline in debug mode', () => {
    expect(evaluate('{{ 1 + }}', {}, { debug: true }))
      .toBe("{{ feel expression  1 +  couldn't be evaluated }}");
  });

  test('parses insertion syntax to a FEELers syntax tree', () => {
    const tree = parseToSimpleTree('{{ name }}');
    const insert = tree.children[0];

    expect(tree.name).toBe('Feelers');
    expect(insert.name).toBe('Insert');
    expect(insert.children[0].content).toBe(' name ');
  });

  test.each([
    'Plain text',
    'Hello {{ user.name }}!',
    'a{{}}b',
    'a{{= user.name}}b',
    '{{#if active}}{{#loop users}}{{name}}{{/loop}}{{/if}}',
    '= if score > 60 then "pass" else "fail"'
  ])('accepts valid FEELers template syntax: %s', (template) => {
    expect(validateTemplateSyntax(template)).toEqual([]);
  });

  test.each([
    [ '{{ name', { from: 7, to: 7 } ],
    [ '{{#if active}}yes', { from: 17, to: 17 } ],
    [ '{{#if active}}yes{{/loop}}', { from: 17, to: 26 } ],
    [ '{{/if}}', { from: 0, to: 7 } ]
  ])('reports malformed FEELers template syntax: %s', (template, expectedError) => {
    expect(validateTemplateSyntax(template)).toEqual([ expectedError ]);
  });
});
