package de.steffendingel.dynamicparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests the default methods of {@link Match}.
 */
class MatchTest {

	private final Nonterminal expr = new Nonterminal("Expr");

	private final Terminal any = new Terminal("any", token -> true);

	private final Rule rule = new Rule(expr, List.of(any, any, expr));

	private final Match match = new TestMatch(rule, 0, 3, Arrays.asList("a", 42, null));

	@Test
	void sizeIsTheNumberOfValues() {
		assertEquals(3, match.size());
	}

	@Test
	void valueReturnsTheValueAtTheIndex() {
		assertEquals("a", match.value(0));
		assertEquals(42, match.value(1));
		assertNull(match.value(2));
	}

	@Test
	void valueRejectsIndexOutOfRange() {
		assertThrows(IndexOutOfBoundsException.class, () -> match.value(3));
		assertThrows(IndexOutOfBoundsException.class, () -> match.value(-1));
	}

	@Test
	void typedValueReturnsTheValueCastToTheType() {
		String text = match.value(0, String.class);
		Integer number = match.value(1, Integer.class);

		assertEquals("a", text);
		assertEquals(42, number);
		assertNull(match.value(2, String.class));
	}

	@Test
	void typedValueRejectsWrongType() {
		assertThrows(ClassCastException.class, () -> match.value(0, Integer.class));
	}
}
