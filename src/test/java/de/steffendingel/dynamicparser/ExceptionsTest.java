package de.steffendingel.dynamicparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ExceptionsTest {

	private final Nonterminal expr = new Nonterminal("Expr");

	private final Terminal plus = new Terminal("'+'", "+"::equals);

	private final Terminal number = new Terminal("number", token -> token instanceof Integer);

	private final Rule rule = new Rule(expr, List.of(expr, plus, expr));

	@Test
	void syntaxExceptionDescribesUnexpectedToken() {
		SyntaxException exception = new SyntaxException(2, "*", List.of(plus, number));

		assertEquals(2, exception.position());
		assertEquals(Optional.of("*"), exception.token());
		assertEquals(List.of(plus, number), List.copyOf(exception.expected()));
		assertEquals("Unexpected token * at position 2; expected: '+', number", exception.getMessage());
	}

	@Test
	void syntaxExceptionDescribesUnexpectedEndOfInput() {
		SyntaxException exception = new SyntaxException(3, null, List.of(number));

		assertEquals(Optional.empty(), exception.token());
		assertEquals("Unexpected end of input at position 3; expected: number", exception.getMessage());
	}

	@Test
	void syntaxExceptionDescribesPositionWhereNothingIsAllowed() {
		SyntaxException exception = new SyntaxException(1, "+", List.of());

		assertTrue(exception.expected().isEmpty());
		assertEquals("Unexpected token + at position 1; nothing is allowed here", exception.getMessage());
	}

	@Test
	void syntaxExceptionListsEveryExpectedTerminalOnce() {
		Terminal otherPlus = new Terminal("'+'", "+"::equals);

		SyntaxException exception = new SyntaxException(0, "x", List.of(plus, number, plus, otherPlus));

		assertEquals(List.of(plus, number, otherPlus), List.copyOf(exception.expected()));
		assertEquals("Unexpected token x at position 0; expected: '+', number", exception.getMessage());
	}

	@Test
	void syntaxExceptionRejectsInvalidArguments() {
		assertThrows(NullPointerException.class, () -> new SyntaxException(0, "x", null));
		assertThrows(NullPointerException.class, () -> new SyntaxException(0, "x", Arrays.asList(plus, null)));
		assertThrows(IllegalArgumentException.class, () -> new SyntaxException(-1, "x", List.of()));
	}

	@Test
	void syntaxExceptionExpectedTerminalsAreUnmodifiable() {
		SyntaxException exception = new SyntaxException(0, "x", List.of(plus));

		assertThrows(UnsupportedOperationException.class, () -> exception.expected().add(number));
	}

	@Test
	void ambiguityExceptionDescribesAmbiguousSection() {
		AmbiguityException exception = new AmbiguityException(expr, 1, 6);

		assertSame(expr, exception.symbol());
		assertEquals(1, exception.start());
		assertEquals(6, exception.end());
		assertEquals(1, exception.position());
		assertEquals("Ambiguous input: Expr matches the section from position 1 to position 6 in more than one way",
				exception.getMessage());
	}

	@Test
	void ambiguityExceptionAllowsEmptySection() {
		AmbiguityException exception = new AmbiguityException(expr, 4, 4);

		assertEquals(4, exception.start());
		assertEquals(4, exception.end());
	}

	@Test
	void ambiguityExceptionRejectsInvalidArguments() {
		assertThrows(NullPointerException.class, () -> new AmbiguityException(null, 0, 1));
		assertThrows(IllegalArgumentException.class, () -> new AmbiguityException(expr, -1, 1));
		assertThrows(IllegalArgumentException.class, () -> new AmbiguityException(expr, 2, 1));
	}

	@Test
	void parseExceptionsAreCheckedExceptions() {
		ParseException syntax = new SyntaxException(0, "x", List.of());
		ParseException ambiguity = new AmbiguityException(expr, 0, 1);

		assertTrue(Exception.class.isInstance(syntax) && !RuntimeException.class.isInstance(syntax));
		assertTrue(Exception.class.isInstance(ambiguity) && !RuntimeException.class.isInstance(ambiguity));
	}

	@Test
	void lateGrammarChangeExceptionDescribesRuleAndPositions() {
		LateGrammarChangeException exception = new LateGrammarChangeException(rule, 3, 5);

		assertSame(rule, exception.rule());
		assertEquals(3, exception.matchEnd());
		assertEquals(5, exception.position());
		assertEquals(
				"The action of the rule Expr -> Expr '+' Expr cannot change the grammar: "
						+ "its match ends at position 3, but it was only certain at position 5",
				exception.getMessage());
	}

	@Test
	void lateGrammarChangeExceptionIsUnchecked() {
		assertTrue(RuntimeException.class.isAssignableFrom(LateGrammarChangeException.class));
	}

	@Test
	void lateGrammarChangeExceptionRejectsInvalidArguments() {
		assertThrows(NullPointerException.class, () -> new LateGrammarChangeException(null, 0, 1));
		assertThrows(IllegalArgumentException.class, () -> new LateGrammarChangeException(rule, -1, 1));
		assertThrows(IllegalArgumentException.class, () -> new LateGrammarChangeException(rule, 3, 3));
		assertThrows(IllegalArgumentException.class, () -> new LateGrammarChangeException(rule, 3, 2));
	}
}
