package com.tech.feelers.templating.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compiles the static FEEL subset used by Form rules into JSON Schema conditions.
 * Runtime functions such as {@code today()} are intentionally not supported here.
 */
final class FeelConditionCompiler {
    private final ObjectMapper objectMapper;
    private final Map<String, String> fieldTypes;

    FeelConditionCompiler(ObjectMapper objectMapper, Map<String, String> fieldTypes) {
        this.objectMapper = objectMapper;
        this.fieldTypes = Map.copyOf(fieldTypes);
    }

    ObjectNode compile(String expression) {
        Parser parser = new Parser(tokenize(expression));
        Node node = parser.parseExpression();
        parser.expect(TokenType.END);
        return node.toSchema(objectMapper);
    }

    private List<Token> tokenize(String expression) {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        while (index < expression.length()) {
            char character = expression.charAt(index);
            if (Character.isWhitespace(character)) {
                index++;
            } else if (character == '(') {
                tokens.add(new Token(TokenType.LEFT_PAREN, "("));
                index++;
            } else if (character == ')') {
                tokens.add(new Token(TokenType.RIGHT_PAREN, ")"));
                index++;
            } else if (character == '>' || character == '<' || character == '=' || character == '!') {
                int end = index + 1;
                if (end < expression.length() && expression.charAt(end) == '=') {
                    end++;
                }
                String operator = expression.substring(index, end);
                if (!List.of("=", "!=", ">", ">=", "<", "<=").contains(operator)) {
                    throw new FormSchemaGenerationException("unsupported FEEL operator: " + operator);
                }
                tokens.add(new Token(TokenType.OPERATOR, operator));
                index = end;
            } else if (character == '"') {
                StringBuilder value = new StringBuilder();
                index++;
                boolean closed = false;
                while (index < expression.length()) {
                    char current = expression.charAt(index++);
                    if (current == '\\') {
                        if (index >= expression.length()) {
                            throw new FormSchemaGenerationException("unterminated escape in FEEL string");
                        }
                        value.append(expression.charAt(index++));
                    } else if (current == '"') {
                        closed = true;
                        break;
                    } else {
                        value.append(current);
                    }
                }
                if (!closed) {
                    throw new FormSchemaGenerationException("unterminated FEEL string");
                }
                tokens.add(new Token(TokenType.STRING, value.toString()));
            } else if (Character.isDigit(character) || character == '-') {
                int end = index + 1;
                while (end < expression.length() && (Character.isDigit(expression.charAt(end)) || expression.charAt(end) == '.')) {
                    end++;
                }
                String number = expression.substring(index, end);
                try {
                    new BigDecimal(number);
                } catch (NumberFormatException exception) {
                    throw new FormSchemaGenerationException("invalid FEEL number: " + number);
                }
                tokens.add(new Token(TokenType.NUMBER, number));
                index = end;
            } else if (Character.isLetter(character) || character == '_') {
                int end = index + 1;
                while (end < expression.length()) {
                    char current = expression.charAt(end);
                    if (Character.isLetterOrDigit(current) || current == '_' || current == '.') {
                        end++;
                    } else {
                        break;
                    }
                }
                String word = expression.substring(index, end);
                String lowerCase = word.toLowerCase(Locale.ROOT);
                tokens.add(switch (lowerCase) {
                    case "and" -> new Token(TokenType.AND, word);
                    case "or" -> new Token(TokenType.OR, word);
                    case "not" -> new Token(TokenType.NOT, word);
                    case "true", "false" -> new Token(TokenType.BOOLEAN, lowerCase);
                    case "null" -> new Token(TokenType.NULL, lowerCase);
                    default -> new Token(TokenType.IDENTIFIER, word);
                });
                index = end;
            } else {
                throw new FormSchemaGenerationException("unexpected FEEL character: " + character);
            }
        }
        tokens.add(new Token(TokenType.END, ""));
        return tokens;
    }

    private sealed interface Node permits ComparisonNode, LogicalNode, NotNode {
        ObjectNode toSchema(ObjectMapper objectMapper);
    }

    private record ComparisonNode(String field, String operator, JsonNode value) implements Node {
        @Override
        public ObjectNode toSchema(ObjectMapper objectMapper) {
            ObjectNode condition = objectMapper.createObjectNode();
            condition.putArray("required").add(field);
            ObjectNode fieldConstraint = condition.putObject("properties").putObject(field);
            switch (operator) {
                case "=" -> fieldConstraint.set("const", value);
                case "!=" -> fieldConstraint.putObject("not").set("const", value);
                case ">" -> fieldConstraint.set("exclusiveMinimum", value);
                case ">=" -> fieldConstraint.set("minimum", value);
                case "<" -> fieldConstraint.set("exclusiveMaximum", value);
                case "<=" -> fieldConstraint.set("maximum", value);
                default -> throw new FormSchemaGenerationException("unsupported FEEL operator: " + operator);
            }
            return condition;
        }
    }

    private record LogicalNode(String keyword, Node left, Node right) implements Node {
        @Override
        public ObjectNode toSchema(ObjectMapper objectMapper) {
            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode branches = result.putArray(keyword);
            branches.add(left.toSchema(objectMapper));
            branches.add(right.toSchema(objectMapper));
            return result;
        }
    }

    private record NotNode(Node node) implements Node {
        @Override
        public ObjectNode toSchema(ObjectMapper objectMapper) {
            ObjectNode result = objectMapper.createObjectNode();
            result.set("not", node.toSchema(objectMapper));
            return result;
        }
    }

    private final class Parser {
        private final List<Token> tokens;
        private int position;

        private Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        Node parseExpression() {
            return parseOr();
        }

        private Node parseOr() {
            Node node = parseAnd();
            while (match(TokenType.OR)) {
                node = new LogicalNode("anyOf", node, parseAnd());
            }
            return node;
        }

        private Node parseAnd() {
            Node node = parseNot();
            while (match(TokenType.AND)) {
                node = new LogicalNode("allOf", node, parseNot());
            }
            return node;
        }

        private Node parseNot() {
            if (match(TokenType.NOT)) {
                return new NotNode(parseNot());
            }
            return parsePrimary();
        }

        private Node parsePrimary() {
            if (match(TokenType.LEFT_PAREN)) {
                Node node = parseExpression();
                expect(TokenType.RIGHT_PAREN);
                return node;
            }
            Token field = expect(TokenType.IDENTIFIER);
            Token operator = expect(TokenType.OPERATOR);
            String fieldType = fieldTypes.get(field.value());
            if (fieldType == null) {
                throw error("unknown field " + field.value());
            }
            JsonNode value = parseLiteral();
            validateComparison(field.value(), fieldType, operator.value(), value);
            return new ComparisonNode(field.value(), operator.value(), value);
        }

        private void validateComparison(String field, String fieldType, String operator, JsonNode value) {
            if (List.of(">", ">=", "<", "<=").contains(operator)) {
                if (!("integer".equals(fieldType) || "number".equals(fieldType)) || !value.isNumber()) {
                    throw error("numeric comparison requires a numeric field and literal: " + field);
                }
                if ("integer".equals(fieldType) && !value.isIntegralNumber()) {
                    throw error("integer comparison requires an integer literal: " + field);
                }
                return;
            }
            boolean compatible = switch (fieldType) {
                case "string" -> value.isTextual();
                case "integer" -> value.isIntegralNumber();
                case "number" -> value.isNumber();
                case "boolean" -> value.isBoolean();
                default -> false;
            };
            if (!compatible) {
                throw error("literal is incompatible with " + fieldType + " field " + field);
            }
        }

        private JsonNode parseLiteral() {
            Token token = advance();
            return switch (token.type()) {
                case STRING -> objectMapper.getNodeFactory().textNode(token.value());
                case NUMBER -> numberNode(token.value());
                case BOOLEAN -> objectMapper.getNodeFactory().booleanNode(Boolean.parseBoolean(token.value()));
                case NULL -> objectMapper.getNodeFactory().nullNode();
                default -> throw error("expected a literal, found " + token.value());
            };
        }

        private JsonNode numberNode(String value) {
            BigDecimal number = new BigDecimal(value);
            if (number.scale() <= 0 && number.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) >= 0
                    && number.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0) {
                return objectMapper.getNodeFactory().numberNode(number.longValueExact());
            }
            return objectMapper.getNodeFactory().numberNode(number);
        }

        private boolean match(TokenType type) {
            if (peek().type() != type) {
                return false;
            }
            position++;
            return true;
        }

        private Token expect(TokenType type) {
            Token token = advance();
            if (token.type() != type) {
                throw error("expected " + type + ", found " + token.value());
            }
            return token;
        }

        private Token advance() {
            return tokens.get(position++);
        }

        private Token peek() {
            return tokens.get(position);
        }

        private FormSchemaGenerationException error(String message) {
            return new FormSchemaGenerationException("invalid FEEL condition: " + message);
        }
    }

    private record Token(TokenType type, String value) {
    }

    private enum TokenType {
        IDENTIFIER, STRING, NUMBER, BOOLEAN, NULL, OPERATOR, AND, OR, NOT, LEFT_PAREN, RIGHT_PAREN, END
    }
}
