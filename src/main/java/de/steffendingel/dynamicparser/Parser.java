package de.steffendingel.dynamicparser;

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
	 * Parses a sequence of tokens.
	 * <p>
	 * The complete sequence must be a match of the start symbol of the grammar. The parser calls the {@link Action
	 * actions} of all matches the result consists of and returns the value of the match of the start symbol.
	 * <p>
	 * The tokens are read one at a time and in order. An action that changes the grammar is called before the token
	 * that follows its match is read.
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
