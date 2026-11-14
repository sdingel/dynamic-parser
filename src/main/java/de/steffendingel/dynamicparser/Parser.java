package de.steffendingel.dynamicparser;

import de.steffendingel.dynamicparser.internal.EarleyParser;

/**
 * Parses sequences of tokens according to a {@link Grammar} that can change while parsing.
 * <p>
 * Every call of {@link #parse(Iterable)} is an independent parse run. It starts with the initial grammar the parser was
 * created with; the changes made by actions during the run are discarded when it ends. A parser has no state of its own
 * and can be used for any number of parse runs, also at the same time from several threads if the actions and terminals
 * of the grammar allow that.
 * <p>
 * The behavior is specified in detail in {@code docs/architecture.md} in the source repository.
 */
public interface Parser {

	/**
	 * Creates a parser for the given grammar.
	 * <p>
	 * <b>Implementation status:</b> Actions cannot change the grammar yet: {@link GrammarEditor#add(Rule)} and
	 * {@link GrammarEditor#remove(Rule)} throw an {@link UnsupportedOperationException}.
	 *
	 * @param grammar the initial grammar of every parse run
	 * @return a new parser
	 * @throws NullPointerException if {@code grammar} is {@code null}
	 */
	static Parser create(Grammar grammar) {
		return new EarleyParser(grammar);
	}

	/**
	 * Parses a sequence of tokens.
	 * <p>
	 * The complete sequence must be a match of the start symbol of the grammar. The parser calls the {@link Action
	 * actions} of all matches the result consists of and returns the value of the match of the start symbol.
	 * <p>
	 * The tokens are read one at a time and in order. Before a token is read, all actions have been called that can be
	 * called for the input read so far, see {@link Action}. If parsing fails, the actions called up to that point
	 * remain called.
	 *
	 * @param tokens the tokens to parse; arbitrary objects other than {@code null}
	 * @return the value returned by the action of the match of the start symbol; may be {@code null}
	 * @throws SyntaxException      if the tokens do not match the grammar
	 * @throws AmbiguityException   if the tokens match the grammar in more than one way
	 * @throws ParseException       if parsing fails for another reason related to the input
	 * @throws NullPointerException if {@code tokens} or one of the tokens is {@code null}
	 * @throws RuntimeException     if an action throws an unchecked exception, in particular a
	 *                              {@link LateGrammarChangeException}; the exception is passed on unchanged
	 */
	Object parse(Iterable<?> tokens) throws ParseException;
}
