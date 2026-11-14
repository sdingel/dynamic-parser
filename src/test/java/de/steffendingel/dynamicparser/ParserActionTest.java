package de.steffendingel.dynamicparser;

import static de.steffendingel.dynamicparser.ParserTesting.token;
import static de.steffendingel.dynamicparser.ParserTesting.tokens;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * Tests how the parser calls actions: order, number of calls, the match passed to them and their values.
 */
class ParserActionTest {

	private final Nonterminal s = new Nonterminal("S");

	private final Nonterminal a = new Nonterminal("A");

	private final Nonterminal b = new Nonterminal("B");

	private final Terminal x = token("x");

	private final Terminal y = token("y");

	private final Terminal z = token("z");

	private final List<String> calls = new ArrayList<>();

	/**
	 * Creates a rule whose action records the call under the given name and returns the name.
	 */
	private Rule recording(String name, Nonterminal lhs, Symbol... rhs) {
		return new Rule(lhs, List.of(rhs), match -> {
			calls.add(name);
			return name;
		});
	}

	@Test
	void callsActionsBottomUpAndFromLeftToRight() throws ParseException {
		Grammar grammar = new Grammar(s, recording("S", s, a, b), recording("A", a, b, x), recording("B", b, y),
				recording("B-empty", b));

		Parser.create(grammar).parse(tokens("y x"));

		assertEquals(List.of("B", "A", "B-empty", "S"), calls);
	}

	@Test
	void callsActionOnceForEveryMatchInTheResult() throws ParseException {
		Grammar grammar = new Grammar(s, recording("more", s, s, x), recording("first", s, x));

		Parser.create(grammar).parse(tokens("x x x x"));

		assertEquals(List.of("first", "more", "more", "more"), calls);
	}

	@Test
	void doesNotCallActionsOfDiscardedAlternatives() throws ParseException {
		// Both A and B match "x"; only the following token decides which of them is part of the result.
		Grammar grammar = new Grammar(s, recording("S-A", s, a, y), recording("S-B", s, b, z), recording("A", a, x),
				recording("B", b, x));

		Parser.create(grammar).parse(tokens("x z"));

		assertEquals(List.of("B", "S-B"), calls);
	}

	@Test
	void callsOnlyTheActionsOfConfirmedMatchesIfTheInputHasASyntaxError() {
		Grammar grammar = new Grammar(s, recording("S", s, a, y), recording("A", a, x));

		assertThrows(SyntaxException.class, () -> Parser.create(grammar).parse(tokens("x z")));

		// A is part of every reading after "x"; S is never completed.
		assertEquals(List.of("A"), calls);
	}

	@Test
	void returnsTheValueOfTheStartSymbol() throws ParseException {
		Object value = new Object();
		Grammar grammar = new Grammar(s, new Rule(s, List.of(x), match -> value));

		assertSame(value, Parser.create(grammar).parse(tokens("x")));
	}

	@Test
	void passesTokensAndValuesOfNonterminalsToTheAction() throws ParseException {
		Rule sum = new Rule(s, List.of(a, x, a),
				match -> match.value(0, Integer.class) + match.value(2, Integer.class));
		Rule one = new Rule(a, List.of(y), match -> 1);
		Rule two = new Rule(a, List.of(z), match -> match.value(0, String.class).length() + 1);

		assertEquals(3, Parser.create(new Grammar(s, sum, one, two)).parse(tokens("y x z")));
	}

	@Test
	void actionsCanReturnNull() throws ParseException {
		AtomicReference<List<Object>> seen = new AtomicReference<>();
		Rule outer = new Rule(s, List.of(a, x), match -> {
			seen.set(new ArrayList<>(match.values()));
			return null;
		});
		Rule inner = new Rule(a, List.of(), match -> null);

		assertNull(Parser.create(new Grammar(s, outer, inner)).parse(tokens("x")));

		assertEquals(2, seen.get().size());
		assertNull(seen.get().get(0));
		assertEquals("x", seen.get().get(1));
	}

	@Test
	void matchDescribesRuleAndPositions() throws ParseException {
		List<Match> matches = new ArrayList<>();
		Action action = match -> {
			matches.add(match);
			return null;
		};
		Rule outer = new Rule(s, List.of(x, a, b, z), action);
		Rule inner = new Rule(a, List.of(y, y), action);
		Rule empty = new Rule(b, List.of(), action);

		Parser.create(new Grammar(s, outer, inner, empty)).parse(tokens("x y y z"));

		assertEquals(3, matches.size());
		assertSame(inner, matches.get(0).rule());
		assertEquals(1, matches.get(0).start());
		assertEquals(3, matches.get(0).end());
		assertSame(empty, matches.get(1).rule());
		assertEquals(3, matches.get(1).start());
		assertEquals(3, matches.get(1).end());
		assertEquals(0, matches.get(1).size());
		assertSame(outer, matches.get(2).rule());
		assertEquals(0, matches.get(2).start());
		assertEquals(4, matches.get(2).end());
		assertEquals(4, matches.get(2).size());
	}

	@Test
	void valuesOfAMatchAreUnmodifiable() throws ParseException {
		Rule rule = new Rule(s, List.of(x), match -> {
			assertThrows(UnsupportedOperationException.class, () -> match.values().set(0, "changed"));
			return null;
		});

		Parser.create(new Grammar(s, rule)).parse(tokens("x"));
	}

	@Test
	void exceptionOfAnActionIsPassedOnUnchanged() {
		IllegalStateException exception = new IllegalStateException("failure in action");
		Grammar grammar = new Grammar(s, recording("S", s, a, x), new Rule(a, List.of(y), match -> {
			throw exception;
		}));

		assertSame(exception,
				assertThrows(IllegalStateException.class, () -> Parser.create(grammar).parse(tokens("y x"))));
		assertEquals(List.of(), calls);
	}

	@Test
	void exceptionOfATerminalIsPassedOnUnchanged() {
		IllegalStateException exception = new IllegalStateException("failure in terminal");
		Terminal failing = new Terminal("failing", token -> {
			throw exception;
		});
		Grammar grammar = new Grammar(s, new Rule(s, List.of(failing)));

		assertSame(exception,
				assertThrows(IllegalStateException.class, () -> Parser.create(grammar).parse(tokens("x"))));
	}

	@Test
	void predicateOfATerminalIsCalledOncePerToken() throws ParseException {
		List<Object> tested = new ArrayList<>();
		Terminal counting = new Terminal("counting", token -> {
			tested.add(token);
			return true;
		});
		// The terminal is the next symbol of two rules at the same position.
		Grammar grammar = new Grammar(s, new Rule(s, List.of(counting, x)), new Rule(s, List.of(counting, y)));

		Parser.create(grammar).parse(tokens("z y"));

		assertEquals(List.of("z"), tested);
	}

	@Test
	void grammarEditorReportsTheRulesOfTheGrammar() throws ParseException {
		Rule other = new Rule(a, List.of(y));
		AtomicReference<Rule> self = new AtomicReference<>();
		Rule rule = new Rule(s, List.of(x), match -> {
			assertTrue(match.grammar().contains(self.get()));
			assertFalse(match.grammar().contains(other));
			assertThrows(NullPointerException.class, () -> match.grammar().contains(null));
			return null;
		});
		self.set(rule);

		Parser.create(new Grammar(s, rule)).parse(tokens("x"));
	}

	@Test
	void grammarEditorDoesNotSupportChangesYet() throws ParseException {
		Rule other = new Rule(a, List.of(y));
		Rule rule = new Rule(s, List.of(x), match -> {
			assertThrows(UnsupportedOperationException.class, () -> match.grammar().add(other));
			assertThrows(UnsupportedOperationException.class, () -> match.grammar().remove(other));
			assertThrows(NullPointerException.class, () -> match.grammar().add(null));
			assertThrows(NullPointerException.class, () -> match.grammar().remove(null));
			return null;
		});

		Parser.create(new Grammar(s, rule)).parse(tokens("x"));
	}

	@Test
	void grammarEditorCannotBeUsedAfterTheActionHasReturned() throws ParseException {
		AtomicReference<GrammarEditor> editor = new AtomicReference<>();
		Rule rule = new Rule(s, List.of(x), match -> {
			editor.set(match.grammar());
			return null;
		});

		Parser.create(new Grammar(s, rule)).parse(tokens("x"));

		assertThrows(IllegalStateException.class, () -> editor.get().contains(rule));
		assertThrows(IllegalStateException.class, () -> editor.get().add(rule));
		assertThrows(IllegalStateException.class, () -> editor.get().remove(rule));
	}
}
