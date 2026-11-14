package de.steffendingel.dynamicparser;

import static de.steffendingel.dynamicparser.ParserTesting.parse;
import static de.steffendingel.dynamicparser.ParserTesting.rule;
import static de.steffendingel.dynamicparser.ParserTesting.token;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Tests the syntax errors reported by the parser.
 */
class ParserSyntaxErrorTest {

	private final Nonterminal s = new Nonterminal("S");

	private final Nonterminal a = new Nonterminal("A");

	private final Terminal x = token("x");

	private final Terminal y = token("y");

	private final Terminal z = token("z");

	private SyntaxException syntaxError(String input, Rule... rules) {
		return assertThrows(SyntaxException.class, () -> parse(input, s, rules));
	}

	@Test
	void reportsUnexpectedToken() {
		SyntaxException exception = syntaxError("x z y", rule(s, x, y, z));

		assertEquals(1, exception.position());
		assertEquals(Optional.of("z"), exception.token());
		assertEquals(Set.of(y), exception.expected());
	}

	@Test
	void reportsUnexpectedFirstToken() {
		SyntaxException exception = syntaxError("y", rule(s, x));

		assertEquals(0, exception.position());
		assertEquals(Optional.of("y"), exception.token());
		assertEquals(Set.of(x), exception.expected());
	}

	@Test
	void reportsUnexpectedEndOfInput() {
		SyntaxException exception = syntaxError("x y", rule(s, x, y, z));

		assertEquals(2, exception.position());
		assertEquals(Optional.empty(), exception.token());
		assertEquals(Set.of(z), exception.expected());
	}

	@Test
	void reportsEmptyInputIfTheStartSymbolCannotBeEmpty() {
		SyntaxException exception = syntaxError("", rule(s, x));

		assertEquals(0, exception.position());
		assertEquals(Optional.empty(), exception.token());
		assertEquals(Set.of(x), exception.expected());
	}

	@Test
	void reportsTokenAfterCompleteInput() {
		SyntaxException exception = syntaxError("x y", rule(s, x));

		assertEquals(1, exception.position());
		assertEquals(Optional.of("y"), exception.token());
		assertEquals(Set.of(), exception.expected());
	}

	@Test
	void reportsTheFirstTokenThatDoesNotFit() {
		SyntaxException exception = syntaxError("x z z z", rule(s, x, y, y, y));

		assertEquals(1, exception.position());
	}

	@Test
	void reportsExpectedTerminalsOfAllOpenAlternatives() {
		// After "x", the input can continue with "y" (first rule), with "z" (second rule) or with "x" (through A).
		SyntaxException exception = syntaxError("x s", rule(s, x, y), rule(s, x, z), rule(s, x, a), rule(a, x));

		assertEquals(1, exception.position());
		assertEquals(Set.of(y, z, x), exception.expected());
	}

	@Test
	void reportsExpectedTerminalsAfterANullableSymbol() {
		// A can be empty, so both "y" and "z" are allowed after "x".
		SyntaxException exception = syntaxError("x x", rule(s, x, a, z), rule(a), rule(a, y));

		assertEquals(1, exception.position());
		assertEquals(Set.of(y, z), exception.expected());
	}

	@Test
	void reportsExpectedTerminalsAtTheEndOfARecursiveList() {
		// The list can end here or continue with "x"; continuing is the only way to use another token.
		SyntaxException exception = syntaxError("x x y", rule(s, x), rule(s, s, x));

		assertEquals(2, exception.position());
		assertEquals(Optional.of("y"), exception.token());
		assertEquals(Set.of(x), exception.expected());
	}

	@Test
	void reportsErrorIfTheStartSymbolHasNoRules() {
		SyntaxException empty = syntaxError("");
		SyntaxException token = syntaxError("x", rule(a, x));

		assertEquals(0, empty.position());
		assertEquals(Optional.empty(), empty.token());
		assertEquals(Set.of(), empty.expected());
		assertEquals(0, token.position());
		assertEquals(Optional.of("x"), token.token());
		assertEquals(Set.of(), token.expected());
	}

	@Test
	void reportsErrorIfANonterminalHasNoRules() {
		// A has no rules, so nothing can follow "x".
		SyntaxException exception = syntaxError("x y", rule(s, x, a));

		assertEquals(1, exception.position());
		assertEquals(Set.of(), exception.expected());
	}
}
