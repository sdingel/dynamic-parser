package de.steffendingel.dynamicparser;

import static de.steffendingel.dynamicparser.ParserTesting.parse;
import static de.steffendingel.dynamicparser.ParserTesting.rule;
import static de.steffendingel.dynamicparser.ParserTesting.token;
import static de.steffendingel.dynamicparser.ParserTesting.tokens;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests the ambiguities reported by the parser.
 */
class ParserAmbiguityTest {

	private final Nonterminal s = new Nonterminal("S");

	private final Nonterminal a = new Nonterminal("A");

	private final Nonterminal b = new Nonterminal("B");

	private final Terminal x = token("x");

	private final Terminal y = token("y");

	private final Terminal z = token("z");

	private AmbiguityException ambiguity(String input, Rule... rules) {
		return assertThrows(AmbiguityException.class, () -> parse(input, s, rules));
	}

	private static void assertSection(Nonterminal symbol, int start, int end, AmbiguityException exception) {
		assertSame(symbol, exception.symbol());
		assertEquals(start, exception.start());
		assertEquals(end, exception.end());
	}

	@Test
	void reportsTwoRulesMatchingTheSameSection() {
		AmbiguityException exception = ambiguity("y x z", rule(s, y, a, z), rule(a, x), rule(a, x));

		assertSection(a, 1, 2, exception);
	}

	@Test
	void reportsTwoDifferentRulesMatchingTheSameSection() {
		AmbiguityException exception = ambiguity("x y", rule(s, a), rule(a, x, y), rule(a, x, b), rule(b, y));

		assertSection(a, 0, 2, exception);
	}

	@Test
	void reportsTheSameRuleMatchingWithDifferentSplits() {
		// x + x + x can be read as (x + x) + x and as x + (x + x).
		Terminal plus = token("+");

		AmbiguityException exception = ambiguity("x + x + x", rule(s, s, plus, s), rule(s, x));

		assertSection(s, 0, 5, exception);
	}

	@Test
	void doesNotReportUnambiguousInputOfAnAmbiguousGrammar() throws ParseException {
		Terminal plus = token("+");

		assertEquals("S(S(x) + S(x))", parse("x + x", s, rule(s, s, plus, s), rule(s, x)));
	}

	@Test
	void reportsAmbiguityCausedByNullableSymbols() {
		// The token can be matched by the first or by the second A; the other one is empty.
		AmbiguityException exception = ambiguity("x", rule(s, a, a), rule(a), rule(a, x));

		assertSection(s, 0, 1, exception);
	}

	@Test
	void reportsAmbiguousEmptyMatch() {
		AmbiguityException exception = ambiguity("x", rule(s, x, a), rule(a), rule(a, b), rule(b));

		assertSection(a, 1, 1, exception);
	}

	@Test
	void reportsCyclicRule() {
		// A -> A allows any number of nested matches of A.
		AmbiguityException exception = ambiguity("y x", rule(s, y, a), rule(a, a), rule(a, x));

		assertSection(a, 1, 2, exception);
	}

	@Test
	void reportsCycleThroughSeveralRules() {
		AmbiguityException exception = ambiguity("x", rule(s, a), rule(a, b), rule(b, a), rule(a, x));

		assertSection(a, 0, 1, exception);
	}

	@Test
	void reportsCycleThroughNullableSymbols() {
		AmbiguityException exception = ambiguity("x", rule(s, a), rule(a, b, a), rule(a, x), rule(b));

		assertSection(a, 0, 1, exception);
	}

	@Test
	void ambiguityInADiscardedAlternativeIsNotAnError() throws ParseException {
		// A matches "x" in two ways, but the following token shows that the result uses B.
		Rule[] rules = { rule(s, a, y), rule(s, b, z), rule(a, x), rule(a, x), rule(b, x) };

		assertEquals("S(B(x) z)", parse("x z", s, rules));
		assertSection(a, 0, 1, ambiguity("x y", rules));
	}

	@Test
	void reportsTheOutermostAmbiguousSection() {
		// S is ambiguous, and so is the A within one of its alternatives.
		AmbiguityException exception = ambiguity("x", rule(s, a), rule(s, x), rule(a, x), rule(a, x));

		assertSection(s, 0, 1, exception);
	}

	@Test
	void reportsTheFirstAmbiguousSectionInTheOrderOfTheActions() {
		Rule[] rules = { rule(s, a, b), rule(a, x), rule(a, x), rule(b, y), rule(b, y) };

		assertSection(a, 0, 1, ambiguity("x y", rules));
	}

	@Test
	void callsTheActionsBeforeTheAmbiguousSectionOnly() {
		List<String> calls = new ArrayList<>();
		Action record = match -> calls.add(match.rule().lhs().name());
		Grammar grammar = new Grammar(s, new Rule(s, List.of(a, b, a), record), new Rule(a, List.of(x), record),
				new Rule(b, List.of(y), record), new Rule(b, List.of(y), record));

		assertThrows(AmbiguityException.class, () -> Parser.create(grammar).parse(tokens("x y x")));

		assertEquals(List.of("A"), calls);
	}

	@Test
	void reportsAmbiguityOfTheStartSymbolForEmptyInput() {
		AmbiguityException exception = ambiguity("", rule(s), rule(s, a), rule(a));

		assertSection(s, 0, 0, exception);
	}

	@Test
	void unusedTokenInTheGrammarDoesNotMatter() throws ParseException {
		assertEquals("S(x)", parse("x", s, rule(s, x), rule(a, z), rule(a, z)));
	}
}
