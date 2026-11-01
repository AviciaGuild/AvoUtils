package info.avicia.avoutils.core.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2fStack;

/**
 * Crisp half-pixel UI icons shared across AvoUtils screens, modals, and panels.
 *
 * <p>Minecraft's bitmap fonts render symbols like {@code ↻}, {@code ✓}, and {@code ✕} at awkward sizes or off-center
 * baselines. Drawing them at half-GUI-pixel resolution gives sharp, consistent icons inside any button or row.
 */
public final class UiIcons {

    public static final Icon REFRESH = Icon.of(
            ".........#....",
            ".........##...",
            ".....#######..",
            "....#....##...",
            "...#.....#....",
            "..#...........",
            "..#...........",
            "..#.........#.",
            "..#.........#.",
            "..#.........#.",
            "...#.......#..",
            "....#.....#...",
            ".....#####....",
            "..............");

    public static final Icon MINUS = Icon.of(
            "########",
            "########");

    public static final Icon PLUS = Icon.of(
            "....##....",
            "....##....",
            "....##....",
            "....##....",
            "##########",
            "##########",
            "....##....",
            "....##....",
            "....##....",
            "....##....");

    public static final Icon CHECK = Icon.of(
            "..........##",
            ".........##.",
            "........##..",
            ".......##...",
            "##....##....",
            ".##..##.....",
            "..####......",
            "...##.......");

    public static final Icon CROSS = Icon.of(
            "##....##",
            ".##..##.",
            "..####..",
            "...##...",
            "...##...",
            "..####..",
            ".##..##.",
            "##....##");

    /** Equilateral right-pointing play/expand triangle with a 1-half-pixel left offset for optical centering. */
    public static final Icon PLAY = Icon.of(
            ".#......",
            ".###....",
            ".####...",
            ".######.",
            ".#######",
            ".######.",
            ".####...",
            ".###....",
            ".#......");

    /** Equilateral downward-pointing triangle for expanded section headers. */
    public static final Icon EXPANDED = Icon.of(
            "#########",
            ".#######.",
            ".#######.",
            "..#####..",
            "...###...",
            "...###...",
            "....#....");

    private UiIcons() {}

    public static final class Icon {
        private record Run(int row, int startCol, int endCol) {}

        private final int halfWidth;
        private final int halfHeight;
        private final List<Run> runs;

        private Icon(int halfWidth, int halfHeight, List<Run> runs) {
            this.halfWidth = halfWidth;
            this.halfHeight = halfHeight;
            this.runs = runs;
        }

        static Icon of(String... rows) {
            int width = rows[0].length();
            List<Run> runs = new ArrayList<>();
            for (int row = 0; row < rows.length; row++) {
                String line = rows[row];
                int col = 0;
                while (col < line.length()) {
                    if (line.charAt(col) != '#') {
                        col++;
                        continue;
                    }
                    int end = col;
                    while (end < line.length() && line.charAt(end) == '#') {
                        end++;
                    }
                    runs.add(new Run(row, col, end));
                    col = end;
                }
            }
            return new Icon(width, rows.length, List.copyOf(runs));
        }

        /** Natural width in GUI pixels (rounded up). */
        public int widthPx() {
            return (halfWidth + 1) / 2;
        }

        /** Natural height in GUI pixels (rounded up). */
        public int heightPx() {
            return (halfHeight + 1) / 2;
        }

        /** Draws the icon centered inside the GUI-pixel box {@code (x, y, width, height)}. */
        public void draw(GuiGraphics graphics, int x, int y, int width, int height, int color) {
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(0.5f, 0.5f);
            int left = (2 * width - halfWidth) / 2;
            int top = (2 * height - halfHeight) / 2;
            for (Run run : runs) {
                graphics.fill(left + run.startCol(), top + run.row(), left + run.endCol(), top + run.row() + 1, color);
            }
            pose.popMatrix();
        }
    }
}

