package de.steffendingel.dynamicparser.internal;

import java.util.List;

import de.steffendingel.dynamicparser.GrammarEditor;
import de.steffendingel.dynamicparser.Match;
import de.steffendingel.dynamicparser.Rule;

/**
 * The match passed to an action.
 */
record MatchImpl(Rule rule, int start, int end, List<Object> values, GrammarEditor grammar) implements Match {
}
