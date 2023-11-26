#include <iostream>
#include <ranges>
#include <vector>
#include <cstdint>
#include <set>
#include <queue>
#include <memory>
#include <algorithm>
#include <unordered_set>

const int INF = INT16_MAX;
const int TABLE_SIZE = 9;

struct cell;

using cell_ptr = std::shared_ptr<cell>;
using game_table_row = std::vector<cell_ptr>;
using game_table = std::vector<game_table_row>;
using restricted_cells = std::unordered_set<cell_ptr>;

/** @brief Represents a cell on the simulation table */

class cell {
    /** The row coordinate of the cell */
    int _n;

    /** The column coordinate of the cell */
    int _m;

public:

    /** The cost to move to this cell from the player's initial position */
    int from_player_cost;

    /** Event of the cell (perception, picked by hero, etc.) */
    char cell_status;

    /** List of possible heroes who occupied this cell */
    std::unordered_set<char> possibly_picked_by;

    /**
     * Constructs a cell object with the specified coordinates, status, and cost
     * @param n The row coordinate of the cell
     * @param m The column coordinate of the cell
     * @param cell_status Event of the cell
     * @param from_player_cost The cost to move from the player's initial position
     */

    explicit cell(
            const int n = 0,
            const int m = 0,
            const char cell_status = 0,
            const int from_player_cost = INF
    ) : _n(n), _m(m),
        cell_status(cell_status),
        from_player_cost(from_player_cost) {}

    ~cell() = default;

    /** The row coordinate of the cell */
    [[nodiscard]] int n() const { return _n; }

    /** The column coordinate of the cell */
    [[nodiscard]] int m() const { return _m; }

    /**
     * Checks whether the cell is dangerous to move
     * @return true if the cell is dangerous, false otherwise.
     */

    [[nodiscard]] bool dangerous_status() const {
        return cell_status == 'P' || cell_status == 'M' || cell_status == 'H' || cell_status == 'T';
    }
};

namespace std {
    template <> struct less<cell_ptr> {
        bool operator()(const cell_ptr& first, const cell_ptr& second) const {
            if (first->from_player_cost != second->from_player_cost)
                return first->from_player_cost < second->from_player_cost;

            if (first->n() != second->n())
                return first->n() < second->n();

            return first->m() < second->m();
        }
    };

    template <> struct hash<cell_ptr> {
        const std::hash<int> hasher;

        std::size_t operator() (const cell_ptr& cell) const noexcept {
            return hasher(cell->n()) * 31 + hasher(cell->m());
        }
    };
}

/**
 * @brief Initializes the game table with the specified coordinates for the Infinity Stone
 * @param inf_stone_n The row coordinate of the Infinity Stone
 * @param inf_stone_m The column coordinate of the Infinity Stone
 * @return The initialized game table.
 */

[[nodiscard]] auto init_game_table(const int inf_stone_n, const int inf_stone_m) {
    game_table table(TABLE_SIZE, game_table_row(TABLE_SIZE));

    for (int i = 0; i < table.size(); ++i)
        for (int q = 0; q < table[i].size(); ++q)
            table[i][q] = std::make_shared<cell>(cell(i, q));

    table[0][0] = std::make_shared<cell>(cell(0, 0, 'A', 0));

    table[inf_stone_n][inf_stone_m] = std::make_shared<cell>(
            cell(inf_stone_n, inf_stone_m, 'I')
    );

    return table;
}

/**
 * @brief Makes a move to the specified cell without the response analysis
 * @param pos The cell to move to
 */

void stupid_move(const cell_ptr& pos) {
    std::cout << "m " << pos->m() << ' ' << pos->n() << std::endl;

    int response_size = 0;
    std::cin >> response_size;

    while (response_size--) {
        int n = 0, m = 0;
        char status = 0;
        std::cin >> m >> n >> status;
    }
}

/**
 * Checks whether the coordinates are in game table's borders
 * @param n cell's row coordinate
 * @param m cell's column coordinate
 */

[[nodiscard]] bool in_borders(const int n, const int m) {
    return n >= 0 && n < TABLE_SIZE && m >= 0 && m < TABLE_SIZE;
}

/**
 * @brief Moves to the specified cell and updates the game state accordingly
 * @param pos The cell to move to
 * @param has_shield Indicates whether the player has a shield
 * @param table The game table
 * @param visited A set of cells that have been visited
 * @return True if the player has reached the Infinity Stone, false otherwise
 */

bool move_then_update(
        const cell_ptr& pos,
        bool& has_shield,
        game_table& table,
        restricted_cells& visited,
        const int thanos_mode
) {
    // Sends request to move
    std::cout << "m " << pos->m() << ' ' << pos->n() << std::endl;
    visited.insert(pos);

    // Picks shield if any
    if (pos->cell_status == 'S')
        has_shield = true;

    int response_size = 0;
    std::cin >> response_size;

    // Handles response and updates the game state with events from the response

    while (response_size--) {
        int n = 0, m = 0;
        char status = 0;
        std::cin >> m >> n >> status;
        table[n][m]->cell_status = status;

        /*if (table[n][m]->dangerous_status() && table[n][m]->possibly_picked_by.empty() && thanos_mode)
            table[n][m]->possibly_picked_by.insert({'H', 'M', 'T'});*/
    }

    // If we have reached the stone, report back
    if (pos->cell_status == 'I')
        return true;

    // Continue to explore unless stone is found
    return false;
}

/**
 * @brief Utilizes a backtracking depth-first search algorithm to find a path to the Infinity Stone.
 * Algorithm will explore the whole map, trying to reach every cell, if possible.
 * Algorithm considers cases where the shield was picked, meaning that it will analyze
 * if Infinity Stone can be reached with the shield (ignoring dangerous cells by Hulk and Thor).
 *
 * @param cur_pos The current cell position
 * @param has_shield Indicates whether the player has a shield
 * @param table The game table
 * @param visited A set of cells that have been visited
 * @param thanos_mode Thanos perception mode to learn about the world
 * @return True if a path to the Infinity Stone is found, false otherwise
 */

bool backtracking_dfs(
        const cell_ptr& cur_pos,
        bool& has_shield,
        game_table& table,
        restricted_cells& visited,
        const int thanos_mode
) {
    // Checks whether the stone is in the current position
    const bool has_solution = move_then_update(cur_pos, has_shield, table, visited, thanos_mode);

    // All possible neighbouring positions
    std::vector<std::pair<int, int>> next = {
            { cur_pos->n() - 1, cur_pos->m() },
            { cur_pos->n(), cur_pos->m() - 1 },
            { cur_pos->n(), cur_pos->m() + 1 },
            { cur_pos->n() + 1, cur_pos->m() }
    };

    // Picking all legal neighboring cells,
    // that were unvisited before and
    // that we may reach without any danger

    auto valid_neighbours = next
            | std::views::filter([](const auto& crds) {
                return in_borders(crds.first, crds.second);
            })
            | std::views::transform([&](const auto& crds) {
                return table[crds.first][crds.second];
            })
            | std::views::filter([&visited](const auto& cell) {
                return !cell->dangerous_status() && !visited.contains(cell);
            });

    // Checking if there is at least one possible way to reach the Infinity Stone
    bool has_solution_in_child = false;

    // Exploring all neighboring cells to find at least one possible way to reach the Infinity Stone
    std::ranges::for_each(valid_neighbours, [&](const auto& cell) {
        const auto res = backtracking_dfs(cell, has_shield, table, visited, thanos_mode);
        stupid_move(cur_pos);

        if (!has_solution_in_child)
            has_solution_in_child = res;
    });

    return has_solution || has_solution_in_child;
}

/**
 * @brief Utilizes a breadth-first search algorithm to construct the costs to find the Infinity Stone.
 * Algorithm updates the costs to reach every cell, until it finds the Infinity Stone.
 * Note that this procedure requires table to be up to date, meaning that user has to explore the map
 * with depth-first search before applying the function
 * @param table The game table
 */

void backtracking_bfs(const game_table& table) {
    // Queue of neighboring cells to visit
    std::queue<cell_ptr> q;

    // Previously visited cells
    restricted_cells visited;

    q.push(table[0][0]);

    while (!q.empty()) {
        // Picking the next cell to visit
        auto cur_pos = q.front(); q.pop();
        visited.insert(cur_pos);

        // All possible neighbouring positions
        std::vector<std::pair<int, int>> next = {
                { cur_pos->n() - 1, cur_pos->m() },
                { cur_pos->n(), cur_pos->m() - 1 },
                { cur_pos->n(), cur_pos->m() + 1 },
                { cur_pos->n() + 1, cur_pos->m() }
        };

        // Picking all legal neighboring cells, that were unvisited before and that we may reach without any danger.
        // At the last step, we are incrementing the cost from the initial position of the player,
        // and pushing the neighboring cell to the queue in order to move to its neighbors in the next iterations

        auto valid_neighbours = next
                | std::views::filter([](const auto& crds) {
                    return in_borders(crds.first, crds.second);
                })
                | std::views::transform([&](const auto& crds) {
                    return table[crds.first][crds.second];
                })
                | std::views::filter([&visited](const auto& cell) {
                    return !cell->dangerous_status() && !visited.contains(cell);
                })
                | std::views::transform([&](auto cell) {
                    cell->from_player_cost = cur_pos->from_player_cost + 1;
                    q.push(cell);
                    return cell;
                });

        // Moving through neighboring cells until we find the one with the Infinity Stone
        const bool is_stone_found = std::ranges::any_of(
                valid_neighbours,
                [&](auto cell) { return cell->cell_status == 'I'; }
        );

        if (is_stone_found)
            return;
    }
}

/**
 * @brief Attempts to find a path to the Infinity Stone using backtracking DFS and BFS algorithms
 * @param table The game table
 * @param thanos_mode Thanos perception mode to learn about the world
 * @return True if a path to the Infinity Stone is found, false otherwise
 */

bool launch_backtracking(game_table& table, const int thanos_mode) {
    bool has_shield = false;
    restricted_cells visited;

    const auto has_solution = backtracking_dfs(table[0][0], has_shield, table, visited, thanos_mode);
    if (!has_solution) return false;

    backtracking_bfs(table);
    return true;
}

int main() {
    std::ios_base::sync_with_stdio(false);
    std::cin.tie(nullptr);

    int thanos_perception_variant = 0;
    std::cin >> thanos_perception_variant;

    int inf_stone_n = 0, inf_stone_m = 0;
    std::cin >> inf_stone_m >> inf_stone_n;

    auto table = init_game_table(inf_stone_n, inf_stone_m);

    if (!launch_backtracking(table, thanos_perception_variant)) {
        std::cout << "e -1" << std::endl;
        return 0;
    }

    std::cout << "e " << table[inf_stone_n][inf_stone_m]->from_player_cost << std::endl;
    return 0;
}
