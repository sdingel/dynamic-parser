package de.steffendingel.dynamicparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Contains the usage example of {@code README.md} to make sure that it compiles against the API. Keep both in sync.
 */
class ReadmeExampleTest {

	@Test
	void exampleGrammarCanBeCreatedAndParsesTheEmptyProgram() throws ParseException {
		// A tiny language: "var x ;" declares a name, "print x ;" uses a declared name.
		// Tokens are strings.
		Nonterminal program = new Nonterminal("Program");
		Nonterminal statement = new Nonterminal("Statement");
		Nonterminal expression = new Nonterminal("Expression");

		Terminal var = new Terminal("'var'", "var"::equals);
		Terminal print = new Terminal("'print'", "print"::equals);
		Terminal semicolon = new Terminal("';'", ";"::equals);
		Terminal anyWord = new Terminal("word", token -> ((String) token).chars().allMatch(Character::isLetter));

		// Program -> ε | Program Statement
		Rule noStatements = new Rule(program, List.of());
		Rule moreStatements = new Rule(program, List.of(program, statement));

		// Statement -> 'var' word ';'
		// The action adds the rule "Expression -> <the declared word>". From the next statement on, the declared
		// word is an expression.
		Rule declaration = new Rule(statement, List.of(var, anyWord, semicolon), match -> {
			String name = match.value(1, String.class);
			Terminal nameTerminal = new Terminal("'" + name + "'", name::equals);
			match.grammar().add(new Rule(expression, List.of(nameTerminal)));
			return ParseNode.of(match);
		});

		// Statement -> 'print' Expression ';'
		// There is no rule for Expression yet: only declared names are expressions.
		Rule printStatement = new Rule(statement, List.of(print, expression, semicolon));

		Grammar grammar = new Grammar(program, noStatements, moreStatements, declaration, printStatement);

		Parser parser = Parser.create(grammar);
		Object result = parser.parse(List.of()); // the empty program: a ParseNode of the rule "Program -> ε"

		assertEquals(new ParseNode(noStatements, 0, 0, List.of()), result);
		// Changing the grammar from an action is not implemented yet.
		assertThrows(UnsupportedOperationException.class, () -> parser.parse(List.of("var", "x", ";")));
	}
}
