import { FeelEngineStatic } from './FeelEngineStatic';
import type { FeelFunctionDefinition } from './FeelExpressionTypes';
import { validateFunctionDefinition } from '../utils/FeelFunctionUtils';

/** Startup-only builder for an immutable custom-function registry. */
export class FeelFunctionRegistry {
  private readonly definitions = new Map<string, FeelFunctionDefinition>();
  private frozen = false;

  register(definition: FeelFunctionDefinition): this {
    if (this.frozen) {
      throw new Error('Cannot register FEEL functions after the registry has been frozen');
    }
    validateFunctionDefinition(definition);
    if (FeelEngineStatic.BUILTIN_FUNCTION_NAMES.has(definition.name)) {
      throw new Error(`Custom FEEL function "${definition.name}" conflicts with a feelin built-in function`);
    }
    if (this.definitions.has(definition.name)) {
      throw new Error(`Custom FEEL function "${definition.name}" is already registered`);
    }

    this.definitions.set(definition.name, Object.freeze({
      name: definition.name,
      args: Object.freeze([ ...definition.args ]),
      handler: definition.handler
    }));
    return this;
  }

  freeze(): readonly FeelFunctionDefinition[] {
    this.frozen = true;
    return Object.freeze([ ...this.definitions.values() ]);
  }
}
