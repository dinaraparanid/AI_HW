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
    private static final int FITNESS_WORD_CRITERIA_AMOUNT = 2;

    // ------------------------- DATA HOLDERS -------------------------

    private record WordState(String word, int startRow, int startColumn, int layout) {}

    private record TableState(char[][] table, List<WordState> words) {}

    private record Coords(int startRow, int startColumn) {}

    private record CoordsWithLayout(int startRow, int startColumn, int layout) {}

    private record NeighbourState(WordState neighbour, NeighbourPosition position) {}

    enum NeighbourPosition { UP, DOWN, LEFT, RIGHT }

    // ------------------------- MAIN -------------------------

    public static void main(final String[] args) {
        try (final var reader = new BufferedReader(new FileReader("input1.txt"))) {
            final var words = reader.lines().toList();
            final var random = SecureRandom.getInstanceStrong();
            final var generatedPopulation = generateInitialPopulation(words, random);

            for (final var row : generatedPopulation.get(0).table) {
                for (final var c : row)
                    System.out.printf("%c ", c == 0 ? '_' : c);
                System.out.println();
            }
        } catch (final Exception ignored) {
        }
    }

    // ---------------------------- INITIAL POPULATION ----------------------------

    private static List<TableState> generateInitialPopulation(final List<String> words, final Random random) {
        return Stream
                .generate(() -> generateWordTable(words, random))
                .limit(POPULATION_SIZE)
                .toList();
    }

    private static TableState generateWordTable(final List<String> words, final Random random) {
        final var table = new char[TABLE_SIZE][TABLE_SIZE];

        final var wordStates = words.stream().map(word -> {
            final var wordState = generateWordState(word, table, random);
            if (wordState.layout == HORIZONTAL) putHorizontalWord(wordState, table);
            else putVerticalWord(wordState, table);
            return wordState;
        }).toList();

        return new TableState(table, wordStates);
    }

    private static WordState generateWordState(
            final String word,
            final char[][] table,
            final Random random
    ) {
        return IntStream
                .generate(() -> random.nextInt(2))
                .mapToObj(layout -> {
                    final var crds = generateStartCoords(word, layout, random);
                    return new CoordsWithLayout(crds.startRow, crds.startColumn, layout);
                })
                .filter(crdsToLayout -> canPutWord(
                        word,
                        crdsToLayout.startRow,
                        crdsToLayout.startColumn,
                        crdsToLayout.layout,
                        table
                ))
                .map(wordCoords -> new WordState(
                        word,
                        wordCoords.startRow,
                        wordCoords.startColumn,
                        wordCoords.layout
                ))
                .findFirst()
                .get();
    }

    private static Coords generateStartCoords(
            final String word,
            final int layout,
            final Random random
    ) {
        return layout == HORIZONTAL
                ? generateHorizontalStartCoords(word, random)
                : generateVerticalStartCoords(word, random);
    }

    private static Coords generateHorizontalStartCoords(
            final String word,
            final Random random
    ) {
        final var startRow = random.nextInt(0, 20);
        final var startColumn = random.nextInt(0, 20 - word.length() + 1);
        return new Coords(startRow, startColumn);
    }

    private static Coords generateVerticalStartCoords(
            final String word,
            final Random random
    ) {
        final var startRow = random.nextInt(0, 20 - word.length() + 1);
        final var startColumn = random.nextInt(0, 20);
        return new Coords(startRow, startColumn);
    }

    private static boolean canPutWord(
            final String word,
            final int startRow,
            final int startColumn,
            final int layout,
            final char[][] table
    ) {
        return layout == HORIZONTAL
                ? canPutHorizontalWord(word, startRow, startColumn, table)
                : canPutVerticalWord(word, startRow, startColumn, table);
    }

    private static boolean canPutHorizontalWord(
            final String word,
            final int startRow,
            final int startColumn,
            final char[][] table
    ) {
        for (int x = startColumn; x < startColumn + word.length(); ++x)
            if (table[startRow][x] != 0 && table[startRow][x] != word.charAt(x - startColumn))
                return true;

        return false;
    }

    private static boolean canPutVerticalWord(
            final String word,
            final int startRow,
            final int startColumn,
            final char[][] table
    ) {
        for (int y = startRow; y < startRow + word.length(); ++y)
            if (table[y][startColumn] != 0 && table[y][startColumn] != word.charAt(y - startRow))
                return false;

        return true;
    }

    private static void putHorizontalWord(
            final WordState wordState,
            final char[][] table
    ) {
        for (int x = wordState.startColumn; x < wordState.startColumn + wordState.word.length(); ++x)
            table[wordState.startRow][x] = wordState.word.charAt(x - wordState.startColumn);
    }

    private static void putVerticalWord(
            final WordState wordState,
            final char[][] table
    ) {
        for (int y = wordState.startRow; y < wordState.startRow + wordState.word.length(); ++y)
            table[y][wordState.startColumn] = wordState.word.charAt(y - wordState.startRow);
    }

    // ---------------------------- SELECTION ----------------------------

    private static List<Float> fitness(final List<TableState> tableStates) {
        return tableStates.stream().map(tableState -> {
            final var wordStates = tableState.words;
            final var wordsCoords = wordsCoords(wordStates);
            final var wordsCrosses = wordsCrosses(wordsCoords);
            final var horizontalNeighbours = wordsNeighbours(HORIZONTAL, wordsCoords);
            final var verticalNeighbours = wordsNeighbours(VERTICAL, wordsCoords);

            final var totalFitness = wordStates
                    .stream()
                    .map(wordState -> wordFitness(
                            wordState,
                            wordsCrosses,
                            horizontalNeighbours,
                            verticalNeighbours
                    ))
                    .reduce(Float::sum)
                    .orElse(0F);

            return totalFitness / wordStates.size();
        }).toList();
    }

    private static float wordFitness(
            final WordState wordState,
            final Map<WordState, Collection<WordState>> wordsCrosses,
            final Map<WordState, Collection<NeighbourState>> horizontalNeighbours,
            final Map<WordState, Collection<NeighbourState>> verticalNeighbours
    ) {
        return (
                wordCrossed(wordState, wordsCrosses) +
                noCloseNeighbours(wordState, horizontalNeighbours, verticalNeighbours)
        ) / FITNESS_WORD_CRITERIA_AMOUNT;
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
                        crosses(wordsCoords, wordCoords.getValue())
                ))
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    private static Collection<Coords> wordCoords(final WordState wordState) {
        return wordState.layout == HORIZONTAL
                ? horizontalWordCoords(wordState)
                : verticalWordCoords(wordState);
    }

    private static Collection<Coords> horizontalWordCoords(final WordState wordState) {
        return IntStream
                .range(wordState.startColumn, wordState.startColumn + wordState.word.length())
                .mapToObj(x -> new Coords(wordState.startRow, x))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Collection<Coords> verticalWordCoords(final WordState wordState) {
        return IntStream
                .range(wordState.startRow, wordState.startRow + wordState.word.length())
                .mapToObj(y -> new Coords(y, wordState.startColumn))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static List<WordState> crosses(
            final Map<WordState, Collection<Coords>> wordsCoords,
            final Collection<Coords> coords
    ) {
        return wordsCoords
                .entrySet()
                .stream()
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

    private static Map<WordState, Collection<NeighbourState>> wordsNeighbours(
            final int layout,
            final Map<WordState, Collection<Coords>> wordsCoords
    ) {
        return wordsCoords
                .entrySet()
                .stream()
                .filter(wordCoords -> wordCoords.getKey().layout == layout)
                .map(wordCoords -> Map.entry(
                        wordCoords.getKey(),
                        neighbours(layout, wordsCoords, wordCoords.getValue())
                ))
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    private static List<NeighbourState> neighbours(
            final int layout,
            final Map<WordState, Collection<Coords>> wordsCoords,
            final Collection<Coords> coords
    ) {
        return layout == HORIZONTAL
                ? horizontalNeighbours(wordsCoords, coords)
                : verticalNeighbours(wordsCoords, coords);
    }

    private static List<NeighbourState> horizontalNeighbours(
            final Map<WordState, Collection<Coords>> wordsCoords,
            final Collection<Coords> coords
    ) {
        final var upperCoords = mapHorizontalCoords(coords, y -> y + 1);
        final var lowerCoords = mapHorizontalCoords(coords, y -> y - 1);

        final var upperNeighbours = directedNeighbours(
                HORIZONTAL,
                NeighbourPosition.UP,
                wordsCoords,
                upperCoords
        );

        final var lowerNeighbours = directedNeighbours(
                HORIZONTAL,
                NeighbourPosition.DOWN,
                wordsCoords,
                lowerCoords
        );

        return Stream
                .of(upperNeighbours, lowerNeighbours)
                .flatMap(Collection::stream)
                .toList();
    }

    private static List<NeighbourState> verticalNeighbours(
            final Map<WordState, Collection<Coords>> wordsCoords,
            final Collection<Coords> coords
    ) {
        final var rightCoords = mapVerticalCoords(coords, x -> x + 1);
        final var leftCoords = mapVerticalCoords(coords, x -> x - 1);

        final var rightNeighbours = directedNeighbours(
                VERTICAL,
                NeighbourPosition.RIGHT,
                wordsCoords,
                rightCoords
        );

        final var leftNeighbours = directedNeighbours(
                VERTICAL,
                NeighbourPosition.LEFT,
                wordsCoords,
                leftCoords
        );

        return Stream
                .of(rightNeighbours, leftNeighbours)
                .flatMap(Collection::stream)
                .toList();
    }

    private static List<NeighbourState> directedNeighbours(
            final int layout,
            final NeighbourPosition direction,
            final Map<WordState, Collection<Coords>> wordsCoords,
            final Collection<Coords> coords
    ) {
        return wordsCoords
                .entrySet()
                .stream()
                .filter(otherWordWithCoords -> otherWordWithCoords.getKey().layout == layout)
                .filter(otherWordWithCoords ->
                        otherWordWithCoords
                                .getValue()
                                .stream()
                                .anyMatch(coords::contains)
                )
                .map(otherWordWithCoords -> new NeighbourState(
                        otherWordWithCoords.getKey(),
                        direction
                ))
                .toList();
    }

    private static Set<Coords> mapHorizontalCoords(
            final Collection<Coords> coords,
            final Function<Integer, Integer> mapper
    ) {
        return coords
                .stream()
                .map(crds -> new Coords(mapper.apply(crds.startRow), crds.startColumn))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<Coords> mapVerticalCoords(
            final Collection<Coords> coords,
            final Function<Integer, Integer> mapper
    ) {
        return coords
                .stream()
                .map(crds -> new Coords(crds.startRow, mapper.apply(crds.startColumn)))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static float noCloseNeighbours(
            final WordState wordState,
            final Map<WordState, Collection<NeighbourState>> horizontalNeighbours,
            final Map<WordState, Collection<NeighbourState>> verticalNeighbours
    ) {
        return wordState.layout == HORIZONTAL
                ? noCloseNeighboursHorizontal(horizontalNeighbours.get(wordState))
                : noCloseNeighboursVertical(verticalNeighbours.get(wordState));
    }

    private static float noCloseNeighboursHorizontal(final Collection<NeighbourState> neighbours) {
        if (tooManyDirectedNeighbours(NeighbourPosition.UP, neighbours)) return 0F;
        if (tooManyDirectedNeighbours(NeighbourPosition.DOWN, neighbours)) return 0F;
        return MAX_FITNESS_CRITERIA_WEIGHT;
    }

    private static float noCloseNeighboursVertical(final Collection<NeighbourState> neighbours) {
        if (tooManyDirectedNeighbours(NeighbourPosition.RIGHT, neighbours)) return 0F;
        if (tooManyDirectedNeighbours(NeighbourPosition.LEFT, neighbours)) return 0F;
        return MAX_FITNESS_CRITERIA_WEIGHT;
    }

    private static boolean tooManyDirectedNeighbours(
            final NeighbourPosition direction,
            final Collection<NeighbourState> neighbours
    ) {
        return neighbours
                .stream()
                .filter(neighbour -> neighbour.position == direction)
                .limit(2)
                .count()> 1;
    }
}
