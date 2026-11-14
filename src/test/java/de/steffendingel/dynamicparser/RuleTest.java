package de.steffendingel.dynamicparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class RuleTest {

	private final Nonterminal expr = new Nonterminal("Expr");

	private final Terminal plus = new Terminal("'+'", "+"::equals);

	@Test
	void hasLeftHandSideRightHandSideAndAction() {
		Action action = match -> "value";
		Rule rule = new Rule(expr, List.of(expr, plus, expr), action);

		assertSame(expr, rule.lhs());
		assertEquals(List.of(expr, plus, expr), rule.rhs());
		assertSame(action, rule.action());
	}

	@Test
	void allowsEmptyRightHandSide() {
		Rule rule = new Rule(expr, List.of());

		assertEquals(List.of(), rule.rhs());
	}

	@Test
	void rejectsNullArguments() {
		Action action = match -> null;

		assertThrows(NullPointerException.class, () -> new Rule(null, List.of(), action));
		assertThrows(NullPointerException.class, () -> new Rule(expr, null, action));
		assertThrows(NullPointerException.class, () -> new Rule(expr, List.of(), null));
		assertThrows(NullPointerException.class, () -> new Rule(expr, Arrays.asList(plus, null), action));
		assertThrows(NullPointerException.class, () -> new Rule(null, List.of()));
		assertThrows(NullPointerException.class, () -> new Rule(expr, null));
	}

	@Test
	void rightHandSideIsIndependentOfThePassedList() {
		List<Symbol> symbols = new ArrayList<>(List.of(expr, plus));
		Rule rule = new Rule(expr, symbols);

		symbols.add(expr);

		assertEquals(List.of(expr, plus), rule.rhs());
	}

	@Test
	void rightHandSideIsUnmodifiable() {
		Rule rule = new Rule(expr, new ArrayList<>(List.of(plus)));

		assertThrows(UnsupportedOperationException.class, () -> rule.rhs().add(expr));
	}

	@Test
	void ruleWithoutActionCreatesParseNode() {
		Rule rule = new Rule(expr, List.of(expr, plus, expr));
		Match match = new TestMatch(rule, 2, 5, List.of("left", "+", "right"));

		Object value = rule.action().apply(match);

		assertEquals(new ParseNode(rule, 2, 5, List.of("left", "+", "right")), value);
	}

	@Test
	void isIdentifiedByIdentityNotByContent() {
		Action action = match -> null;
		Rule first = new Rule(expr, List.of(plus), action);
		Rule second = new Rule(expr, List.of(plus), action);

		assertEquals(first, first);
		assertNotEquals(first, second);
	}

	@Test
	void describesItselfWithSymbolNames() {
		assertEquals("Expr -> Expr '+' Expr", new Rule(expr, List.of(expr, plus, expr)).toString());
		assertEquals("Expr -> ε", new Rule(expr, List.of()).toString());
	}
}
