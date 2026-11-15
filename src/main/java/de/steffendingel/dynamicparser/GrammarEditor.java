package de.steffendingel.dynamicparser;

/**
 * Changes the grammar of a running parse. An {@link Action} obtains the editor from {@link Match#grammar()}.
 * <p>
 * Changes apply to the input that follows the match of the action:
 * <ul>
 * <li>An added rule can begin matches at or after the end position of the match of the action.</li>
 * <li>A removed rule begins no new matches from that position on. Matches of the rule that have already begun are
 * completed.</li>
 * </ul>
 * A change exists from the moment the action makes it. Calling an action is binding: its match is part of the result,
 * and interpretations of the input that do not contain it are no longer considered, even if a rule added later would
 * make them possible.
 * <p>
 * Changes belong to the running parse only. They do not modify the {@link Grammar} the parser was created with, and
 * they are discarded when the parse ends.
 * <p>
 * <b>Strict timing.</b> An action can only change the grammar if it runs before the parser reads the token that follows
 * its match. An action runs later if the parser needs more input to be certain that the match is part of the result. In
 * that case {@link #add(Rule)} and {@link #remove(Rule)} throw a {@link LateGrammarChangeException}.
 * <p>
 * The editor must only be used while the action it was passed to is running.
 */
public interface GrammarEditor {

	/**
	 * Adds a rule to the grammar. A rule that was removed earlier can be added again.
	 *
	 * @param rule the rule to add
	 * @return {@code true} if the rule was added, {@code false} if it was already in the grammar
	 * @throws NullPointerException       if {@code rule} is {@code null}
	 * @throws LateGrammarChangeException if the action runs too late to change the grammar, even if the rule is already
	 *                                    in the grammar
	 * @throws IllegalStateException      if the action has already returned
	 */
	boolean add(Rule rule);

	/**
	 * Removes a rule from the grammar.
	 *
	 * @param rule the rule to remove
	 * @return {@code true} if the rule was removed, {@code false} if it was not in the grammar
	 * @throws NullPointerException       if {@code rule} is {@code null}
	 * @throws LateGrammarChangeException if the action runs too late to change the grammar, even if the rule is not in
	 *                                    the grammar
	 * @throws IllegalStateException      if the action has already returned
	 */
	boolean remove(Rule rule);

	/**
	 * Returns whether a rule is in the grammar that is in effect at the current position of the parser, including the
	 * changes already made by the running action.
	 *
	 * @param rule the rule to look for
	 * @return {@code true} if the rule is in the grammar
	 * @throws NullPointerException  if {@code rule} is {@code null}
	 * @throws IllegalStateException if the action has already returned
	 */
	boolean contains(Rule rule);
}
