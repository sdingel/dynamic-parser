package de.steffendingel.dynamicparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Compares the parser with a simple reference implementation of the specification, for many small random grammars and
 * inputs. In about half of the grammars, actions add and remove rules.
 * <p>
 * The reference enumerates all readings of every beginning of the input explicitly, as the runs of a nondeterministic
 * bottom-up parser: a reading is a way to shift the tokens read so far and to reduce matches such that the next token
 * can be shifted or the input can end. For every reading it records the sequence of reductions and the matches they
 * create. The actions that may have been called at a position are the reductions that the sequences of all readings
 * begin with. The input is ambiguous at a position if the sequences of all readings continue within a match of the same
 * nonterminal and the same section that has a different structure in some of them. When an action changes the grammar,
 * the readings of the position are enumerated again: only runs that begin with the reductions called so far are
 * considered, and every reduction is followed by the rules that were in the grammar after it. This takes exponential
 * time and does not terminate for some grammars, so inputs for which the enumeration exceeds a limit are skipped.
 */
class ParserReferenceTest {

	private static final int GRAMMARS = 5000;

	/** The maximum number of reductions of a reading between two tokens before the reference gives up. */
	private static final int MAX_REDUCTIONS = 12;

	/** The maximum number of readings at one position before the reference gives up. */
	private static final int MAX_CONFIGURATIONS = 3000;

	private static final String[] TOKENS = { "a", "b", "c" };

	// A random grammar. Symbols are numbers: nonterminals from 0 (the start symbol), terminal i is -1 - i.

	/**
	 * @param initial whether the rule is in the initial grammar
	 * @param added   the rules that the action of this rule adds to the grammar
	 * @param removed the rules that the action of this rule removes from the grammar, after adding
	 */
	private record RandomRule(int lhs, int[] rhs, boolean initial, List<Integer> added, List<Integer> removed) {

		boolean changesGrammar() {
			return !added.isEmpty() || !removed.isEmpty();
		}
	}

	private static List<RandomRule> randomGrammar(Random random) {
		int nonterminals = 1 + random.nextInt(3);
		int terminals = 1 + random.nextInt(TOKENS.length);
		int count = 2 + random.nextInt(5);
		boolean dynamic = random.nextBoolean();
		List<RandomRule> rules = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			int lhs = i == 0 ? 0 : random.nextInt(nonterminals);
			int length = new int[] { 0, 1, 1, 2, 2, 2, 3 }[random.nextInt(7)];
			int[] rhs = new int[length];
			for (int j = 0; j < length; j++) {
				rhs[j] = random.nextInt(100) < 55 ? -1 - random.nextInt(terminals) : random.nextInt(nonterminals);
			}
			List<Integer> added = new ArrayList<>();
			List<Integer> removed = new ArrayList<>();
			if (dynamic && random.nextInt(100) < 35) {
				for (int j = 1 + random.nextInt(2); j > 0; j--) {
					(random.nextInt(100) < 65 ? added : removed).add(random.nextInt(count));
				}
			}
			rules.add(new RandomRule(lhs, rhs, i == 0 || !dynamic || random.nextInt(100) < 65, added, removed));
		}
		return rules;
	}

	/**
	 * Derives a random sentence from the start symbol, using all rules, or returns {@code null} if the derivation gets
	 * too long.
	 */
	private static List<String> randomSentence(List<RandomRule> rules, Random random) {
		List<Integer> symbols = new ArrayList<>(List.of(0));
		for (int step = 0; step < 30; step++) {
			int index = -1;
			for (int i = 0; i < symbols.size() && index < 0; i++) {
				if (symbols.get(i) >= 0) {
					index = i;
				}
			}
			if (index < 0) {
				return symbols.size() > 7 ? null : symbols.stream().map(symbol -> TOKENS[-1 - symbol]).toList();
			}
			int nonterminal = symbols.get(index);
			List<RandomRule> candidates = rules.stream().filter(rule -> rule.lhs() == nonterminal).toList();
			if (candidates.isEmpty() || symbols.size() > 10) {
				return null;
			}
			RandomRule rule = candidates.get(random.nextInt(candidates.size()));
			symbols.remove(index);
			for (int i = rule.rhs().length - 1; i >= 0; i--) {
				symbols.add(index, rule.rhs()[i]);
			}
		}
		return null;
	}

	private static List<String> randomTokens(Random random) {
		List<String> tokens = new ArrayList<>();
		for (int i = random.nextInt(6); i > 0; i--) {
			tokens.add(TOKENS[random.nextInt(TOKENS.length)]);
		}
		return tokens;
	}

	// The reference implementation

	private record LrItem(int rule, int dot) {
	}

	/**
	 * A token or match on the stack of the bottom-up parser.
	 *
	 * @param start the position at which it begins
	 * @param tree  its structure as text
	 * @param first the index of the first reduction within it, or -1 if there is none
	 */
	private record StackEntry(int start, String tree, int first) {
	}

	/**
	 * A match created by a reduction.
	 *
	 * @param section the nonterminal and the section of the input
	 * @param tree    its structure as text
	 * @param first   the index of the first reduction within it
	 * @param last    the index of the reduction that created it
	 */
	private record Match(String section, String tree, int first, int last) {
	}

	/**
	 * A reduction: the rule and the section of the input.
	 */
	private record Reduction(int rule, int start, int end) {

		@Override
		public String toString() {
			return "rule " + rule + " from " + start + " to " + end;
		}
	}

	/**
	 * A run of the nondeterministic bottom-up parser.
	 *
	 * @param states     the stack of states, each one a set of items
	 * @param entries    for every state but the first one, the token or match leading to it
	 * @param reductions the reductions made so far
	 * @param matches    the matches created by the reductions
	 * @param sinceShift the number of reductions since the last token was shifted
	 * @param accepted   whether the complete input read so far has been reduced to the start symbol
	 */
	private record Configuration(List<Set<LrItem>> states, List<StackEntry> entries, List<Reduction> reductions,
			List<Match> matches, int sinceShift, boolean accepted) {
	}

	private static final class LimitExceeded extends RuntimeException {

		private static final long serialVersionUID = 1L;
	}

	private static final class Reference {

		private final List<RandomRule> rules;

		/** The reductions whose actions have been called. */
		private final List<Reduction> called = new ArrayList<>();

		/** The rules in the grammar after each number of called actions. */
		private final List<Set<Integer>> grammars = new ArrayList<>();

		Reference(List<RandomRule> rules) {
			this.rules = rules;
			Set<Integer> initial = new HashSet<>();
			for (int i = 0; i < rules.size(); i++) {
				if (rules.get(i).initial()) {
					initial.add(i);
				}
			}
			grammars.add(initial);
		}

		Set<Integer> grammar() {
			return grammars.get(grammars.size() - 1);
		}

		/**
		 * Records that the action of a reduction has been called.
		 *
		 * @return {@code true} if the action has changed the grammar
		 */
		boolean call(Reduction reduction) {
			Set<Integer> grammar = new HashSet<>(grammar());
			grammar.addAll(rules.get(reduction.rule()).added());
			grammar.removeAll(rules.get(reduction.rule()).removed());
			called.add(reduction);
			grammars.add(grammar);
			return !grammar.equals(grammars.get(grammars.size() - 2));
		}

		private Set<LrItem> closure(Set<LrItem> kernel, Set<Integer> grammar) {
			Set<LrItem> state = new LinkedHashSet<>(kernel);
			List<LrItem> pending = new ArrayList<>(kernel);
			while (!pending.isEmpty()) {
				LrItem item = pending.remove(pending.size() - 1);
				int[] rhs = rules.get(item.rule()).rhs();
				if (item.dot() < rhs.length && rhs[item.dot()] >= 0) {
					for (int i : grammar) {
						LrItem predicted = new LrItem(i, 0);
						if (rules.get(i).lhs() == rhs[item.dot()] && state.add(predicted)) {
							pending.add(predicted);
						}
					}
				}
			}
			return state;
		}

		private Set<LrItem> advance(Set<LrItem> state, int symbol) {
			Set<LrItem> kernel = new LinkedHashSet<>();
			for (LrItem item : state) {
				int[] rhs = rules.get(item.rule()).rhs();
				if (item.dot() < rhs.length && rhs[item.dot()] == symbol) {
					kernel.add(new LrItem(item.rule(), item.dot() + 1));
				}
			}
			return kernel;
		}

		Configuration initial() {
			Set<LrItem> kernel = new LinkedHashSet<>();
			for (int i : grammar()) {
				if (rules.get(i).lhs() == 0) {
					kernel.add(new LrItem(i, 0));
				}
			}
			return new Configuration(List.of(closure(kernel, grammar())), List.of(), List.of(), List.of(), 0, false);
		}

		/**
		 * Returns the given configurations and all configurations that result from them by reductions. A configuration
		 * must make the reductions whose actions have been called, in the same order, before it can make others.
		 */
		List<Configuration> reduce(List<Configuration> configurations, int position) {
			List<Configuration> result = new ArrayList<>(configurations);
			for (int i = 0; i < result.size(); i++) {
				Configuration configuration = result.get(i);
				if (configuration.accepted()) {
					continue;
				}
				List<Set<LrItem>> states = configuration.states();
				for (LrItem item : states.get(states.size() - 1)) {
					RandomRule rule = rules.get(item.rule());
					int length = rule.rhs().length;
					if (item.dot() < length) {
						continue;
					}
					int remaining = states.size() - length;
					List<StackEntry> parts = configuration.entries().subList(remaining - 1, states.size() - 1);
					int start = length == 0 ? position : parts.get(0).start();
					int index = configuration.reductions().size();
					Reduction reduction = new Reduction(item.rule(), start, position);
					if (index < called.size() && !called.get(index).equals(reduction)) {
						continue;
					}
					if (configuration.sinceShift() >= MAX_REDUCTIONS || result.size() >= MAX_CONFIGURATIONS) {
						throw new LimitExceeded();
					}
					// The rules that can begin after the reduction are the ones in the grammar after its action.
					Set<Integer> grammar = index < called.size() ? grammars.get(index + 1) : grammar();
					int first = parts.stream().mapToInt(StackEntry::first).filter(value -> value >= 0).findFirst()
							.orElse(index);
					String tree = "(" + item.rule()
							+ parts.stream().map(part -> " " + part.tree()).reduce("", String::concat) + ")";
					List<Reduction> reductions = new ArrayList<>(configuration.reductions());
					reductions.add(reduction);
					List<Match> matches = new ArrayList<>(configuration.matches());
					matches.add(new Match("N" + rule.lhs() + " from " + start + " to " + position, tree, first, index));
					if (rule.lhs() == 0 && remaining == 1) {
						result.add(new Configuration(List.of(), List.of(), reductions, matches, 0, true));
					}
					Set<LrItem> kernel = advance(states.get(remaining - 1), rule.lhs());
					if (!kernel.isEmpty()) {
						List<Set<LrItem>> newStates = new ArrayList<>(states.subList(0, remaining));
						newStates.add(closure(kernel, grammar));
						List<StackEntry> newEntries = new ArrayList<>(
								configuration.entries().subList(0, remaining - 1));
						newEntries.add(new StackEntry(start, tree, first));
						result.add(new Configuration(newStates, newEntries, reductions, matches,
								configuration.sinceShift() + 1, false));
					}
				}
			}
			// Configurations that have not made all the reductions called so far are not possible any more.
			result.removeIf(configuration -> configuration.reductions().size() < called.size());
			return result;
		}

		List<Configuration> shift(List<Configuration> configurations, String token, int position) {
			int terminal = -1 - List.of(TOKENS).indexOf(token);
			List<Configuration> result = new ArrayList<>();
			for (Configuration configuration : configurations) {
				if (configuration.accepted()) {
					continue;
				}
				List<Set<LrItem>> states = configuration.states();
				Set<LrItem> kernel = advance(states.get(states.size() - 1), terminal);
				if (!kernel.isEmpty()) {
					List<Set<LrItem>> newStates = new ArrayList<>(states);
					newStates.add(closure(kernel, grammar()));
					List<StackEntry> newEntries = new ArrayList<>(configuration.entries());
					newEntries.add(new StackEntry(position, token, -1));
					result.add(new Configuration(newStates, newEntries, configuration.reductions(),
							configuration.matches(), 0, false));
				}
			}
			return result;
		}

		/**
		 * Returns whether the configuration can take a token.
		 */
		boolean canShift(Configuration configuration) {
			return !configuration.accepted()
					&& configuration.states().get(configuration.states().size() - 1).stream().anyMatch(item -> {
						int[] rhs = rules.get(item.rule()).rhs();
						return item.dot() < rhs.length && rhs[item.dot()] < 0;
					});
		}

		/**
		 * Returns whether the configuration waits for a nonterminal that has no rules.
		 */
		boolean waitsForRules(Configuration configuration) {
			if (configuration.accepted()) {
				return false;
			}
			Set<LrItem> top = configuration.states().get(configuration.states().size() - 1);
			return top.stream().anyMatch(item -> {
				int[] rhs = rules.get(item.rule()).rhs();
				return item.dot() < rhs.length && rhs[item.dot()] >= 0 && top.stream()
						.noneMatch(other -> other.dot() == 0 && rules.get(other.rule()).lhs() == rhs[item.dot()]);
			});
		}

		/**
		 * Returns the readings among the given configurations.
		 */
		List<Configuration> readings(List<Configuration> configurations, boolean endOfInput) {
			if (endOfInput) {
				return configurations.stream().filter(Configuration::accepted).toList();
			}
			List<Configuration> readings = configurations.stream()
					.filter(configuration -> configuration.accepted() || canShift(configuration)).toList();
			// If the input cannot continue with the current rules, the readings are the configurations that could
			// continue if rules were added.
			return readings.isEmpty() ? configurations.stream().filter(this::waitsForRules).toList() : readings;
		}
	}

	private static List<Reduction> commonPrefix(List<Configuration> readings) {
		List<Reduction> common = new ArrayList<>(readings.get(0).reductions());
		for (Configuration reading : readings) {
			List<Reduction> sequence = reading.reductions();
			int length = 0;
			while (length < common.size() && length < sequence.size()
					&& common.get(length).equals(sequence.get(length))) {
				length++;
			}
			common.subList(length, common.size()).clear();
		}
		return common;
	}

	/**
	 * Returns the sections that are certain to be ambiguous: every reading continues, after what all readings have in
	 * common, within a match of the section, and the matches do not all have the same structure.
	 *
	 * @param common the number of reductions that the sequences of all readings begin with
	 */
	private static Set<String> ambiguousSections(List<Configuration> readings, int common) {
		Set<String> sections = new HashSet<>();
		for (Match candidate : readings.get(0).matches()) {
			Set<String> trees = new HashSet<>();
			boolean inAllReadings = true;
			for (Configuration reading : readings) {
				List<String> found = reading.matches().stream()
						.filter(match -> match.section().equals(candidate.section()) && match.first() <= common
								&& common <= match.last())
						.map(Match::tree).toList();
				inAllReadings &= !found.isEmpty();
				trees.addAll(found);
			}
			if (inAllReadings && trees.size() > 1) {
				sections.add(candidate.section());
			}
		}
		return sections;
	}

	/**
	 * What the specification demands for an input.
	 *
	 * @param outcome  "accepted", "syntax error", "ambiguous" or "late change"
	 * @param position the number of tokens read when parsing ends
	 * @param calls    the calls of actions, each one with the number of tokens read at the time of the call
	 * @param sections for an ambiguous input, the sections that can be reported
	 */
	private record Expectation(String outcome, int position, List<String> calls, Set<String> sections) {
	}

	private static Expectation expect(List<RandomRule> rules, List<String> tokens) {
		Reference reference = new Reference(rules);
		List<String> calls = new ArrayList<>();
		List<Configuration> shifted = List.of(reference.initial());
		int position = 0;
		boolean endOfInput = false;
		while (true) {
			List<Configuration> configurations = reference.reduce(shifted, position);
			List<Configuration> readings = reference.readings(configurations, endOfInput);
			if (readings.isEmpty()) {
				if (endOfInput) {
					return new Expectation("syntax error", position, calls, Set.of());
				}
			} else {
				// Call the actions of the reductions that the sequences of all readings begin with.
				List<Reduction> common = commonPrefix(readings);
				boolean changed = false;
				while (!changed && reference.called.size() < common.size()) {
					Reduction reduction = common.get(reference.called.size());
					calls.add(reduction + " after " + position);
					if (rules.get(reduction.rule()).changesGrammar() && reduction.end() < position) {
						return new Expectation("late change", position, calls, Set.of());
					}
					changed = reference.call(reduction);
				}
				if (changed) {
					// Enumerate the readings again with the new grammar.
					continue;
				}
				Set<String> sections = ambiguousSections(readings, common.size());
				if (!sections.isEmpty()) {
					return new Expectation("ambiguous", position, calls, sections);
				}
				if (endOfInput) {
					return new Expectation("accepted", position, calls, Set.of());
				}
			}
			if (position == tokens.size()) {
				// The parser does not know in advance that the input ends. The position is evaluated once more, now
				// for the readings that accept the end of the input.
				endOfInput = true;
			} else {
				shifted = reference.shift(configurations, tokens.get(position), position);
				if (shifted.isEmpty()) {
					return new Expectation("syntax error", position, calls, Set.of());
				}
				position++;
			}
		}
	}

	/**
	 * Parses the input with the parser and returns the outcome in the same form as the reference.
	 */
	private static Expectation parse(List<RandomRule> randomRules, List<String> tokens) {
		List<Nonterminal> nonterminals = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			nonterminals.add(new Nonterminal("N" + i));
		}
		List<Terminal> terminals = new ArrayList<>();
		for (String token : TOKENS) {
			terminals.add(new Terminal(token, token::equals));
		}
		List<String> calls = new ArrayList<>();
		int[] requested = new int[1];
		List<Rule> rules = new ArrayList<>();
		for (int i = 0; i < randomRules.size(); i++) {
			RandomRule randomRule = randomRules.get(i);
			Reduction[] reduction = new Reduction[1];
			int index = i;
			List<Symbol> rhs = new ArrayList<>();
			for (int symbol : randomRule.rhs()) {
				rhs.add(symbol >= 0 ? nonterminals.get(symbol) : terminals.get(-1 - symbol));
			}
			rules.add(new Rule(nonterminals.get(randomRule.lhs()), rhs, match -> {
				reduction[0] = new Reduction(index, match.start(), match.end());
				calls.add(reduction[0] + " after " + requested[0]);
				randomRule.added().forEach(added -> match.grammar().add(rules.get(added)));
				randomRule.removed().forEach(removed -> match.grammar().remove(rules.get(removed)));
				return null;
			}));
		}
		List<Rule> initial = new ArrayList<>();
		for (int i = 0; i < rules.size(); i++) {
			if (randomRules.get(i).initial()) {
				initial.add(rules.get(i));
			}
		}
		Iterable<String> input = () -> new Iterator<>() {

			private final Iterator<String> iterator = tokens.iterator();

			@Override
			public boolean hasNext() {
				return iterator.hasNext();
			}

			@Override
			public String next() {
				requested[0]++;
				return iterator.next();
			}
		};
		try {
			Parser.create(new Grammar(nonterminals.get(0), initial)).parse(input);
			return new Expectation("accepted", requested[0], calls, Set.of());
		} catch (SyntaxException exception) {
			return new Expectation("syntax error", exception.position(), calls, Set.of());
		} catch (AmbiguityException exception) {
			return new Expectation("ambiguous", requested[0], calls,
					Set.of(exception.symbol() + " from " + exception.start() + " to " + exception.end()));
		} catch (LateGrammarChangeException exception) {
			return new Expectation("late change", requested[0], calls, Set.of());
		} catch (ParseException exception) {
			throw new AssertionError(exception);
		}
	}

	private static String describe(List<RandomRule> rules, List<String> tokens) {
		StringBuilder description = new StringBuilder("input " + tokens + " with the rules");
		for (int i = 0; i < rules.size(); i++) {
			RandomRule rule = rules.get(i);
			description.append("\n  rule ").append(i).append(": N").append(rule.lhs()).append(" ->");
			for (int symbol : rule.rhs()) {
				description.append(' ').append(symbol >= 0 ? "N" + symbol : TOKENS[-1 - symbol]);
			}
			description.append(rule.initial() ? "" : "   (not in the initial grammar)");
			description.append(rule.added().isEmpty() ? "" : "   adds " + rule.added());
			description.append(rule.removed().isEmpty() ? "" : "   removes " + rule.removed());
		}
		return description.toString();
	}

	@Test
	void parserBehavesLikeTheReferenceForRandomGrammars() {
		Random random = new Random(20240607);
		int compared = 0;
		int accepted = 0;
		int ambiguous = 0;
		int late = 0;
		int changed = 0;
		for (int i = 0; i < GRAMMARS; i++) {
			List<RandomRule> rules = randomGrammar(random);
			Set<List<String>> inputs = new LinkedHashSet<>();
			for (int j = 0; j < 12; j++) {
				List<String> sentence = randomSentence(rules, random);
				if (sentence != null) {
					inputs.add(sentence);
				}
			}
			for (int j = 0; j < 4; j++) {
				inputs.add(randomTokens(random));
			}
			for (List<String> tokens : inputs) {
				Expectation actual = parse(rules, tokens);
				Expectation expected;
				try {
					expected = expect(rules, tokens);
				} catch (LimitExceeded exception) {
					continue;
				}
				if (expected.sections().containsAll(actual.sections())) {
					// Of several ambiguous sections, the parser reports one.
					expected = new Expectation(expected.outcome(), expected.position(), expected.calls(),
							actual.sections());
				}
				assertEquals(expected, actual, () -> describe(rules, tokens));
				compared++;
				accepted += "accepted".equals(actual.outcome()) ? 1 : 0;
				ambiguous += "ambiguous".equals(actual.outcome()) ? 1 : 0;
				late += "late change".equals(actual.outcome()) ? 1 : 0;
				boolean grammarChanged = actual.calls().stream()
						.anyMatch(call -> rules.get(Integer.parseInt(call.split(" ")[1])).changesGrammar());
				changed += grammarChanged && !"late change".equals(actual.outcome()) ? 1 : 0;
			}
		}
		// Make sure that the test compares a substantial number of inputs of every kind.
		assertTrue(compared > 20_000, "compared: " + compared);
		assertTrue(accepted > 4_000, "accepted: " + accepted);
		assertTrue(ambiguous > 1_500, "ambiguous: " + ambiguous);
		assertTrue(changed > 1_000, "inputs for which an action changed the grammar: " + changed);
		assertTrue(late > 50, "late changes: " + late);
	}
}
