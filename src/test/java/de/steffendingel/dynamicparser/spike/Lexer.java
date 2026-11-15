package de.steffendingel.dynamicparser.spike;

import java.util.ArrayList;
import java.util.List;

import de.steffendingel.dynamicparser.spike.Token.Kind;

/**
 * Splits lines into tokens by fixed character classes. Knows nothing about the grammar: which words are keywords and
 * which runs of special characters are operators is decided by the terminals.
 * <p>
 * Every line is followed by exactly one token that ends it: a {@link Kind#BLOCK} token if the line has a block, an
 * {@link Kind#END_OF_LINE} token otherwise. The lines of a block are not tokenized; that happens when the block is
 * parsed.
 */
final class Lexer {

	private Lexer() {
	}

	/** Tokenizes a sequence of lines of the same block. */
	static List<Token> tokens(List<Line> lines) {
		List<Token> tokens = new ArrayList<>();
		for (Line line : lines) {
			tokenize(line, tokens);
			int endColumn = endColumn(line);
			if (line.children().isEmpty()) {
				tokens.add(new Token(Kind.END_OF_LINE, "", line.number(), endColumn, List.of()));
			} else {
				tokens.add(new Token(Kind.BLOCK, "", line.number(), endColumn, line.children()));
			}
		}
		return tokens;
	}

	/**
	 * Tokenizes a sequence of lines of the same block into a flat token stream, for comparison: every line is followed
	 * by an {@link Kind#END_OF_LINE} token, and the tokens of the block of a line are enclosed in {@link Kind#INDENT}
	 * and {@link Kind#DEDENT} tokens. An {@code INDENT} token has the position of the first token of the block, a
	 * {@code DEDENT} token the position of the end of the last line of the block.
	 */
	static List<Token> flatTokens(List<Line> lines) {
		List<Token> tokens = new ArrayList<>();
		for (Line line : lines) {
			tokenize(line, tokens);
			tokens.add(new Token(Kind.END_OF_LINE, "", line.number(), endColumn(line), List.of()));
			if (!line.children().isEmpty()) {
				Line first = line.children().getFirst();
				tokens.add(new Token(Kind.INDENT, "", first.number(), first.textColumn(), List.of()));
				List<Token> inner = flatTokens(line.children());
				tokens.addAll(inner);
				Token last = inner.getLast();
				tokens.add(new Token(Kind.DEDENT, "", last.line(), last.column(), List.of()));
			}
		}
		return tokens;
	}

	private static int endColumn(Line line) {
		return line.textColumn() + line.text().stripTrailing().length();
	}

	private static void tokenize(Line line, List<Token> tokens) {
		String text = line.text();
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			int start = i;
			Kind kind;
			if (Character.isWhitespace(c)) {
				i++;
				continue;
			} else if (isWordStart(c)) {
				kind = Kind.WORD;
				while (i < text.length() && isWordPart(text.charAt(i))) {
					i++;
				}
			} else if (isDigit(c)) {
				kind = Kind.INTEGER;
				while (i < text.length() && isDigit(text.charAt(i))) {
					i++;
				}
			} else if (isBracket(c)) {
				kind = Kind.BRACKET;
				i++;
			} else {
				kind = Kind.SPECIAL;
				while (i < text.length() && isSpecial(text.charAt(i))) {
					i++;
				}
			}
			tokens.add(new Token(kind, text.substring(start, i), line.number(), line.textColumn() + start, List.of()));
		}
	}

	private static boolean isWordStart(char c) {
		return Character.isLetter(c) || c == '_';
	}

	private static boolean isWordPart(char c) {
		return isWordStart(c) || isDigit(c);
	}

	private static boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	private static boolean isBracket(char c) {
		return "()[]{}".indexOf(c) >= 0;
	}

	private static boolean isSpecial(char c) {
		return !Character.isWhitespace(c) && !isWordPart(c) && !isBracket(c);
	}
}
