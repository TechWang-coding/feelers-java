import { builtinFunctionNames } from '../adapters/feelin/builtinFunctionNames';
import type { FeelFunctionDefinition } from '../types';
import { validateFunctionDefinition } from '../validation/validateFunctionDefinition';

/** Startup-only builder for an immutable custom-function registry. */
export class FeelFunctionRegistry {
  private readonly definitions = new Map<string, FeelFunctionDefinition>();
  private frozen = false;

  /**
   * Validates and records one function before the registry is frozen. Centralizing this check
   * prevents caller functions from shadowing each other or the runtime's built-ins.
   *
   * @param definition Function contract to register.
   * @returns This registry for startup-time composition.
   * @throws {Error} When the registry is frozen, the definition is invalid, or its name conflicts.
   */
  register(definition: FeelFunctionDefinition): this {
    if (this.frozen) {
      throw new Error('Cannot register FEEL functions after the registry has been frozen');
    }
    validateFunctionDefinition(definition);
    if (builtinFunctionNames.has(definition.name)) {
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

  /**
   * Closes startup registration and returns an immutable snapshot for engine construction.
   * This prevents runtime mutation of executable capabilities.
   *
   * @returns Registered definitions in insertion order.
   */
  freeze(): readonly FeelFunctionDefinition[] {
    this.frozen = true;
    return Object.freeze([ ...this.definitions.values() ]);
  }
}
