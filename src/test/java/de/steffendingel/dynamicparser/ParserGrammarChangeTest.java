package de.steffendingel.dynamicparser;

import static de.steffendingel.dynamicparser.ParserTesting.rule;
import static de.steffendingel.dynamicparser.ParserTesting.token;
import static de.steffendingel.dynamicparser.ParserTesting.tokens;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Tests actions that change the grammar while parsing.
 */
class ParserGrammarChangeTest {

	private final Nonterminal program = new Nonterminal("Program");

	private final Nonterminal statement = new Nonterminal("Statement");

	private final Nonterminal name = new Nonterminal("Name");

	private final Terminal semicolon = token(";");

	private final Terminal word = new Terminal("word", token -> ((String) token).matches("[a-z]"));

	/** The terminals for declared names, so that the tests can refer to them. */
	private final Map<String, Terminal> declared = new HashMap<>();

	private static String text(Match match) {
		return match.rule().lhs().name()
				+ match.values().stream().map(String::valueOf).collect(Collectors.joining(" ", "(", ")"));
	}

	/**
	 * Creates the rule {@code Name -> <text>} for a declared name.
	 */
	private Rule nameRule(String text, Object value) {
		Terminal terminal = declared.computeIfAbsent(text, key -> token(key));
		return new Rule(name, List.of(terminal), match -> value);
	}

	// Declarations: "var x ;" declares a name, "use x ;" uses a declared name

	private Rule[] declarations() {
		return new Rule[] { rule(program), rule(program, program, statement),
				new Rule(statement, List.of(token("var"), word, semicolon), match -> {
					match.grammar().add(nameRule(match.value(1, String.class), "name"));
					return text(match);
				}), rule(statement, token("use"), name, semicolon) };
	}

	private Object parse(String input, Nonterminal start, Rule... rules) throws ParseException {
		return Parser.create(new Grammar(start, rules)).parse(tokens(input));
	}

	@Test
	void declaredNameIsUsableInTheFollowingStatement() throws ParseException {
		assertEquals("Program(Program(Program() Statement(var a ;)) Statement(use name ;))",
				parse("var a ; use a ;", program, declarations()));
	}

	@Test
	void undeclaredNameIsASyntaxError() {
		SyntaxException none = assertThrows(SyntaxException.class, () -> parse("use a ;", program, declarations()));
		SyntaxException other = assertThrows(SyntaxException.class,
				() -> parse("var a ; var b ; use c ;", program, declarations()));

		assertEquals(1, none.position());
		assertEquals(Set.of(), none.expected());
		assertEquals(7, other.position());
		assertEquals(Optional.of("c"), other.token());
		assertEquals(Set.of(declared.get("a"), declared.get("b")), other.expected());
	}

	@Test
	void nameIsNotUsableBeforeItsDeclaration() {
		SyntaxException exception = assertThrows(SyntaxException.class,
				() -> parse("use a ; var a ;", program, declarations()));

		assertEquals(1, exception.position());
	}

	@Test
	void addedRuleIsNotAvailableEarlierInTheSameStatement() throws ParseException {
		// "var x = y ;" declares x; the initializer y must have been declared before.
		Rule[] rules = { rule(program), rule(program, program, statement),
				new Rule(statement, List.of(token("var"), word, token("="), name, semicolon), match -> {
					match.grammar().add(nameRule(match.value(1, String.class), "name"));
					return text(match);
				}), new Rule(statement, List.of(token("var"), word, semicolon), match -> {
					match.grammar().add(nameRule(match.value(1, String.class), "name"));
					return text(match);
				}) };

		assertEquals("Program(Program(Program() Statement(var a ;)) Statement(var b = name ;))",
				parse("var a ; var b = a ;", program, rules));
		SyntaxException exception = assertThrows(SyntaxException.class, () -> parse("var a = a ;", program, rules));
		assertEquals(3, exception.position());
	}

	@Test
	void changesOfOneParseRunDoNotAffectAnother() throws ParseException {
		Grammar grammar = new Grammar(program, declarations());
		Parser parser = Parser.create(grammar);

		parser.parse(tokens("var a ; use a ;"));

		assertThrows(SyntaxException.class, () -> parser.parse(tokens("use a ;")));
		assertEquals(4, grammar.rules().size());
		parser.parse(tokens("var a ; use a ;"));
	}

	// Blocks with scopes

	private final Nonterminal block = new Nonterminal("Block");

	private final Nonterminal blockStart = new Nonterminal("BlockStart");

	private final Nonterminal statements = new Nonterminal("Statements");

	/** For every open block, the rules to remove and the rules to add again at its end. */
	private final Deque<List<Rule>> removeAtEnd = new ArrayDeque<>();

	private final Deque<List<Rule>> restoreAtEnd = new ArrayDeque<>();

	/** The rule that is currently in the grammar for every declared name. */
	private final Map<String, Rule> visible = new HashMap<>();

	private int declarations;

	/**
	 * A language with nested blocks: "{ ... }". A declaration "var x ;" is visible until the end of its block and hides
	 * a declaration of the same name in an enclosing block. "use x ;" returns the number of the declaration it refers
	 * to.
	 */
	private Rule[] blocks() {
		Rule blockRule = new Rule(block, List.of(token("{"), blockStart, statements, token("}")), match -> {
			removeAtEnd.pop().forEach(match.grammar()::remove);
			for (Rule rule : restoreAtEnd.pop()) {
				match.grammar().add(rule);
				visible.put(rule.rhs().get(0).name(), rule);
			}
			return match.value(2);
		});
		// The empty rule marks the beginning of the block.
		Rule start = new Rule(blockStart, List.of(), match -> {
			removeAtEnd.push(new ArrayList<>());
			restoreAtEnd.push(new ArrayList<>());
			return null;
		});
		Rule declaration = new Rule(statement, List.of(token("var"), word, semicolon), match -> {
			String declaredName = match.value(1, String.class);
			Rule rule = nameRule(declaredName, ++declarations);
			Rule hidden = visible.put("'" + declaredName + "'", rule);
			if (hidden != null && match.grammar().remove(hidden)) {
				restoreAtEnd.peek().add(hidden);
			}
			match.grammar().add(rule);
			removeAtEnd.peek().add(rule);
			return "";
		});
		Rule use = new Rule(statement, List.of(token("use"), name, semicolon), match -> match.value(1) + " ");
		Rule nested = new Rule(statement, List.of(block), match -> match.value(0));
		Rule empty = new Rule(statements, List.of(), match -> "");
		Rule more = new Rule(statements, List.of(statements, statement),
				match -> match.value(0, String.class) + match.value(1));
		return new Rule[] { blockRule, start, declaration, use, nested, empty, more };
	}

	@Test
	void ruleAddedInABlockIsRemovedAtItsEnd() throws ParseException {
		assertEquals("1 1 2 1 ", parse("{ var a ; use a ; { var b ; use a ; use b ; } use a ; }", block, blocks()));
		SyntaxException exception = assertThrows(SyntaxException.class,
				() -> parse("{ var a ; { var b ; } use b ; }", block, blocks()));
		assertEquals(10, exception.position());
		assertEquals(Optional.of("b"), exception.token());
		assertEquals(Set.of(declared.get("a")), exception.expected());
	}

	@Test
	void declarationInAnInnerBlockHidesTheOuterOne() throws ParseException {
		assertEquals("1 2 1 ", parse("{ var a ; use a ; { var a ; use a ; } use a ; }", block, blocks()));
	}

	@Test
	void declarationsOfSeveralNestedBlocksAreRestoredInOrder() throws ParseException {
		assertEquals("1 2 3 2 1 ",
				parse("{ var a ; use a ; { var a ; use a ; { var a ; use a ; } use a ; } use a ; }", block, blocks()));
	}

	@Test
	void changeThatMakesTheFollowingInputAmbiguousIsReported() {
		// Declaring a name twice without removing the first rule leaves two rules for the same name.
		AmbiguityException exception = assertThrows(AmbiguityException.class,
				() -> parse("var a ; var a ; use a ;", program, declarations()));

		assertSame(name, exception.symbol());
		assertEquals(7, exception.start());
		assertEquals(8, exception.end());
	}

	// Rules that are removed or added while they are in use

	@Test
	void matchOfARemovedRuleThatHasBegunIsCompleted() throws ParseException {
		Nonterminal stop = new Nonterminal("Stop");
		Rule[] holder = new Rule[1];
		Rule bracket = rule(statement, token("begin"), stop, token("end"));
		holder[0] = bracket;
		Rule[] rules = { rule(program), rule(program, program, statement), bracket,
				new Rule(stop, List.of(token("stop")), match -> {
					assertTrue(match.grammar().remove(holder[0]));
					return "Stop";
				}) };

		assertEquals("Program(Program() Statement(begin Stop end))", parse("begin stop end", program, rules));
		SyntaxException exception = assertThrows(SyntaxException.class,
				() -> parse("begin stop end begin stop end", program, rules));
		assertEquals(3, exception.position());
		assertEquals(Set.of(), exception.expected());
	}

	@Test
	void removedLeftRecursiveRuleStillExtendsAListThatHasBegun() throws ParseException {
		Nonterminal list = new Nonterminal("List");
		Nonterminal item = new Nonterminal("Item");
		Rule more = rule(list, list, item);
		Rule[] rules = { rule(list, item), more, rule(item, token("x")),
				new Rule(item, List.of(token("stop")), match -> {
					match.grammar().remove(more);
					return "Stop";
				}), rule(item, token("("), list, token(")")) };

		// The list began before the rule was removed, so it can still grow.
		assertEquals("List(List(List(List(Item(x)) Stop) Item(x)) Item(x))", parse("x stop x x", list, rules));
		// A list that begins after the removal cannot use the rule any more.
		assertEquals("List(List(List(Item(x)) Stop) Item(( List(Item(x)) )))", parse("x stop ( x )", list, rules));
		SyntaxException exception = assertThrows(SyntaxException.class, () -> parse("x stop ( x x )", list, rules));
		assertEquals(4, exception.position());
	}

	@Test
	void addedLeftRecursiveRuleDoesNotExtendAListThatHasBegun() throws ParseException {
		Nonterminal list = new Nonterminal("List");
		Nonterminal item = new Nonterminal("Item");
		Terminal y = token("y");
		Rule[] rules = { rule(list, item), rule(list, list, item), rule(item, token("x")),
				new Rule(item, List.of(token("go")), match -> {
					match.grammar().add(rule(list, list, y));
					return "Go";
				}), rule(item, token("("), list, token(")")) };

		// The list began before the rule was added.
		SyntaxException exception = assertThrows(SyntaxException.class, () -> parse("x go y", list, rules));
		assertEquals(2, exception.position());
		// A list that begins after the addition can use the rule.
		assertEquals("List(List(List(Item(x)) Go) Item(( List(List(Item(x)) y) )))",
				parse("x go ( x y )", list, rules));
	}

	@Test
	void removedRuleCanBeAddedAgain() throws ParseException {
		Rule useRule = rule(statement, token("use"), semicolon);
		Rule[] rules = { rule(program), rule(program, program, statement), useRule,
				new Rule(statement, List.of(token("off"), semicolon), match -> {
					assertTrue(match.grammar().remove(useRule));
					return "Off";
				}), new Rule(statement, List.of(token("on"), semicolon), match -> {
					assertTrue(match.grammar().add(useRule));
					return "On";
				}) };

		assertEquals("Program(Program(Program(Program() Off) On) Statement(use ;))",
				parse("off ; on ; use ;", program, rules));
		SyntaxException exception = assertThrows(SyntaxException.class, () -> parse("off ; use ;", program, rules));
		assertEquals(2, exception.position());
	}

	@Test
	void editorReportsWhetherTheGrammarChanged() throws ParseException {
		Rule other = rule(name, word);
		Rule rule = new Rule(statement, List.of(semicolon), match -> {
			GrammarEditor editor = match.grammar();
			assertFalse(editor.contains(other));
			assertFalse(editor.remove(other));
			assertTrue(editor.add(other));
			assertTrue(editor.contains(other));
			assertFalse(editor.add(other));
			assertTrue(editor.remove(other));
			assertFalse(editor.contains(other));
			assertTrue(editor.add(other));
			assertThrows(NullPointerException.class, () -> editor.add(null));
			assertThrows(NullPointerException.class, () -> editor.remove(null));
			return "done";
		});

		assertEquals("done", parse(";", statement, rule));
	}

	// Empty matches

	@Test
	void emptyMatchesAtTheSamePositionSeeTheChangesInTheirOrder() throws ParseException {
		// First adds the rule for Second, and Second adds the empty rule for Third, all before the first token.
		Nonterminal s = new Nonterminal("S");
		Nonterminal first = new Nonterminal("First");
		Nonterminal second = new Nonterminal("Second");
		Nonterminal third = new Nonterminal("Third");
		Rule secondEmpty = new Rule(second, List.of(), match -> {
			match.grammar().add(rule(third));
			return "Second()";
		});
		Rule[] rules = { rule(s, first, second, third, token("x")), new Rule(first, List.of(), match -> {
			match.grammar().add(secondEmpty);
			return "First()";
		}), rule(third, token("z")) };

		assertEquals("S(First() Second() Third() x)", parse("x", s, rules));
		assertEquals("S(First() Second() Third(z) x)", parse("z x", s, rules));
	}

	@Test
	void emptyMatchIsNotConfirmedWhileAnotherRuleCouldMatch() {
		// Second can be empty or "y", so its empty match is not confirmed before the next token. When "x" is read,
		// it is too late for the rule that the empty match would add for Third.
		Nonterminal s = new Nonterminal("S");
		Nonterminal second = new Nonterminal("Second");
		Nonterminal third = new Nonterminal("Third");
		Rule[] rules = { rule(s, second, third, token("x")), new Rule(second, List.of(), match -> {
			match.grammar().add(rule(third));
			return "Second()";
		}), rule(second, token("y")), rule(third, token("z")) };

		SyntaxException exception = assertThrows(SyntaxException.class, () -> parse("x", s, rules));

		assertEquals(0, exception.position());
	}

	@Test
	void actionOfTheCompleteInputCanChangeTheGrammar() throws ParseException {
		Nonterminal s = new Nonterminal("S");
		Rule other = rule(name, word);
		Rule rule = new Rule(s, List.of(token("x")), match -> {
			match.grammar().add(other);
			return "S";
		});

		assertEquals("S", parse("x", s, rule));
	}

	@Test
	void emptyStartRuleCanChangeTheGrammar() throws ParseException {
		// The first statement of the program is only possible because of the rule added at the very beginning.
		Rule[] rules = { new Rule(program, List.of(), match -> {
			match.grammar().add(rule(statement, token("x"), semicolon));
			return "Program()";
		}), rule(program, program, statement), rule(statement, token("y"), semicolon) };

		assertEquals("Program(Program() Statement(x ;))", parse("x ;", program, rules));
	}

	// Strict timing

	private final Nonterminal function = new Nonterminal("Function");

	private final Nonterminal header = new Nonterminal("Header");

	private final Nonterminal type = new Nonterminal("Type");

	private final Terminal integer = token("Integer");

	private final Rule parameter = rule(name, token("p"));

	@Test
	void lateChangeFails() {
		// After "Integer" the type could continue with "[", so the header is only confirmed by the "{".
		Rule headerRule = new Rule(header, List.of(token("fun"), word, token(":"), type), match -> {
			match.grammar().add(parameter);
			return "Header";
		});
		Rule[] rules = { rule(function, header, token("{"), token("}")), headerRule, rule(type, integer),
				rule(type, integer, token("["), token("]")) };

		LateGrammarChangeException exception = assertThrows(LateGrammarChangeException.class,
				() -> parse("fun f : Integer { }", function, rules));

		assertSame(headerRule, exception.rule());
		assertEquals(4, exception.matchEnd());
		assertEquals(5, exception.position());
	}

	@Test
	void changeIsOnTimeIfTheRuleEndsWithTheConfirmingToken() throws ParseException {
		// The same language, but the "{" is part of the header.
		Rule[] rules = { rule(function, header, name, token("}")),
				new Rule(header, List.of(token("fun"), word, token(":"), type, token("{")), match -> {
					match.grammar().add(parameter);
					return "Header";
				}), rule(type, integer), rule(type, integer, token("["), token("]")) };

		assertEquals("Function(Header Name(p) })", parse("fun f : Integer { p }", function, rules));
		assertEquals("Function(Header Name(p) })", parse("fun f : Integer [ ] { p }", function, rules));
	}

	@Test
	void actionCanCatchTheLateChangeAndContinue() throws ParseException {
		List<String> caught = new ArrayList<>();
		Rule[] rules = { rule(function, header, token("{"), token("}")),
				new Rule(header, List.of(token("fun"), word, token(":"), type), match -> {
					try {
						match.grammar().add(parameter);
					} catch (LateGrammarChangeException exception) {
						caught.add("add");
					}
					try {
						match.grammar().remove(parameter);
					} catch (LateGrammarChangeException exception) {
						caught.add("remove");
					}
					assertFalse(match.grammar().contains(parameter));
					return "Header";
				}), rule(type, integer), rule(type, integer, token("["), token("]")) };

		assertEquals("Function(Header { })", parse("fun f : Integer { }", function, rules));
		// Changes fail if the action is late, even if they would not change anything.
		assertEquals(List.of("add", "remove"), caught);
	}

	@Test
	void actionCanAddTheOnlyRuleWithWhichTheInputContinues() throws ParseException {
		// After the marker, the input can only continue with the rule that the marker adds.
		Nonterminal s = new Nonterminal("S");
		Nonterminal marker = new Nonterminal("Marker");
		Nonterminal body = new Nonterminal("Body");
		Rule[] rules = { rule(s, marker, body), new Rule(marker, List.of(), match -> {
			match.grammar().add(rule(body, token("x")));
			return "Marker";
		}) };

		assertEquals("S(Marker Body(x))", parse("x", s, rules));
	}

	@Test
	void interpretationWaitingForARuleDoesNotCountWhileTheInputCanContinue() throws ParseException {
		// After "x", the input can continue with "z". The interpretation with B waits for a nonterminal without
		// rules and is not considered, so A is confirmed. Its action adds the missing rule, but that does not bring
		// the interpretation with B back: it does not contain A.
		Nonterminal s = new Nonterminal("S");
		Nonterminal a = new Nonterminal("A");
		Nonterminal b = new Nonterminal("B");
		Nonterminal missing = new Nonterminal("Missing");
		Rule[] rules = { rule(s, a, token("z")), rule(s, b, missing), new Rule(a, List.of(token("x")), match -> {
			match.grammar().add(rule(missing, token("y")));
			return "A";
		}), rule(b, token("x")) };

		assertEquals("S(A z)", parse("x z", s, rules));
		SyntaxException exception = assertThrows(SyntaxException.class, () -> parse("x y", s, rules));
		assertEquals(1, exception.position());
		assertEquals(Optional.of("y"), exception.token());
	}

	// Interpretations that do not contain a called match

	/**
	 * A grammar in which "a" can be A or B. A match of Y or Q can follow both, a match of X only A. After "a k", only
	 * the interpretation with X can take a token, so A and K are confirmed. The action of K then adds the rules with
	 * which Y and Q can continue. The interpretations with B must stay excluded, because the action of A has been
	 * called.
	 */
	private Rule[] excludedInterpretations() {
		Nonterminal s = start;
		Nonterminal a = new Nonterminal("A");
		Nonterminal b = new Nonterminal("B");
		Nonterminal x = new Nonterminal("X");
		Nonterminal y = new Nonterminal("Y");
		Nonterminal q = new Nonterminal("Q");
		Nonterminal k = new Nonterminal("K");
		Nonterminal z = new Nonterminal("Z");
		Nonterminal f = new Nonterminal("F");
		Nonterminal m = new Nonterminal("M");
		Rule other = rule(name, word);
		return new Rule[] { rule(s, a, x), rule(s, a, y), rule(s, b, y), rule(s, b, q), rule(x, k, token("e")),
				rule(y, k, z), rule(q, k, f), rule(a, token("a")), rule(b, token("a")),
				new Rule(k, List.of(token("k")), match -> {
					match.grammar().add(rule(z, m, token("w")));
					match.grammar().add(rule(f, token("f")));
					return "K";
				}), new Rule(m, List.of(token("z")), match -> {
					// This fails if the match is not confirmed as soon as the "z" is read.
					match.grammar().add(other);
					return "M";
				}) };
	}

	private final Nonterminal start = new Nonterminal("S");

	@Test
	void interpretationWithoutACalledMatchIsNotCompletedAfterAChange() throws ParseException {
		// Y began before the change, where both A and B could precede it. It must be completed for A only;
		// otherwise the input would be ambiguous.
		assertEquals("S(A(a) Y(K Z(M w)))", parse("a k z w", start, excludedInterpretations()));
		assertEquals("S(A(a) X(K e))", parse("a k e", start, excludedInterpretations()));
	}

	@Test
	void interpretationWithoutACalledMatchDoesNotContinueAfterAChange() {
		// Q can only follow B, so the rule added for F must not make "f" possible.
		SyntaxException exception = assertThrows(SyntaxException.class,
				() -> parse("a k f", start, excludedInterpretations()));

		assertEquals(2, exception.position());
		assertEquals(Optional.of("f"), exception.token());
		assertEquals(2, exception.expected().size());
	}
}
