#include <iostream>
#include <ranges>
#include <vector>
#include <cstdint>
#include <set>
#include <memory>
#include <algorithm>
#include <unordered_set>

const int INF = INT16_MAX;
const int TABLE_SIZE = 9;

struct cell;

using cell_ptr = std::shared_ptr<cell>;
using game_table_row = std::vector<cell_ptr>;
using game_table = std::vector<game_table_row>;
using cell_priority_queue = std::set<cell_ptr>;
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

    /** Manhattan distance to the Infinity Stone */
    int to_target_cost;

    /** Event of the cell (perception, picked by hero, etc.) */
    char cell_status;

    /** Parent cell to reconstruct the path */
    cell_ptr parent;

    /** List of possible heroes who occupied this cell */
    std::unordered_set<char> possibly_picked_by;

    /**
     * Constructs a cell object with the specified coordinates, status, and cost
     * @param n The row coordinate of the cell
     * @param m The column coordinate of the cell
     * @param from_player_cost The cost to move from the player's initial position
     * @param to_target_cost The cost to move to the Infinity Stone (Manhattan distance)
     * @param cell_status Event of the cell (perception, picked by hero, etc.)
     */

    explicit cell(
            const int n = 0,
            const int m = 0,
            const int from_player_cost = INF,
            const int to_target_cost = INF,
            const char cell_status = 0,
            cell_ptr parent = nullptr
    ) : _n(n), _m(m),
        from_player_cost(from_player_cost),
        to_target_cost(to_target_cost),
        cell_status(cell_status),
        parent(std::move(parent)) {}

    ~cell() = default;

    /** The row coordinate of the cell */
    [[nodiscard]] int n() const { return _n; }

    /** The column coordinate of the cell */
    [[nodiscard]] int m() const { return _m; }

    /** Heuristics function to use for the priority queue in A* algorithm */
    [[nodiscard]] int sum_cost() const { return from_player_cost + to_target_cost; }

    /**
     * Checks whether the cell is the neighbour of the current
     * @param other Cell to check
     * @return true if both cells are neighbours, false otherwise
     */

    [[nodiscard]] bool neighbour(const cell_ptr& other) const {
        return std::abs(_n - other->_n) + std::abs(_m - other->_m) < 2;
    }

    /**
     * Checks whether the cell is dangerous to move
     * @return true if the cell is dangerous, false otherwise.
     */

    [[nodiscard]] bool dangerous_status() const {
        return cell_status == 'P' || cell_status == 'M' || cell_status == 'H' || cell_status == 'T';
    }

    /**
     * Constructs path for the given cell.
     * Path includes all cells from initial player position (0, 0) to the given one.
     * Note that the path is optimal only for the current state of the game.
     * Result is the local best, however, it may be better after other steps of
     * algorithm are launched and analysed.
     *
     * @param c Cell for which path should be built
     * @return constructed reversed path, stored in the vector
     * (e.g. [cell, cell.parent, cell.parent.parent, ... initial cell])
     */

    [[nodiscard]] static std::vector<cell_ptr> path(const cell_ptr& c) {
        std::vector<cell_ptr> path;

        for (auto cl = c; cl; cl = cl->parent)
            path.push_back(cl);

        return path;
    }
};

// Template specializations for the std::shared_ptr<cell>

namespace std {
    template <> struct less<cell_ptr> {
        bool operator()(const cell_ptr& first, const cell_ptr& second) const {
            if (first->sum_cost() != second->sum_cost())
                return first->sum_cost() < second->sum_cost();

            if (first->to_target_cost != second->to_target_cost)
                return first->to_target_cost < second->to_target_cost;

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
 * @brief Calculates the Manhattan distance between two cells on the game table
 * @param from_n The row coordinate of the starting cell
 * @param from_m The column coordinate of the starting cell
 * @param to_n The row coordinate of the destination cell
 * @param to_m The column coordinate of the destination cell
 * @return The Manhattan distance between the two cells
 */

[[nodiscard]] int manhattan_distance(
        const int from_n,
        const int from_m,
        const int to_n,
        const int to_m
) {
    const int delta_n = std::abs(from_n - to_n);
    const int delta_m = std::abs(from_m - to_m);
    return delta_n + delta_m;
}

/**
 * Constructs path for the given cell.
 * Path includes all cells from initial player position (0, 0) to the given one.
 * Note that the path is optimal only for the current state of the game.
 * Result is the local best, however, it may be better after other steps of
 * algorithm are launched and analysed.
 *
 * @param c Cell for which path should be built
 * @return constructed path, stored in the hash set
 * (e.g. {cell, cell.parent, cell.parent.parent, ... initial cell})
 */

[[nodiscard]] auto cell_path_as_set(const cell_ptr& c) {
    std::unordered_set<cell_ptr> path = { c };

    for (auto cl = c->parent; cl; cl = cl->parent)
        path.insert(cl);

    return path;
}

/**
 * @brief Initializes the game table with the specified coordinates for the Infinity Stone
 * @param inf_stone_n The row coordinate of the Infinity Stone
 * @param inf_stone_m The column coordinate of the Infinity Stone
 * @return The initialized game table.
 */

[[nodiscard]] game_table init_game_table(const int inf_stone_n, const int inf_stone_m) {
    // Creating game table
    game_table table(TABLE_SIZE, game_table_row(TABLE_SIZE));

    // Initializing all cells with their positions.
    // Setting all statistical parameters to INF

    for (int i = 0; i < table.size(); ++i)
        for (int q = 0; q < table[i].size(); ++q)
            table[i][q] = std::make_shared<cell>(cell(i, q));

    // Initializing the initial player coordinate (0, 0)
    table[0][0] = std::make_shared<cell>(
            cell(0, 0, 0, manhattan_distance(0, 0, inf_stone_n, inf_stone_m), 'A')
    );

    // Initializing the Infinity Stone coordinate
    table[inf_stone_n][inf_stone_m] = std::make_shared<cell>(
            cell(inf_stone_n, inf_stone_m, INF, 0, 'I')
    );

    return table;
}

/**
 * @brief Makes a move to the specified cell without the response analysis
 * @param cur_pos Current player's position
 * @param c The cell to move to
 */

void stupid_move(cell_ptr& cur_pos, const cell_ptr& c) {
    std::cout << "m " << c->m() << ' ' << c->n() << std::endl;
    cur_pos = c;

    int response_size = 0;
    std::cin >> response_size;

    while (response_size--) {
        int n = 0, m = 0;
        char status = 0;
        std::cin >> m >> n >> status;
    }
}

/**
 * Performs simple moves without the response analysis,
 * until it reaches the initial cell
 *
 * @param cur_pos current position, that will be mutated,
 * until the initial position is reached
 */

void return_to_start(cell_ptr& cur_pos) {
    for (;;) {
        if (!cur_pos->parent) return;
        stupid_move(cur_pos, cur_pos->parent);
    }
}

/**
 * Searches for the least common ancestor of both cells.
 * Utilizes paths of both cells, so it is required that parent nodes are valid.
 * Algorithm constructs vectored cell for the first one and hash set-based path for the second one.
 * Starting from the last one, it scans the whole path of the first cell to find any cell
 * that is located in the hash set-based path of the second one.
 *
 * @param first First cell, from which vectored path will be constructed
 * @param second Second cell, from which hash set-based path will be constructed.
 * @return std::shared_ptr with LCA cell if it is found, std::shared_ptr(nullptr) otherwise
 */

[[nodiscard]] auto least_common_ancestor(const cell_ptr& first, const cell_ptr& second) {
    auto first_path = cell::path(first);
    auto second_path = cell_path_as_set(second);

    for (const auto& c : std::ranges::reverse_view(first_path))
        if (second_path.contains(c))
            return c;

    return std::shared_ptr<cell>(nullptr);
}

/**
 * Performs simple moves without the response analysis,
 * until it reaches the LCA cell
 *
 * @param cur_pos current position, that will be mutated,
 * until the initial position is reached
 */

void return_to_lca(cell_ptr& cur_pos, const cell_ptr& target) {
    auto lca = least_common_ancestor(cur_pos, target);

    for (;;) {
        if (cur_pos == lca) return;
        stupid_move(cur_pos, cur_pos->parent);
    }
}

/**
 * Performs simple moves without the response analysis,
 * until it reaches the target position.
 * If path contains cell with the shield, we pick it.
 * Procedure considers that this cell was visited before,
 * and it is possible to construct the path with previously gained knowledge.
 * Note that the path is optimal only for the current state of the game.
 * Result is the local best, however, it may be better after other steps of
 * algorithm are launched and analysed.
 *
 * @param cur_pos current position, that will be mutated,
 * until the target position is reached
 * @param target position to move to
 */

void stupid_move_to_known_target(cell_ptr& cur_pos, const cell_ptr& target, bool& has_shield) {
    auto path = cell::path(target);

    for (auto it = std::next(path.rbegin()); it != path.rend(); ++it) {
        stupid_move(cur_pos, *it);

        if ((*it)->cell_status == 'S')
            has_shield = true;
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
 * @brief Opens neighbouring cells and updates their states
 * @param cur_pos Current player position
 * @param inf_stone_n The row coordinate of the Infinity Stone
 * @param inf_stone_m The column coordinate of the Infinity Stone
 * @param table The game table (updated after the algorithm)
 * @param open Priority queue for the A* algorithm (updated after the algorithm)
 */

void open_neighbours(
        const cell_ptr& cur_pos,
        const int inf_stone_n,
        const int inf_stone_m,
        game_table& table,
        cell_priority_queue& open
) {
    // Current coordinates
    const int n = cur_pos->n();
    const int m = cur_pos->m();

    auto update_then_check_cell = [&](const int cn, const int cm) {
        // Updating all valid cells that can be moved,
        // even visited ones to reuse in the future, after the shield is picked

        if (in_borders(cn, cm) && !table[cn][cm]->dangerous_status()) {
            const int new_from_player_cost = cur_pos->from_player_cost + 1;
            const int new_to_target_cost = manhattan_distance(cn, cm, inf_stone_n, inf_stone_m);

            // If we found a better path, we can update both table and open PQ
            if (new_from_player_cost < table[cn][cm]->from_player_cost) {
                open.erase(table[cn][cm]);
                table[cn][cm]->from_player_cost = new_from_player_cost;
                table[cn][cm]->to_target_cost = new_to_target_cost;
                table[cn][cm]->parent = cur_pos;
                open.insert(table[cn][cm]);
            }
        }
    };

    // Trying to update all possible neighboring cells
    update_then_check_cell(n - 1, m);
    update_then_check_cell(n + 1, m);
    update_then_check_cell(n, m - 1);
    update_then_check_cell(n, m + 1);
}

/**
 * @brief Moves to the specified cell and updates the game state accordingly
 * @oaram cur_pos Current player position
 * @param new_pos The cell to move to
 * @param inf_stone_n The row coordinate of the Infinity Stone
 * @param inf_stone_m The column coordinate of the Infinity Stone
 * @param has_shield Indicates whether the player has a shield
 * @param table The game table
 * @param open A priority queue of cells to explore, sorted by their estimated total cost
 * @param closed A set of cells that should not be reached in the current iteration
 * @param thanos_mode Thanos perception mode to learn about the world
 * @return True if the player has reached the Infinity Stone, false otherwise
 */

bool move_then_update(
        cell_ptr& cur_pos,
        const cell_ptr& new_pos,
        const int inf_stone_n,
        const int inf_stone_m,
        bool& has_shield,
        game_table& table,
        cell_priority_queue& open,
        restricted_cells& closed,
        const int thanos_mode
) {
    // Sends request to move
    std::cout << "m " << new_pos->m() << ' ' << new_pos->n() << std::endl;

    // We are done and not interested in the response
    if (new_pos->cell_status == 'I')
        return true;

    cur_pos = new_pos;
    closed.insert(cur_pos);

    // Picking shield if any
    if (cur_pos->cell_status == 'S')
        has_shield = true;

    int response_size = 0;
    std::cin >> response_size;

    // Handles response and updates the game state with events from the response

    while (response_size--) {
        int n = 0, m = 0;
        char status = 0;
        std::cin >> m >> n >> status;
        table[n][m]->cell_status = status;

        if (table[n][m]->dangerous_status())
            closed.insert(table[n][m]);

        if (table[n][m]->dangerous_status() && table[n][m]->possibly_picked_by.empty() && thanos_mode)
            table[n][m]->possibly_picked_by.insert({'H', 'M', 'T'});
    }

    open_neighbours(cur_pos, inf_stone_n, inf_stone_m, table, open);
    return false;
}

/**
 * @brief Attempts to find a path to the Infinity Stone using an A* search algorithm
 * @param inf_stone_n The row coordinate of the Infinity Stone
 * @param inf_stone_m The column coordinate of the Infinity Stone
 * @param has_shield Indicates whether the player picked the shield
 * @param table The game table
 * @param open A priority queue of cells to explore, sorted by their estimated total cost
 * @param closed A set of cells that have been explored and their paths have been evaluated.
 * @param thanos_mode Indicates whether to use the Thanos mode, which modifies the heuristics.
 * @return True if a path to the Infinity Stone is found, false otherwise.
 */

bool launch_a_star(
        const int inf_stone_n,
        const int inf_stone_m,
        bool& has_shield,
        game_table& table,
        cell_priority_queue& open,
        restricted_cells& closed,
        const int thanos_mode
) {
    // Initializing the current position to the initial cell
    auto cur_pos = table[0][0];

    // Continue the search as long as the open queue is not empty

    while (!open.empty()) {
        // Find the cell with the lowest estimated total cost from the open queue
        cell_ptr best;

        // Iterate over the open queue until a cell is found that is not in the closed set

        for (;;) {
            best = *open.begin();
            open.erase(open.begin());

            // Skip cells that have already been explored and their paths have been evaluated.
            if (!closed.contains(best))
                break;
        }

        // If the best position is not the neighbouring one,
        // We have to return to the start and moves to its parent
        // that was previously visited during the steps of the A* algorithm

        if (!cur_pos->neighbour(best)) {
            // Moving to the start
            return_to_start(cur_pos);

            // Resetting shield state
            has_shield = false;

            // Moving to the parent of the best
            stupid_move_to_known_target(cur_pos, best->parent, has_shield);
        }

        // If stone is found in the best cell,
        // Reporting the success and stopping the algorithm

        const bool is_stone_found = move_then_update(
                cur_pos, best,
                inf_stone_n, inf_stone_m,
                has_shield, table,
                open, closed,
                thanos_mode
        );

        if (is_stone_found)
            return true;
    }

    return false;
}

int main() {
    std::ios_base::sync_with_stdio(false);
    std::cin.tie(nullptr);

    int thanos_perception_variant = 0;
    std::cin >> thanos_perception_variant;

    int inf_stone_n = 0, inf_stone_m = 0;
    std::cin >> inf_stone_m >> inf_stone_n;

    auto table = init_game_table(inf_stone_n, inf_stone_m);

    cell_priority_queue open;
    open.insert(table[0][0]);

    restricted_cells closed;
    bool has_shield = false;

    if (!launch_a_star(inf_stone_n, inf_stone_m, has_shield, table, open, closed, thanos_perception_variant)) {
        std::cout << "e -1" << std::endl;
        return 0;
    }

    std::cout << "e " << table[inf_stone_n][inf_stone_m]->from_player_cost << std::endl;
    return 0;
}
