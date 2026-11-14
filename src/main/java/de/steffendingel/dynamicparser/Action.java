package de.steffendingel.dynamicparser;

/**
 * The action of a {@link Rule}: computes the value of a {@link Match} of the rule and can change the grammar.
 * <p>
 * The parser guarantees the following:
 * <ul>
 * <li>The action is called exactly once for every match of the rule that is part of the result of parsing. It is never
 * called for an alternative that the parser discards.</li>
 * <li>The action is called only after the match is confirmed, that is, when it is part of every way in which the input
 * read so far can still be interpreted. This can be later than the moment the parser reaches the end of the match. The
 * action is called as soon as the match and all matches before it are confirmed, before the next token is read.</li>
 * <li>Actions are called in the order of a bottom-up, left-to-right traversal of the result: the actions of the matches
 * of the symbols on the right-hand side are called before the action of the rule itself, from left to right.</li>
 * </ul>
 * An action cannot reject a match. An unchecked exception thrown by an action aborts parsing and is passed on to the
 * caller of {@link Parser#parse(Iterable)} unchanged.
 */
@FunctionalInterface
public interface Action {

	/**
	 * Computes the value of a match.
	 *
	 * @param match the match; it must only be used until this method returns
	 * @return the value of the match, which is passed to the action of the enclosing match or, for a match of the start
	 *         symbol, returned by the parser; may be {@code null}
	 */
	Object apply(Match match);
}
