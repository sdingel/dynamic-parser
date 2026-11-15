# Architecture

This document specifies what the parser does and records the fundamental design decisions with their reasoning. The
sections up to [Public API](#public-api) describe the behavior independently of the algorithm;
[Implementation](#implementation) describes how the parser works and which parts of the specified behavior are not
implemented yet.

## Purpose

dynamic-parser is a generic parser whose grammar can be modified while parsing.

It is intended for extensible programming languages: languages in which new constructs can be defined within the
language itself. One idea to be explored with it is that every declaration adds grammar rules. For example, declaring
a variable `x` of type `Integer` adds a rule saying that `x` may appear wherever an `Integer` expression is expected.
Keywords and identifiers are then no longer distinguished. This is an experimental project.

## Terminology

- **Token:** One element of the input. Tokens are arbitrary objects supplied by the caller.
- **Position:** A place between tokens. Position 0 is before the first token, position *n* after the *n*-th token.
- **Symbol:** A terminal or a nonterminal.
- **Terminal:** A symbol that stands for a single token. A terminal decides for each token whether it matches.
- **Nonterminal:** A symbol that is defined by rules.
- **Rule:** `A -> X1 … Xn` with a nonterminal `A` (left-hand side) and a possibly empty sequence of symbols
  (right-hand side).
- **Grammar:** A start symbol and a set of rules. The set of rules changes while parsing.
- **Match:** The application of a rule to a section of the input, from a start position to an end position, together
  with the matches of the nonterminals on its right-hand side. An *empty match* has equal start and end positions.
- **Result:** The match of the start symbol that covers the complete input. With all the matches it contains, it
  forms a tree.
- **Action:** The callback of a rule. It computes the value of a match and can change the grammar.
- **Value:** The value of a terminal is the matched token. The value of a match is what its action returns.
- **Confirmed:** A match is confirmed once it is certain that it is part of the result. See
  [Confirmed matches](#confirmed-matches).

## Decisions

### 1. Generic parser

The parser receives its grammar at runtime through the API. It is not a parser generator: nothing is generated for a
specific grammar. Rules have the classical form `A -> X1 … Xn` with n ≥ 0. The start symbol is fixed for a grammar.

### 2. Symbols and rules are identified by object identity

Two symbols with the same name are different symbols, and two rules with the same content are different rules. Names
are only used in messages.

Reason: rules are created and removed at runtime by actions. Identity makes it unambiguous which rule an action
removes, and it requires no naming scheme for generated symbols.

Consequence: two rules with the same content can be in the grammar at the same time. Every input they apply to is
then ambiguous.

### 3. Tokens are objects, terminals decide what they match

The input is a sequence of token objects. Tokenizing is the caller's task for now. A terminal contains a predicate
that decides whether a token matches. This allows a terminal for one specific token (the word `x`) as well as a
terminal for a class of tokens (any word); both are needed when declarations add rules for the declared names.

Tokens and values have the type `Object`. Reason: the values passed to an action are heterogeneous anyway (tokens and
the results of other actions), so type parameters for tokens and values would add noise without real safety.

Whether terminals should rather work on characters, and how a fixed lexer can cope with newly defined operators, is
open.

### 4. Actions compute values

Every rule has an action. It receives the values of the symbols on the right-hand side of the rule and returns the
value of the match. The parser returns the value of the result, that is, of the match of the start symbol. The parser
builds no parse tree of its own; a rule created without an action gets an action that returns a generic tree node
(`ParseNode`).

### 5. Guarantees for actions

- An action is called exactly once for every match that is part of the result.
- An action is never called for a match that is not part of the result, in particular not for alternatives that the
  parser pursues for a while and then discards.
- An action is called only after its match is confirmed.
- Actions are called in bottom-up, left-to-right order: the actions of the matches on the right-hand side are called
  before the action of the rule itself, from left to right. This is the order of a classical bottom-up parser.

Reason: actions have side effects, above all changes of the grammar. These must not happen for alternatives that turn
out to be wrong, and their order must be predictable.

Consequence: an action can be called later than at the end of its match, namely when the parser needs more input to
confirm the match.

### 6. Actions change the grammar for the following input

An action can add rules to the grammar and remove rules from it.

A rule may begin a match only if it is in the grammar at that point. Decisive is where the match **begins**, not where
it ends. Precisely: a match of a rule is permitted only if the rule is in the grammar that results from the initial
grammar by applying the changes of the actions of all matches that lie to the left of the beginning of the match in
the result. These are the matches whose actions are called earlier and that are not part of the match itself.

It follows:

- A rule added by an action is available for matches beginning at or after the end of the match of that action.
- A removed rule begins no new matches from there on.
- Matches that have already begun are completed, even if their rule is removed in the meantime.
- The definition also covers several matches at the same position, for example empty matches: what counts is their
  order in the result.

A change exists from the moment the action is called:

- **Uncalled actions change nothing.** As long as a match is not confirmed, its action has not been called, and the
  input after the match is interpreted with the grammar as it is. If the match is confirmed later, decision 7
  applies.
- **Called actions are binding.** Once an action has been called, its match is part of the result. Interpretations of
  the input that do not contain the match are no longer considered, even if a later change of the grammar would make
  them possible. Otherwise an action could have been called for a match that is not part of the result (decision 5).

Consequence for left recursion: a match of a left-recursive rule such as `List -> List Element` begins where the list
begins. The rule therefore stays applicable to a list that has already begun after it is removed, and a newly added
left-recursive rule cannot extend a list that has already begun. Whether this is the desired behavior is open.

### 7. Strict timing of grammar changes

An action that changes the grammar must be called before the parser reads the token following its match. If the match
is confirmed only later, the attempt to change the grammar fails with a `LateGrammarChangeException` naming the rule.

Example: a function header `fun f(a: Integer): Integer` whose action is supposed to introduce `f` and `a` for the
function body. After `Integer` the type could continue (`Integer[]`), so the header is only confirmed when the
following `{` has been read. The grammar must be changed so that the header is confirmed at its end, for example by
making `{` part of the header rule.

Alternative considered: letting a late change take effect from the position at which the action is actually called.
Rejected because the scope of a change would then shift silently, depending on details of the grammar, and such
errors are hard to find. The strict rule can be relaxed later without breaking existing grammars; the reverse is not
possible.

The failure is reported by the methods that change the grammar, as an unchecked exception. Reason: it indicates a
defect of the grammar, not of the input, and the stack trace then points to the action concerned. If the action does
not catch the exception, it aborts parsing like any other exception thrown by an action.

### 8. Ambiguity is an error

Parsing fails with an `AmbiguityException` if the input matches the grammar in more than one way, that is, if a
section of the input that is part of the result can be derived from a nonterminal in several ways. When exactly the
ambiguity is reported, and which actions have been called by then, is defined in
[Confirmed matches](#confirmed-matches).

Reason: actions are called exactly once and only for the result, so there must be exactly one result. A programming
language that is ambiguous for the parser is ambiguous for its readers as well.

Alternatives that the parser has to pursue only temporarily are not an error: they delay actions but do not make the
input ambiguous.

### 9. Actions cannot reject a match

An action cannot veto the application of its rule. An unchecked exception thrown by an action aborts parsing and is
passed on to the caller unchanged.

### 10. Grammar changes belong to one parse run

The initial grammar is an immutable object that can be reused. Every parse run starts with the initial grammar; the
changes made by actions are discarded when the run ends. Parse runs do not influence each other.

### 11. Supported grammars

Every context-free grammar is supported, including left recursion, right recursion and empty rules, provided that

- the input is unambiguous (decision 8) and
- every match whose action changes the grammar is confirmed at its end (decision 7).

Grammars for programming languages can be expected to meet both conditions at the important places, because
declarations are typically delimited by keywords, terminators and brackets.

## Confirmed matches

A match can be complete and unambiguous without being confirmed. Example with the usual expression grammar:

```
Sum     -> Sum '+' Product | Product
Product -> Product '*' Atom | Atom
```

After `a + b` the match `Sum(a + b)` is complete. If `* c` follows, it is not part of the result: the result contains
`Sum(a + (b * c))` instead. The action of `Sum(a + b)` must therefore not be called before the next token is known.

### Readings

A **reading** is a complete interpretation of the input read so far that could take the next token or accept the end
of the input. The parser pursues all readings at the same time. A reading is dropped as soon as the next token does
not fit it. At the end of the input, the readings are the ones that accept the end.

In the example, there are three readings after `a + b`: one in which the product `b` continues with `*`, one in
which the sum `a + b` continues with `+`, and one in which the sum is complete and whatever encloses it continues.
All three contain the matches of `a` and of `b`, only two of them contain `Sum(a + b)`.

Every reading determines a sequence of matches: the completed matches it contains, in the order in which their
actions are called (decision 5).

### Definition

1. **Confirmed:** A match is confirmed when it is part of every remaining reading, with the same rule, the same
   section of the input and the same parts.
2. **Calling the action:** The action of a match is called as soon as the match is confirmed and all matches whose
   actions come before it are confirmed as well. In other words: the actions that have been called at any time are
   the matches that the sequences of all readings begin with. What lies to the left of a match must be certain;
   what will be built above it may still be open.
3. **Ambiguity:** Parsing fails with an `AmbiguityException` as soon as all remaining readings agree on everything
   before a section of the input, all of them contain the section as a match of the same nonterminal, and they do
   not agree on its structure. Before the failure, the actions of the matches that the sequences of all readings
   begin with have been called. This includes matches within the ambiguous section that all its alternatives have
   in common.

Strict timing (decision 7) refers to point 2: an action that changes the grammar must be callable at the end of its
match.

Properties of this definition:

- **No lookahead.** The parser does not check whether a reading can be completed later, only whether it could take a
  next token.
- **Interpretations waiting for rules.** An interpretation that is stuck at a nonterminal without rules cannot take a
  token, so it is not a reading and does not prevent confirmation. There is one exception: if no interpretation can
  take a token or accept the end of the input, the interpretations that are stuck at a nonterminal without rules are
  the readings. Reason: the input can then only continue if an action adds rules, and this makes it possible that the
  action of a match adds the only rule with which the input continues, for example a function header that
  introduces what its body may contain. If the input can continue without such a rule, an interpretation waiting for
  it only comes back when the rule is added by an action of a match that the interpretation contains.
- **The end of the input is not known in advance.** After the last token, the parser first calls the actions that
  are confirmed regardless of whether the input continues, and only then finds out that it ends.
- **Actions before an error.** When parsing fails with a `SyntaxException` or an `AmbiguityException`, the actions of
  the matches confirmed before that point have been called.
- **Typical case.** After reading a token, if exactly one rule application has taken this token and is complete with
  it, its match is confirmed. If exactly one rule application is waiting for this match and is completed by it as
  well, its match is confirmed too, and so on upwards. Example: a declaration ending in `;`.

Alternative considered and rejected: "exactly one rule can reduce the section, and there is exactly one path to the
root".

- Too weak in the first part: with `Type -> 'Integer' | 'Integer' '[' ']'`, after `Integer` exactly one rule reduces
  the section and the path to the root is unique, but if `[` follows, this `Type` match is not part of the result.
- Too strict in the second part: with `Stmts -> ε | Stmts Stmt` inside `Block -> '{' Stmts '}'`, the path to the root
  is never unique after a statement (the list may end or continue), although the statement is certain.

## Public API

The API consists of the package `de.steffendingel.dynamicparser`, which is the only package exported by the module
`de.steffendingel.dynamicparser`. The Javadoc of the types is the reference; this is an overview.

| Type | Kind | Purpose |
|---|---|---|
| `Symbol` | sealed interface | Common type of `Nonterminal` and `Terminal`; has a name |
| `Nonterminal` | final class | Nonterminal symbol |
| `Terminal` | final class | Name and a predicate deciding whether a token matches |
| `Rule` | final class | Left-hand side, right-hand side and action |
| `Action` | functional interface | Computes the value of a match: `Object apply(Match match)` |
| `Match` | interface | Rule, start and end position, values, access to the `GrammarEditor` |
| `GrammarEditor` | interface | Adds and removes rules during a parse run |
| `Grammar` | final class | Immutable initial grammar: start symbol and rules |
| `Parser` | interface | `Object parse(Iterable<?> tokens) throws ParseException` |
| `ParseNode` | record | Generic tree node; the value of a rule created without an action |
| `ParseException` | sealed checked exception | Base class of `SyntaxException` and `AmbiguityException` |
| `LateGrammarChangeException` | unchecked exception | A grammar change that comes too late (decision 7) |

Design decisions of the API:

- **Grammar and parser are separate.** A `Grammar` is the immutable starting point, a `Parser` executes parse runs on
  it (decision 10).
- **Constructors for identity types.** `Nonterminal`, `Terminal`, `Rule` and `Grammar` are created with `new`. Reason:
  every call creates a new, distinct object (decision 2); a static factory such as `Rule.of` would suggest value
  semantics like `List.of`.
- **Errors of the input are checked exceptions.** `SyntaxException` and `AmbiguityException` are expected outcomes of
  parsing that every caller has to handle.
- **`GrammarEditor.add` and `remove` return a `boolean`** like Java collections instead of failing if the rule is
  already present or absent.
- **`GrammarEditor` and `Match` are only valid while their action is running.**
- **Tokens are read lazily.** The parser reads the tokens one at a time, and only after it has called all actions
  that can be called at the current position.

## Implementation

The implementation is the package `de.steffendingel.dynamicparser.internal`, which the module does not export. A
parser is created with `Parser.create(Grammar)`.

### Status

The specification is implemented completely (issue #5), apart from the [limitations](#limitations) below.

### Algorithm

The parser is based on Earley's algorithm. Reason: it works directly on the rules, without tables computed from the
grammar in advance, so the grammar can change between two positions; it handles every context-free grammar; and its
central notion matches decision 6: an Earley item is a rule application that began at a certain position.

The input is processed token by token. At every position the parser performs three steps before it reads the next
token, and repeats them whenever an action has changed the grammar.

**1. Recognition (`Chart`).** The parser computes the set of Earley items of the position: all rule applications
that are consistent with the tokens before that position, each with the position at which it began and the number of
symbols matched so far. The set results from the items of the previous position that can take the token (scanning)
and from everything that follows from them: the rules of the nonterminals that can begin here (prediction) and the
items advanced by matches that end here (completion). If no item can take a token, or if the input ends without a
complete match of the start symbol, this is a syntax error; the terminals that the items of the position expect are
reported as the expected terminals.

Empty rules need special care: an empty match can be completed before all items waiting for its nonterminal are
known. An item that starts waiting for a nonterminal is therefore advanced immediately if an empty match of that
nonterminal has already been completed at the same position.

**2. Analysis (`Analyzer`).** The parser determines what all readings have in common. In the chart, a reading ends
at an item whose next symbol is a terminal, or at a complete match of the start symbol. The readings are not
enumerated, because there can be exponentially many. Instead, the parser computes summaries for the position:

- for every item, the *forest* of the section it has matched: one entry per matched symbol, where an entry is a
  token, a match with a unique structure, or an ambiguous section;
- for every nonterminal that can begin at the position, its *context*: what is known about all readings in which it
  begins here. It follows from the items waiting for the nonterminal.

The summary of a reading that ends at an item is the context of the item's nonterminal at the position where the
item began, followed by the forest the item has matched. This is sufficient because readings are composed freely:
every way to reach an item can be combined with every way to continue from it. For the same reason the summaries of
a position never change once they are computed.

What two sets of readings have in common is determined by comparing forests (`Forests.commonPrefix`). A forest
stands for a sequence: its tokens and matches, each match after everything it consists of, which is the order in
which a bottom-up parser shifts tokens and reduces matches. What two forests have in common is the forest of the
longest beginning that their sequences share. Example: `Sum(a + b)` and `Sum(a) + Product(b)` have
`Sum(a) + Product(b)` in common, because the first forest contains the same matches and one more. Equal parts of
forests are the same objects, so they are recognized without looking into them.

Usually the sequences of the readings of a context differ somewhere, and then it does not matter how the readings
continue. The exception are readings whose complete sequence is the beginning of the sequences of all others; this
happens when a nonterminal can begin at a position both before and after an empty match. A context therefore keeps
such readings individually, up to a limit of four.

**3. Execution (`Executor`).** The forest that all readings have in common contains the confirmed matches. The
parser calls the actions of those that have not been called yet, bottom-up and from left to right. A match is
identified by its content together with the forest before it; this distinguishes several empty matches with the same
content at the same position. If the forest contains an ambiguous section, the parser calls the actions of the
matches that all alternatives of the section begin with and then fails.

At the end of the input the steps 2 and 3 are repeated for the readings that accept the end.

**Changes of the grammar (`Commitments`).** When an action changes the grammar, the execution stops, and the item set
of the position is built again with the new rules. The previous item set stays in the chart, because the items of
rule applications that began there refer to it; an item set is therefore identified by its index in the chart, not
by its position. The new item set does not start from all items of the previous one, but only from the items that
are *valid* for the forest of the matches called so far: the items of rule applications whose matched symbols are
exactly the last entries of that forest and that are themselves waited for by valid items. This is the item set a
deterministic bottom-up parser would be in after shifting the tokens and reducing the matches of the forest. It
realizes both parts of decision 6:

- Rules that could begin after the match are predicted again, now with the new grammar. Rule applications that
  began before are kept, also those of removed rules.
- Interpretations that do not contain a called match are not valid for the forest and therefore do not come back,
  even if the new rules would allow them to continue.

For the items the parser continues with, it is known exactly which items wait for their match and what precedes them:
the forest before their rule application. This knowledge replaces the summaries of step 2 for them, because those
summaries also cover interpretations that are no longer possible, and it is passed on to the waiting items when the
match is completed.

Afterwards the steps 2 and 3 are repeated. Actions that have been called are not called again; matches that were
confirmed before the change but whose actions have not been called yet are confirmed again only if they are part of
every reading with the new grammar.

### Limitations

- **Sections that contain themselves.** With rules such as `A -> A`, a section can be matched in infinitely many
  ways. Such a section is treated as ambiguous without determining what its alternatives have in common. If the
  section is part of the result, parsing fails as specified. If it is only part of a reading that is dropped later,
  matches within it that other readings contain as well are confirmed only when that reading is dropped, which is
  later than the definition demands.
- **More than four nested readings.** If more than four readings of a context have to be kept individually, the
  others are combined. Matches can then be confirmed later than the definition demands. This requires more than four
  empty matches in a row at one position that are each optional.
- **Nested rule applications at one position.** If, after a change of the grammar, applications of the same rule
  that began in the same item set are valid at two different depths, which requires a rule that contains itself
  behind empty matches, the knowledge about the last one is used for both.
- **Performance.** The implementation is not optimized. Lists defined by left-recursive rules and typical statements
  and expressions take time linear in the length of the input. Right-recursive rules take quadratic time; Leo's
  optimization, which avoids that, is left for later. Parser state for confirmed input is never discarded, so memory
  grows with the length of the input.
- **Many rules for one nonterminal.** Wherever a nonterminal can begin, the parser creates an item for each of its
  rules. If every declared name is a rule, time and memory grow with the product of the number of declarations and
  the number of places where a name can be used: in a measurement, 2,000 declarations with one use each took 1.4
  seconds, and 8,000 exceeded the available memory.

The first two limitations are deviations from the definition on the safe side: an action is never called for a
match that is not confirmed.

### Testing

Besides tests for individual scenarios, `ParserReferenceTest` compares the parser with a simple reference
implementation of the definition above, which enumerates all readings explicitly as the runs of a nondeterministic
bottom-up parser. It checks the outcome and the time of every call of an action for several thousand random grammars
and inputs. In about half of the grammars, actions add and remove rules.

## Open points

- The cost of many rules for one nonterminal, see [Limitations](#limitations).
- Whether the behavior of left-recursive rules under grammar changes (decision 6) is the desired one.
- Characters or tokens as terminals, and lexing in general (decision 3).
- Operator precedence and associativity are not separate concepts so far; they have to be expressed in the rules.
