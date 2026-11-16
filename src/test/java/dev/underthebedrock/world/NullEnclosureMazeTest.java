package dev.underthebedrock.world;

import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import static org.junit.jupiter.api.Assertions.*;

class NullEnclosureMazeTest {
    @Test void layoutsAreDeterministicAndConnectedFromSouthToNorth() {
        for (int cells : new int[]{7, 8}) {
            char[][] first = NullEnclosureManager.Maze.generate(cells, 0x4E554C4CL + cells);
            char[][] second = NullEnclosureManager.Maze.generate(cells, 0x4E554C4CL + cells);
            assertEquals(first.length, second.length);
            for (int row = 0; row < first.length; row++) assertArrayEquals(first[row], second[row]);
            int centre = (cells / 2) * 2 + 1;
            assertTrue(reachable(first, first.length - 1, centre, 0, centre));
        }
    }

    @Test void mazeKeepsSolidOuterBoundaryExceptItsTwoPassages() {
        char[][] grid = NullEnclosureManager.Maze.generate(7, 42L);
        for (int x = 0; x < grid.length; x++) {
            if (x != 7) {
                assertEquals('#', grid[0][x]);
                assertEquals('#', grid[grid.length - 1][x]);
            }
        }
        assertEquals('.', grid[0][7]);
        assertEquals('.', grid[grid.length - 1][7]);
    }

    private boolean reachable(char[][] grid, int startRow, int startCol, int endRow, int endCol) {
        boolean[][] seen = new boolean[grid.length][grid.length];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startRow, startCol});
        seen[startRow][startCol] = true;
        int[][] directions = {{1,0},{-1,0},{0,1},{0,-1}};
        while (!queue.isEmpty()) {
            int[] current = queue.removeFirst();
            if (current[0] == endRow && current[1] == endCol) return true;
            for (int[] direction : directions) {
                int row = current[0] + direction[0], col = current[1] + direction[1];
                if (row < 0 || col < 0 || row >= grid.length || col >= grid.length
                        || seen[row][col] || grid[row][col] == '#') continue;
                seen[row][col] = true;
                queue.addLast(new int[]{row, col});
            }
        }
        return false;
    }
}
