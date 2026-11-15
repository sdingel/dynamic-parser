package de.steffendingel.dynamicparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
 * inputs.
 * <p>
 * The reference enumerates all readings of every beginning of the input explicitly, as the runs of a nondeterministic
 * bottom-up parser: a reading is a way to shift the tokens read so far and to reduce matches such that the next token
 * can be shifted or the input can end. For every reading it records the sequence of reductions and the matches they
 * create. The actions that may have been called at a position are the reductions that the sequences of all readings
 * begin with. The input is ambiguous at a position if the sequences of all readings continue within a match of the same
 * nonterminal and the same section that has a different structure in some of them. This takes exponential time and does
 * not terminate for some grammars, so inputs for which the enumeration exceeds a limit are skipped.
 */
class ParserReferenceTest {

	private static final int GRAMMARS = 4000;

	/** The maximum number of reductions of a reading between two tokens before the reference gives up. */
	private static final int MAX_REDUCTIONS = 12;

	/** The maximum number of readings at one position before the reference gives up. */
	private static final int MAX_CONFIGURATIONS = 3000;

	private static final String[] TOKENS = { "a", "b", "c" };

	// A random grammar. Symbols are numbers: nonterminals from 0 (the start symbol), terminal i is -1 - i.

	private record RandomRule(int lhs, int[] rhs) {
	}

	private static List<RandomRule> randomGrammar(Random random) {
		int nonterminals = 1 + random.nextInt(3);
		int terminals = 1 + random.nextInt(TOKENS.length);
		int count = 2 + random.nextInt(5);
		List<RandomRule> rules = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			int lhs = i == 0 ? 0 : random.nextInt(nonterminals);
			int length = new int[] { 0, 1, 1, 2, 2, 2, 3 }[random.nextInt(7)];
			int[] rhs = new int[length];
			for (int j = 0; j < length; j++) {
				rhs[j] = random.nextInt(100) < 55 ? -1 - random.nextInt(terminals) : random.nextInt(nonterminals);
			}
			rules.add(new RandomRule(lhs, rhs));
		}
		return rules;
	}

	/**
	 * Derives a random sentence from the start symbol, or returns {@code null} if the derivation gets too long.
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
	 * A run of the nondeterministic bottom-up parser.
	 *
	 * @param states     the stack of states, each one a set of items
	 * @param entries    for every state but the first one, the token or match leading to it
	 * @param reductions the reductions made so far, each one as rule, start position and end position
	 * @param matches    the matches created by the reductions
	 * @param sinceShift the number of reductions since the last token was shifted
	 * @param accepted   whether the complete input read so far has been reduced to the start symbol
	 */
	private record Configuration(List<Set<LrItem>> states, List<StackEntry> entries, List<String> reductions,
			List<Match> matches, int sinceShift, boolean accepted) {
	}

	private static final class LimitExceeded extends RuntimeException {

		private static final long serialVersionUID = 1L;
	}

	private static final class Reference {

		private final List<RandomRule> rules;

		Reference(List<RandomRule> rules) {
			this.rules = rules;
		}

		private Set<LrItem> closure(Set<LrItem> kernel) {
			Set<LrItem> state = new LinkedHashSet<>(kernel);
			List<LrItem> pending = new ArrayList<>(kernel);
			while (!pending.isEmpty()) {
				LrItem item = pending.remove(pending.size() - 1);
				int[] rhs = rules.get(item.rule()).rhs();
				if (item.dot() < rhs.length && rhs[item.dot()] >= 0) {
					for (int i = 0; i < rules.size(); i++) {
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
			for (int i = 0; i < rules.size(); i++) {
				if (rules.get(i).lhs() == 0) {
					kernel.add(new LrItem(i, 0));
				}
			}
			return new Configuration(List.of(closure(kernel)), List.of(), List.of(), List.of(), 0, false);
		}

		/**
		 * Returns the given configurations and all configurations that result from them by reductions.
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
					if (configuration.sinceShift() >= MAX_REDUCTIONS || result.size() >= MAX_CONFIGURATIONS) {
						throw new LimitExceeded();
					}
					int remaining = states.size() - length;
					List<StackEntry> parts = configuration.entries().subList(remaining - 1, states.size() - 1);
					int start = length == 0 ? position : parts.get(0).start();
					int index = configuration.reductions().size();
					int first = parts.stream().mapToInt(StackEntry::first).filter(value -> value >= 0).findFirst()
							.orElse(index);
					String tree = "(" + item.rule()
							+ parts.stream().map(part -> " " + part.tree()).reduce("", String::concat) + ")";
					List<String> reductions = new ArrayList<>(configuration.reductions());
					reductions.add(event(item.rule(), start, position));
					List<Match> matches = new ArrayList<>(configuration.matches());
					matches.add(new Match("N" + rule.lhs() + " from " + start + " to " + position, tree, first, index));
					if (rule.lhs() == 0 && remaining == 1) {
						result.add(new Configuration(List.of(), List.of(), reductions, matches, 0, true));
					}
					Set<LrItem> kernel = advance(states.get(remaining - 1), rule.lhs());
					if (!kernel.isEmpty()) {
						List<Set<LrItem>> newStates = new ArrayList<>(states.subList(0, remaining));
						newStates.add(closure(kernel));
						List<StackEntry> newEntries = new ArrayList<>(
								configuration.entries().subList(0, remaining - 1));
						newEntries.add(new StackEntry(start, tree, first));
						result.add(new Configuration(newStates, newEntries, reductions, matches,
								configuration.sinceShift() + 1, false));
					}
				}
			}
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
					newStates.add(closure(kernel));
					List<StackEntry> newEntries = new ArrayList<>(configuration.entries());
					newEntries.add(new StackEntry(position, token, -1));
					result.add(new Configuration(newStates, newEntries, configuration.reductions(),
							configuration.matches(), 0, false));
				}
			}
			return result;
		}

		/**
		 * Returns whether the configuration is a reading: it can take a token or has accepted the input.
		 */
		boolean isReading(Configuration configuration) {
			if (configuration.accepted()) {
				return true;
			}
			Set<LrItem> top = configuration.states().get(configuration.states().size() - 1);
			return top.stream().anyMatch(item -> {
				int[] rhs = rules.get(item.rule()).rhs();
				return item.dot() < rhs.length && rhs[item.dot()] < 0;
			});
		}
	}

	private static String event(int rule, int start, int end) {
		return "rule " + rule + " from " + start + " to " + end;
	}

	private static List<String> commonPrefix(List<List<String>> sequences) {
		List<String> common = new ArrayList<>(sequences.get(0));
		for (List<String> sequence : sequences) {
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
	 * @param outcome  "accepted", "syntax error" or "ambiguous"
	 * @param position the number of tokens read when parsing ends
	 * @param calls    the calls of actions, each one with the number of tokens read at the time of the call
	 * @param sections for an ambiguous input, the sections that can be reported
	 */
	private record Expectation(String outcome, int position, List<String> calls, Set<String> sections) {
	}

	private static Expectation expect(List<RandomRule> rules, List<String> tokens) {
		Reference reference = new Reference(rules);
		List<String> calls = new ArrayList<>();
		int called = 0;
		List<Configuration> configurations = List.of(reference.initial());
		for (int position = 0;; position++) {
			configurations = reference.reduce(configurations, position);
			// The parser does not know yet whether the input ends here, so the readings are the ones that can take
			// another token as well as the ones that accept the end of the input.
			List<Configuration> readings = configurations.stream().filter(reference::isReading).toList();
			if (position == tokens.size()) {
				called = call(readings, called, position, calls);
				Set<String> sections = readings.isEmpty() ? Set.of() : ambiguousSections(readings, called);
				if (!sections.isEmpty()) {
					return new Expectation("ambiguous", position, calls, sections);
				}
				// At the end of the input, the readings are the ones that accept it.
				readings = readings.stream().filter(Configuration::accepted).toList();
				if (readings.isEmpty()) {
					return new Expectation("syntax error", position, calls, Set.of());
				}
				called = call(readings, called, position, calls);
				sections = ambiguousSections(readings, called);
				return new Expectation(sections.isEmpty() ? "accepted" : "ambiguous", position, calls, sections);
			}
			if (!readings.isEmpty()) {
				called = call(readings, called, position, calls);
				Set<String> sections = ambiguousSections(readings, called);
				if (!sections.isEmpty()) {
					return new Expectation("ambiguous", position, calls, sections);
				}
			}
			configurations = reference.shift(configurations, tokens.get(position), position);
			if (configurations.isEmpty()) {
				return new Expectation("syntax error", position, calls, Set.of());
			}
		}
	}

	/**
	 * Adds the calls for the reductions that the sequences of all readings begin with and that have not been called
	 * yet.
	 *
	 * @return the number of reductions called so far
	 */
	private static int call(List<Configuration> readings, int called, int position, List<String> calls) {
		if (readings.isEmpty()) {
			return called;
		}
		List<String> common = commonPrefix(readings.stream().map(Configuration::reductions).toList());
		for (int i = called; i < common.size(); i++) {
			calls.add(common.get(i) + " after " + position);
		}
		return Math.max(called, common.size());
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
			int index = i;
			List<Symbol> rhs = new ArrayList<>();
			for (int symbol : randomRules.get(i).rhs()) {
				rhs.add(symbol >= 0 ? nonterminals.get(symbol) : terminals.get(-1 - symbol));
			}
			rules.add(new Rule(nonterminals.get(randomRules.get(i).lhs()), rhs,
					match -> calls.add(event(index, match.start(), match.end()) + " after " + requested[0])));
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
			Parser.create(new Grammar(nonterminals.get(0), rules)).parse(input);
			return new Expectation("accepted", requested[0], calls, Set.of());
		} catch (SyntaxException exception) {
			return new Expectation("syntax error", exception.position(), calls, Set.of());
		} catch (AmbiguityException exception) {
			return new Expectation("ambiguous", requested[0], calls,
					Set.of(exception.symbol() + " from " + exception.start() + " to " + exception.end()));
		} catch (ParseException exception) {
			throw new AssertionError(exception);
		}
	}

	private static String describe(List<RandomRule> rules, List<String> tokens) {
		StringBuilder description = new StringBuilder("input " + tokens + " with the rules");
		for (int i = 0; i < rules.size(); i++) {
			description.append("\n  rule ").append(i).append(": N").append(rules.get(i).lhs()).append(" ->");
			for (int symbol : rules.get(i).rhs()) {
				description.append(' ').append(symbol >= 0 ? "N" + symbol : TOKENS[-1 - symbol]);
			}
		}
		return description.toString();
	}

	@Test
	void parserBehavesLikeTheReferenceForRandomGrammars() {
		Random random = new Random(20240607);
		int compared = 0;
		int accepted = 0;
		int ambiguous = 0;
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
			}
		}
		// Make sure that the test compares a substantial number of inputs of every kind.
		assertTrue(compared > 15_000, "compared: " + compared);
		assertTrue(accepted > 3_000, "accepted: " + accepted);
		assertTrue(ambiguous > 1_000, "ambiguous: " + ambiguous);
	}
}
