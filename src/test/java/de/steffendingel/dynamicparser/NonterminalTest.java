package de.steffendingel.dynamicparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NonterminalTest {

	@Test
	void hasName() {
		Nonterminal nonterminal = new Nonterminal("Expr");

		assertEquals("Expr", nonterminal.name());
		assertEquals("Expr", nonterminal.toString());
	}

	@Test
	void rejectsNullName() {
		assertThrows(NullPointerException.class, () -> new Nonterminal(null));
	}

	@Test
	void isIdentifiedByIdentityNotByName() {
		Nonterminal first = new Nonterminal("Expr");
		Nonterminal second = new Nonterminal("Expr");

		assertEquals(first, first);
		assertNotEquals(first, second);
	}
}
