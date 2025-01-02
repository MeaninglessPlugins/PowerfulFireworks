import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;

import java.nio.file.Files;
import java.nio.file.Path;

public class TestBDFLoader {
    public static void main(String[] args) throws Throwable {
        BitmapFont bitmap = BitmapFont.parseBDF(Files.readString(Path.of("test.bdf")));
        BitmapFont.CharBitmap charArray = bitmap.getCharacter('P');
        for (String row : charArray.ch) {
            System.out.println(row.replace('0',' '));
        }
        System.out.println();
        BitmapFont.CharBitmap string = bitmap.fromString("Minecraft", 2);
        for (String row : string.ch) {
            System.out.println(row.replace('0',' '));
        }
    }
}
