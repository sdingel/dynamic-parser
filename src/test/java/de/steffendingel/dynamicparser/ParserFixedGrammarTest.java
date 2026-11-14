package de.steffendingel.dynamicparser;

import static de.steffendingel.dynamicparser.ParserTesting.parse;
import static de.steffendingel.dynamicparser.ParserTesting.rule;
import static de.steffendingel.dynamicparser.ParserTesting.token;
import static de.steffendingel.dynamicparser.ParserTesting.tokens;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests parsing with grammars that do not change: which inputs are matched and with which structure.
 */
class ParserFixedGrammarTest {

	private final Nonterminal s = new Nonterminal("S");

	private final Nonterminal a = new Nonterminal("A");

	private final Nonterminal b = new Nonterminal("B");

	private final Terminal x = token("x");

	private final Terminal y = token("y");

	private final Terminal z = token("z");

	@Test
	void parsesEmptyInputWithEmptyRule() throws ParseException {
		assertEquals("S()", parse("", s, rule(s)));
	}

	@Test
	void parsesSingleToken() throws ParseException {
		assertEquals("S(x)", parse("x", s, rule(s, x)));
	}

	@Test
	void parsesSequenceOfTokens() throws ParseException {
		assertEquals("S(x y z)", parse("x y z", s, rule(s, x, y, z)));
	}

	@Test
	void parsesNestedRules() throws ParseException {
		Rule[] rules = { rule(s, x, a, z), rule(a, b, b), rule(b, y) };

		assertEquals("S(x A(B(y) B(y)) z)", parse("x y y z", s, rules));
	}

	@Test
	void choosesTheRuleThatMatches() throws ParseException {
		Rule[] rules = { rule(s, x, y), rule(s, x, z), rule(s, a), rule(a, y) };

		assertEquals("S(x y)", parse("x y", s, rules));
		assertEquals("S(x z)", parse("x z", s, rules));
		assertEquals("S(A(y))", parse("y", s, rules));
	}

	@Test
	void parsesLeftRecursion() throws ParseException {
		Rule[] rules = { rule(s, s, x), rule(s, y) };

		assertEquals("S(y)", parse("y", s, rules));
		assertEquals("S(S(S(y) x) x)", parse("y x x", s, rules));
	}

	@Test
	void parsesRightRecursion() throws ParseException {
		Rule[] rules = { rule(s, x, s), rule(s, y) };

		assertEquals("S(y)", parse("y", s, rules));
		assertEquals("S(x S(x S(y)))", parse("x x y", s, rules));
	}

	@Test
	void parsesRecursionInTheMiddle() throws ParseException {
		Rule[] rules = { rule(s, x, s, z), rule(s, y) };

		assertEquals("S(x S(x S(y) z) z)", parse("x x y z z", s, rules));
	}

	@Test
	void parsesLeftRecursiveListWithEmptyStart() throws ParseException {
		Rule[] rules = { rule(s), rule(s, s, x) };

		assertEquals("S()", parse("", s, rules));
		assertEquals("S(S(S() x) x)", parse("x x", s, rules));
	}

	@Test
	void parsesNullableSymbolInTheMiddleOfARule() throws ParseException {
		Rule[] rules = { rule(s, x, a, z), rule(a), rule(a, y) };

		assertEquals("S(x A() z)", parse("x z", s, rules));
		assertEquals("S(x A(y) z)", parse("x y z", s, rules));
	}

	@Test
	void parsesNullableSymbolsAtTheStartAndTheEndOfARule() throws ParseException {
		Rule[] rules = { rule(s, a, x, b), rule(a), rule(a, y), rule(b), rule(b, z) };

		assertEquals("S(A() x B())", parse("x", s, rules));
		assertEquals("S(A(y) x B())", parse("y x", s, rules));
		assertEquals("S(A() x B(z))", parse("x z", s, rules));
		assertEquals("S(A(y) x B(z))", parse("y x z", s, rules));
	}

	@Test
	void parsesRuleConsistingOfNullableSymbolsOnly() throws ParseException {
		Rule[] rules = { rule(s, a, b), rule(a), rule(a, x), rule(b), rule(b, y) };

		assertEquals("S(A() B())", parse("", s, rules));
		assertEquals("S(A(x) B())", parse("x", s, rules));
		assertEquals("S(A() B(y))", parse("y", s, rules));
		assertEquals("S(A(x) B(y))", parse("x y", s, rules));
	}

	@Test
	void parsesNullableSymbolDefinedThroughAnotherNullableSymbol() throws ParseException {
		// The empty match of A is completed after the items waiting for A have been processed.
		Rule[] rules = { rule(s, a, a, x), rule(a, b), rule(b) };

		assertEquals("S(A(B()) A(B()) x)", parse("x", s, rules));
	}

	@Test
	void parsesLeftRecursionHiddenBehindANullableSymbol() throws ParseException {
		Rule[] rules = { rule(s, a, s, x), rule(s, y), rule(a) };

		assertEquals("S(A() S(A() S(y) x) x)", parse("y x x", s, rules));
	}

	@Test
	void temporarilyOpenAlternativeIsNotAnAmbiguity() throws ParseException {
		Nonterminal decl = new Nonterminal("Decl");
		Nonterminal type = new Nonterminal("Type");
		Terminal integer = token("Integer");
		Rule[] rules = { rule(decl, token("var"), x, token(":"), type, token(";")), rule(type, integer),
				rule(type, integer, token("["), token("]")) };

		assertEquals("Decl(var x : Type(Integer) ;)", parse("var x : Integer ;", decl, rules));
		assertEquals("Decl(var x : Type(Integer [ ]) ;)", parse("var x : Integer [ ] ;", decl, rules));
	}

	@Test
	void parsesExpressionsWithPrecedenceExpressedInTheRules() throws ParseException {
		Nonterminal sum = new Nonterminal("Sum");
		Nonterminal product = new Nonterminal("Product");
		Nonterminal atom = new Nonterminal("Atom");
		Terminal name = new Terminal("name", token -> ((String) token).chars().allMatch(Character::isLetter));
		Rule[] rules = { rule(sum, sum, token("+"), product), rule(sum, product),
				rule(product, product, token("*"), atom), rule(product, atom), rule(atom, name),
				rule(atom, token("("), sum, token(")")) };

		assertEquals("Sum(Sum(Product(Atom(a))) + Product(Product(Atom(b)) * Atom(c)))",
				parse("a + b * c", sum, rules));
		assertEquals("Sum(Product(Product(Atom(( Sum(Sum(Product(Atom(a))) + Product(Atom(b))) ))) * Atom(c)))",
				parse("( a + b ) * c", sum, rules));
	}

	@Test
	void terminalsCanMatchSeveralTokensAndTokensSeveralTerminals() throws ParseException {
		Terminal any = new Terminal("any", token -> true);
		Rule[] rules = { rule(s, x, any, x) };

		assertEquals("S(x y x)", parse("x y x", s, rules));
		assertEquals("S(x x x)", parse("x x x", s, rules));
	}

	@Test
	void tokensCanBeArbitraryObjects() throws ParseException {
		Terminal number = new Terminal("number", token -> token instanceof Integer);
		Terminal text = new Terminal("text", token -> token instanceof String);
		Rule pair = new Rule(s, List.of(number, text));
		Parser parser = Parser.create(new Grammar(s, pair));

		assertEquals(new ParseNode(pair, 0, 2, List.of(42, "answer")), parser.parse(List.of(42, "answer")));
	}

	@Test
	void rulesWithoutActionReturnTheCompleteParseTree() throws ParseException {
		Rule outer = new Rule(s, List.of(x, a, z));
		Rule empty = new Rule(a, List.of());
		Rule inner = new Rule(a, List.of(y, a));
		Parser parser = Parser.create(new Grammar(s, outer, empty, inner));

		Object result = parser.parse(tokens("x y z"));

		ParseNode emptyNode = new ParseNode(empty, 2, 2, List.of());
		ParseNode innerNode = new ParseNode(inner, 1, 2, List.of("y", emptyNode));
		assertEquals(new ParseNode(outer, 0, 3, List.of("x", innerNode, "z")), result);
	}

	@Test
	void parsesLongListsWithoutOverflowingTheCallStack() throws ParseException {
		Rule empty = new Rule(s, List.of(), match -> 0);
		Rule more = new Rule(s, List.of(s, x), match -> match.value(0, Integer.class) + 1);
		Parser parser = Parser.create(new Grammar(s, empty, more));

		assertEquals(20_000, parser.parse(Collections.nCopies(20_000, "x")));
	}

	@Test
	void twoParseRunsWithTheSameParserAreIndependent() throws ParseException {
		Parser parser = Parser.create(new Grammar(s, rule(s, s, x), rule(s, y)));

		assertEquals("S(S(y) x)", parser.parse(tokens("y x")));
		assertEquals("S(y)", parser.parse(tokens("y")));
		assertEquals("S(S(y) x)", parser.parse(tokens("y x")));
	}

	@Test
	void theSameGrammarCanBeUsedByTwoParsers() throws ParseException {
		Grammar grammar = new Grammar(s, rule(s, x));

		assertEquals("S(x)", Parser.create(grammar).parse(tokens("x")));
		assertEquals("S(x)", Parser.create(grammar).parse(tokens("x")));
	}

	@Test
	void rejectsNullArguments() {
		Parser parser = Parser.create(new Grammar(s, rule(s, x)));

		assertThrows(NullPointerException.class, () -> Parser.create(null));
		assertThrows(NullPointerException.class, () -> parser.parse(null));
		assertThrows(NullPointerException.class, () -> parser.parse(Arrays.asList("x", null)));
	}
}
