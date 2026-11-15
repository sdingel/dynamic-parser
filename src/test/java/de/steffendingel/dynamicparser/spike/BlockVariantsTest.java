package de.steffendingel.dynamicparser.spike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.steffendingel.dynamicparser.Grammar;
import de.steffendingel.dynamicparser.Match;
import de.steffendingel.dynamicparser.Nonterminal;
import de.steffendingel.dynamicparser.ParseException;
import de.steffendingel.dynamicparser.ParseNode;
import de.steffendingel.dynamicparser.Parser;
import de.steffendingel.dynamicparser.Rule;
import de.steffendingel.dynamicparser.SyntaxException;
import de.steffendingel.dynamicparser.Terminal;
import de.steffendingel.dynamicparser.spike.Token.Kind;

/**
 * Compares ways to represent blocks in the token stream, with a minimal language: {@code var x} declares a name,
 * {@code use x} uses a declared name.
 * <ul>
 * <li>A flat token stream with {@code INDENT} and {@code DEDENT} tokens, in which a block that is parsed immediately
 * belongs to the same parse run and a deferred block is skipped by a generic grammar for raw tokens.</li>
 * <li>A block as a single token that is parsed by a nested parse run, whose declarations remain visible after the
 * block.</li>
 * </ul>
 */
class BlockVariantsTest {

	private static Terminal kind(Kind kind, String name) {
		return new Terminal(name, token -> ((Token) token).kind() == kind);
	}

	private static Terminal keyword(String text) {
		return new Terminal("'" + text + "'", token -> ((Token) token).is(Kind.WORD, text));
	}

	private static String text(Match match, int index) {
		return match.value(index, Token.class).text();
	}

	private final Terminal word = kind(Kind.WORD, "word");

	private final Terminal endOfLine = kind(Kind.END_OF_LINE, "end of line");

	private final Nonterminal body = new Nonterminal("Body");

	private final Nonterminal statement = new Nonterminal("Statement");

	private final Nonterminal name = new Nonterminal("Name");

	/** Creates the rule {@code Name -> 'x'} for a declared name. */
	private Rule nameRule(String text) {
		return new Rule(name, List.of(keyword(text)));
	}

	// Flat token stream with INDENT and DEDENT

	private final Terminal indent = kind(Kind.INDENT, "indent");

	private final Terminal dedent = kind(Kind.DEDENT, "dedent");

	/** Any token except INDENT and DEDENT. */
	private final Terminal raw = new Terminal("raw token",
			token -> ((Token) token).kind() != Kind.INDENT && ((Token) token).kind() != Kind.DEDENT);

	private final Nonterminal ifHeader = new Nonterminal("IfHeader");

	private final Nonterminal rawBlock = new Nonterminal("RawBlock");

	private final Nonterminal rawTokens = new Nonterminal("RawTokens");

	/** The rules declared at the top level, recorded because the rules of a parse run cannot be listed. */
	private final List<Rule> topLevelRules = new ArrayList<>();

	/** For every open if block, the rules declared in it, which are removed at its end. */
	private final Deque<List<Rule>> openBlocks = new ArrayDeque<>();

	/** The deferred function bodies: their tokens without the enclosing INDENT and DEDENT. */
	private final List<List<Token>> deferredBodies = new ArrayList<>();

	private List<Rule> flatBaseRules() {
		return List.of(new Rule(body, List.of()), new Rule(body, List.of(body, statement)),
				// Statement -> 'var' word EOL
				new Rule(statement, List.of(keyword("var"), word, endOfLine), match -> {
					Rule rule = nameRule(text(match, 1));
					match.grammar().add(rule);
					(openBlocks.isEmpty() ? topLevelRules : openBlocks.peek()).add(rule);
					return ParseNode.of(match);
				}),
				// Statement -> 'use' Name EOL
				new Rule(statement, List.of(keyword("use"), name, endOfLine)),
				// IfHeader -> 'if' Name EOL INDENT: opens the scope of the block
				new Rule(ifHeader, List.of(keyword("if"), name, endOfLine, indent), match -> {
					openBlocks.push(new ArrayList<>());
					return ParseNode.of(match);
				}),
				// Statement -> IfHeader Body DEDENT: closes the scope of the block in the same parse run
				new Rule(statement, List.of(ifHeader, body, dedent), match -> {
					openBlocks.pop().forEach(match.grammar()::remove);
					return ParseNode.of(match);
				}),
				// Statement -> 'fun' word EOL RawBlock: declares the function, defers the body
				new Rule(statement, List.of(keyword("fun"), word, endOfLine, rawBlock), match -> {
					Rule rule = nameRule(text(match, 1));
					match.grammar().add(rule);
					topLevelRules.add(rule);
					List<Token> tokens = asTokens(match.value(3));
					deferredBodies.add(tokens.subList(1, tokens.size() - 1));
					return ParseNode.of(match);
				}),
				// RawBlock -> INDENT RawTokens DEDENT, RawTokens -> ε | RawTokens raw | RawTokens RawBlock
				new Rule(rawBlock, List.of(indent, rawTokens, dedent), BlockVariantsTest::concatenate),
				new Rule(rawTokens, List.of(), BlockVariantsTest::concatenate),
				new Rule(rawTokens, List.of(rawTokens, raw), BlockVariantsTest::concatenate),
				new Rule(rawTokens, List.of(rawTokens, rawBlock), BlockVariantsTest::concatenate));
	}

	/** Concatenates the values of a match: tokens and lists of tokens. */
	private static List<Token> concatenate(Match match) {
		List<Token> tokens = new ArrayList<>();
		for (Object value : match.values()) {
			if (value instanceof Token token) {
				tokens.add(token);
			} else {
				tokens.addAll(asTokens(value));
			}
		}
		return tokens;
	}

	@SuppressWarnings("unchecked")
	private static List<Token> asTokens(Object value) {
		return (List<Token>) value;
	}

	private void parseFlat(String source) throws ParseException, IndentationException {
		List<Rule> base = flatBaseRules();
		Parser.create(new Grammar(body, base)).parse(Lexer.flatTokens(StructureReader.read(source)));
		List<Rule> rules = new ArrayList<>(base);
		rules.addAll(topLevelRules);
		for (List<Token> deferred : deferredBodies) {
			Parser.create(new Grammar(body, rules)).parse(deferred);
		}
	}

	@Test
	void flatStreamParsesAnIfBlockInTheSameRunAndDefersFunctionBodies() throws Exception {
		parseFlat("""
				fun f
				    use g
				    if g
				        var local
				        use local
				fun g
				    use f
				var x
				if x
				    var y
				    use y
				    if y
				        use x
				use x
				""");

		assertEquals(2, deferredBodies.size());
	}

	@Test
	void flatStreamRemovesTheDeclarationsOfAnIfBlockAtItsEnd() {
		String source = """
				var x
				if x
				    var y
				use y
				""";

		SyntaxException exception = assertThrows(SyntaxException.class, () -> parseFlat(source));

		// Positions count all tokens of the main run, including INDENT and DEDENT: var x EOL if x EOL INDENT var y
		// EOL DEDENT use y
		assertEquals(12, exception.position());
	}

	@Test
	void flatStreamReportsErrorsInDeferredBodiesWithTokenPositionsOfTheirOwnRun() {
		String source = """
				fun f
				    use g
				""";

		SyntaxException exception = assertThrows(SyntaxException.class, () -> parseFlat(source));

		Token token = (Token) exception.token().orElseThrow();
		assertEquals(1, exception.position());
		assertEquals("2:9", token.line() + ":" + token.column());
	}

	// Block as a single token, parsed by a nested run whose declarations remain visible after the block

	private final Terminal block = kind(Kind.BLOCK, "indented block");

	/** The names declared so far in the outer run, recorded for building the grammar of the nested run. */
	private final List<Rule> visible = new ArrayList<>();

	/** The rules declared by the nested run in progress. */
	private final Deque<List<Rule>> nestedDeclarations = new ArrayDeque<>();

	private List<Rule> nestedBaseRules() {
		return List.of(new Rule(body, List.of()), new Rule(body, List.of(body, statement)),
				// Statement -> 'var' word EOL
				new Rule(statement, List.of(keyword("var"), word, endOfLine), match -> {
					Rule rule = nameRule(text(match, 1));
					match.grammar().add(rule);
					(nestedDeclarations.isEmpty() ? visible : nestedDeclarations.peek()).add(rule);
					return ParseNode.of(match);
				}),
				// Statement -> 'use' Name EOL
				new Rule(statement, List.of(keyword("use"), name, endOfLine)),
				// Statement -> 'public' BLOCK: the declarations of the block remain visible after it
				new Rule(statement, List.of(keyword("public"), block), this::publicBlock));
	}

	private Object publicBlock(Match match) {
		List<Rule> rules = new ArrayList<>(nestedBaseRules());
		rules.addAll(visible);
		nestedDeclarations.push(new ArrayList<>());
		List<Rule> declared;
		try {
			Parser.create(new Grammar(body, rules)).parse(Lexer.tokens(match.value(1, Token.class).block()));
		} catch (ParseException e) {
			throw new IllegalStateException(e);
		} finally {
			declared = nestedDeclarations.pop();
		}
		// The action of the outer match is still running, so its GrammarEditor is still valid: the declarations of
		// the nested run are added to the outer run here.
		for (Rule rule : declared) {
			match.grammar().add(rule);
			visible.add(rule);
		}
		return ParseNode.of(match);
	}

	@Test
	void declarationsOfANestedRunCanBeAddedToTheOuterRunAfterwards() throws Exception {
		String source = """
				var x
				public
				    use x
				    var y
				use y
				""";

		Parser.create(new Grammar(body, nestedBaseRules())).parse(Lexer.tokens(StructureReader.read(source)));

		assertEquals(2, visible.size());
	}
}
