package de.steffendingel.dynamicparser.spike;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import de.steffendingel.dynamicparser.Action;
import de.steffendingel.dynamicparser.Grammar;
import de.steffendingel.dynamicparser.Match;
import de.steffendingel.dynamicparser.Nonterminal;
import de.steffendingel.dynamicparser.ParseException;
import de.steffendingel.dynamicparser.Parser;
import de.steffendingel.dynamicparser.Rule;
import de.steffendingel.dynamicparser.Symbol;
import de.steffendingel.dynamicparser.Terminal;
import de.steffendingel.dynamicparser.spike.Ast.ClassDecl;
import de.steffendingel.dynamicparser.spike.Ast.Expression;
import de.steffendingel.dynamicparser.spike.Ast.ExpressionStatement;
import de.steffendingel.dynamicparser.spike.Ast.FunctionDecl;
import de.steffendingel.dynamicparser.spike.Ast.IfStatement;
import de.steffendingel.dynamicparser.spike.Ast.Param;
import de.steffendingel.dynamicparser.spike.Ast.ReturnStatement;
import de.steffendingel.dynamicparser.spike.Ast.Type;
import de.steffendingel.dynamicparser.spike.Ast.VarStatement;
import de.steffendingel.dynamicparser.spike.Token.Kind;

/**
 * A small example language with deferred, multi-stage parsing of indented blocks, built on the public API only.
 *
 * <pre>
 * class A
 *     fun f(b: B): Integer
 *         var x = b.g()
 *         if x
 *             return g()
 *         return x
 * fun g(): Integer
 *     return 1
 * </pre>
 *
 * Every declaration adds grammar rules; using an undeclared name is a syntax error. Class blocks and function bodies
 * are deferred: the header rule takes the block as a single {@link Kind#BLOCK} token, and its action puts the block
 * into a queue with a start symbol, a phase and a scope. After the main input, the queue is processed by phase and,
 * within a phase, in source order. The block of an {@code if} statement is parsed immediately, by a nested parse run
 * started from the action of the statement.
 * <p>
 * The signature of a top-level function is deferred as well: the main run only takes the name and collects the other
 * tokens of the header line unparsed, so that the signature can refer to classes that are declared later. This shows
 * that the unit of deferral is not only an indented block but also the rest of a line. Methods and local functions do
 * not need this, because all class names are known when their headers are parsed.
 * <p>
 * Grammar ({@code BLOCK} is a block token, {@code EOL} the end of a line without a block):
 *
 * <pre>
 * Program    -> ε | Program TopLevel
 * TopLevel   -> 'class' word BLOCK                                     // defers the block: phase 1, Members
 * TopLevel   -> 'fun' word RawSignature BLOCK                         // defers the signature: phase 1, Signature
 * RawSignature -> ε | RawSignature token                               // any token of the header line
 * Signature  -> '(' Params ')' ':' Type BLOCK                          // defers the body: phase 2, Body
 * Members    -> ε | Members Method
 * Method     -> 'fun' word '(' Params ')' ':' Type BLOCK               // defers the body: phase 2, Body
 * Body       -> ε | Body Statement
 * Statement  -> 'var' word '=' Expression EOL
 * Statement  -> Expression EOL
 * Statement  -> 'if' Expression&lt;Integer&gt; BLOCK                         // parses the block in a nested run
 * Statement  -> 'fun' word '(' Params ')' ':' Type BLOCK               // local function, defers the body
 * Params     -> ε | ParamList
 * ParamList  -> Param | ParamList ',' Param
 * Param      -> word ':' Type
 * Type       -> 'Integer'
 * Expression -> Expression&lt;Integer&gt;
 * Expression&lt;Integer&gt; -> integer
 *
 * // Added by actions. For a class A, in the global scope:
 * Type          -> 'A'
 * Expression    -> Expression&lt;A&gt;
 * // For a parameter or variable x of type T, in the scope of the declaration:
 * Expression&lt;T&gt; -> 'x'
 * // For a function f(T1, ...): R, global for a top-level function, in the enclosing scope for a local one:
 * Expression&lt;R&gt; -> 'f' '(' Expression&lt;T1&gt; ',' ... ')'
 * // For a method g(T1, ...): R of class A, global and in the scope of A:
 * Expression&lt;R&gt; -> Expression&lt;A&gt; '.' 'g' '(' Expression&lt;T1&gt; ',' ... ')'
 * Expression&lt;R&gt; -> 'g' '(' Expression&lt;T1&gt; ',' ... ')'
 * // For a function or method with return type R, in its scope, shadowing that of an enclosing function:
 * Statement     -> 'return' Expression&lt;R&gt; EOL
 * </pre>
 *
 * Because the library has no scopes, the language keeps a tree of {@link Scope scopes} next to the parser. Every rule
 * added by a declaration is recorded in its scope; if that scope applies to the running parse, the rule is also added
 * to the running parse through the {@link de.steffendingel.dynamicparser.GrammarEditor}. Every parse run gets a new
 * {@link Grammar} built from the base rules and the rules of its scope chain.
 */
final class ExampleLanguage {

	/** The phase of signatures: class blocks with their member signatures, and signatures of top-level functions. */
	static final int PHASE_SIGNATURES = 1;

	/** The phase of function and method bodies, including the bodies of local functions. */
	static final int PHASE_BODIES = 2;

	/** The result of compiling a source text. */
	record Compilation(List<Object> declarations, List<SourceError> errors, Statistics statistics) {

		/** Returns the declarations in normalized source form. */
		String print() {
			return Ast.print(declarations);
		}
	}

	/** Counts for observations on performance. */
	static final class Statistics {

		int parseRuns;

		int maxRulesPerRun;

		long totalRules;
	}

	/** A parse run: its scope, its tokens and the class whose block or method is being parsed. */
	private record Run(Scope scope, List<Token> tokens, Type owner) {
	}

	/** The value of a deferred signature. */
	private record Signature(List<Param> params, Type returnType) {
	}

	/** An entry of the queue: a block or the signature of a top-level function. */
	private record Deferred(int phase, int line, long sequence, Nonterminal start, Run run, Consumer<Object> attach) {
	}

	/**
	 * Thrown out of an action when the nested parse run of a block that is parsed immediately fails. Actions cannot
	 * throw the checked {@link ParseException}, so the error is passed on as an unchecked exception, which aborts the
	 * enclosing parse run and is caught by {@link ExampleLanguage#parseBlock}.
	 */
	private static final class NestedBlockFailure extends RuntimeException {

		private static final long serialVersionUID = 1L;

		final transient SourceError error;

		NestedBlockFailure(SourceError error) {
			super(error.toString(), null, false, false);
			this.error = error;
		}
	}

	// Terminals. Keywords are ordinary words; the terminals decide which words they match.

	private final Terminal word = new Terminal("word", token -> ((Token) token).kind() == Kind.WORD);

	private final Terminal integer = new Terminal("integer", token -> ((Token) token).kind() == Kind.INTEGER);

	private final Terminal endOfLine = new Terminal("end of line", token -> ((Token) token).kind() == Kind.END_OF_LINE);

	private final Terminal block = new Terminal("indented block", token -> ((Token) token).kind() == Kind.BLOCK);

	/** Any token within a line, that is, any token that does not end a line. */
	private final Terminal tokenInLine = new Terminal("token",
			token -> ((Token) token).kind() != Kind.END_OF_LINE && ((Token) token).kind() != Kind.BLOCK);

	private final Terminal openParen = symbol(Kind.BRACKET, "(");

	private final Terminal closeParen = symbol(Kind.BRACKET, ")");

	private final Terminal colon = symbol(Kind.SPECIAL, ":");

	private final Terminal comma = symbol(Kind.SPECIAL, ",");

	private final Terminal dot = symbol(Kind.SPECIAL, ".");

	private final Terminal equalsSign = symbol(Kind.SPECIAL, "=");

	// Nonterminals

	private final Nonterminal program = new Nonterminal("Program");

	private final Nonterminal topLevel = new Nonterminal("TopLevel");

	private final Nonterminal rawSignature = new Nonterminal("RawSignature");

	private final Nonterminal signature = new Nonterminal("Signature");

	private final Nonterminal members = new Nonterminal("Members");

	private final Nonterminal method = new Nonterminal("Method");

	private final Nonterminal body = new Nonterminal("Body");

	private final Nonterminal statement = new Nonterminal("Statement");

	private final Nonterminal params = new Nonterminal("Params");

	private final Nonterminal paramList = new Nonterminal("ParamList");

	private final Nonterminal param = new Nonterminal("Param");

	private final Nonterminal type = new Nonterminal("Type");

	private final Nonterminal expression = new Nonterminal("Expression");

	private final Type integerType = new Type("Integer");

	/** The rules that every parse run starts with, in addition to the rules of its scope chain. */
	private final List<Rule> baseRules = new ArrayList<>();

	private final Scope global = new Scope(null, "global");

	/** The key of the return statement rule, which an inner function scope shadows. */
	private static final Object RETURN = new Object();

	private final PriorityQueue<Deferred> queue = new PriorityQueue<>(Comparator.comparingInt(Deferred::phase)
			.thenComparingInt(Deferred::line).thenComparingLong(Deferred::sequence));

	private long sequence;

	/**
	 * The parse runs in progress, innermost first. Actions of the base rules are shared by all runs and learn from this
	 * stack which run they belong to: a {@link Match} gives no access to anything that identifies its parse run.
	 */
	private final Deque<Run> runs = new ArrayDeque<>();

	private final List<SourceError> errors = new ArrayList<>();

	private final Statistics statistics = new Statistics();

	private ExampleLanguage() {
		// Program -> ε | Program TopLevel
		base(program, List.of(), match -> List.of());
		base(program, List.of(program, topLevel), ExampleLanguage::append);
		// TopLevel -> 'class' word BLOCK
		base(topLevel, List.of(keyword("class"), word, block), this::classDeclaration);
		// TopLevel -> 'fun' word RawSignature BLOCK
		base(topLevel, List.of(keyword("fun"), word, rawSignature, block), this::topLevelFunction);
		// RawSignature -> ε | RawSignature token
		base(rawSignature, List.of(), match -> List.of());
		base(rawSignature, List.of(rawSignature, tokenInLine), ExampleLanguage::append);
		// Signature -> '(' Params ')' ':' Type BLOCK
		base(signature, List.of(openParen, params, closeParen, colon, type, block),
				match -> new Signature(castList(match.value(1)), match.value(4, Type.class)));
		// Members -> ε | Members Method
		base(members, List.of(), match -> List.of());
		base(members, List.of(members, method), ExampleLanguage::append);
		// Method -> 'fun' word '(' Params ')' ':' Type BLOCK
		base(method, functionHeader(), match -> functionDeclaration(match, FunctionKind.METHOD));
		// Body -> ε | Body Statement
		base(body, List.of(), match -> List.of());
		base(body, List.of(body, statement), ExampleLanguage::append);
		// Statement -> 'var' word '=' Expression EOL
		base(statement, List.of(keyword("var"), word, equalsSign, expression, endOfLine), this::varStatement);
		// Statement -> Expression EOL
		base(statement, List.of(expression, endOfLine),
				match -> new ExpressionStatement(match.value(0, Expression.class)));
		// Statement -> 'if' Expression<Integer> BLOCK
		base(statement, List.of(keyword("if"), integerType.expression(), block), this::ifStatement);
		// Statement -> 'fun' word '(' Params ')' ':' Type BLOCK
		base(statement, functionHeader(), match -> functionDeclaration(match, FunctionKind.LOCAL));
		// Params -> ε | ParamList
		base(params, List.of(), match -> List.of());
		base(params, List.of(paramList), match -> match.value(0));
		// ParamList -> Param | ParamList ',' Param
		base(paramList, List.of(param), match -> List.of(match.value(0)));
		base(paramList, List.of(paramList, comma, param), match -> {
			List<Object> list = new ArrayList<>((List<?>) match.value(0));
			list.add(match.value(2));
			return List.copyOf(list);
		});
		// Param -> word ':' Type
		base(param, List.of(word, colon, type), match -> new Param(text(match, 0), match.value(2, Type.class)));
		// Type -> 'Integer'
		base(type, List.of(keyword("Integer")), match -> integerType);
		// Expression -> Expression<Integer>
		base(expression, List.of(integerType.expression()), match -> match.value(0));
		// Expression<Integer> -> integer
		base(integerType.expression(), List.of(integer), match -> new Expression(integerType, text(match, 0)));
	}

	/**
	 * Compiles a source text: parses the main input, then all deferred blocks. Errors in blocks are collected; the
	 * remaining blocks are still parsed.
	 *
	 * @throws IndentationException if the indentation is inconsistent
	 */
	static Compilation compile(String source) throws IndentationException {
		return new ExampleLanguage().run(source);
	}

	private Compilation run(String source) throws IndentationException {
		List<Object> declarations = new ArrayList<>();
		Run main = new Run(global, Lexer.tokens(StructureReader.read(source)), null);
		parseBlock(program, main, value -> declarations.addAll((List<?>) value));
		while (!queue.isEmpty()) {
			Deferred next = queue.poll();
			parseBlock(next.start(), next.run(), next.attach());
		}
		return new Compilation(declarations, List.copyOf(errors), statistics);
	}

	/** Parses a top-level parse run: the main input or a deferred block. */
	private void parseBlock(Nonterminal start, Run run, Consumer<Object> attach) {
		try {
			attach.accept(parse(start, run));
		} catch (ParseException e) {
			errors.add(sourceError(e, run));
		} catch (NestedBlockFailure e) {
			errors.add(e.error);
		}
	}

	/** Parses the tokens of a run with a new parser for the rules of its scope chain. */
	private Object parse(Nonterminal start, Run run) throws ParseException {
		List<Rule> rules = new ArrayList<>(baseRules);
		rules.addAll(run.scope().visibleRules());
		statistics.parseRuns++;
		statistics.maxRulesPerRun = Math.max(statistics.maxRulesPerRun, rules.size());
		statistics.totalRules += rules.size();
		runs.push(run);
		try {
			return Parser.create(new Grammar(start, rules)).parse(run.tokens());
		} finally {
			runs.pop();
		}
	}

	/** Maps the token position of a parse exception to the source position of the token. */
	private static SourceError sourceError(ParseException e, Run run) {
		List<Token> tokens = run.tokens();
		if (tokens.isEmpty()) {
			return new SourceError(1, 1, run.scope().toString(), e);
		}
		// At the end of the input there is no token at the position; the error is reported at the last token, which
		// ends the last line.
		Token token = tokens.get(Math.min(e.position(), tokens.size() - 1));
		return new SourceError(token.line(), token.column(), run.scope().toString(), e);
	}

	// Actions

	private enum FunctionKind {
		TOP_LEVEL, METHOD, LOCAL
	}

	private Object classDeclaration(Match match) {
		String name = text(match, 1);
		Type classType = new Type(name);
		ClassDecl declaration = new ClassDecl(name, classType);
		// Type -> 'A' and Expression -> Expression<A>, both global
		declare(match, global, new Rule(type, List.of(keyword(name)), m -> classType));
		declare(match, global, new Rule(expression, List.of(classType.expression()), m -> m.value(0)));
		Scope classScope = new Scope(global, "class " + name);
		defer(match.value(2, Token.class), PHASE_SIGNATURES, members, classScope, classType,
				value -> declaration.methods = castList(value));
		return declaration;
	}

	/**
	 * The header of a top-level function in the main run. Only the name is known here; the rest of the header line is
	 * put into the queue unparsed, followed by the block token, which ends the line. When the signature has been
	 * parsed, the function is declared and its body is put into the queue.
	 */
	private Object topLevelFunction(Match match) {
		FunctionDecl declaration = new FunctionDecl(text(match, 1));
		Token blockToken = match.value(3, Token.class);
		List<Token> tokens = new ArrayList<>(ExampleLanguage.<Token>castList(match.value(2)));
		tokens.add(blockToken);
		Run run = new Run(global, tokens, null);
		queue.add(new Deferred(PHASE_SIGNATURES, blockToken.line(), sequence++, signature, run, value -> {
			Signature parsed = (Signature) value;
			declaration.params = parsed.params();
			declaration.returnType = parsed.returnType();
			// No parse run is in progress here, so the rules only go into the scopes.
			declareFunction(null, declaration, FunctionKind.TOP_LEVEL, global, null, blockToken);
		}));
		return declaration;
	}

	/** The header of a method or of a local function, which is parsed where it stands. */
	private Object functionDeclaration(Match match, FunctionKind kind) {
		FunctionDecl declaration = new FunctionDecl(text(match, 1));
		declaration.params = castList(match.value(3));
		declaration.returnType = match.value(6, Type.class);
		Run run = runs.peek();
		declareFunction(match, declaration, kind, run.scope(), run.owner(), match.value(7, Token.class));
		return declaration;
	}

	/**
	 * Declares a function whose signature is known and defers its body.
	 *
	 * @param match     the match of the header if the header is parsed in the running parse, otherwise {@code null}
	 * @param enclosing the scope in which the function is declared
	 * @param owner     the class of a method, otherwise the class whose method encloses the function, or {@code null}
	 */
	private void declareFunction(Match match, FunctionDecl declaration, FunctionKind kind, Scope enclosing, Type owner,
			Token blockToken) {
		switch (kind) {
		case TOP_LEVEL -> declare(match, global, callRule(declaration, null));
		case METHOD -> {
			declare(match, global, callRule(declaration, owner));
			declare(match, enclosing, callRule(declaration, null));
		}
		case LOCAL -> declare(match, enclosing, callRule(declaration, null));
		}
		Scope functionScope = new Scope(enclosing, "fun " + declaration.name);
		for (Param parameter : declaration.params) {
			functionScope.add(nameRule(parameter.name(), parameter.type()));
		}
		// Statement -> 'return' Expression<R> EOL
		// WORKAROUND (shadowing): with a plain scope chain, the return rule of an enclosing function would also apply
		// in a local function. With equal return types the two rules make every return statement ambiguous, with
		// different ones a return value of the wrong type would be accepted. The rule therefore shadows the return rule
		// of enclosing scopes.
		Type returnType = declaration.returnType;
		functionScope.addShadowing(RETURN,
				new Rule(statement, List.of(keyword("return"), returnType.expression(), endOfLine),
						m -> new ReturnStatement(m.value(1, Expression.class))));
		defer(blockToken, PHASE_BODIES, body, functionScope, owner, value -> declaration.body = castList(value));
	}

	private Object varStatement(Match match) {
		String name = text(match, 1);
		Expression value = match.value(3, Expression.class);
		declare(match, runs.peek().scope(), nameRule(name, value.type()));
		return new VarStatement(name, value);
	}

	private Object ifStatement(Match match) {
		Expression condition = match.value(1, Expression.class);
		Token blockToken = match.value(2, Token.class);
		Run outer = runs.peek();
		// The block is parsed immediately, in a nested parse run with a new scope below the current one. Its
		// declarations go into that scope and are not visible after the block.
		Run nested = new Run(new Scope(outer.scope(), "if@" + blockToken.line()), Lexer.tokens(blockToken.block()),
				outer.owner());
		try {
			return new IfStatement(condition, castList(parse(body, nested)));
		} catch (ParseException e) {
			throw new NestedBlockFailure(sourceError(e, nested));
		}
	}

	// Helpers

	/**
	 * Records a rule in a scope and, if the scope applies to the running parse, also adds it to the running parse.
	 *
	 * @param match the match whose action declares the rule, or {@code null} if no parse run is in progress
	 */
	private void declare(Match match, Scope scope, Rule rule) {
		scope.add(rule);
		if (match != null && runs.peek().scope().isWithin(scope)) {
			match.grammar().add(rule);
		}
	}

	/** Puts the block at the end of a header into the queue. */
	private void defer(Token blockToken, int phase, Nonterminal start, Scope scope, Type owner,
			Consumer<Object> attach) {
		// The block is tokenized now; it could just as well be done when the block is taken from the queue.
		Run run = new Run(scope, Lexer.tokens(blockToken.block()), owner);
		queue.add(new Deferred(phase, blockToken.line(), sequence++, start, run, attach));
	}

	/** Creates the rule {@code Expression<T> -> 'name'} for a parameter or variable. */
	private Rule nameRule(String name, Type variableType) {
		return new Rule(variableType.expression(), List.of(keyword(name)), m -> new Expression(variableType, name));
	}

	/**
	 * Creates the rule for calling a function: {@code Expression<R> -> 'f' '(' Expression<T1> ',' ... ')'}, or, with a
	 * receiver type {@code A}, {@code Expression<R> -> Expression<A> '.' 'f' '(' ... ')'}.
	 */
	private Rule callRule(FunctionDecl function, Type receiver) {
		List<Symbol> rhs = new ArrayList<>();
		if (receiver != null) {
			rhs.add(receiver.expression());
			rhs.add(dot);
		}
		rhs.add(keyword(function.name));
		rhs.add(openParen);
		for (int i = 0; i < function.params.size(); i++) {
			if (i > 0) {
				rhs.add(comma);
			}
			rhs.add(function.params.get(i).type().expression());
		}
		rhs.add(closeParen);
		return new Rule(function.returnType.expression(), rhs, m -> {
			List<Expression> expressions = m.values().stream().filter(Expression.class::isInstance)
					.map(Expression.class::cast).toList();
			String prefix = receiver == null ? "" : expressions.getFirst() + ".";
			String arguments = expressions.stream().skip(receiver == null ? 0 : 1).map(Expression::text)
					.collect(Collectors.joining(", ", "(", ")"));
			return new Expression(function.returnType, prefix + function.name + arguments);
		});
	}

	private void base(Nonterminal lhs, List<? extends Symbol> rhs, Action action) {
		baseRules.add(new Rule(lhs, rhs, action));
	}

	private List<Symbol> functionHeader() {
		return List.of(keyword("fun"), word, openParen, params, closeParen, colon, type, block);
	}

	private static Terminal keyword(String text) {
		return new Terminal("'" + text + "'", token -> ((Token) token).is(Kind.WORD, text));
	}

	private static Terminal symbol(Kind kind, String text) {
		return new Terminal("'" + text + "'", token -> ((Token) token).is(kind, text));
	}

	private static String text(Match match, int index) {
		return match.value(index, Token.class).text();
	}

	private static List<Object> append(Match match) {
		List<Object> list = new ArrayList<>((List<?>) match.value(0));
		list.add(match.value(1));
		return List.copyOf(list);
	}

	@SuppressWarnings("unchecked")
	private static <T> List<T> castList(Object value) {
		return (List<T>) value;
	}
}
