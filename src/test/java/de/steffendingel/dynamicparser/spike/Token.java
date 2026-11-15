package de.steffendingel.dynamicparser.spike;

import java.util.List;

/**
 * A token produced by the {@link Lexer}. Every token carries its source position.
 *
 * @param kind   the character class of the token
 * @param text   the text of the token; empty for the kinds that do not stand for characters
 * @param line   the line number, starting at 1
 * @param column the column of the first character, starting at 1; for {@link Kind#END_OF_LINE} and {@link Kind#BLOCK}
 *               the column after the last character of the line
 * @param block  for {@link Kind#BLOCK}, the lines of the block, which are not tokenized yet; otherwise empty
 */
record Token(Kind kind, String text, int line, int column, List<Line> block) {

	/** The character classes of tokens. */
	enum Kind {
		/** A letter or underscore followed by letters, digits and underscores. */
		WORD,
		/** A sequence of digits. */
		INTEGER,
		/** A run of characters that are neither whitespace, letters, digits, underscores nor brackets. */
		SPECIAL,
		/** A single bracket: one of {@code ( ) [ ] { }}. */
		BRACKET,
		/** The end of a line that has no block. */
		END_OF_LINE,
		/** The block of the preceding line, as a single token. It also ends that line. */
		BLOCK,
		/** The beginning of a block in a flat token stream, see {@link Lexer#flatTokens(List)}. */
		INDENT,
		/** The end of a block in a flat token stream, see {@link Lexer#flatTokens(List)}. */
		DEDENT
	}

	Token {
		block = List.copyOf(block);
	}

	/** Returns whether this token is of the given kind and has the given text. */
	boolean is(Kind kind, String text) {
		return this.kind == kind && this.text.equals(text);
	}

	/** Returns the token in a form suitable for error messages, for example {@code 'x'} or {@code end of line}. */
	@Override
	public String toString() {
		return switch (kind) {
		case END_OF_LINE -> "end of line";
		case BLOCK -> "indented block";
		case INDENT -> "indent";
		case DEDENT -> "dedent";
		default -> "'" + text + "'";
		};
	}
}
