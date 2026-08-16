/**
 * Built-in function names in @bpmn-io/feelin 6.1.0. Keep this list in sync when upgrading
 * feelin: its built-ins are not exported as public API.
 */
export class FeelEngineStatic {
  static readonly BUILTIN_FUNCTION_NAMES: ReadonlySet<string> = new Set([
    '@', 'now', 'today',
    'date and time', 'time', 'date', 'number', 'string', 'duration', 'years and months duration',
    'not',
    'substring', 'string length', 'upper case', 'lower case', 'substring before', 'substring after',
    'replace', 'contains', 'matches', 'starts with', 'ends with', 'split', 'string join',
    'list contains', 'list replace', 'count', 'min', 'max', 'sum', 'mean', 'all', 'any', 'sublist',
    'append', 'concatenate', 'insert before', 'remove', 'reverse', 'index of', 'union',
    'distinct values', 'flatten', 'product', 'median', 'stddev', 'mode',
    'decimal', 'floor', 'ceiling', 'abs', 'modulo', 'sqrt', 'log', 'exp', 'odd', 'even',
    'is',
    'day of year', 'day of week', 'month of year', 'week of year',
    'before', 'after', 'meets', 'met by', 'overlaps', 'overlaps before', 'overlaps after',
    'finishes', 'finished by', 'includes', 'during', 'starts', 'started by', 'coincides',
    'sort', 'list', 'precedes', 'get value', 'get entries', 'context', 'context merge', 'context put'
  ]);
}
