package de.steffendingel.dynamicparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class ParseNodeTest {

	private final Nonterminal expr = new Nonterminal("Expr");

	private final Terminal plus = new Terminal("'+'", "+"::equals);

	private final Rule sum = new Rule(expr, List.of(expr, plus, expr));

	private final Rule empty = new Rule(expr, List.of());

	@Test
	void hasRulePositionsAndChildren() {
		ParseNode node = new ParseNode(sum, 1, 4, List.of("a", "+", "b"));

		assertSame(sum, node.rule());
		assertEquals(1, node.start());
		assertEquals(4, node.end());
		assertEquals(List.of("a", "+", "b"), node.children());
	}

	@Test
	void isCreatedFromMatch() {
		Match match = new TestMatch(sum, 1, 4, List.of("a", "+", "b"));

		assertEquals(new ParseNode(sum, 1, 4, List.of("a", "+", "b")), ParseNode.of(match));
	}

	@Test
	void allowsEmptyMatch() {
		ParseNode node = new ParseNode(empty, 3, 3, List.of());

		assertEquals(3, node.start());
		assertEquals(3, node.end());
	}

	@Test
	void allowsNullChildren() {
		ParseNode node = new ParseNode(sum, 0, 3, Arrays.asList(null, "+", null));

		assertEquals(Arrays.asList(null, "+", null), node.children());
	}

	@Test
	void rejectsNullArguments() {
		assertThrows(NullPointerException.class, () -> new ParseNode(null, 0, 0, List.of()));
		assertThrows(NullPointerException.class, () -> new ParseNode(empty, 0, 0, null));
		assertThrows(NullPointerException.class, () -> ParseNode.of(null));
	}

	@Test
	void rejectsInvalidPositions() {
		assertThrows(IllegalArgumentException.class, () -> new ParseNode(empty, -1, 0, List.of()));
		assertThrows(IllegalArgumentException.class, () -> new ParseNode(empty, 2, 1, List.of()));
	}

	@Test
	void rejectsWrongNumberOfChildren() {
		assertThrows(IllegalArgumentException.class, () -> new ParseNode(sum, 0, 2, List.of("a", "+")));
		assertThrows(IllegalArgumentException.class, () -> new ParseNode(empty, 0, 1, List.of("a")));
	}

	@Test
	void childrenAreIndependentOfThePassedList() {
		List<Object> children = new ArrayList<>(List.of("a", "+", "b"));
		ParseNode node = new ParseNode(sum, 0, 3, children);

		children.set(0, "changed");

		assertEquals(List.of("a", "+", "b"), node.children());
	}

	@Test
	void childrenAreUnmodifiable() {
		ParseNode node = new ParseNode(sum, 0, 3, new ArrayList<>(List.of("a", "+", "b")));

		assertThrows(UnsupportedOperationException.class, () -> node.children().set(0, "changed"));
	}

	@Test
	void nodesWithEqualContentAreEqual() {
		ParseNode node = new ParseNode(sum, 0, 3, List.of("a", "+", "b"));

		assertEquals(new ParseNode(sum, 0, 3, List.of("a", "+", "b")), node);
		assertNotEquals(new ParseNode(sum, 1, 4, List.of("a", "+", "b")), node);
		assertNotEquals(new ParseNode(sum, 0, 3, List.of("a", "+", "c")), node);
		assertNotEquals(new ParseNode(new Rule(expr, List.of(expr, plus, expr)), 0, 3, List.of("a", "+", "b")), node);
	}
}
