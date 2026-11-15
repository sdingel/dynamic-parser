package de.steffendingel.dynamicparser.internal;

/**
 * An element of a forest: a token, a match or an ambiguous section.
 * <p>
 * A forest is a sequence of entries that covers a section of the input from left to right. It describes what has been
 * recognized in that section: the matches that are complete, with everything they consist of, and the tokens that are
 * not part of a complete match. Entries are unique objects: two entries with the same content are the same object, so
 * they can be compared by identity (see {@link Forests}).
 */
sealed interface Entry permits TokenEntry, Node, AmbiguousNode {
}
