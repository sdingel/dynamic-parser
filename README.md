# dynamic-parser

A generic parser for Java whose grammar can be modified while parsing.

The parser is intended for extensible programming languages: languages in which new constructs can be defined
within the language itself.

**Status:** The parser works for grammars that do not change while parsing, and it calls actions as early as
specified. One part of the specified behavior is still missing: actions cannot change the grammar yet.

## What the parser does

- **Generic.** The parser is not a parser generator. It receives its grammar at runtime through an API, as ordinary
  rules of the form `A -> X1 … Xn`.
- **Actions.** Every rule has an action: a callback that computes the value of a match of the rule from the values of
  its parts. The parser returns the value computed for the complete input.
- **Dynamic grammar.** An action can add rules to the grammar and remove rules from it. The changes apply to the
  input that follows: an added rule can be used from there on, a removed rule begins no new matches, and matches
  that have already begun are completed.
- **Reliable actions.** An action is called exactly once for every match that is part of the result, never for an
  alternative that the parser discards, and in bottom-up, left-to-right order. The parser calls an action only when
  it is certain that the match is part of the result.
- **Any context-free grammar,** including left recursion and empty rules. Input that matches the grammar in more
  than one way is rejected as ambiguous.
- **Strict timing.** An action that changes the grammar must be certain when its match ends, so that the change can
  apply to the very next token. Otherwise the change fails with an exception instead of taking effect later.

The complete specification with the reasoning behind these decisions is in
[`docs/architecture.md`](docs/architecture.md).

## Example

A tiny language in which `var x ;` declares a name and `print x ;` uses a declared name. Tokens are strings.
Declaring a name adds a grammar rule, so a name that is not declared is a syntax error.

```java
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
```

A parser is created from the grammar and can then parse any number of inputs:

```java
Parser parser = Parser.create(grammar);
Object result = parser.parse(List.of()); // the empty program: a ParseNode of the rule "Program -> ε"
```

Changing the grammar from an action is not implemented yet, so the declaration above currently fails with an
`UnsupportedOperationException`. Once it is implemented, the example behaves like this:

```java
parser.parse(List.of("var", "x", ";", "print", "x", ";")); // returns the parse tree
parser.parse(List.of("print", "y", ";")); // throws SyntaxException: y is not declared
```

## Requirements

- JDK 21 or newer

Maven does not need to be installed. The Maven Wrapper (`./mvnw`, or `mvnw.cmd` on Windows) downloads the required
Maven version on first use.

## Building

```bash
./mvnw verify
```

This compiles the sources, runs the tests and builds the JAR in `target/`.

## Code formatting

The Java sources are formatted with the Eclipse formatter: the built-in Eclipse profile, indentation with tabs and a
maximum line length of 120. The profile is stored in [`eclipse-formatter.xml`](eclipse-formatter.xml).

Format the sources before committing:

```bash
./mvnw spotless:apply
```

In Eclipse, import the profile under *Preferences > Java > Code Style > Formatter > Import...* and format as usual.
The build uses the formatter of Eclipse 4.41 (2026-09); other Eclipse versions may format a few edge cases
differently.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
