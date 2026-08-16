/**
 * Built-in function names in @bpmn-io/feelin 6.1.0. Keep this list in sync when upgrading
 * feelin: its built-ins are not exported as public API.
 */
export const builtinFunctionNames: ReadonlySet<string> = new Set([
  'date', 'time', 'date and time', 'duration', 'years and months duration', 'string', 'number',
  'substring', 'string length', 'upper case', 'lower case', 'substring before', 'substring after',
  'replace', 'contains', 'starts with', 'ends with', 'matches', 'split', 'list contains',
  'list replace', 'count', 'min', 'max', 'sum', 'mean', 'all', 'any', 'sublist', 'append',
  'concatenate', 'insert before', 'remove', 'reverse', 'index of', 'union', 'distinct values',
  'flatten', 'product', 'median', 'stddev', 'mode', 'get entries', 'get value', 'context put',
  'context merge', 'context', 'sort', 'decimal', 'floor', 'ceiling', 'abs', 'modulo', 'sqrt', 'log',
  'exp', 'odd', 'even', 'median', 'stddev', 'mode', 'day of week', 'day of year', 'week of year',
  'month of year', 'today', 'now', 'before', 'after', 'meets', 'met by', 'overlaps', 'overlaps before',
  'overlaps after', 'finishes', 'finished by', 'includes', 'during', 'starts', 'started by', 'coincides'
]);
