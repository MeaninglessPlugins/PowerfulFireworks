import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;

import java.nio.file.Files;
import java.nio.file.Path;

public class TestBDFLoader {
    public static void main(String[] args) throws Throwable {
        BitmapFont bitmap = BitmapFont.parseBDF(Files.readString(Path.of("test.bdf")));
        int[][] charArray = bitmap.getCharacter('中');
        for (int[] row : charArray) {
            for (int pixel : row) {
                System.out.print(pixel == 1 ? "#" : " ");
            }
            System.out.println();
        }

        int[][] string = bitmap.fromString("Minecraft", 2);
        for (int[] row : string) {
            for (int pixel : row) {
                System.out.print(pixel == 1 ? "#" : " ");
            }
            System.out.println();
        }
    }
}
