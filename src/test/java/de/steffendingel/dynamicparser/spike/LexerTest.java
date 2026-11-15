package de.steffendingel.dynamicparser.spike;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import de.steffendingel.dynamicparser.spike.Token.Kind;

/**
 * Tests the lexer: fixed character classes, source positions and the tokens that end lines.
 */
class LexerTest {

	private static List<Token> tokens(String text) throws IndentationException {
		return Lexer.tokens(StructureReader.read(text));
	}

	/** Describes tokens as {@code KIND:text@line:column}. */
	private static String describe(List<Token> tokens) {
		return tokens.stream()
				.map(token -> token.kind() + ":" + token.text() + "@" + token.line() + ":" + token.column())
				.collect(Collectors.joining(" "));
	}

	@Test
	void splitsByCharacterClasses() throws IndentationException {
		assertEquals(
				"WORD:fun@1:1 WORD:f_1@1:5 BRACKET:(@1:8 WORD:b@1:9 SPECIAL::@1:10 WORD:B@1:12 BRACKET:)@1:13 "
						+ "SPECIAL::@1:14 WORD:Integer@1:16 END_OF_LINE:@1:23",
				describe(tokens("fun f_1(b: B): Integer")));
	}

	@Test
	void runsOfSpecialCharactersAreOneToken() throws IndentationException {
		assertEquals("WORD:a@1:1 SPECIAL:<=@1:3 INTEGER:12@1:6 SPECIAL:+-@1:9 WORD:x@1:11 END_OF_LINE:@1:12",
				describe(tokens("a <= 12 +-x")));
	}

	@Test
	void bracketsAreSingleTokensAndEndRunsOfSpecialCharacters() throws IndentationException {
		assertEquals("BRACKET:[@1:1 BRACKET:[@1:2 WORD:a@1:3 SPECIAL:,@1:4 WORD:b@1:6 BRACKET:)@1:7 SPECIAL:.@1:8 "
				+ "BRACKET:}@1:9 END_OF_LINE:@1:10", describe(tokens("[[a, b).}")));
	}

	@Test
	void integerFollowedByLettersIsTwoTokens() throws IndentationException {
		assertEquals("INTEGER:12@1:1 WORD:ab@1:3 END_OF_LINE:@1:5", describe(tokens("12ab")));
	}

	@Test
	void columnsCountFromTheStartOfTheLineIncludingIndentation() throws IndentationException {
		Token block = tokens("a\n\t  b c  ").get(1);

		assertEquals("WORD:b@2:4 WORD:c@2:6 END_OF_LINE:@2:7", describe(Lexer.tokens(block.block())));
	}

	@Test
	void lineWithABlockEndsWithABlockToken() throws IndentationException {
		List<Token> tokens = tokens("if x\n  y\n  z\nw");

		assertEquals("WORD:if@1:1 WORD:x@1:4 BLOCK:@1:5 WORD:w@4:1 END_OF_LINE:@4:2", describe(tokens));
		assertEquals(Kind.BLOCK, tokens.get(2).kind());
		assertEquals(List.of(2, 3), tokens.get(2).block().stream().map(Line::number).toList());
	}
}
