import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;

import java.nio.file.Files;
import java.nio.file.Path;

public class TestBDFLoader {
    public static void main(String[] args) throws Throwable {
        BitmapFont bitmap = BitmapFont.parseBDF(Files.readString(Path.of("test.bdf")));
        BitmapFont.CharBitmap charArray = bitmap.getCharacter('中');
        for (String row : charArray.getChars()) {
            System.out.println(row.replace('0',' ').replace('1', '#'));
        }
        System.out.println();
        BitmapFont.CharBitmap string = bitmap.fromString("Minecraft", 2);
        for (String row : string.getChars()) {
            System.out.println(row.replace('0',' ').replace('1', '#'));
        }
    }
}
