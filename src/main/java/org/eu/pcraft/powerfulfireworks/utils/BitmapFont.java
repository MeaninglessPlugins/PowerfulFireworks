package org.eu.pcraft.powerfulfireworks.utils;

import lombok.AllArgsConstructor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

import static java.lang.Math.max;

@AllArgsConstructor
public class BitmapFont {

    public record CharBitmap(String[] chars) {
            CharBitmap(int length) {
                this(new String[length]);
                Arrays.fill(this.chars, "");
            }
        }

    private final Map<Character, CharBitmap> characters;
    private final int charHeight;

    public CharBitmap getCharacter(char character) {
//        System.out.println(Arrays.toString(characters.get(character).ch));
        return characters.getOrDefault(character, new CharBitmap(charHeight));
    }

    public CharBitmap fromString(String text, int gap) {
        if (text == null || text.isEmpty()) {
            return new CharBitmap(0);
        }

        // create gap string
        String gapStr = "0".repeat(Math.max(1, gap));

        StringBuilder[] builders = new StringBuilder[charHeight];

        // Second pass: copy characters
        for (char c : text.toCharArray()) {
            CharBitmap charBitmap = this.getCharacter(c);
            if (charBitmap != null && charBitmap.chars.length > 0) {
                for (int i = 0; i < charHeight; i++) {
                    StringBuilder sb = builders[i];
                    if (sb == null) {
                        sb = new StringBuilder();
                        builders[i] = sb;
                    }
                    sb.append(charBitmap.chars[i]).append(gapStr);
                }
            }
        }

        CharBitmap result = new CharBitmap(charHeight);
        for (int i = 0; i < builders.length; i++) {
            result.chars[i] = builders[i].toString();
        }

        return result;
    }

    public static BitmapFont parseBDF(String text) throws IOException {
        int maxCurrentRow = 0;
        Map<Character, CharBitmap> fontMap = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            Character currentChar = null;
            CharBitmap currentBitmap = null;
            int bitmapWidth = 0;
            int bitmapHeight = 0;
            int currentRow = 0;
            boolean inBitmap = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Parse character encoding
                if (line.startsWith("ENCODING")) {
                    int charCode = Integer.parseInt(line.split("\\s+")[1]);
                    currentChar = (char) charCode;
                }
                // Parse bitmap dimensions
                else if (line.startsWith("BBX")) {
                    String[] parts = line.split("\\s+");
                    bitmapWidth = Integer.parseInt(parts[1]);
                    bitmapHeight = Integer.parseInt(parts[2]);
                    currentBitmap = new CharBitmap(bitmapHeight);
                }
                // Start of bitmap data
                else if (line.equals("BITMAP")) {
                    inBitmap = true;
                    currentRow = 0;
                }
                // End of bitmap data
                else if (line.equals("ENDCHAR")) {
                    if (currentChar != null && currentBitmap != null) {
                        fontMap.put(currentChar, currentBitmap);
                    }
                    inBitmap = false;
                    currentChar = null;
                    currentBitmap = null;
                }
                // Parse bitmap data
                else if (inBitmap && currentBitmap != null && currentRow < bitmapHeight) {
                    // Convert hex string to binary representation
                    currentBitmap.chars[currentRow] = hexToBinary(line, bitmapWidth);
                    maxCurrentRow = max(currentRow,maxCurrentRow);
                    currentRow++;
                }
            }
        }

        // reformat fonts
        for (Map.Entry<Character, CharBitmap> ent : fontMap.entrySet()) {
            CharBitmap value = ent.getValue();
            if (value.chars.length > 0 && value.chars.length < maxCurrentRow) {   // pad header
                String[] reformatted = new String[maxCurrentRow];
                int width = value.chars[0].length();
                System.arraycopy(value.chars, 0, reformatted, maxCurrentRow - value.chars.length, value.chars.length);
                Arrays.fill(reformatted, 0, maxCurrentRow - value.chars.length, "0".repeat(width));
                ent.setValue(new CharBitmap(reformatted));
            }
        }

        return new BitmapFont(fontMap, maxCurrentRow);
    }

    private static String hexToBinary(String hex, int width) {
        // Convert hex string to binary string
        StringBuilder binary = new StringBuilder();
        for (char c : hex.toCharArray()) {
            StringBuilder bin = new StringBuilder(Integer.toBinaryString(Integer.parseInt(String.valueOf(c), 16)));
            // Pad with leading zeros
            while (bin.length() < 4) {
                bin.insert(0, "0");
            }
            binary.append(bin);
        }

        // Trim or pad to match the required width
        if (binary.length() > width) {
            return binary.substring(0, width);
        } else {
            while (binary.length() < width) {
                binary.append("0");
            }
            return binary.toString();
        }
    }
}
