package assignment2.students;

import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
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

    /**
     * A single word in crossword's table
     * @param word The word itself
     * @param startRow zero-based row index where the word starts
     * @param startColumn zero-based column index where the word starts
     * @param layout layout of the word: 0 - horizontal, 1 - vertical
     */

    private record WordState(String word, int startRow, int startColumn, int layout) {}

    /**
     * The state of a crossword's table
     * @param table table with placed words
     * @param words words' states, placed in the table
     */

    private record TableState(char[][] table, List<WordState> words) {}

    /**
     * A specific character's coordinate within a table
     * @param row zero-based row index of the character
     * @param column zero-based column index of the character
     * @param c character itself
     */

    private record Coords(int row, int column, char c) {}

    /**
     * Word's coordinates and the layout within a table
     * @param startRow zero-based row index of the coordinate
     * @param startColumn zero-based column index of the coordinate
     * @param layout layout of the word: 0 - horizontal, 1 - vertical
     */

    private record CoordsWithLayout(int startRow, int startColumn, int layout) {}

    /**
     * State of a fitness prefix sum after scan-like operation
     * @param index index of the current element in population being evaluated
     * @param fitness current fitness sum
     */

    private record ScanFitnessState(int index, float fitness) {}

    /**
     * Word state with its fitness
     * @param word the word itself
     * @param fitness word's fitness
     */

    private record WordFitnessState(WordState word, float fitness) {}

    /**
     * Fitness values of a table
     * @param table the table itself
     * @param wordsFitness list of words' fitness scores (number in range [0;1])
     * @param graphFitness fitness score of the words' connection graph
     * (longest connectivity divided by all words, number in range [0;1])
     */

    private record TableFitnessState(
            TableState table,
            List<WordFitnessState> wordsFitness,
            float graphFitness
    ) {
        /**
         * Calculates the total table's fitness,
         * which is the sum of graph fitness
         * and the average of the words' fitness
         * @return the total fitness score of the table
         * @see TableFitnessState#totalWordsFitness()
         */

        public float totalFitness() {
            return graphFitness + (totalWordsFitness() / wordsFitness.size());
        }

        /**
         * Calculates the sum of the words' fitness
         * @return The sum of the words' fitness
         * @see WordFitnessState#fitness
         */

        public float totalWordsFitness() {
            return wordsFitness
                    .stream()
                    .map(WordFitnessState::fitness)
                    .reduce(Float::sum)
                    .orElse(0F);
        }
    }

    /**
     * Fitness score with its index
     * @param fitness fitness score
     * @param index index of the element to which the fitness score corresponds
     */

    private record FitnessWithIndex(float fitness, int index) {}

    /**
     * Filename and the generated crossword result
     * @param filename input file name
     * @param result result of crossword generation
     */

    private record FilenameWithGenerationResult(String filename, Optional<TableState> result) {}

    // ------------------------- GENERATION -------------------------

    public static void main(final String[] args) {
        Arrays
                .stream(inputFiles())
                .map(filename -> new FilenameWithGenerationResult(filename, generateForFile(filename)))
                .filter(res -> res.result.isPresent())
                .forEach(fileWithTable -> printTableToFile(fileWithTable.result.get(), fileWithTable.filename));
    }

    /**
     * Retrieves the list of input files from the current directory
     * @return An array of input file names
     */

    private static String[] inputFiles() {
        return Objects.requireNonNull(
                new File(".")
                        .list((dir, name) -> name.matches("input[0-9]+\\.txt"))
        );
    }

    /**
     * Writes the generated table to a file with a corresponding output filename
     * @param result The generated table state
     * @param inputFilename The input filename corresponding to the generated table state
     */

    private static void printTableToFile(
            final TableState result,
            final String inputFilename
    ) {
        try (final var writer = new PrintWriter(new FileWriter(outputFilename(inputFilename)))) {
            printWordsToFile(result, writer);
        } catch (final Exception ignored) {
        }
    }

    /**
     * Generates the output filename based on the provided input filename
     * @param inputFilename The input filename
     * @return The corresponding output filename
     */

    private static String outputFilename(final String inputFilename) {
        return "output" + fileNumber(inputFilename) + ".txt";
    }

    /**
     * Extracts the file number from the provided input filename
     * @param inputFilename the input filename
     * @return the file number extracted from the filename
     */

    private static int fileNumber(final String inputFilename) {
        final var pattern = Pattern.compile("input([0-9]+)\\.txt");
        final var matcher = pattern.matcher(inputFilename);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    /**
     * Prints the words in the given table state to the provided file
     * @param result table state with words
     * @param writer file writer
     */

    private static void printWordsToFile(final TableState result, final PrintWriter writer) {
        result.words.forEach(word -> printWordToFile(word, writer));
    }

    /**
     * Prints the information of the given word state to the provided file
     * @param wordState The word state to print
     * @param writer file writer
     */

    private static void printWordToFile(final WordState wordState, final PrintWriter writer) {
        writer.printf("%d %d %d\n", wordState.startRow, wordState.startColumn, wordState.layout);
    }

    /**
     * Generates crossword for the given file
     * @param filename given input file name
     * @return obtained table, if generation is successful
     */

    private static Optional<TableState> generateForFile(final String filename) {
        try (final var reader = new BufferedReader(new FileReader(filename))) {
            final var words = reader.lines().toList();
            final var random = SecureRandom.getInstanceStrong();

            // -------- Generation loop --------

            for (var population = initialPopulation(words, random);;) {
                final var selected = selectWithRoulette(population, random);
                population = nextPopulation(selected, random);

                final var fitnessValues = fitnessValues(population);
                final var maxFitness = maxFitness(fitnessValues);

                // 1 + 1 <- graph connectivity + words crossing

                if (maxFitness.fitness >= 2F) {
                    System.out.printf("DONE FOR %s:", filename);
                    final var res = population.get(maxFitness.index);
                    printTable(population.get(maxFitness.index));
                    return Optional.of(res);
                }
            }
        } catch (final Exception ignored) {
            return Optional.empty();
        }
    }

    /**
     * Calculates maximum fitness value with the corresponding index
     * @param fitnessValues the list of fitness values
     * @return the maximum fitness value and its corresponding index
     */

    private static FitnessWithIndex maxFitness(final List<Float> fitnessValues) {
        return IntStream
                .range(0, fitnessValues.size())
                .mapToObj(i -> new FitnessWithIndex(fitnessValues.get(i), i))
                .max((a, b) -> Float.compare(a.fitness, b.fitness))
                .orElseGet(() -> new FitnessWithIndex(fitnessValues.get(0), 0));
    }

    /**
     * Prints the given table state to the console
     * @param tableState The table state to be printed
     */

    private static void printTable(final TableState tableState) {
        for (final var row : tableState.table) {
            for (final var c : row)
                System.out.printf("%c ", c == 0 ? '_' : c);
            System.out.println();
        }
    }

    // ---------------------------- INITIAL POPULATION ----------------------------

    /**
     * Creates an initial population of table states
     * by randomly generating a specified number of tables using the provided words list.
     * All generated tables consist of correctly inserted words:
     * no overlapping with neighbours and no overlapping with borders.
     * Some words may cross each other, graph connectivity is not guaranteed
     *
     * @param words list of words to use for table generation
     * @param random random generator
     * @return A list of randomly generated table states
     */

    private static List<TableState> initialPopulation(final List<String> words, final Random random) {
        return Stream
                .generate(() -> wordTable(words, random))
                .limit(POPULATION_SIZE)
                .toList();
    }

    /**
     * Generates a table state with randomly placed words from a given list of words.
     * Generated table consists of correctly inserted words:
     * no overlapping with neighbours and no overlapping with borders.
     * Some words may cross each other, graph connectivity is not guaranteed
     *
     * @param words list of words to place in the table
     * @param random random generator
     * @return A table state representing the generated table
     */

    private static TableState wordTable(final List<String> words, final Random random) {
        final var table = new char[TABLE_SIZE][TABLE_SIZE];
        final var horizontalWords = new ArrayList<WordState>();
        final var verticalWords = new ArrayList<WordState>();

        final var wordStates = wordStates(words, table, horizontalWords, verticalWords, random);
        return new TableState(table, wordStates);
    }

    /**
     * Generates random positions for words,
     * randomly places them into the provided table
     * and updates the corresponding word lists.
     * For each word there is no overlapping with neighbours and no overlapping with borders.
     * Some words may cross each other, graph connectivity is not guaranteed
     *
     * @param words list of words to place in the table
     * @param table table in which to place the words
     * @param horizontalWords horizontal words placed in the table
     * @param verticalWords vertical words placed in the table
     * @param random random generator
     * @return A list of word states representing the placed words
     */

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

    /**
     * Generates random position for the given word,
     * randomly places it into the provided table
     * and updates the corresponding word lists.
     * For each word there is no overlapping with neighbours and no overlapping with borders.
     * Some words may cross each other, graph connectivity is not guaranteed
     *
     * @param word word to be placed in the table
     * @param table table in which to place the word
     * @param horizontalWords horizontal words placed in the table
     * @param verticalWords vertical words placed in the table
     * @param random random generator
     * @return The word state representing the placed word
     */

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
                    return new CoordsWithLayout(crds.row, crds.column, layout);
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

    /**
     * Generates the starting coordinates for placing a word in the table
     * based on the given layout (0 - horizontal, 1 - vertical).
     * Generated coordinates guaranteed to have no overlap with both neighbours and table's borders
     *
     * @param word word to be placed in the table
     * @param layout layout of the word (0 - horizontal, 1 - vertical)
     * @param random random generator
     * @return tuple of starting row and column
     */

    private static Coords startCoords(
            final String word,
            final int layout,
            final Random random
    ) {
        return layout == HORIZONTAL
                ? startCoordsHorizontal(word, random)
                : startCoordsVertical(word, random);
    }

    /**
     * Generates the starting coordinates for placing a word in the table in horizontal position.
     * Generated coordinates guaranteed to have no overlap with both neighbours and table's borders
     *
     * @param word word to be placed in the table
     * @param random random generator
     * @return tuple of starting row and column
     */

    private static Coords startCoordsHorizontal(
            final String word,
            final Random random
    ) {
        final var startRow = random.nextInt(0, 20);
        final var startColumn = random.nextInt(0, 20 - word.length() + 1);
        return new Coords(startRow, startColumn, '\0');
    }

    /**
     * Generates the starting coordinates for placing a word in the table in vertical position.
     * Generated coordinates guaranteed to have no overlap with both neighbours and table's borders
     *
     * @param word word to be placed in the table
     * @param random random generator
     * @return tuple of starting row and column
     */

    private static Coords startCoordsVertical(
            final String word,
            final Random random
    ) {
        final var startRow = random.nextInt(0, 20 - word.length() + 1);
        final var startColumn = random.nextInt(0, 20);
        return new Coords(startRow, startColumn, '\0');
    }

    /**
     * Checks if the word can be placed in the table
     * with the given starting coordinates and the layout.
     * Word's placement has to fit table's bounds and make no overlaps with neighbours.
     *
     * @param word word to be placed in the table
     * @param startRow starting row coordinate for the word
     * @param startColumn starting column coordinate for the word
     * @param layout layout of the word (0 - horizontal, 1 - vertical)
     * @param table table in which to place the word
     * @param horizontalWords horizontal words placed in the table
     * @param verticalWords vertical words placed in the table
     * @return true if the word can be placed without overlaps
     */

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

    /**
     * Checks if the word can be placed in the table
     * with the given starting coordinates and the layout.
     * Word's placement has to fit table's bounds and make no overlaps with neighbours.
     *
     * @param wordState word with its starting coordinates and the layout
     * @param table table in which to place the word
     * @param horizontalWords horizontal words placed in the table
     * @param verticalWords vertical words placed in the table
     * @return true if the word can be placed without overlaps
     */

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

    /**
     * Checks if the word can be placed horizontally
     * in the table with the given starting coordinates.
     * Word's placement has to fit table's bounds and make no overlaps with neighbours.
     *
     * @param word word to be placed in the table
     * @param startRow starting row coordinate for the word
     * @param startColumn starting column coordinate for the word
     * @param table table in which to place the word
     * @param horizontalWords horizontal words placed in the table
     * @return true if the word can be placed without overlaps
     */

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
                        || adjacentHorizontal(wordState, w)
                );
    }

    /**
     * Checks if the word can be placed vertically
     * in the table with the given starting coordinates.
     * Word's placement has to fit table's bounds and make no overlaps with neighbours.
     *
     * @param word word to be placed in the table
     * @param startRow starting row coordinate for the word
     * @param startColumn starting column coordinate for the word
     * @param table table in which to place the word
     * @param verticalWords vertical words placed in the table
     * @return true if the word can be placed without overlaps
     */

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
                        || adjacentVertical(wordState, w)
                );
    }

    /**
     * Checks whether two horizontal word states
     * have a neighboring border greater than 1,
     * indicating an overlap
     *
     * @param a first word state
     * @param b second word state
     * @return true if the neighboring border is too large.
     */

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

    /**
     * Checks whether two vertical word states
     * have a neighboring border greater than 1,
     * indicating an overlap
     *
     * @param a first word state
     * @param b second word state
     * @return true if the neighboring border is too large.
     */

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

    /**
     * Extracts the set of column coordinates
     * occupied by a given word state in the table
     *
     * @param wordState word from which to extract the columns
     * @return set of column indices occupied by the word
     */

    private static Set<Integer> columnsSet(final WordState wordState) {
        return wordCoords(wordState)
                .stream()
                .map(Coords::column)
                .collect(Collectors.toSet());
    }

    /**
     * Extracts the set of rows coordinates
     * occupied by a given word state in the table
     *
     * @param wordState word from which to extract the rows
     * @return set of row indices occupied by the word
     */

    private static Set<Integer> rowsSet(final WordState wordState) {
        return wordCoords(wordState)
                .stream()
                .map(Coords::row)
                .collect(Collectors.toSet());
    }

    /**
     * Checks whether two horizontal words are directly adjacent to each other
     * @param a first word state
     * @param b second word state
     * @return true if the word states are directly adjacent
     */

    private static boolean adjacentHorizontal(
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

    /**
     * Checks whether two vertical words are directly adjacent to each other
     * @param a first word state
     * @param b second word state
     * @return true if the word states are directly adjacent
     */

    private static boolean adjacentVertical(
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

    /**
     * Places a word with the given positions and the layout
     * and updates word lists to reflect the placement of a given word state
     *
     * @param wordState word with its positions and the layout to place
     * @param table table to place the word
     * @param horizontalWords horizontal words placed in the table
     * @param verticalWords vertical words placed in the table
     */

    private static void putWord(
            final WordState wordState,
            final char[][] table,
            final Collection<WordState> horizontalWords,
            final Collection<WordState> verticalWords
    ) {
        if (wordState.layout == HORIZONTAL) putHorizontalWord(wordState, table, horizontalWords);
        else putVerticalWord(wordState, table, verticalWords);
    }

    /**
     * Places a word with the given positions horizontally
     * and updates word lists to reflect the placement of a given word state
     *
     * @param wordState word with its positions and the layout to place
     * @param table table to place the word
     * @param horizontalWords horizontal words placed in the table
     */

    private static void putHorizontalWord(
            final WordState wordState,
            final char[][] table,
            final Collection<WordState> horizontalWords
    ) {
        for (int x = wordState.startColumn; x < wordState.startColumn + wordState.word.length(); ++x)
            table[wordState.startRow][x] = wordState.word.charAt(x - wordState.startColumn);
        horizontalWords.add(wordState);
    }

    /**
     * Places a word with the given positions vertically
     * and updates word lists to reflect the placement of a given word state
     *
     * @param wordState word with its positions and the layout to place
     * @param table table to place the word
     * @param verticalWords vertical words placed in the table
     */

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

    /**
     * Selects a subset of table states from a given population
     * using the roulette wheel selection algorithm.
     * Algorithm computes fitness values for each table,
     * then finds prefix sums for all fitness values,
     * then applies roulette algorithm for fitness prefix sums
     *
     * @param population list of table states to select from
     * @param random random generator
     * @return A list of selected table states
     */

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

    /**
     * Calculates the fitness state of each table state in a given population
     * @param population The list of table states to evaluate
     * @return A list of fitness states representing the fitness of each table
     */

    private static List<TableFitnessState> fitnessStates(final List<TableState> population) {
        return population.stream().map(Main::fitness).toList();
    }

    /**
     * Extracts the fitness values from a list of table fitness states
     * @param fitnessStates The list of table fitness states
     * @return A list of fitness values
     */

    private static List<Float> fitnessValuesFromStates(final List<TableFitnessState> fitnessStates) {
        return fitnessStates.stream().map(TableFitnessState::totalFitness).toList();
    }

    /**
     * Calculates the fitness values for a given population
     * @param population The list of table states to evaluate
     * @return A list of fitness values corresponding to each table state
     */

    private static List<Float> fitnessValues(final List<TableState> population) {
        return fitnessValuesFromStates(fitnessStates(population));
    }

    /**
     * Calculates the fitness prefix sums for a given list of fitness values
     * @param fitnessValues The list of fitness values
     * @return A list of the cumulative fitness sums (prefix sums)
     */

    private static List<ScanFitnessState> fitnessSums(final List<Float> fitnessValues) {
        final var sums = new ArrayList<ScanFitnessState>(fitnessValues.size());
        float acc = 0;

        for (int i = 0; i < fitnessValues.size(); ++i) {
            acc += fitnessValues.get(i);
            sums.add(new ScanFitnessState(i, acc));
        }

        return sums;
    }

    /**
     * Calculates the fitness of a given table based on word placement and connectivity
     * @param tableState The table state to evaluate
     * @return table with its fitness results (each words' fitness + graph connectivity)
     * @see TableFitnessState
     */

    private static TableFitnessState fitness(final TableState tableState) {
        final var wordStates = tableState.words;
        final var wordsCoords = wordsCoords(wordStates);
        final var wordsCrosses = wordsCrosses(wordsCoords);
        final var wordsFitness = wordsFitness(wordStates, wordsCrosses);
        final float connectivityFitness = longestConnectivity(wordsCrosses);
        return new TableFitnessState(tableState, wordsFitness, connectivityFitness / wordStates.size());
    }

    /**
     * Calculates the fitness of each word, considering words' crosses.
     * If the word is crossed at least once - result is 1, otherwise - 0.
     *
     * @param wordStates list of words to evaluate
     * @param wordsCrosses map representing the words' intersections
     * @return list of fitness values for each word
     */

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

    /**
     * Generates a stream of connected graph components,
     * represented as sets of word states,
     * from a map of words' intersections
     *
     * @param wordsCrosses map representing the words' intersections
     * @return stream of sets of word states, each representing a connectivity component
     */

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

    /**
     * Generates a list of connected graph components,
     * represented as sets of word states,
     * from a map of words' intersections
     *
     * @param wordsCrosses map representing the words' intersections
     * @return list of sets of word states, each representing a connectivity component
     */

    private static List<Set<WordState>> connectivityComponents(
            final Map<WordState, Collection<WordState>> wordsCrosses
    ) {
        return connectivityComponentsStream(wordsCrosses).toList();
    }

    /**
     * Determines the length of the longest connectivity component in the word placement graph
     * @param wordsCrosses map representing the words' intersections
     * @return length of the longest connected component
     */

    private static int longestConnectivity(final Map<WordState, Collection<WordState>> wordsCrosses) {
        return connectivityComponentsStream(wordsCrosses)
                .map(Collection::size)
                .max(Integer::compareTo)
                .orElse(0);
    }

    /**
     * Performs a depth-first search traversal
     * starting from the given word
     * and produces the set of connected word states
     *
     * @param start the word state from which to start the DFS traversal
     * @param wordsCrosses map representing the words' intersections
     * @return set of word states connected to the starting word state
     */

    private static Set<WordState> dfs(
            final WordState start,
            final Map<WordState, Collection<WordState>> wordsCrosses
    ) {
        final var set = new HashSet<WordState>();
        dfsImpl(start, set, wordsCrosses);
        return set;
    }

    /**
     * Implementation of a depth-first search traversal starting from the given word
     *
     * @param current The current word state being visited.
     * @param set The set of visited word states.
     * @param wordsCrosses A map representing word intersections
     * @see Main#dfs(WordState, Map)
     */

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

    /**
     * Calculates the word fitness based on the intersections.
     * If the word is crossed at least once - result is 1, otherwise - 0
     *
     * @param wordState the word state to evaluate
     * @param wordsCrosses map representing word intersections
     * @return If the word is crossed at least once - 1, otherwise - 0
     */

    private static float wordFitness(
            final WordState wordState,
            final Map<WordState, Collection<WordState>> wordsCrosses
    ) {
        return wordCrossed(wordState, wordsCrosses) / FITNESS_WORD_CRITERIA_AMOUNT;
    }

    /**
     * Creates a map associating each word
     * with its coordinates for every symbol
     * @param wordStates list of word states to process
     * @return map where each key represents a word state
     * and the value as every symbol's coordinates
     */

    private static Map<WordState, Collection<Coords>> wordsCoords(final List<WordState> wordStates) {
        return wordStates
                .stream()
                .map(word -> Map.entry(word, wordCoords(word)))
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    /**
     * Creates a map associating each word with the set of word it intersects with
     * @param wordsCoords map where each key represents a word
     * and the value as every symbol's coordinates
     * @return map where each key represents a word
     * and the value represents the set of word states it intersects with
     */

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

    /**
     * Retrieves the coordinates of a word for every symbol based on its layout
     * @param wordState the word state to process
     * @return A set of coordinates representing the positions
     * occupied by every symbol of the word
     */

    private static Set<Coords> wordCoords(final WordState wordState) {
        return wordState.layout == HORIZONTAL
                ? horizontalWordCoords(wordState)
                : verticalWordCoords(wordState);
    }

    /**
     * Retrieves the coordinates of a horizontally-placed word for every symbol
     * @param wordState the word state to process
     * @return A set of coordinates representing the positions
     * occupied by every symbol of the word
     */

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

    /**
     * Retrieves the coordinates of a vertically-placed word for every symbol
     * @param wordState the word state to process
     * @return A set of coordinates representing the positions
     * occupied by every symbol of the word
     */

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

    /**
     * Determines the word states that intersect
     * with a given word based on their coordinates
     *
     * @param wordState the word state itself
     * @param coords coordinates of every symbol of the word
     * @param wordsCoords A map associating each word with its coordinates
     * @return A list of words that intersect with the given word
     */

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

    /**
     * Determines whether a given word intersects with any other word.
     * If the word is crossed at least once - result is 1, otherwise - 0
     *
     * @param wordState the word state to evaluate.
     * @param wordsCrosses map associating each word with the set of words it intersects with
     * @return fitness value indicating whether the word state is crossed by any other word
     */

    private static float wordCrossed(
            final WordState wordState,
            final Map<WordState, Collection<WordState>> wordsCrosses
    ) {
        if (!wordsCrosses.containsKey(wordState)) return 0F;
        return wordsCrosses.get(wordState).isEmpty() ? 0F : MAX_FITNESS_CRITERIA_WEIGHT;
    }

    // ---------------------------- NEXT POPULATION ----------------------------

    /**
     * Generates the next population of table states
     * based on the selected parents.
     * Applies both {@link Main#crossover(TableState, TableState, Random)} and {@link Main#mutation(TableState)}
     *
     * @param selected selected table states to improve
     * @param random random generator
     * @return table states representing the next generation
     */

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

    /**
     * Generates a collection of child table states
     * by performing Applies both {@link Main#crossover(TableState, TableState, Random)}
     * and {@link Main#mutation(TableState)} on selected parents.
     * With probability of {@link Main#INCLUDE_PARENT_PROBABILITY},
     * may pick one random parent.
     *
     * @param selected selected table states
     * @param random random generator
     * @return table states representing the next generation
     */

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

    /**
     * Performs crossover between two parent tables to generate an offspring table state.
     * Algorithm alternates words between two tables, attempting to place them in the same position
     * as it was in the parent. In case of failure, it tries to cross any word that is already in the table.
     * If fails again, randomly picks any position using {@link Main#wordState(String, char[][], Collection, Collection, Random)}
     *
     * @param parent1 the first parent table state
     * @param parent2 the second parent table state
     * @param random random generator
     * @return The offspring table state
     */

    private static TableState crossover(
            final TableState parent1,
            final TableState parent2,
            final Random random
    ) {
        final var table = new char[TABLE_SIZE][TABLE_SIZE];
        final var horizontalWords = new ArrayList<WordState>();
        final var verticalWords = new ArrayList<WordState>();
        final var wordStates = crossoverWordStates(parent1, parent2, table, horizontalWords, verticalWords, random);
        return new TableState(table, wordStates);
    }

    /**
     * Generates the offspring list of words, produced from the given parent tables.
     * Algorithm alternates words between two tables, attempting to place them in the same position
     * as it was in the parent. In case of failure, it tries to cross any word that is already in the table.
     * If fails again, randomly picks any position using {@link Main#wordState(String, char[][], Collection, Collection, Random)}
     *
     * @param parent1 the first parent table state
     * @param parent2 the second parent table state
     * @param table table to place the word
     * @param horizontalWords horizontal words placed in the table
     * @param verticalWords vertical words placed in the table
     * @param random random generator
     * @return The offspring table state
     * @see Main#crossover(TableState, TableState, Random)
     */

    private static List<WordState> crossoverWordStates(
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

    /**
     * Generates the offspring word based on already placed words in the table.
     * Algorithm alternates words between two tables, attempting to place them in the same position
     * as it was in the parent. In case of failure, it tries to cross any word that is already in the table.
     * If fails again, randomly picks any position using {@link Main#wordState(String, char[][], Collection, Collection, Random)}
     *
     * @param table table to place the word
     * @param horizontalWords horizontal words placed in the table
     * @param verticalWords vertical words placed in the table
     * @param random random generator
     * @return The offspring table state
     * @see Main#crossoverWordStates(TableState, TableState, char[][], Collection, Collection, Random)
     */

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

    /**
     * Attempts to place a word state in the table
     * while maintaining the same position as in the parent's table if possible
     *
     * @param wordState the word state to place
     * @param table the table to place the word
     * @param horizontalWords horizontal words placed in the table
     * @param verticalWords vertical words placed in the table
     * @return the same word state, if placement is successful
     */

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

    /**
     * Attempts to place a word state in the table
     * while trying to cross with other words to improve fitness
     *
     * @param wordState the word state to place
     * @param table the table to place the word
     * @param horizontalWords horizontal words placed in the table
     * @param verticalWords vertical words placed in the table
     * @return the same word state, if placement is successful
     */

    private static Optional<WordState> tryPutCrossing(
            final WordState wordState,
            final char[][] table,
            final Collection<WordState> horizontalWords,
            final Collection<WordState> verticalWords
    ) {
        return tryCrossingStartCoords(wordState.word, table, horizontalWords, verticalWords)
                .map(coordsWithLayout -> new WordState(
                        wordState.word,
                        coordsWithLayout.startRow,
                        coordsWithLayout.startColumn,
                        coordsWithLayout.layout
                ));
    }

    /**
     * Attempts to find starting coordinates for a word on the table,
     * that may produce an intersection with other words,
     * considering both horizontal and vertical placement
     *
     * @param word the word to place
     * @param table the table to place the word
     * @param horizontalWords horizontal words placed in the table
     * @param verticalWords vertical words placed in the table
     * @return the same word state, if placement is successful
     */

    private static Optional<CoordsWithLayout> tryCrossingStartCoords(
            final String word,
            final char[][] table,
            final Collection<WordState> horizontalWords,
            final Collection<WordState> verticalWords
    ) {
        return Stream
                .of(
                        tryCrossingStartCoordsHorizontal(word, table, horizontalWords, verticalWords),
                        tryCrossingStartCoordsVertical(word, table, horizontalWords, verticalWords)
                )
                .filter(Optional::isPresent)
                .findFirst()
                .flatMap(Function.identity());
    }

    /**
     * Attempts to find starting coordinates for a word on the table,
     * that may produce an intersection with other words,
     * considering only horizontal placement
     *
     * @param word the word to place
     * @param table the table to place the word
     * @param horizontalWords horizontal words placed in the table
     * @param verticalWords vertical words placed in the table
     * @return the same word state, if placement is successful
     * @see Main#tryCrossingStartCoords(String, char[][], Collection, Collection)
     */

    private static Optional<CoordsWithLayout> tryCrossingStartCoordsHorizontal(
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

    /**
     * Attempts to find starting coordinates for a word on the table,
     * that may produce an intersection with other words,
     * considering only vertical placement
     *
     * @param word the word to place
     * @param table the table to place the word
     * @param horizontalWords horizontal words placed in the table
     * @param verticalWords vertical words placed in the table
     * @return the same word state, if placement is successful
     * @see Main#tryCrossingStartCoords(String, char[][], Collection, Collection)
     */

    private static Optional<CoordsWithLayout> tryCrossingStartCoordsVertical(
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

    /**
     * Generates a stream of possible intersection points
     * for a word based on the coordinates of existing word states.
     * Computes coordinates of every word using {@link Main#wordCoordsMap(WordState)},
     * then filters coordinates of characters that are present in word,
     * using {@link Main#filterLetterMap(String, Map)}
     *
     * @param word the word that has to be intersected
     * @param directedWords the list of directed word states (either horizontal or vertical)
     * @return A stream of `Coords` objects representing the possible connection points.
     */

    private static Stream<Coords> possibleConnectionsStream(
            final String word,
            final Collection<WordState> directedWords
    ) {
        return directedWords
                .stream()
                .map(Main::wordCoordsMap)
                .flatMap(letterMap -> filterLetterMap(word, letterMap));
    }

    /**
     * Filters coordinates of characters in the map that are present in word
     * @param word word to check for connection points
     * @param letterMap map associating each letter with a list of corresponding coordinates
     * @return stream representing the potential connection points
     * @see Main#possibleConnectionsStream(String, Collection)
     */

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

    /**
     * Produces the coordinate map for a word,
     * where the key is the character
     * and the value is the list of coordinates,
     * where the symbol is located in the table
     *
     * @param wordState the word state to process
     * @return map associating each character in the word
     * with a list of coordinates in the table
     */

    private static Map<Character, List<Coords>> wordCoordsMap(final WordState wordState) {
        return wordState.layout == HORIZONTAL
                ? horizontalWordCoordsMap(wordState)
                : verticalWordCoordsMap(wordState);
    }

    /**
     * Produces the coordinate map for a horizontal word,
     * where the key is the character
     * and the value is the list of coordinates,
     * where the symbol is located in the table
     *
     * @param wordState the word state to process
     * @return map associating each character in the word
     * with a list of coordinates in the table
     */

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

    /**
     * Produces the coordinate map for a vertical word,
     * where the key is the character
     * and the value is the list of coordinates,
     * where the symbol is located in the table
     *
     * @param wordState the word state to process
     * @return map associating each character in the word
     * with a list of coordinates in the table
     */

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

    /**
     * Calculates the starting coordinates
     * for a horizontal word placement
     * based on a given intersection point
     *
     * @param word the word to place
     * @param connection the connection point coordinates
     * @return obtained starting coordinates with a horizontal layout,
     * if word can be placed with an intersection.
     * @see Main#tryCrossingStartCoordsHorizontal(String, char[][], Collection, Collection)
     */

    private static Optional<CoordsWithLayout> possibleStartCoordsHorizontal(final String word, final Coords connection) {
        final var index = word.indexOf(connection.c);
        return connection.column < index
                ? Optional.empty()
                : Optional.of(new CoordsWithLayout(connection.row, connection.column - index, HORIZONTAL));
    }

    /**
     * Calculates the starting coordinates
     * for a vertical word placement
     * based on a given intersection point
     *
     * @param word the word to place
     * @param connection the connection point coordinates
     * @return obtained starting coordinates with a vertical layout,
     * if word can be placed with an intersection.
     * @see Main#tryCrossingStartCoordsVertical(String, char[][], Collection, Collection)
     */

    private static Optional<CoordsWithLayout> possibleStartCoordsVertical(final String word, final Coords connection) {
        final var index = word.indexOf(connection.c);
        return connection.row < index
                ? Optional.empty()
                : Optional.of(new CoordsWithLayout(connection.row - index, connection.column, VERTICAL));
    }

    // ---------------------------- MUTATION ----------------------------

    /**
     * Performs mutation on a table state, introducing changes to its word placements.
     * Algorithm searches all connectivity components, using {@link Main#connectivityComponents(List)},
     * picks the last one as the component, from which first ceil(size * MUTATION_RATE)
     * words have to be intersected with other connectivity components.
     * For such mutated words, it tries to cross them with others,
     * using {@link Main#mergedOrSameWords(Collection, char[][], Collection, Collection)}.
     * In case of failure, position remains the same.
     *
     * @param tableState the table state to mutate
     * @return the mutated table state with improved fitness value
     * @see Main#nextChildWithMbParent(List, Random)
     */

    private static TableState mutation(final TableState tableState) {
        final var table = new char[TABLE_SIZE][TABLE_SIZE];
        final var wordStatesMap = new HashMap<String, WordState>();
        final var horizontalWords = new ArrayList<WordState>();
        final var verticalWords = new ArrayList<WordState>();

        final var connectivityComponents = connectivityComponents(tableState.words);
        final var notMutatedWords = notMutatedWordStates(connectivityComponents);
        putWords(notMutatedWords, table, horizontalWords, verticalWords, wordStatesMap);

        final var mergableComponent = last(connectivityComponents).stream().toList();
        final var maxWordsToMutate = (int) Math.ceil(mergableComponent.size() * MUTATION_RATE);
        final var mutatedWords = mergableComponent.subList(0, maxWordsToMutate);

        final var sameWords = mergableComponent.subList(maxWordsToMutate, mergableComponent.size());
        putWords(sameWords, table, horizontalWords, verticalWords, wordStatesMap);

        final var mergedOrSameWords = mergedOrSameWords(mutatedWords, table, horizontalWords, verticalWords);
        putWords(mergedOrSameWords, table, horizontalWords, verticalWords, wordStatesMap);

        final var wordStates = reorderWordStates(tableState.words, wordStatesMap);
        return new TableState(table, wordStates);
    }

    /**
     * Attempts to intersect the given words
     * with placed ones, or leaves it as it is.
     *
     * @param mutatedWords mutated words, for which algorithm is applied
     * @param table the table to place the word
     * @param horizontalWords list of horizontal word states
     * @param verticalWords list of vertical word states
     * @return list of word states, either merged or kept the same
     * @see Main#mutation(TableState)
     */

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

    /**
     * Reorders the word states based on their original order in the canonical order list
     * @param canonicalOrder list of word states in their original order
     * @param wordStatesMap map associating words to corresponding word states
     * @return list of word states in the original order
     */

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

    /**
     * Identifies connectivity components among word states
     * based on their coordinates and crossing relationships
     *
     * @param wordStates list of word states
     * @return list of sets of word states representing the connected components
     * @see Main#connectivityComponents(Map)
     */

    private static List<Set<WordState>> connectivityComponents(final List<WordState> wordStates) {
        final var wordsCords = wordsCoords(wordStates);
        final var wordsCrosses = wordsCrosses(wordsCords);
        return connectivityComponents(wordsCrosses);
    }

    /**
     * Retrieves the words from connectivity components except the last one
     * @param connectivityComponents lhe list of connectivity components
     * @return A collection of words that will not mutate
     */

    private static Collection<WordState> notMutatedWordStates(
            final List<Set<WordState>> connectivityComponents
    ) { return notMutatedWordStates(notMutatedComponents(connectivityComponents)); }

    /**
     * Extracts all connectivity components except the last one
     * @param connectivityComponents list of connectivity components
     * @return collection of connectivity components excluding the last one
     */

    private static Collection<Set<WordState>> notMutatedComponents(
            final List<Set<WordState>> connectivityComponents
    ) { return connectivityComponents.subList(0, connectivityComponents.size() - 1); }

    /**
     * Retrieves the words from connectivity components except the last one
     * @param notMutatedComponents non-mutated connectivity components (all except the last one)
     * @return non-mutated word states from
     */

    private static Collection<WordState> notMutatedWordStates(
            final Collection<Set<WordState>> notMutatedComponents
    ) {
        return notMutatedComponents
                .stream()
                .flatMap(Collection::stream)
                .toList();
    }

    /**
     * Places a collection of non-mutated word states
     * onto the table and updates the corresponding lists
     *
     * @param notMutatedWords non-mutated word states
     * @param table the table to place words
     * @param horizontalWords list of horizontal word states
     * @param verticalWords list of vertical word states
     * @param wordStatesMap map associating words to corresponding word states
     */

    private static void putWords(
            final Collection<WordState> notMutatedWords,
            final char[][] table,
            final Collection<WordState> horizontalWords,
            final Collection<WordState> verticalWords,
            final Map<String, WordState> wordStatesMap
    ) {
        notMutatedWords.forEach(word -> {
            putWord(word, table, horizontalWords, verticalWords);
            wordStatesMap.put(word.word, word);
        });
    }

    // ---------------------------- UTILS ----------------------------

    /**
     * Retrieves a random element from the list
     * @param list the list of elements to choose from
     * @param random random generator
     * @return randomly selected element from the list
     * @throws IllegalStateException if the list is empty
     */

    private static <T> T randomElement(final List<T> list, final Random random) {
        if (list.isEmpty()) throw new IllegalStateException("List is empty");
        return list.get(random.nextInt(0, list.size()));
    }

    /**
     * Retrieves the last element from the list
     * @param list the list of elements to pick
     * @return last element from the list
     * @throws IllegalStateException if the list is empty
     */

    private static <T> T last(final List<T> list) {
        if (list.isEmpty()) throw new IllegalStateException("List is empty");
        return list.get(list.size() - 1);
    }
}
