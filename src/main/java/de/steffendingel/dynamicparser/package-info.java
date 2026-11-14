/**
 * The public API of the dynamic parser: a generic parser whose grammar can be modified while parsing.
 * <p>
 * A {@link de.steffendingel.dynamicparser.Grammar} consists of a start symbol and
 * {@link de.steffendingel.dynamicparser.Rule rules}. Every rule has an {@link de.steffendingel.dynamicparser.Action}
 * that computes the value of a {@link de.steffendingel.dynamicparser.Match} of the rule and that can add rules to and
 * remove rules from the grammar through a {@link de.steffendingel.dynamicparser.GrammarEditor}. Such changes apply to
 * the input that follows the match. A {@link de.steffendingel.dynamicparser.Parser} parses a sequence of tokens and
 * returns the value of the match of the start symbol.
 * <p>
 * The behavior of the parser is specified in {@code docs/architecture.md} in the source repository.
 */
package de.steffendingel.dynamicparser;
