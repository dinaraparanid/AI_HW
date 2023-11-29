package assignment2.students;

import java.io.BufferedReader;
import java.io.FileReader;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class Main {
    private static final int TABLE_SIZE = 20;
    private static final int POPULATION_SIZE = 100;

    private static final int HORIZONTAL = 0;
    private static final int VERTICAL = 1;

    private static final float MAX_FITNESS_CRITERIA_WEIGHT = 1F;
    private static final int FITNESS_WORD_CRITERIA_AMOUNT = 1;

    private static final float INCLUDE_PARENT_PROBABILITY = 0.25F;
    private static final float MUTATION_RATE = 0.33F;

    // ------------------------- DATA HOLDERS -------------------------

    private record WordState(String word, int startRow, int startColumn, int layout) {}

    private record TableState(char[][] table, List<WordState> words) {}

    private record Coords(int startRow, int startColumn, char c) {}

    private record CoordsWithLayout(int startRow, int startColumn, int layout) {}

    private record ScanFitnessState(int index, float fitness) {}

    private record WordFitnessState(WordState word, float fitness) {}

    private record TableFitnessState(
            TableState table,
            List<WordFitnessState> wordsFitness,
            float graphFitness
    ) {
        public float totalFitness() {
            return graphFitness + (totalWordsFitness() / wordsFitness.size());
        }

        public float totalWordsFitness() {
            return wordsFitness
                    .stream()
                    .map(WordFitnessState::fitness)
                    .reduce(Float::sum)
                    .orElse(0F);
        }
    }

    private record FitnessWithIndex(float fitness, int index) {}

    // ------------------------- GENERATION -------------------------

    public static void main(final String[] args) {
        try (final var reader = new BufferedReader(new FileReader("input1.txt"))) {
            final var words = reader.lines().toList();
            final var random = SecureRandom.getInstanceStrong();

            for (var population = initialPopulation(words, random);;) {
                final var selected = selectWithRoulette(population, random);
                population = nextPopulation(selected, random);

                final var fitnessValues = fitnessValues(population);
                final var maxFitness = maxFitness(fitnessValues);

                if (maxFitness.fitness >= 2F) {
                    System.out.println("DONE:");
                    printTable(population.get(maxFitness.index));
                    return;
                }
            }
        } catch (final Exception ignored) {
        }
    }

    private static FitnessWithIndex maxFitness(final List<Float> fitnessValues) {
        return IntStream
                .range(0, fitnessValues.size())
                .mapToObj(i -> new FitnessWithIndex(fitnessValues.get(i), i))
                .max((a, b) -> Float.compare(a.fitness, b.fitness))
                .orElseGet(() -> new FitnessWithIndex(fitnessValues.get(0), 0));
    }

    private static void printTable(final TableState tableState) {
        for (final var row : tableState.table) {
            for (final var c : row)
                System.out.printf("%c ", c == 0 ? '_' : c);
            System.out.println();
        }
    }

    // ---------------------------- INITIAL POPULATION ----------------------------

    private static List<TableState> initialPopulation(final List<String> words, final Random random) {
        return Stream
                .generate(() -> wordTable(words, random))
                .limit(POPULATION_SIZE)
                .toList();
    }

    private static TableState wordTable(final List<String> words, final Random random) {
        final var table = new char[TABLE_SIZE][TABLE_SIZE];
        final var horizontalWords = new ArrayList<WordState>();
        final var verticalWords = new ArrayList<WordState>();

        final var wordStates = wordStates(words, table, horizontalWords, verticalWords, random);
        return new TableState(table, wordStates);
    }

    private static List<WordState> wordStates(
            final List<String> words,
            final char[][] table,
            final Collection<WordState> horizontalWords,
            final Collection<WordState> verticalWords,
            final Random random
    ) {
        return words
                .stream()
                .map(word -> wordState(word, table, horizontalWords, verticalWords, random))
                .toList();
    }

    private static WordState wordState(
            final String word,
            final char[][] table,
            final Collection<WordState> horizontalWords,
            final Collection<WordState> verticalWords,
            final Random random
    ) {
        return IntStream
                .generate(() -> random.nextInt(2))
                .mapToObj(layout -> {
                    final var crds = startCoords(word, layout, random);
                    return new CoordsWithLayout(crds.startRow, crds.startColumn, layout);
                })
                .filter(crdsToLayout -> canPutWord(
                        word,
                        crdsToLayout.startRow,
                        crdsToLayout.startColumn,
                        crdsToLayout.layout,
                        table,
                        horizontalWords,
                        verticalWords
                ))
                .map(wordCoords -> new WordState(
                        word,
                        wordCoords.startRow,
                        wordCoords.startColumn,
                        wordCoords.layout
                ))
                .peek(wordState -> putWord(wordState, table, horizontalWords, verticalWords))
                .findFirst()
                .get();
    }

    private static Coords startCoords(
            final String word,
            final int layout,
            final Random random
    ) {
        return layout == HORIZONTAL
                ? horizontalStartCoords(word, random)
                : verticalStartCoords(word, random);
    }

    private static Coords horizontalStartCoords(
            final String word,
            final Random random
    ) {
        final var startRow = random.nextInt(0, 20);
        final var startColumn = random.nextInt(0, 20 - word.length() + 1);
        return new Coords(startRow, startColumn, '\0');
    }

    private static Coords verticalStartCoords(
            final String word,
            final Random random
    ) {
        final var startRow = random.nextInt(0, 20 - word.length() + 1);
        final var startColumn = random.nextInt(0, 20);
        return new Coords(startRow, startColumn, '\0');
    }

    private static boolean canPutWord(
            final String word,
            final int startRow,
            final int startColumn,
            final int layout,
            final char[][] table,
            final Collection<WordState> horizontalWords,
            final Collection<WordState> verticalWords
    ) {
        return layout == HORIZONTAL
                ? canPutWordHorizontal(word, startRow, startColumn, table, horizontalWords)
                : canPutWordVertical(word, startRow, startColumn, table, verticalWords);
    }

    private static boolean canPutWord(
            final WordState wordState,
            final char[][] table,
            final Collection<WordState> horizontalWords,
            final Collection<WordState> verticalWords
    ) {
        return canPutWord(
                wordState.word,
                wordState.startRow,
                wordState.startColumn,
                wordState.layout,
                table,
                horizontalWords,
                verticalWords
        );
    }

    private static boolean canPutWordHorizontal(
            final String word,
            final int startRow,
            final int startColumn,
            final char[][] table,
            final Collection<WordState> horizontalWords
    ) {
        if (startColumn + word.length() >= TABLE_SIZE)
            return false;

        for (int x = startColumn; x < startColumn + word.length(); ++x)
            if (table[startRow][x] != 0 && table[startRow][x] != word.charAt(x - startColumn))
                return false;

        final var wordState = new WordState(word, startRow, startColumn, HORIZONTAL);

        return horizontalWords
                .stream()
                .noneMatch(w -> tooBigNeighbouringBorderHorizontal(wordState, w)
                        || followingHorizontal(wordState, w)
                );
    }

    private static boolean canPutWordVertical(
            final String word,
            final int startRow,
            final int startColumn,
            final char[][] table,
            final Collection<WordState> verticalWords
    ) {
        if (startRow + word.length() >= TABLE_SIZE)
            return false;

        for (int y = startRow; y < startRow + word.length(); ++y)
            if (table[y][startColumn] != 0 && table[y][startColumn] != word.charAt(y - startRow))
                return false;

        final var wordState = new WordState(word, startRow, startColumn, VERTICAL);

        return verticalWords
                .stream()
                .noneMatch(w -> tooBigNeighbouringBorderVertical(wordState, w)
                        || followingVertical(wordState, w)
                );
    }

    private static boolean tooBigNeighbouringBorderHorizontal(
            final WordState a,
            final WordState b
    ) {
        if (Math.abs(a.startRow - b.startRow) != 1)
            return false;

        final var aCoords = columnsSet(a);
        final var bCoords = columnsSet(b);

        aCoords.retainAll(bCoords);
        return aCoords.size() > 1;
    }

    private static boolean tooBigNeighbouringBorderVertical(
            final WordState a,
            final WordState b
    ) {
        if (Math.abs(a.startColumn - b.startColumn) != 1)
            return false;

        final var aCoords = rowsSet(a);
        final var bCoords = rowsSet(b);

        aCoords.retainAll(bCoords);
        return aCoords.size() > 1;
    }

    private static Set<Integer> columnsSet(final WordState wordState) {
        return wordCoords(wordState)
                .stream()
                .map(Coords::startColumn)
                .collect(Collectors.toSet());
    }

    private static Set<Integer> rowsSet(final WordState wordState) {
        return wordCoords(wordState)
                .stream()
                .map(Coords::startRow)
                .collect(Collectors.toSet());
    }

    private static boolean followingHorizontal(
            final WordState a,
            final WordState b
    ) {
        if (a.startRow != b.startRow)
            return false;

        final var aStart = a.startColumn;
        final var aEnd = a.startColumn + a.word.length();

        final var bStart = b.startColumn;
        final var bEnd = b.startColumn + b.word.length();

        return Math.abs(aEnd - bStart) == 1 || Math.abs(bEnd - aStart) == 1;
    }

    private static boolean followingVertical(
            final WordState a,
            final WordState b
    ) {
        if (a.startColumn != b.startColumn)
            return false;

        final var aStart = a.startRow;
        final var aEnd = a.startRow + a.word.length();

        final var bStart = b.startRow;
        final var bEnd = b.startRow + b.word.length();

        return Math.abs(aEnd - bStart) == 1 || Math.abs(bEnd - aStart) == 1;
    }

    private static void putWord(
            final WordState wordState,
            final char[][] table,
            final Collection<WordState> horizontalWords,
            final Collection<WordState> verticalWords
    ) {
        if (wordState.layout == HORIZONTAL) putHorizontalWord(wordState, table, horizontalWords);
        else putVerticalWord(wordState, table, verticalWords);
    }

    private static void putHorizontalWord(
            final WordState wordState,
            final char[][] table,
            final Collection<WordState> horizontalWords
    ) {
        for (int x = wordState.startColumn; x < wordState.startColumn + wordState.word.length(); ++x)
            table[wordState.startRow][x] = wordState.word.charAt(x - wordState.startColumn);
        horizontalWords.add(wordState);
    }

    private static void putVerticalWord(
            final WordState wordState,
            final char[][] table,
            final Collection<WordState> verticalWords
    ) {
        for (int y = wordState.startRow; y < wordState.startRow + wordState.word.length(); ++y)
            table[y][wordState.startColumn] = wordState.word.charAt(y - wordState.startRow);
        verticalWords.add(wordState);
    }

    // ---------------------------- SELECTION ----------------------------

    private static List<TableState> selectWithRoulette(
            final List<TableState> population,
            final Random random
    ) {
        final var fitnessStates = fitnessStates(population);
        final var fitnessValues = fitnessValuesFromStates(fitnessStates);
        final var fitnessSums = fitnessSums(fitnessValues);
        final var totalFitness = last(fitnessSums).fitness;
        final var rouletteValue = random.nextFloat(0, totalFitness);

        return fitnessSums
                .stream()
                .filter(fitnessState -> fitnessState.fitness > rouletteValue)
                .map(fitnessState -> fitnessStates.get(fitnessState.index).table)
                .toList();
    }

    private static List<TableFitnessState> fitnessStates(final List<TableState> population) {
        return population.stream().map(Main::fitness).toList();
    }

    private static List<Float> fitnessValuesFromStates(final List<TableFitnessState> fitnessStates) {
        return fitnessStates.stream().map(TableFitnessState::totalFitness).toList();
    }

    private static List<Float> fitnessValues(final List<TableState> population) {
        return fitnessValuesFromStates(fitnessStates(population));
    }

    private static List<ScanFitnessState> fitnessSums(final List<Float> fitnessValues) {
        final var sums = new ArrayList<ScanFitnessState>(fitnessValues.size());
        float acc = 0;

        for (int i = 0; i < fitnessValues.size(); ++i) {
            acc += fitnessValues.get(i);
            sums.add(new ScanFitnessState(i, acc));
        }

        return sums;
    }

    private static TableFitnessState fitness(final TableState tableState) {
        final var wordStates = tableState.words;
        final var wordsCoords = wordsCoords(wordStates);
        final var wordsCrosses = wordsCrosses(wordsCoords);
        final var wordsFitness = wordsFitness(wordStates, wordsCrosses);
        final float connectivityFitness = longestConnectivity(wordsCrosses);
        return new TableFitnessState(tableState, wordsFitness, connectivityFitness / wordStates.size());
    }

    private static List<WordFitnessState> wordsFitness(
            final List<WordState> wordStates,
            final Map<WordState, Collection<WordState>> wordsCrosses
    ) {
        return wordStates
                .stream()
                .map(wordState -> new WordFitnessState(
                        wordState,
                        wordFitness(wordState, wordsCrosses)
                ))
                .toList();
    }

    private static Stream<Set<WordState>> connectivityComponentsStream(
            final Map<WordState, Collection<WordState>> wordsCrosses
    ) {
        final var allVisited = new HashSet<WordState>();
        final var allWords = wordsCrosses.keySet();

        return allWords
                .stream()
                .filter(word -> !allVisited.contains(word))
                .map(word -> dfs(word, wordsCrosses))
                .peek(allVisited::addAll);
    }

    private static List<Set<WordState>> connectivityComponents(
            final Map<WordState, Collection<WordState>> wordsCrosses
    ) {
        return connectivityComponentsStream(wordsCrosses).toList();
    }

    private static int longestConnectivity(final Map<WordState, Collection<WordState>> wordsCrosses) {
        return connectivityComponentsStream(wordsCrosses)
                .map(Collection::size)
                .max(Integer::compareTo)
                .orElse(0);
    }

    private static Set<WordState> dfs(
            final WordState start,
            final Map<WordState, Collection<WordState>> wordsCrosses
    ) {
        final var set = new HashSet<WordState>();
        dfsImpl(start, set, wordsCrosses);
        return set;
    }

    private static void dfsImpl(
            final WordState current,
            final Set<WordState> set,
            final Map<WordState, Collection<WordState>> wordsCrosses
    ) {
        if (set.contains(current))
            return;

        set.add(current);

        wordsCrosses
                .get(current)
                .stream()
                .filter(word -> !set.contains(word))
                .forEach(word -> dfsImpl(word, set, wordsCrosses));
    }

    private static float wordFitness(
            final WordState wordState,
            final Map<WordState, Collection<WordState>> wordsCrosses
    ) {
        return wordCrossed(wordState, wordsCrosses) / FITNESS_WORD_CRITERIA_AMOUNT;
    }

    private static Map<WordState, Collection<Coords>> wordsCoords(final List<WordState> wordStates) {
        return wordStates
                .stream()
                .map(word -> Map.entry(word, wordCoords(word)))
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    private static Map<WordState, Collection<WordState>> wordsCrosses(
            final Map<WordState, Collection<Coords>> wordsCoords
    ) {
        return wordsCoords
                .entrySet()
                .stream()
                .map(wordCoords -> Map.entry(
                        wordCoords.getKey(),
                        crosses(wordCoords.getKey(), wordCoords.getValue(), wordsCoords)
                ))
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    private static Set<Coords> wordCoords(final WordState wordState) {
        return wordState.layout == HORIZONTAL
                ? horizontalWordCoords(wordState)
                : verticalWordCoords(wordState);
    }

    private static Set<Coords> horizontalWordCoords(final WordState wordState) {
        return IntStream
                .range(wordState.startColumn, wordState.startColumn + wordState.word.length())
                .mapToObj(x -> new Coords(
                        wordState.startRow,
                        x,
                        wordState.word.charAt(x - wordState.startColumn)
                ))
                .collect(Collectors.toSet());
    }

    private static Set<Coords> verticalWordCoords(final WordState wordState) {
        return IntStream
                .range(wordState.startRow, wordState.startRow + wordState.word.length())
                .mapToObj(y -> new Coords(
                        y,
                        wordState.startColumn,
                        wordState.word.charAt(y - wordState.startRow)
                ))
                .collect(Collectors.toSet());
    }

    private static List<WordState> crosses(
            final WordState wordState,
            final Collection<Coords> coords,
            final Map<WordState, Collection<Coords>> wordsCoords
    ) {
        return wordsCoords
                .entrySet()
                .stream()
                .filter(otherWordWithCoords -> !otherWordWithCoords.getKey().equals(wordState))
                .filter(otherWordWithCoords ->
                        otherWordWithCoords
                                .getValue()
                                .stream()
                                .anyMatch(coords::contains)
                )
                .map(Map.Entry::getKey)
                .toList();
    }

    private static float wordCrossed(
            final WordState wordState,
            final Map<WordState, Collection<WordState>> wordsCrosses
    ) {
        if (!wordsCrosses.containsKey(wordState)) return 0F;
        return wordsCrosses.get(wordState).isEmpty() ? 0F : MAX_FITNESS_CRITERIA_WEIGHT;
    }

    // ---------------------------- NEXT POPULATION ----------------------------

    private static List<TableState> nextPopulation(
            final List<TableState> selected,
            final Random random
    ) {
        return Stream
                .generate(() -> nextChildWithMbParent(selected, random))
                .flatMap(Collection::stream)
                .limit(POPULATION_SIZE)
                .toList();
    }

    private static Collection<TableState> nextChildWithMbParent(
            final List<TableState> selected,
            final Random random
    ) {
        final var tables = new ArrayList<TableState>(2);
        final var parent1 = randomElement(selected, random);
        final var parent2 = randomElement(selected, random);

        final var child = crossover(parent1, parent2, random);
        final var mutatedChild = mutation(child);
        tables.add(mutatedChild);

        if (random.nextFloat(0F, 1F) <= INCLUDE_PARENT_PROBABILITY)
            tables.add(randomElement(List.of(parent1, parent2), random));

        return tables;
    }

    // ---------------------------- CROSSOVER ----------------------------

    private static TableState crossover(
            final TableState parent1,
            final TableState parent2,
            final Random random
    ) {
        final var table = new char[TABLE_SIZE][TABLE_SIZE];
        final var horizontalWords = new ArrayList<WordState>();
        final var verticalWords = new ArrayList<WordState>();
        final var wordStates = crossoverWords(parent1, parent2, table, horizontalWords, verticalWords, random);
        return new TableState(table, wordStates);
    }

    private static List<WordState> crossoverWords(
            final TableState parent1,
            final TableState parent2,
            final char[][] table,
            final Collection<WordState> horizontalWords,
            final Collection<WordState> verticalWords,
            final Random random
    ) {
        return IntStream
                .range(0, parent1.words.size())
                .mapToObj(i -> i % 2 == 0 ? parent1.words.get(i) : parent2.words.get(i))
                .map(wordState -> crossoverWordState(wordState, table, horizontalWords, verticalWords, random))
                .toList();
    }

    private static WordState crossoverWordState(
            final WordState wordState,
            final char[][] table,
            final Collection<WordState> horizontalWords,
            final Collection<WordState> verticalWords,
            final Random random
    ) {
        return Stream
                .of(
                        tryPutWithSamePosition(wordState, table, horizontalWords, verticalWords),
                        tryPutCrossing(wordState, table, horizontalWords, verticalWords)
                )
                .filter(Optional::isPresent)
                .findFirst()
                .flatMap(Function.identity())
                .map(word -> {
                    putWord(word, table, horizontalWords, verticalWords);
                    return word;
                })
                .orElseGet(() -> wordState(wordState.word, table, horizontalWords, verticalWords, random));
    }

    private static Optional<WordState> tryPutWithSamePosition(
            final WordState wordState,
            final char[][] table,
            final Collection<WordState> horizontalWords,
            final Collection<WordState> verticalWords
    ) {
        return canPutWord(wordState, table, horizontalWords, verticalWords)
                ? Optional.of(wordState)
                : Optional.empty();
    }

    private static Optional<WordState> tryPutCrossing(
            final WordState wordState,
            final char[][] table,
            final Collection<WordState> horizontalWords,
            final Collection<WordState> verticalWords
    ) {
        return tryStartCoords(wordState.word, table, horizontalWords, verticalWords)
                .map(coordsWithLayout -> new WordState(
                        wordState.word,
                        coordsWithLayout.startRow,
                        coordsWithLayout.startColumn,
                        coordsWithLayout.layout
                ));
    }

    private static Optional<CoordsWithLayout> tryStartCoords(
            final String word,
            final char[][] table,
            final Collection<WordState> horizontalWords,
            final Collection<WordState> verticalWords
    ) {
        return Stream
                .of(
                        tryStartCoordsHorizontal(word, table, horizontalWords, verticalWords),
                        tryStartCoordsVertical(word, table, horizontalWords, verticalWords)
                )
                .filter(Optional::isPresent)
                .findFirst()
                .flatMap(Function.identity());
    }

    private static Optional<CoordsWithLayout> tryStartCoordsHorizontal(
            final String word,
            final char[][] table,
            final Collection<WordState> horizontalWords,
            final Collection<WordState> verticalWords
    ) {
        return possibleConnectionsStream(word, verticalWords)
                .map(coords -> possibleStartCoordsHorizontal(word, coords))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(coords -> canPutWordHorizontal(
                        word,
                        coords.startRow,
                        coords.startColumn,
                        table,
                        horizontalWords
                ))
                .findFirst();
    }

    private static Optional<CoordsWithLayout> tryStartCoordsVertical(
            final String word,
            final char[][] table,
            final Collection<WordState> horizontalWords,
            final Collection<WordState> verticalWords
    ) {
        return possibleConnectionsStream(word, horizontalWords)
                .map(coords -> possibleStartCoordsVertical(word, coords))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(coords -> canPutWordVertical(
                        word,
                        coords.startRow,
                        coords.startColumn,
                        table,
                        verticalWords
                ))
                .findFirst();
    }

    private static Stream<Coords> possibleConnectionsStream(
            final String word,
            final Collection<WordState> directedWords
    ) {
        return directedWords
                .stream()
                .map(Main::wordCoordsMap)
                .flatMap(letterMap -> filterLetterMap(word, letterMap));
    }

    private static Stream<Coords> filterLetterMap(
            final String word,
            final Map<Character, List<Coords>> letterMap
    ) {
        return word
                .chars()
                .filter(c -> letterMap.containsKey((char) c))
                .mapToObj(c -> letterMap.get((char) c))
                .flatMap(Collection::stream);
    }

    private static Map<Character, List<Coords>> wordCoordsMap(final WordState wordState) {
        return wordState.layout == HORIZONTAL
                ? horizontalWordCoordsMap(wordState)
                : verticalWordCoordsMap(wordState);
    }

    private static Map<Character, List<Coords>> horizontalWordCoordsMap(final WordState wordState) {
        final var map = new HashMap<Character, List<Coords>>();

        IntStream
                .range(wordState.startColumn, wordState.startColumn + wordState.word.length())
                .mapToObj(x -> new Coords(
                        wordState.startRow,
                        x,
                        wordState.word.charAt(x - wordState.startColumn)
                ))
                .forEach(coords -> {
                    if (!map.containsKey(coords.c))
                        map.put(coords.c, new ArrayList<>());
                    map.get(coords.c).add(coords);
                });

        return map;
    }

    private static Map<Character, List<Coords>> verticalWordCoordsMap(final WordState wordState) {
        final var map = new HashMap<Character, List<Coords>>();

        IntStream
                .range(wordState.startRow, wordState.startRow + wordState.word.length())
                .mapToObj(y -> new Coords(
                        y,
                        wordState.startColumn,
                        wordState.word.charAt(y - wordState.startRow)
                ))
                .forEach(coords -> {
                    if (!map.containsKey(coords.c))
                        map.put(coords.c, new ArrayList<>());
                    map.get(coords.c).add(coords);
                });

        return map;
    }

    private static Optional<CoordsWithLayout> possibleStartCoordsHorizontal(final String word, final Coords connection) {
        final var index = word.indexOf(connection.c);
        return connection.startColumn < index
                ? Optional.empty()
                : Optional.of(new CoordsWithLayout(connection.startRow, connection.startColumn - index, HORIZONTAL));
    }

    private static Optional<CoordsWithLayout> possibleStartCoordsVertical(final String word, final Coords connection) {
        final var index = word.indexOf(connection.c);
        return connection.startRow < index
                ? Optional.empty()
                : Optional.of(new CoordsWithLayout(connection.startRow - index, connection.startColumn, VERTICAL));
    }

    // ---------------------------- MUTATION ----------------------------

    private static TableState mutation(final TableState tableState) {
        final var table = new char[TABLE_SIZE][TABLE_SIZE];
        final var wordStatesMap = new HashMap<String, WordState>();
        final var horizontalWords = new ArrayList<WordState>();
        final var verticalWords = new ArrayList<WordState>();

        final var connectivityComponents = connectivityComponents(tableState.words);
        final var notMutatedWords = notMutatedWords(connectivityComponents);
        putWords(notMutatedWords, table, wordStatesMap, horizontalWords, verticalWords);

        final var mergableComponent = last(connectivityComponents).stream().toList();
        final var maxWordsToMutate = (int) Math.ceil(mergableComponent.size() * MUTATION_RATE);
        final var mutatedWords = mergableComponent.subList(0, maxWordsToMutate);

        final var sameWords = mergableComponent.subList(maxWordsToMutate, mergableComponent.size());
        putWords(sameWords, table, wordStatesMap, horizontalWords, verticalWords);

        final var mergedOrSameWords = mergedOrSameWords(mutatedWords, table, horizontalWords, verticalWords);
        putWords(mergedOrSameWords, table, wordStatesMap, horizontalWords, verticalWords);

        final var wordStates = reorderWordStates(tableState.words, wordStatesMap);
        return new TableState(table, wordStates);
    }

    private static List<WordState> mergedOrSameWords(
            final Collection<WordState> mutatedWords,
            final char[][] table,
            final Collection<WordState> horizontalWords,
            final Collection<WordState> verticalWords
    ) {
        return mutatedWords
                .stream()
                .map(wordState -> tryPutCrossing(wordState, table, horizontalWords, verticalWords).orElse(wordState))
                .toList();
    }

    private static List<WordState> reorderWordStates(
            final List<WordState> canonicalOrder,
            final Map<String, WordState> wordStatesMap
    ) {
       return canonicalOrder
               .stream()
               .map(WordState::word)
               .map(wordStatesMap::get)
               .toList();
    }

    private static List<Set<WordState>> connectivityComponents(final List<WordState> wordStates) {
        final var wordsCords = wordsCoords(wordStates);
        final var wordsCrosses = wordsCrosses(wordsCords);
        return connectivityComponents(wordsCrosses);
    }

    private static Collection<WordState> notMutatedWords(
            final List<Set<WordState>> connectivityComponents
    ) { return notMutatedWords(notMutatedComponents(connectivityComponents)); }

    private static Collection<Set<WordState>> notMutatedComponents(
            final List<Set<WordState>> connectivityComponents
    ) { return connectivityComponents.subList(0, connectivityComponents.size() - 1); }

    private static Collection<WordState> notMutatedWords(
            final Collection<Set<WordState>> notMutatedComponents
    ) {
        return notMutatedComponents
                .stream()
                .flatMap(Collection::stream)
                .toList();
    }

    private static void putWords(
            final Collection<WordState> notMutatedWords,
            final char[][] table,
            final Map<String, WordState> wordStatesMap,
            final Collection<WordState> horizontalWords,
            final Collection<WordState> verticalWords
    ) {
        notMutatedWords.forEach(word -> {
            putWord(word, table, horizontalWords, verticalWords);
            wordStatesMap.put(word.word, word);
        });
    }

    // ---------------------------- UTILS ----------------------------

    private static <T> T randomElement(final List<T> list, final Random random) {
        return list.get(random.nextInt(0, list.size()));
    }

    private static <T> T last(final List<T> list) { return list.get(list.size() - 1); }
}
