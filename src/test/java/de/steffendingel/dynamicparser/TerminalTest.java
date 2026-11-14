package de.steffendingel.dynamicparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

class TerminalTest {

	@Test
	void hasName() {
		Terminal terminal = new Terminal("'+'", "+"::equals);

		assertEquals("'+'", terminal.name());
		assertEquals("'+'", terminal.toString());
	}

	@Test
	void matchesTokensAcceptedByThePredicate() {
		Terminal plus = new Terminal("'+'", "+"::equals);
		Terminal number = new Terminal("number", token -> token instanceof Integer);

		assertTrue(plus.matches("+"));
		assertFalse(plus.matches("-"));
		assertFalse(plus.matches(1));
		assertTrue(number.matches(1));
		assertTrue(number.matches(2));
		assertFalse(number.matches("+"));
	}

	@Test
	void rejectsNullArguments() {
		assertThrows(NullPointerException.class, () -> new Terminal(null, token -> true));
		assertThrows(NullPointerException.class, () -> new Terminal("any", null));
	}

	@Test
	void rejectsNullTokenWithoutCallingThePredicate() {
		Terminal terminal = new Terminal("any", token -> {
			throw new AssertionError("predicate must not be called");
		});

		assertThrows(NullPointerException.class, () -> terminal.matches(null));
	}

	@Test
	void isIdentifiedByIdentityNotByNameAndPredicate() {
		Predicate<Object> predicate = "+"::equals;
		Terminal first = new Terminal("'+'", predicate);
		Terminal second = new Terminal("'+'", predicate);

		assertEquals(first, first);
		assertNotEquals(first, second);
	}
}
