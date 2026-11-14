package de.steffendingel.dynamicparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

class GrammarTest {

	private final Nonterminal start = new Nonterminal("Start");

	private final Terminal a = new Terminal("a", "a"::equals);

	private final Rule first = new Rule(start, List.of(a));

	private final Rule second = new Rule(start, List.of(a, a));

	@Test
	void hasStartSymbolAndRules() {
		Grammar grammar = new Grammar(start, List.of(first, second));

		assertSame(start, grammar.start());
		assertEquals(List.of(first, second), List.copyOf(grammar.rules()));
	}

	@Test
	void canBeCreatedFromIndividualRules() {
		Grammar grammar = new Grammar(start, second, first);

		assertEquals(List.of(second, first), List.copyOf(grammar.rules()));
	}

	@Test
	void allowsGrammarWithoutRules() {
		assertTrue(new Grammar(start).rules().isEmpty());
		assertTrue(new Grammar(start, List.of()).rules().isEmpty());
	}

	@Test
	void acceptsRulesWithEqualContent() {
		Rule sameContent = new Rule(start, List.of(a), first.action());

		Grammar grammar = new Grammar(start, first, sameContent);

		assertEquals(2, grammar.rules().size());
	}

	@Test
	void rejectsNullArguments() {
		assertThrows(NullPointerException.class, () -> new Grammar(null, List.of(first)));
		assertThrows(NullPointerException.class, () -> new Grammar(start, (Collection<Rule>) null));
		assertThrows(NullPointerException.class, () -> new Grammar(start, (Rule[]) null));
		assertThrows(NullPointerException.class, () -> new Grammar(start, Arrays.asList(first, null)));
		assertThrows(NullPointerException.class, () -> new Grammar(start, first, null));
	}

	@Test
	void rejectsTheSameRuleTwice() {
		assertThrows(IllegalArgumentException.class, () -> new Grammar(start, first, second, first));
	}

	@Test
	void rulesAreIndependentOfThePassedCollection() {
		List<Rule> rules = new ArrayList<>(List.of(first));
		Grammar grammar = new Grammar(start, rules);

		rules.add(second);

		assertEquals(List.of(first), List.copyOf(grammar.rules()));
	}

	@Test
	void rulesAreUnmodifiable() {
		Grammar grammar = new Grammar(start, first);

		assertThrows(UnsupportedOperationException.class, () -> grammar.rules().add(second));
		assertThrows(UnsupportedOperationException.class, () -> grammar.rules().remove(first));
	}
}
