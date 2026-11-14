package de.steffendingel.dynamicparser;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helpers for parser tests. The tokens of these tests are strings.
 */
final class ParserTesting {

	private ParserTesting() {
	}

	/**
	 * Creates a terminal that matches the token with the given text.
	 */
	static Terminal token(String text) {
		return new Terminal("'" + text + "'", text::equals);
	}

	/**
	 * Splits the input at spaces into tokens. The empty string is the empty input.
	 */
	static List<String> tokens(String input) {
		return input.isEmpty() ? List.of() : Arrays.asList(input.split(" "));
	}

	/**
	 * Creates a rule whose action returns the structure of the match as text: the name of the left-hand side followed
	 * by the values of the right-hand side in parentheses, for example {@code Sum(Atom(a) + Atom(b))}.
	 */
	static Rule rule(Nonterminal lhs, Symbol... rhs) {
		return new Rule(lhs, List.of(rhs), match -> lhs.name()
				+ match.values().stream().map(String::valueOf).collect(Collectors.joining(" ", "(", ")")));
	}

	/**
	 * Parses the input with a new parser for the given start symbol and rules.
	 */
	static Object parse(String input, Nonterminal start, Rule... rules) throws ParseException {
		return Parser.create(new Grammar(start, rules)).parse(tokens(input));
	}
}
