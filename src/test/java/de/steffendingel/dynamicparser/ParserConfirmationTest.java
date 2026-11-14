package de.steffendingel.dynamicparser;

import static de.steffendingel.dynamicparser.ParserTesting.token;
import static de.steffendingel.dynamicparser.ParserTesting.tokens;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests when the parser calls actions: as soon as a match is part of every reading of the input read so far and all
 * matches before it are as well.
 * <p>
 * The tests record every call of an action together with the number of tokens the parser has requested at that time,
 * for example {@code Statement@3} for a call after the third token and before the fourth one is requested.
 */
class ParserConfirmationTest {

	private final List<String> calls = new ArrayList<>();

	/** The number of tokens the parser has requested so far. */
	private int requested;

	/**
	 * Returns the input as tokens that count how many of them the parser has requested.
	 */
	private Iterable<String> counting(String input) {
		List<String> list = tokens(input);
		return () -> new Iterator<>() {

			private final Iterator<String> iterator = list.iterator();

			@Override
			public boolean hasNext() {
				return iterator.hasNext();
			}

			@Override
			public String next() {
				requested++;
				return iterator.next();
			}
		};
	}

	/**
	 * Creates a rule whose action records the call under the given name.
	 */
	private Rule rule(String name, Nonterminal lhs, Symbol... rhs) {
		return new Rule(lhs, List.of(rhs), match -> {
			calls.add(name + "@" + requested);
			return name;
		});
	}

	private List<String> parse(String input, Nonterminal start, Rule... rules) throws ParseException {
		calls.clear();
		requested = 0;
		Parser.create(new Grammar(start, rules)).parse(counting(input));
		return List.copyOf(calls);
	}

	private final Terminal x = token("x");

	private final Terminal semicolon = token(";");

	private final Terminal name = new Terminal("name", token -> ((String) token).matches("[a-z]"));

	// Statements and lists

	private final Nonterminal program = new Nonterminal("Program");

	private final Nonterminal statement = new Nonterminal("Statement");

	private Rule[] declarations() {
		return new Rule[] { rule("Empty", program), rule("List", program, program, statement),
				rule("Statement", statement, token("var"), name, semicolon) };
	}

	@Test
	void statementEndingInATerminatorIsConfirmedAtItsEnd() throws ParseException {
		assertEquals(List.of("Empty@0", "Statement@3", "List@3", "Statement@6", "List@6"),
				parse("var a ; var b ;", program, declarations()));
	}

	@Test
	void emptyMatchAtTheBeginningIsConfirmedBeforeTheFirstTokenIsRead() throws ParseException {
		assertEquals(List.of("Empty@0"), parse("", program, declarations()));
	}

	@Test
	void actionsOfConfirmedMatchesHaveBeenCalledWhenASyntaxErrorIsFound() {
		SyntaxException exception = assertThrows(SyntaxException.class,
				() -> parse("var a ; var ;", program, declarations()));

		assertEquals(4, exception.position());
		assertEquals(List.of("Empty@0", "Statement@3", "List@3"), calls);
	}

	@Test
	void doesNotReadTheTokenAfterASyntaxError() {
		assertThrows(SyntaxException.class, () -> parse("var ; a ; var b ;", program, declarations()));

		assertEquals(2, requested);
	}

	@Test
	void statementsOfARightRecursiveListAreConfirmedAtTheirEnd() throws ParseException {
		// Whether the list ends is only known at the end of the input, so the list itself is confirmed last.
		Rule[] rules = { rule("Empty", program), rule("List", program, statement, program),
				rule("Statement", statement, x, semicolon) };

		assertEquals(List.of("Statement@2", "Statement@4", "Empty@4", "List@4", "List@4"),
				parse("x ; x ;", program, rules));
	}

	// Expressions

	private final Nonterminal sum = new Nonterminal("Sum");

	private final Nonterminal product = new Nonterminal("Product");

	private final Nonterminal atom = new Nonterminal("Atom");

	private Rule[] expressions() {
		return new Rule[] { rule("Statement", statement, sum, semicolon), rule("Add", sum, sum, token("+"), product),
				rule("Sum", sum, product), rule("Multiply", product, product, token("*"), atom),
				rule("Product", product, atom), rule("Atom", atom, name) };
	}

	@Test
	void sumIsConfirmedWhenTheNextTokenRulesOutAProduct() throws ParseException {
		// After "a + b" the input could continue with "* c", so Add(a + b) is confirmed only by the ";". The left
		// operand is confirmed by the "+": no "*" can follow it any more.
		assertEquals(List.of("Atom@1", "Product@1", "Sum@2", "Atom@3", "Product@3", "Add@4", "Statement@4"),
				parse("a + b ;", statement, expressions()));
	}

	@Test
	void operandsOfAProductAreConfirmedWhileTheSumIsStillOpen() throws ParseException {
		assertEquals(List.of("Atom@1", "Product@1", "Sum@2", "Atom@3", "Product@3", "Atom@5", "Multiply@5", "Add@6",
				"Statement@6"), parse("a + b * c ;", statement, expressions()));
	}

	// Alternatives that stay open for a while

	@Test
	void matchThatCouldBeLongerIsConfirmedWhenTheEnclosingMatchEnds() throws ParseException {
		Nonterminal declaration = new Nonterminal("Declaration");
		Nonterminal type = new Nonterminal("Type");
		Terminal integer = token("Integer");
		Rule[] rules = { rule("Declaration", declaration, token("var"), name, token(":"), type, semicolon),
				rule("Simple", type, integer), rule("Array", type, integer, token("["), token("]")) };

		// After "Integer" the type could continue with "[", so nothing is confirmed.
		assertEquals(List.of("Simple@5", "Declaration@5"), parse("var a : Integer ;", declaration, rules));
		// The "]" can only end the array type.
		assertEquals(List.of("Array@6", "Declaration@7"), parse("var a : Integer [ ] ;", declaration, rules));
	}

	@Test
	void confirmedMatchWaitsForTheMatchesBeforeIt() throws ParseException {
		// C is part of every reading after "c", but whether A or B comes before it is only known after "d".
		Nonterminal s = new Nonterminal("S");
		Nonterminal a = new Nonterminal("A");
		Nonterminal b = new Nonterminal("B");
		Nonterminal c = new Nonterminal("C");
		Rule[] rules = { rule("S-A", s, a, c, token("d")), rule("S-B", s, b, c, token("e")), rule("A", a, x),
				rule("B", b, x), rule("C", c, token("c")) };

		assertEquals(List.of("A@3", "C@3", "S-A@3"), parse("x c d", s, rules));
		assertEquals(List.of("B@3", "C@3", "S-B@3"), parse("x c e", s, rules));
	}

	@Test
	void innerStatementOfADanglingElseIsConfirmedBeforeTheEnclosingOne() throws ParseException {
		Rule[] rules = { rule("If", statement, token("if"), statement),
				rule("IfElse", statement, token("if"), statement, token("else"), statement),
				rule("Simple", statement, x) };

		// After "if x" an "else" could follow, so only the inner statement is confirmed.
		assertEquals(List.of("Simple@2", "If@2"), parse("if x", statement, rules));
		assertEquals(List.of("Simple@2", "Simple@4", "IfElse@4"), parse("if x else x", statement, rules));
	}

	@Test
	void readingStuckAtANonterminalWithoutRulesDoesNotPreventConfirmation() throws ParseException {
		Nonterminal s = new Nonterminal("S");
		Nonterminal a = new Nonterminal("A");
		Nonterminal b = new Nonterminal("B");
		Nonterminal missing = new Nonterminal("Missing");
		Rule[] rules = { rule("S-A", s, a, token("z")), rule("S-B", s, b, missing), rule("A", a, x), rule("B", b, x) };

		// The reading with B cannot take any token, because Missing has no rules.
		assertEquals(List.of("A@1", "S-A@2"), parse("x z", s, rules));
	}

	@Test
	void readingThatCanTakeATokenPreventsConfirmation() throws ParseException {
		// The same grammar as in the previous test, but with a rule for the nonterminal.
		Nonterminal s = new Nonterminal("S");
		Nonterminal a = new Nonterminal("A");
		Nonterminal b = new Nonterminal("B");
		Nonterminal other = new Nonterminal("Other");
		Rule[] rules = { rule("S-A", s, a, token("z")), rule("S-B", s, b, other), rule("A", a, x), rule("B", b, x),
				rule("Other", other, token("y")) };

		assertEquals(List.of("A@2", "S-A@2"), parse("x z", s, rules));
		assertEquals(List.of("B@2", "Other@2", "S-B@2"), parse("x y", s, rules));
	}

	// Empty rules

	@Test
	void markerRuleIsConfirmedBeforeTheNextTokenIsRead() throws ParseException {
		Nonterminal block = new Nonterminal("Block");
		Nonterminal blockStart = new Nonterminal("BlockStart");
		Nonterminal statements = new Nonterminal("Statements");
		Rule[] rules = { rule("Block", block, token("{"), blockStart, statements, token("}")),
				rule("BlockStart", blockStart), rule("Empty", statements),
				rule("List", statements, statements, statement), rule("Statement", statement, x, semicolon) };

		assertEquals(List.of("BlockStart@1", "Empty@1", "Statement@3", "List@3", "Block@4"),
				parse("{ x ; }", block, rules));
	}

	@Test
	void emptyMatchThatIsOnlyOneAlternativeIsNotConfirmedEarly() throws ParseException {
		Nonterminal s = new Nonterminal("S");
		Nonterminal optional = new Nonterminal("Optional");
		Rule[] rules = { rule("S", s, token("("), optional, token(")")), rule("None", optional),
				rule("Some", optional, x) };

		// After "(" the optional part could be empty or not.
		assertEquals(List.of("None@2", "S@2"), parse("( )", s, rules));
		assertEquals(List.of("Some@2", "S@3"), parse("( x )", s, rules));
	}

	@Test
	void twoEmptyMatchesAtTheSamePositionAreCalledInTheirOrder() throws ParseException {
		Nonterminal s = new Nonterminal("S");
		Nonterminal first = new Nonterminal("First");
		Nonterminal second = new Nonterminal("Second");
		Rule[] rules = { rule("S", s, first, second, x), rule("First", first), rule("Second", second) };

		assertEquals(List.of("First@0", "Second@0", "S@1"), parse("x", s, rules));
	}

	// Ambiguity

	@Test
	void ambiguityIsReportedAsSoonAsItIsPartOfEveryReading() {
		Nonterminal a = new Nonterminal("A");
		Rule[] rules = { rule("Empty", program), rule("List", program, program, statement),
				rule("Statement", statement, a, semicolon), rule("A1", a, x), rule("A2", a, x) };

		AmbiguityException exception = assertThrows(AmbiguityException.class, () -> parse("x ; x ;", program, rules));

		assertSame(a, exception.symbol());
		assertEquals(0, exception.start());
		assertEquals(1, exception.end());
		assertEquals(1, requested);
		assertEquals(List.of("Empty@0"), calls);
	}

	@Test
	void ambiguityInAnOpenAlternativeIsReportedOnlyWhenTheAlternativeRemains() throws ParseException {
		// A matches "x" in two ways, but as long as "y" or "z" can follow, B is a reading as well.
		Nonterminal s = new Nonterminal("S");
		Nonterminal a = new Nonterminal("A");
		Nonterminal b = new Nonterminal("B");
		Rule[] rules = { rule("S-A", s, a, token("y")), rule("S-B", s, b, token("z")), rule("A1", a, x),
				rule("A2", a, x), rule("B", b, x) };

		assertEquals(List.of("B@2", "S-B@2"), parse("x z", s, rules));
		AmbiguityException exception = assertThrows(AmbiguityException.class, () -> parse("x y", s, rules));
		assertSame(a, exception.symbol());
		assertEquals(2, requested);
		assertEquals(List.of(), calls);
	}

	@Test
	void matchesThatAllAlternativesOfAnAmbiguousSectionShareAreCalledOnce() {
		Nonterminal s = new Nonterminal("S");
		Nonterminal a = new Nonterminal("A");
		Nonterminal b = new Nonterminal("B");
		Rule[] rules = { rule("S", s, a), rule("A1", a, b, token("y")), rule("A2", a, b, token("y")), rule("B", b, x) };

		AmbiguityException exception = assertThrows(AmbiguityException.class, () -> parse("x y", s, rules));

		assertSame(a, exception.symbol());
		assertEquals(List.of("B@1"), calls);
	}

	// Reading tokens

	@Test
	void readsTokensOneAtATimeAndOnlyAfterCallingTheActions() throws ParseException {
		List<String> events = new ArrayList<>();
		List<String> list = tokens("x ; x ;");
		Iterable<String> input = () -> new Iterator<>() {

			private final Iterator<String> iterator = list.iterator();

			@Override
			public boolean hasNext() {
				return iterator.hasNext();
			}

			@Override
			public String next() {
				String next = iterator.next();
				events.add("read " + next);
				return next;
			}
		};
		Action record = match -> events.add("call " + match.rule().lhs());
		Grammar grammar = new Grammar(program, new Rule(program, List.of(), record),
				new Rule(program, List.of(program, statement), record),
				new Rule(statement, List.of(x, semicolon), record));

		Parser.create(grammar).parse(input);

		assertEquals(List.of("call Program", "read x", "read ;", "call Statement", "call Program", "read x", "read ;",
				"call Statement", "call Program"), events);
	}
}
